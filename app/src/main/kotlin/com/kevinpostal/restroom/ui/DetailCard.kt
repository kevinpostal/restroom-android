package com.kevinpostal.restroom.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevinpostal.restroom.Place

private const val KIND_NOTE = "Parks and campgrounds usually have a public restroom. Not verified — from OpenStreetMap, not a restroom report."

@Composable
fun DetailCard(place: Place, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val p = Theme.palette
    val context = LocalContext.current
    val name = place.name.ifEmpty { "Restroom" }
    Column(modifier.fillMaxSize()) {
        // Header: label, name, close.
        Row(Modifier.fillMaxWidth().padding(horizontal = Theme.unit * 2), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                InkText("RESTROOM", Theme.label, color = p.graphite)
                InkText(name, Theme.display, Modifier.testTag("detail.name").semantics { heading() }, maxLines = 2)
            }
            Tile(Modifier.testTag("detail.close"), description = "Close", onClick = onClose) {
                InkText("×", Theme.title)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Theme.unit * 2),
            verticalArrangement = Arrangement.spacedBy(Theme.unit),
        ) {
            if (place.addressLine.isNotEmpty()) InkText(place.addressLine, Theme.body)
            place.distanceText?.let { InkText(it, Theme.mono, Modifier.semantics { contentDescription = place.distanceSpoken ?: it }) }

            place.accessTitle?.let { title ->
                Hairline()
                Section("Code")
                val code = place.accessCode
                InkText(
                    code ?: title,
                    if (code != null) Theme.display else Theme.body,
                    Modifier.testTag("detail.code").semantics { contentDescription = place.accessSpoken ?: title },
                )
            }

            Hairline()
            val tag = place.kindTag
            when {
                tag != null -> {
                    Section(tag)
                    InkText(KIND_NOTE, Theme.body, Modifier.testTag("detail.kindNote"))
                }
                place.amenities.isEmpty() -> InkText("No amenity details", Theme.body, color = p.graphite)
                else -> place.amenities.forEach { amenity ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.unit)) {
                        Badge(amenity, 20.dp)
                        InkText(amenity, Theme.body)
                    }
                }
            }

            if (place.directions.isNotBlank()) {
                Hairline()
                Section("Directions")
                InkText(place.directions, Theme.body)
            }
            if (place.comment.isNotBlank()) {
                Hairline()
                Section("Notes")
                InkText(place.comment, Theme.body)
            }
            if (place.isRestroom) {
                Hairline()
                Section("Votes")
                InkText(
                    "${place.upvote} up · ${place.downvote} down", Theme.mono,
                    Modifier.semantics { contentDescription = "${place.upvote} upvotes, ${place.downvote} downvotes" },
                )
            }
            TextAction("Copy address", "detail.copy", onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Address", place.addressLine))
            })
            Box(Modifier.height(Theme.unit))
        }
        // Pinned Directions bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(p.red)
                .clickable(role = Role.Button) {
                    val uri = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(name)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                .testTag("detail.directions")
                .semantics { contentDescription = "Directions" },
            contentAlignment = Alignment.Center,
        ) {
            InkText("Directions", Theme.title, color = LightPalette.paper)
        }
    }
}

@Composable
private fun Section(title: String) {
    InkText(title.uppercase(), Theme.label, Modifier.semantics { heading() }, color = Theme.palette.graphite)
}
