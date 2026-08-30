package com.lifeos.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Surface ramp: elevation == lightness ----
val Backdrop   = Color(0xFF07090C) // behind everything, scrims
val Surface0   = Color(0xFF0B0E13) // page background
val Surface1   = Color(0xFF11151C) // cards, list rows
val Surface2   = Color(0xFF171C25) // raised cards, bottom sheets, nav bar
val Surface3   = Color(0xFF1E242F) // text fields, pressed states

// ---- Hairlines (never shadows) ----
val BorderSubtle = Color(0x0FFFFFFF) //  6% white
val BorderStrong = Color(0x1FFFFFFF) // 12% white

// ---- Accent: blue, M3 dark tonal roles ----
val AccentHigh  = Color(0xFFD7E3FF) // tone 90 — text on accent containers
val Accent      = Color(0xFFA8C7FA) // tone 80 — PRIMARY
val AccentVivid = Color(0xFF4C8DFF) // tone 60 — rings, progress, "now" line
val AccentDeep  = Color(0xFF28497A) // tone 30 — primaryContainer
val AccentInk   = Color(0xFF0A305F) // tone 20 — text/icon ON an Accent fill
val AccentWash  = Color(0x1F4C8DFF) // 12% — halos, tinted fills

// ---- Semantic ----
val Warn    = Color(0xFFFFD8A8)
val Danger  = Color(0xFFFFB4AB)
val Success = Color(0xFF5AF0BE) // retired mint, kept for completion + calendar
val Violet  = Color(0xFFC7A9FF) // agent / AI moments

val WarnWash    = Color(0x1FF5A524)
val DangerWash  = Color(0x1FFF5C5C)
val SuccessWash = Color(0x1F2EE6A6)
val VioletWash  = Color(0x1FC7A9FF)

// ---- Text ----
val TextPrimary   = Color(0xFFE8EEF5)
val TextSecondary = Color(0xFF97A3B2)
val TextTertiary  = Color(0xFF616C7A)

/** Radial glow: AccentVivid @ 18% → Violet @ 14% → transparent. */
val AgentGradient: Brush = Brush.radialGradient(
    colorStops = arrayOf(
        0.00f to AccentVivid.copy(alpha = 0.18f),
        0.45f to Violet.copy(alpha = 0.14f),
        1.00f to Color.Transparent,
    ),
)

/** Horizontal wash for the 1px top edge on assistant bubbles. */
val AgentGradientEdge: Brush = Brush.horizontalGradient(
    colors = listOf(
        AccentVivid.copy(alpha = 0.18f),
        Violet.copy(alpha = 0.14f),
        Color.Transparent,
    ),
)

/** Surface0 → transparent. Pair with [ScrimEdge] at 24.dp. */
val ScrimTop: Brush = Brush.verticalGradient(
    colors = listOf(Surface0, Color.Transparent),
)

/** Transparent → Surface0. Pair with [ScrimEdge] at 24.dp. */
val ScrimBottom: Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Surface0),
)

@Deprecated("Use Surface0", ReplaceWith("Surface0"))
val MdBg = Surface0

@Deprecated("Use Surface1", ReplaceWith("Surface1"))
val MdSurface = Surface1

@Deprecated("Use Accent", ReplaceWith("Accent"))
val MdPrimary = Accent

@Deprecated("Use AccentInk", ReplaceWith("AccentInk"))
val MdOnPrimary = AccentInk

@Deprecated("Use Danger", ReplaceWith("Danger"))
val MdDanger = Danger

@Deprecated("Use Warn", ReplaceWith("Warn"))
val MdWarn = Warn

@Deprecated("Use TextPrimary", ReplaceWith("TextPrimary"))
val MdOnSurface = TextPrimary

@Deprecated("Use TextSecondary", ReplaceWith("TextSecondary"))
val MdOnSurfaceVariant = TextSecondary
