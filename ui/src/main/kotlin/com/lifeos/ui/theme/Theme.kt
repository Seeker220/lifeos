package com.lifeos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val LifeOsSpacing = object {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}

val LifeOsRadius = 8.dp

private val DarkColors = darkColorScheme(
    primary = MdPrimary,
    onPrimary = MdOnPrimary,
    background = MdBg,
    surface = MdSurface,
    onBackground = MdOnSurface,
    onSurface = MdOnSurface,
    onSurfaceVariant = MdOnSurfaceVariant,
    error = MdDanger,
    tertiary = MdWarn,
)

@Composable
fun LifeOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = LifeOsTypography,
        content = content,
    )
}
