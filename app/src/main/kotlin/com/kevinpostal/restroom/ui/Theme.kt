package com.kevinpostal.restroom.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// Rams × Bauhaus tokens, identical to the iOS Theme: paper/ink, three primaries for badges,
/// one 8 dp grid, no corner radii, no shadows.
data class Palette(
    val paper: Color,
    val ink: Color,
    val graphite: Color,
    val line: Color,
    val red: Color = Color(0xFFD7231F),
    val blue: Color = Color(0xFF1D4ED8),
    val yellow: Color = Color(0xFFF2C200),
)

val LightPalette = Palette(paper = Color(0xFFF4F2EC), ink = Color(0xFF111111), graphite = Color(0xFF6B6B6B), line = Color(0xFFD9D6CE))
val DarkPalette = Palette(paper = Color(0xFF121212), ink = Color(0xFFF2F0EA), graphite = Color(0xFF9A9A9A), line = Color(0xFF2A2A2A))

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Theme {
    val unit = 8.dp
    val palette: Palette @Composable get() = LocalPalette.current

    val display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 40.sp)
    val title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 23.sp)
    val mono = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp)
    val label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, lineHeight = 16.sp)
}

@Composable
fun RestroomTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}
