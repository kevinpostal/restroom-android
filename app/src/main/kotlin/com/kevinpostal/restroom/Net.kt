package com.kevinpostal.restroom

import android.content.Context
import android.location.Geocoder
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/// Raw bytes from the three data sources; parsing happens in the C++ core.
interface DataSource {
    /// Refuge nearest-N page. Throws IOException with the user-visible message on failure.
    suspend fun refuge(lat: Double, lon: Double, perPage: Int = 100): ByteArray
    /// Overpass parks/campgrounds within 1.6 km; null on any failure (bonus data).
    suspend fun overpass(lat: Double, lon: Double): ByteArray?
    /// PottyPins dump; null on failure (codes are a bonus).
    suspend fun pottyPins(): ByteArray?
    /// Forward geocode; null when nothing matches.
    suspend fun geocode(query: String): Pair<String, LatLon>?
}

class Net(private val context: Context) : DataSource {
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 2 })
        .build()

    private fun OkHttpClient.with(timeoutSec: Long) =
        newBuilder().callTimeout(timeoutSec, TimeUnit.SECONDS).build()

    override suspend fun refuge(lat: Double, lon: Double, perPage: Int): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.refugerestrooms.org/api/v1/restrooms/by_location?lat=$lat&lng=$lon&per_page=$perPage")
            .header("Accept", "application/json")
            .build()
        try {
            client.with(15).newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Server returned ${res.code}")
                res.body?.bytes() ?: throw IOException("Couldn't read restroom data")
            }
        } catch (e: UnknownHostException) {
            throw IOException("You're offline")
        } catch (e: ConnectException) {
            throw IOException("You're offline")
        }
    }

    override suspend fun overpass(lat: Double, lon: Double): ByteArray? = withContext(Dispatchers.IO) {
        val query = "[out:json][timeout:10];(nwr[\"leisure\"=\"park\"](around:1600,$lat,$lon);" +
            "nwr[\"tourism\"=\"camp_site\"](around:1600,$lat,$lon););out center 50;"
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(FormBody.Builder().add("data", query).build())
            .build()
        runCatching {
            client.with(12).newCall(req).execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
        }.getOrNull()
    }

    override suspend fun pottyPins(): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "pins.json")
        runCatching {
            val saved = JSONObject(file.readText())
            if (nowSec() - saved.getLong("at") < PINS_TTL_SEC) return@withContext saved.getString("body").toByteArray()
        }
        val req = Request.Builder().url("https://pottypins.com/api/posts").header("Accept", "application/json").build()
        val body = runCatching {
            client.with(10).newCall(req).execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
        }.getOrNull() ?: return@withContext null
        runCatching {
            val tmp = File(context.cacheDir, "pins.json.tmp")
            tmp.writeText(JSONObject().put("at", nowSec()).put("body", body.decodeToString()).toString())
            tmp.renameTo(file)
        }
        body
    }

    @Suppress("DEPRECATION")
    override suspend fun geocode(query: String): Pair<String, LatLon>? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val hit = runCatching { Geocoder(context).getFromLocationName(query, 1)?.firstOrNull() }.getOrNull()
            ?: return@withContext null
        val name = hit.featureName?.takeIf { it.isNotBlank() } ?: hit.locality ?: query
        name to LatLon(hit.latitude, hit.longitude)
    }

    companion object {
        const val PINS_TTL_SEC = 24L * 60 * 60
    }
}
