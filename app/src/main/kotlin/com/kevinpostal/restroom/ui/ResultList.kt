package com.kevinpostal.restroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevinpostal.restroom.LoadState
import com.kevinpostal.restroom.Place

@Composable
fun ResultList(
    state: LoadState,
    places: List<Place>,
    locationDenied: Boolean,
    onSelect: (Place) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Theme.palette
    val pad = Modifier.padding(horizontal = Theme.unit * 2, vertical = Theme.unit * 2)
    LazyColumn(modifier.fillMaxSize().testTag("list")) {
        when (state) {
            LoadState.Loading -> item {
                InkText("Loading…", Theme.mono, pad.testTag("state.note").semantics { contentDescription = "Loading restrooms" }, color = p.graphite)
            }
            LoadState.Empty -> item { InkText("No restrooms within range.", Theme.mono, pad.testTag("state.note"), color = p.graphite) }
            is LoadState.Failed -> item {
                Column(pad) {
                    InkText(state.message, Theme.body, Modifier.testTag("state.message"))
                    TextAction("Retry", "state.retry", onRetry)
                }
            }
            LoadState.Idle -> item {
                if (locationDenied) {
                    Column(pad) {
                        InkText("Location is off. Search a place above or enable it in Settings.", Theme.body, Modifier.testTag("state.message"))
                        TextAction("Open Settings", "state.settings", onOpenSettings)
                    }
                } else {
                    InkText("Finding you…", Theme.mono, pad.testTag("state.note"), color = p.graphite)
                }
            }
            LoadState.Loaded -> items(places, key = { it.id }) { place ->
                PlaceRow(place) { onSelect(place) }
                Hairline()
            }
        }
    }
}

/// Red text button, 44 dp minimum, no chrome.
@Composable
fun TextAction(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    InkText(
        text, Theme.title,
        modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(top = Theme.unit)
            .semantics { contentDescription = text },
        color = Theme.palette.red,
    )
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    val p = Theme.palette
    val name = place.name.ifEmpty { "Restroom" }
    val amenities = if (place.isRestroom) {
        place.amenities.ifEmpty { listOf("no amenity details") }.joinToString(", ")
    } else {
        "${place.kindTag ?: ""}, usually has restrooms"
    }
    val label = listOf(name, place.addressLine, place.accessSpoken ?: "", place.distanceSpoken ?: "", amenities)
        .filter { it.isNotEmpty() }
        .joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .testTag("row.${place.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .padding(Theme.unit * 2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Theme.unit / 2)) {
            InkText(name, Theme.title, maxLines = 1)
            if (place.addressLine.isNotEmpty()) InkText(place.addressLine, Theme.body, color = p.graphite)
            place.kindTag?.let { InkText(it.uppercase(), Theme.label, color = p.graphite) }
            place.accessTitle?.let { InkText(it, Theme.mono) }
            if (place.amenities.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.unit)) {
                    place.amenities.forEach { Badge(it, 10.dp) }
                }
            }
        }
        place.distanceText?.let {
            Spacer(Modifier.width(Theme.unit))
            InkText(it, Theme.mono)
        }
    }
}
