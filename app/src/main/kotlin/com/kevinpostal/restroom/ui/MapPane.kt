package com.kevinpostal.restroom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonPrimitive
import com.kevinpostal.restroom.LatLon
import com.kevinpostal.restroom.Place
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import kotlin.math.max

const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ZOOM_CENTER = 14.5
private const val ZOOM_SELECTED = 16.0
private const val ZOOM_MIN = 8.0
private const val ZOOM_MAX = 18.0
private const val ICON_PIN = "pin"
private const val ICON_PIN_SELECTED = "pin-selected"

/// Handle the zoom tiles use; the map is only available once the style has loaded.
class MapHandle {
    internal var map: MapLibreMap? = null
    /// Camera target the last pan-search was issued for, so recentring on it is a no-op.
    internal var explored: LatLon? = null

    fun zoomBy(delta: Double) {
        val m = map ?: return
        val z = (m.cameraPosition.zoom + delta).coerceIn(ZOOM_MIN, ZOOM_MAX)
        if (z != m.cameraPosition.zoom) m.animateCamera(CameraUpdateFactory.zoomTo(z))
    }
}

@Composable
fun MapPane(
    handle: MapHandle,
    center: LatLon?,
    places: List<Place>,
    selected: Place?,
    busy: Boolean,
    locationGranted: Boolean,
    onSelect: (Place?) -> Unit,
    onExplore: (LatLon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = Theme.palette
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val state = remember { MapState() }
    val latestSelect = rememberUpdatedState(onSelect)
    val latestExplore = rememberUpdatedState(onExplore)
    val latestSelected = rememberUpdatedState(selected)
    val latestCenter = rememberUpdatedState(center)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            handle.map = map
            map.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true   // OpenFreeMap / OpenStreetMap credit is required
                attributionGravity = Gravity.BOTTOM or Gravity.START
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                val density = context.resources.displayMetrics.density
                style.addImage(ICON_PIN, pinBitmap(density, 26, palette.paper.toArgb(), palette.ink.toArgb(), palette.ink.toArgb()))
                style.addImage(ICON_PIN_SELECTED, pinBitmap(density, 32, palette.red.toArgb(), palette.red.toArgb(), palette.paper.toArgb()))
                val manager = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                    iconIgnorePlacement = true
                    addClickListener { symbol ->
                        val id = symbol.data?.asLong
                        latestSelect.value(state.places.firstOrNull { it.id == id })
                        true
                    }
                }
                state.symbols = manager
                state.style = style
                state.render(state.places, latestSelected.value)
                if (locationGranted) enableLocationDot(context, map, style)
                // Keep the attribution above the sheet: the sheet peeks at 45% of the height.
                mapView.post {
                    val px = (8 * density).toInt()
                    map.uiSettings.setAttributionMargins(px, 0, 0, (mapView.height * 0.45f).toInt() + px)
                }
                latestCenter.value?.let { c -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), ZOOM_CENTER)) }
            }
            // Only a finger on the map counts as a pan; programmatic moves (recentre, select) never trigger a search,
            // and neither does the idle event the map fires at its default world view before the first fix.
            var gesture = false
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gesture = true
            }
            map.addOnCameraIdleListener {
                if (!gesture) return@addOnCameraIdleListener
                gesture = false
                val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
                val anchor = latestSelected.value?.let { LatLon(it.lat, it.lon) } ?: latestCenter.value ?: return@addOnCameraIdleListener
                val moved = distanceMeters(anchor.lat, anchor.lon, target.latitude, target.longitude)
                val spanMeters = map.projection.visibleRegion.latLngBounds.latitudeSpan * 111_000.0
                if (moved > max(200.0, spanMeters / 8)) {
                    val here = LatLon(target.latitude, target.longitude)
                    handle.explored = here
                    latestSelect.value(null)
                    latestExplore.value(here)
                }
            }
        }
    }

    LaunchedEffect(places, selected?.id) {
        state.places = places
        state.render(places, selected)
    }

    LaunchedEffect(center) {
        val map = handle.map ?: return@LaunchedEffect
        val c = center ?: return@LaunchedEffect
        if (c == handle.explored) return@LaunchedEffect
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), ZOOM_CENTER))
    }

    LaunchedEffect(selected?.id) {
        val map = handle.map ?: return@LaunchedEffect
        if (selected != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(selected.lat, selected.lon), ZOOM_SELECTED))
        } else {
            val c = center ?: return@LaunchedEffect
            if (c == handle.explored) return@LaunchedEffect
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), ZOOM_CENTER))
        }
    }

    LaunchedEffect(locationGranted) {
        val map = handle.map ?: return@LaunchedEffect
        val style = state.style ?: return@LaunchedEffect
        if (locationGranted) enableLocationDot(context, map, style)
    }

    Box(modifier.fillMaxSize().testTag("map").semantics { contentDescription = "Map" }) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        // The map gets a light paper wash while fetching so the stale pins read as provisional.
        if (busy) Box(Modifier.matchParentSize().background(palette.paper.copy(alpha = 0.35f)))
    }
}

private class MapState {
    var places: List<Place> = emptyList()
    var symbols: SymbolManager? = null
    var style: Style? = null

    fun render(list: List<Place>, selected: Place?) {
        val manager = symbols ?: return
        manager.deleteAll()
        val options = list.map { p ->
            SymbolOptions()
                .withLatLng(LatLng(p.lat, p.lon))
                .withIconImage(if (p.id == selected?.id) ICON_PIN_SELECTED else ICON_PIN)
                .withData(JsonPrimitive(p.id))
        }
        if (options.isNotEmpty()) manager.create(options)
    }
}

@android.annotation.SuppressLint("MissingPermission")
private fun enableLocationDot(context: Context, map: MapLibreMap, style: Style) {
    val component = map.locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
    }
    component.isLocationComponentEnabled = true
}

/// Paper tile with an ink border and a head-and-body pictogram; red with a paper figure when selected.
private fun pinBitmap(density: Float, sideDp: Int, fill: Int, border: Int, figure: Int): Bitmap {
    val side = (sideDp * density).toInt()
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = fill
    canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
    paint.color = border
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    val half = paint.strokeWidth / 2
    canvas.drawRect(half, half, side - half, side - half, paint)
    paint.style = Paint.Style.FILL
    paint.color = figure
    val head = side * 0.22f
    val gap = side * 0.06f
    val bodyW = side * 0.30f
    val bodyH = side * 0.34f
    val top = (side - (head + gap + bodyH)) / 2
    canvas.drawOval(RectF((side - head) / 2, top, (side + head) / 2, top + head), paint)
    canvas.drawRect((side - bodyW) / 2, top + head + gap, (side + bodyW) / 2, top + head + gap + bodyH, paint)
    return bmp
}

/// Haversine, duplicated from the core only for the pan threshold (keeps the map pane free of JNI).
internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371008.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
