package com.conreo.couchytv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val ACCENTS = listOf(
    Color(0xFF8AB4F8),
    Color(0xFF81C995),
    Color(0xFFFDD663),
    Color(0xFFF28B82),
    Color(0xFFD7AEFB),
    Color(0xFF78D9EC),
)

data class WallpaperPreset(val name: String, val colors: List<Color>)

val WALLPAPERS = listOf(
    WallpaperPreset("Midnight", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    WallpaperPreset("Aurora", listOf(Color(0xFF13547A), Color(0xFF2B825B), Color(0xFF80D0C7))),
    WallpaperPreset("Sunset", listOf(Color(0xFF41295A), Color(0xFF8F4A3E), Color(0xFFD76D77))),
    WallpaperPreset("Deep", listOf(Color(0xFF000428), Color(0xFF004683))),
    WallpaperPreset("Charcoal", listOf(Color(0xFF16161A), Color(0xFF232526), Color(0xFF2F3437))),
)

fun WallpaperPreset.brush(): Brush = Brush.linearGradient(colors)

/** Deterministic tile color for apps that ship no banner artwork. */
fun tileColor(pkg: String): Color {
    val palette = listOf(
        Color(0xFF37474F), Color(0xFF4E342E), Color(0xFF1B5E20),
        Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F),
        Color(0xFF3E2723), Color(0xFF263238), Color(0xFF33691E),
    )
    var h = 0
    for (c in pkg) h = h * 31 + c.code
    return palette[((h % palette.size) + palette.size) % palette.size]
}

@Composable
fun LiteTvTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            surface = Color(0xEE1C1E24),
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = Color(0xFF303440),
            onSurfaceVariant = Color(0xFF9AA0A6),
        ),
        content = content,
    )
}
