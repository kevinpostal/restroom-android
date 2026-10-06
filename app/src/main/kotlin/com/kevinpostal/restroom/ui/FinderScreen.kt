package com.kevinpostal.restroom.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kevinpostal.restroom.FinderViewModel
import com.kevinpostal.restroom.Locator
import com.kevinpostal.restroom.UiTestMode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(vm: FinderViewModel, mode: UiTestMode) {
    val ui by vm.ui.collectAsState()
    val p = Theme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = remember { MapHandle() }
    val scaffold = rememberBottomSheetScaffoldState()
    var granted by remember { mutableStateOf(Locator.granted(context)) }
    var locateRequest by remember { mutableStateOf(0) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) locateRequest++ else vm.locationDenied()
    }

    LaunchedEffect(Unit) {
        when {
            mode.active -> if (mode.denied) vm.locationDenied() else vm.useCurrentLocation(UiTestMode.start.lat, UiTestMode.start.lon)
            granted -> locateRequest++
            else -> permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(locateRequest) {
        if (locateRequest == 0 || mode.active) return@LaunchedEffect
        if (!granted) { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION); return@LaunchedEffect }
        Locator.fixes(context).collect { vm.useCurrentLocation(it.latitude, it.longitude) }
    }

    // A selection pulls the sheet to half height so the card and the pin are both visible.
    LaunchedEffect(ui.selected?.id) {
        if (ui.selected != null) scaffold.bottomSheetState.partialExpand()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.paper)) {
        val peek = maxHeight * 0.45f
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = peek,
            sheetShape = RectangleShape,
            sheetContainerColor = p.paper,
            sheetContentColor = p.ink,
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            containerColor = p.paper,
            sheetDragHandle = {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).testTag("sheet.grabber").semantics { contentDescription = "Panel" },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(60.dp).height(4.dp).background(p.ink)) }
            },
            sheetContent = {
                Column(Modifier.fillMaxWidth().fillMaxHeight().testTag("sheet")) {
                    Hairline()
                    val selected = ui.selected
                    if (selected != null) {
                        DetailCard(selected, onClose = { vm.select(null) })
                    } else {
                        SheetHeader(
                            query = ui.query,
                            onQuery = vm::setQuery,
                            onSearch = { vm.search(ui.query) },
                            onFocus = { scope.launch { scaffold.bottomSheetState.expand() } },
                            title = ui.centerLabel,
                            busy = ui.busy,
                            onRefresh = vm::refresh,
                            onLocate = {
                                if (mode.active) vm.useCurrentLocation(UiTestMode.start.lat, UiTestMode.start.lon) else locateRequest++
                            },
                        )
                        ResultList(
                            state = ui.state,
                            places = ui.places,
                            locationDenied = ui.locationDenied,
                            onSelect = vm::select,
                            onRetry = { vm.reload() },
                            onOpenSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                runCatching { context.startActivity(intent) }
                            },
                        )
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                MapPane(
                    handle = handle,
                    center = ui.center,
                    places = ui.places,
                    selected = ui.selected,
                    busy = ui.busy,
                    locationGranted = granted && !mode.active,
                    onSelect = vm::select,
                    onExplore = { vm.explore(it.lat, it.lon) },
                )
                ZoomTiles(handle, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Theme.unit))
                AnimatedVisibility(
                    visible = ui.busy,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(Theme.unit),
                ) { SearchingTile() }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onFocus: () -> Unit,
    title: String,
    busy: Boolean,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
) {
    val p = Theme.palette
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Theme.unit * 2)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Theme.body.copy(color = p.ink),
                cursorBrush = SolidColor(p.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .testTag("search.field")
                    .semantics { contentDescription = "Search a place" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) InkText("Search a place", Theme.body, color = p.graphite)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Tile(Modifier.testTag("search.clear"), description = "Clear search", onClick = { onQuery("") }) {
                    InkText("×", Theme.title)
                }
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            InkText(title, Theme.title, Modifier.weight(1f).testTag("header.title").semantics { heading() }, maxLines = 1)
            Tile(
                Modifier.testTag("header.refresh"),
                description = "Refresh",
                state = if (busy) "Loading" else null,
                onClick = onRefresh,
            ) { LoadingRing(busy) }
            Tile(Modifier.testTag("map.locate"), description = "Use my location", onClick = onLocate) {
                Box(Modifier.size(32.dp).background(p.red, CircleShape))
            }
        }
        Spacer(Modifier.height(Theme.unit / 2))
    }
}

/// Two stacked paper tiles, hairline ink border, + / − glyphs. Each at least 44 dp.
@Composable
private fun ZoomTiles(handle: MapHandle, modifier: Modifier = Modifier) {
    val p = Theme.palette
    Column(modifier.width(44.dp).background(p.paper).border(1.dp, p.ink)) {
        Tile(Modifier.size(44.dp).testTag("map.zoomIn"), description = "Zoom in", onClick = { handle.zoomBy(1.0) }) { InkText("+", Theme.title) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink))
        Tile(Modifier.size(44.dp).testTag("map.zoomOut"), description = "Zoom out", onClick = { handle.zoomBy(-1.0) }) { InkText("−", Theme.title) }
    }
}

/// Ring + "Searching…" on paper; purely informative, never in the touch path.
@Composable
private fun SearchingTile() {
    val p = Theme.palette
    Row(
        Modifier
            .background(p.paper)
            .border(1.dp, p.ink)
            .heightIn(min = 44.dp)
            .padding(horizontal = Theme.unit * 1.5f)
            .testTag("map.loading")
            .semantics { contentDescription = "Searching this area" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.unit),
    ) {
        LoadingRing(busy = true, size = 16.dp)
        InkText("Searching…", Theme.mono)
    }
}
