package com.kevinpostal.restroom

import android.content.Intent
import kotlinx.coroutines.delay
import java.io.IOException

/// Deterministic seams for the instrumented tests. Active only when the launch intent carries `uitest=true`.
class UiTestMode(intent: Intent?) {
    val active = intent?.getBooleanExtra("uitest", false) == true
    val fail = intent?.getBooleanExtra("uitest_fail", false) == true
    val empty = intent?.getBooleanExtra("uitest_empty", false) == true
    val slow = intent?.getBooleanExtra("uitest_slow", false) == true
    val noPlace = intent?.getBooleanExtra("uitest_noplace", false) == true
    val denied = intent?.getBooleanExtra("uitest_denied", false) == true

    companion object {
        /// Apple Park; the core ranks fixtures by real distance from here (0.18, 0.27, 0.31, 0.38 mi).
        val start = LatLon(37.3349, -122.0090)
    }
}

/// Refuge-, Overpass- and PottyPins-shaped JSON for the four Apple Park fixtures plus Memorial Park.
class FixtureNet(private val mode: UiTestMode) : DataSource {
    override suspend fun refuge(lat: Double, lon: Double, perPage: Int): ByteArray {
        if (mode.fail) throw IOException("You're offline")
        if (mode.slow) delay(3_000)   // lets tests see the loading ring
        if (mode.empty) return "[]".toByteArray()
        return """[
          {"id":1,"name":"Happy Lemon","street":"10963 N Wolfe Road","city":"Cupertino","state":"CA",
           "accessible":true,"latitude":37.3372,"longitude":-122.0075},
          {"id":2,"name":"Kaiser Hospital","street":"10992 N De Anza Blvd","city":"Cupertino","state":"CA",
           "accessible":true,"unisex":true,"comment":"Customers only. Ask at the counter for the code.",
           "latitude":37.3335,"longitude":-122.0045},
          {"id":3,"name":"Library","street":"10800 Torre Ave","city":"Cupertino","state":"CA",
           "unisex":true,"changing_table":true,"latitude":37.3380,"longitude":-122.0050},
          {"id":4,"name":"Park Kiosk","city":"Cupertino","state":"CA","latitude":37.3310,"longitude":-122.0140}
        ]""".toByteArray()
    }

    override suspend fun overpass(lat: Double, lon: Double): ByteArray? {
        if (mode.empty) return null
        return """{"elements":[{"type":"node","id":5,"lat":37.3300,"lon":-122.0150,
          "tags":{"leisure":"park","name":"Memorial Park","addr:street":"21121 Stevens Creek Blvd",
          "addr:city":"Cupertino","addr:state":"CA"}}]}""".toByteArray()
    }

    /// Exactly Happy Lemon's coordinate (0 m match); nothing near the other fixtures.
    override suspend fun pottyPins(): ByteArray =
        """[{"id":1,"name":"Happy Lemon","latitude":37.3372,"longitude":-122.0075,
             "restrooms":[{"id":1,"name":"Unisex","pin":"2580"}]}]""".toByteArray()

    override suspend fun geocode(query: String): Pair<String, LatLon>? =
        if (mode.noPlace) null else "Union Square" to LatLon(37.7879, -122.4075)
}
