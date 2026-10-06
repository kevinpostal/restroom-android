package com.kevinpostal.restroom

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/// One place as published by the C++ core: stored fields plus the derived display text.
@Serializable
data class Place(
    val id: Long,
    val name: String,
    val street: String,
    val city: String,
    val state: String,
    val directions: String,
    val comment: String,
    val accessible: Boolean,
    val unisex: Boolean,
    val changingTable: Boolean,
    val lat: Double,
    val lon: Double,
    val upvote: Int,
    val downvote: Int,
    val distanceMiles: Double?,
    val kind: String,
    val pin: String?,
    val accessTitle: String?,
    val accessSpoken: String?,
    val accessCode: String?,
    val kindTag: String?,
    val addressLine: String,
    val distanceText: String?,
    val distanceSpoken: String?,
) {
    val isRestroom: Boolean get() = kind == "restroom"
    val amenities: List<String>
        get() = buildList {
            if (accessible) add("Accessible")
            if (unisex) add("Unisex")
            if (changingTable) add("Changing table")
        }
}

/// JNI surface over `restroom::Core` (librestroomjni.so). Every decision — parsing, caching, ranking,
/// pin matching — happens in C++; Kotlin passes bytes in and decodes the published list.
object Core {
    init { System.loadLibrary("restroomjni") }

    private val json = Json { ignoreUnknownKeys = true }

    external fun init(cachePath: String, nowSec: Long)
    external fun setPins(pottyPinsJson: ByteArray): Int
    external fun pinsCached(): Int
    external fun store(x: Int, y: Int, refugeJson: ByteArray, overpassJson: ByteArray?, nowSec: Long): Int
    external fun erase(x: Int, y: Int)
    external fun cellOf(lat: Double, lon: Double): IntArray
    external fun cellCenter(x: Int, y: Int): DoubleArray
    external fun isFresh(x: Int, y: Int, nowSec: Long): Boolean
    external fun isServable(x: Int, y: Int, nowSec: Long): Boolean
    external fun ringServable(x: Int, y: Int, nowSec: Long): Boolean
    external fun missingRing(x: Int, y: Int, nowSec: Long): IntArray
    external fun publish(lat: Double, lon: Double, nowSec: Long): ByteArray

    fun published(lat: Double, lon: Double, nowSec: Long): List<Place> =
        json.decodeFromString(publish(lat, lon, nowSec).decodeToString())
}

data class Cell(val x: Int, val y: Int) {
    companion object {
        fun of(lat: Double, lon: Double): Cell = Core.cellOf(lat, lon).let { Cell(it[0], it[1]) }
    }
    val center: LatLon get() = Core.cellCenter(x, y).let { LatLon(it[0], it[1]) }
}

data class LatLon(val lat: Double, val lon: Double)

fun nowSec(): Long = System.currentTimeMillis() / 1000
