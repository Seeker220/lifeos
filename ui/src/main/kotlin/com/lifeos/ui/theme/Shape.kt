package com.lifeos.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Corner radius scale.
 *
 * Must not be named `R`: AGP filters `R.class` out of library packaging, so a
 * top-level object with that name compiles but is absent at runtime.
 */
object Radius {
    val xs = 8.dp; val sm = 12.dp; val md = 16.dp
    val lg = 20.dp; val xl = 28.dp; val full = 999.dp
}
// cards = lg, sheets = xl (top only), chips/pills = full, inputs = xl, bars = full

@Deprecated("Use Radius.xs", ReplaceWith("Radius.xs"))
val LifeOsRadius = Radius.xs
