package com.lifeos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    primaryContainer = AccentDeep,
    onPrimaryContainer = AccentHigh,
    inversePrimary = AccentDeep,
    secondary = AccentHigh,
    onSecondary = AccentInk,
    secondaryContainer = AccentWash,
    onSecondaryContainer = AccentHigh,
    tertiary = Warn,
    onTertiary = Surface0,
    tertiaryContainer = WarnWash,
    onTertiaryContainer = Warn,
    background = Surface0,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface3,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Accent,
    inverseSurface = TextPrimary,
    inverseOnSurface = Surface0,
    error = Danger,
    onError = Color(0xFF140404),
    errorContainer = DangerWash,
    onErrorContainer = Danger,
    outline = BorderStrong,
    outlineVariant = BorderSubtle,
    scrim = Backdrop,
    surfaceBright = Surface3,
    surfaceDim = Surface0,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceContainerLow = Surface0,
    surfaceContainerLowest = Backdrop,
)

private val LifeOsShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

@Composable
fun LifeOsTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (dynamicColor) {
        val dyn = dynamicDarkColorScheme(LocalContext.current)
        DarkColors.copy(
            primary = dyn.primary,
            onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primaryContainer,
            onPrimaryContainer = dyn.onPrimaryContainer,
            secondary = dyn.secondary,
            tertiary = dyn.tertiary,
            surfaceTint = dyn.primary,
        )
    } else {
        DarkColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = LifeOsTypography,
        shapes = LifeOsShapes,
        content = content,
    )
}
