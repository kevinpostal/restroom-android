package com.kevinpostal.restroom.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(Theme.palette.line))
}

/// Ink text in one of the five theme styles. Compose text defaults to black; the palette decides.
@Composable
fun InkText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Theme.palette.ink, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier = modifier, style = style.copy(color = color), maxLines = maxLines)
}

/// Ink ring, 32 dp: closed at rest; while `busy` it opens to a three-quarter arc and turns once a second.
@Composable
fun LoadingRing(busy: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val sweep by animateFloatAsState(if (busy) 270f else 360f, tween(200, easing = LinearEasing), label = "sweep")
    val angle = if (busy) {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
        a
    } else 0f
    val ink = Theme.palette.ink
    val stroke = with(LocalDensity.current) { 1.5.dp.toPx() }
    Canvas(modifier.size(size)) {
        val inset = stroke / 2
        drawArc(
            color = ink, startAngle = angle - 90f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke),
        )
    }
}

/// Red circle / blue square / yellow triangle: the three amenity badges.
@Composable
fun Badge(amenity: String, size: Dp, modifier: Modifier = Modifier) {
    val p = Theme.palette
    Canvas(modifier.size(size)) {
        when (amenity) {
            "Accessible" -> drawCircle(p.red)
            "Unisex" -> drawRect(p.blue)
            else -> drawPath(Path().apply {
                moveTo(this@Canvas.size.width / 2, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height)
                lineTo(0f, this@Canvas.size.height)
                close()
            }, p.yellow)
        }
    }
}

/// A 44 dp-minimum tappable tile with no ripple: the whole app is flat paper and ink.
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    description: String,
    state: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
