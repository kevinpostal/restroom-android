package com.kevinpostal.restroom

import android.app.Application
import java.io.File
import org.maplibre.android.MapLibre

class RestroomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Warm the tile cache from disk before any screen exists; fixture mode re-inits memory-only.
        Core.init(File(cacheDir, "tiles.json").path, nowSec())
    }
}
