package com.kevinpostal.restroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Loaded : LoadState
    data object Empty : LoadState
    data class Failed(val message: String) : LoadState
}

data class UiState(
    val places: List<Place> = emptyList(),
    val center: LatLon? = null,
    val centerLabel: String = "Near you",
    val state: LoadState = LoadState.Idle,
    val selected: Place? = null,
    val query: String = "",
    /// True while a network fetch for the current centre is in flight (cache hits never set it).
    val busy: Boolean = false,
    /// Location permission was refused; the list shows the Settings prompt while Idle.
    val locationDenied: Boolean = false,
)

/// Mirrors the iOS FinderModel; every cache/rank decision is delegated to the C++ core.
class FinderViewModel(private val net: DataSource) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private val state get() = _ui.value

    private var inflight: Job? = null
    private var prefetching: Job? = null
    private var pinsWarm: Job? = null

    init {
        // Warm the door-code table before the first results land; the first publish waits for it.
        pinsWarm = viewModelScope.launch(Dispatchers.IO) {
            if (Core.pinsCached() == 0) net.pottyPins()?.let(Core::setPins)
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun select(place: Place?) = _ui.update { it.copy(selected = place) }
    fun locationDenied() = _ui.update { it.copy(locationDenied = true) }

    fun useCurrentLocation(lat: Double, lon: Double) {
        _ui.update { it.copy(center = LatLon(lat, lon), centerLabel = "Near you", locationDenied = false) }
        reload()
    }

    fun search(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        _ui.update { it.copy(state = LoadState.Loading) }
        viewModelScope.launch {
            val place = net.geocode(q)
            if (place == null) {
                _ui.update { it.copy(state = LoadState.Failed("No place named \u201C$q\u201D")) }
                return@launch
            }
            _ui.update { it.copy(center = place.second, centerLabel = place.first) }
            reload()
        }
    }

    /// Map was panned: search around the new centre, keeping the current rows on screen until new ones arrive.
    fun explore(lat: Double, lon: Double) {
        _ui.update { it.copy(center = LatLon(lat, lon), centerLabel = "This area") }
        reload(keepingResults = true)
    }

    /// Refresh button: drop the cached page for this spot and fetch it again, keeping rows on screen.
    fun refresh() {
        val c = state.center ?: return
        Cell.of(c.lat, c.lon).let { Core.erase(it.x, it.y) }
        reload(keepingResults = true)
    }

    fun reload(keepingResults: Boolean = false) {
        val center = state.center ?: return
        inflight?.cancel()
        _ui.update { it.copy(busy = false) }
        val cell = Cell.of(center.lat, center.lon)
        val now = nowSec()
        if (Core.isFresh(cell.x, cell.y, now)) {
            inflight = viewModelScope.launch { publish(center) }
            prefetch(cell)
            return
        }
        // Refuge serialises requests per client; stop background fetches so this one isn't queued behind them.
        prefetching?.cancel()
        // Stale-but-servable pages (this cell, or neighbours when panning) go on screen now; the fetch replaces them.
        val canServe = Core.isServable(cell.x, cell.y, now) || (keepingResults && Core.ringServable(cell.x, cell.y, now))
        if (!canServe && !(keepingResults && state.state == LoadState.Loaded)) _ui.update { it.copy(state = LoadState.Loading) }
        _ui.update { it.copy(busy = true) }
        inflight = viewModelScope.launch {
            try {
                if (canServe) publish(center)
                fetchPage(cell, center)
                publish(center)
                prefetch(cell)
            } catch (e: IOException) {
                if (state.places.isEmpty() || !canServe) _ui.update { it.copy(state = LoadState.Failed(e.message ?: "Couldn't read restroom data")) }
            } finally {
                if (coroutineContext[Job]?.isCancelled != true) _ui.update { it.copy(busy = false) }
            }
        }
    }

    /// One tile page: Refuge restrooms plus parks/campgrounds, fetched concurrently. Refuge errors propagate;
    /// the park lookup is a bonus and its failure just yields restrooms alone.
    private suspend fun fetchPage(cell: Cell, at: LatLon) = coroutineScope {
        val refuge = async(Dispatchers.IO) { net.refuge(at.lat, at.lon) }
        val overpass = async(Dispatchers.IO) { net.overpass(at.lat, at.lon) }
        val page = refuge.await()
        val extras = overpass.await()
        val stored = withContext(Dispatchers.IO) { Core.store(cell.x, cell.y, page, extras, nowSec()) }
        if (stored < 0) throw IOException("Couldn't read restroom data")
    }

    /// Union of servable pages for the centre cell and its ring, ranked by distance, with door pins attached.
    private suspend fun publish(center: LatLon) {
        emit(center)
        // Codes are a bonus: the list is already visible; re-publish once the pins arrive.
        val warm = pinsWarm
        if (warm != null && !warm.isCompleted) {
            warm.join()
            emit(center)
        }
    }

    private suspend fun emit(center: LatLon) {
        val list = withContext(Dispatchers.Default) { Core.published(center.lat, center.lon, nowSec()) }
        _ui.update { s ->
            s.copy(
                places = list,
                state = if (list.isEmpty()) LoadState.Empty else LoadState.Loaded,
                selected = s.selected?.let { sel -> list.firstOrNull { it.id == sel.id } ?: sel },
            )
        }
    }

    /// Fetches the ring cells not yet cached, one at a time at low priority; a new reload restarts it.
    private fun prefetch(cell: Cell) {
        prefetching?.cancel()
        val flat = Core.missingRing(cell.x, cell.y, nowSec())
        if (flat.isEmpty()) return
        val missing = flat.toList().chunked(2).map { Cell(it[0], it[1]) }
        prefetching = viewModelScope.launch(Dispatchers.IO) {
            for (c in missing) {
                try {
                    fetchPage(c, c.center)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
            }
        }
    }
}
