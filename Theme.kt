package com.ultraplayer.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF00174A),
    secondary = Color(0xFF7CF5D1),
    tertiary = Color(0xFFFF8FB1),
    background = Color(0xFF0B0D14),
    onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF141824),
    onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1D2233),
    onSurfaceVariant = Color(0xFFB4B9CC),
    error = Color(0xFFFF6B6B),
)

@Composable
fun UltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
