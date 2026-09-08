package com.george.camarawatch.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFFFF4D4D),
    onPrimary = Color.White,
    surface = Color(0xFF121212),
    background = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679),
)

@Composable
fun CamaraWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
