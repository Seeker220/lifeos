package com.lifeos.ui.theme

import androidx.compose.ui.unit.dp

object S { // spacing
    val x1 = 4.dp;  val x2 = 8.dp;  val x3 = 12.dp; val x4 = 16.dp
    val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp; val x10 = 40.dp
}

@Deprecated("Use S", ReplaceWith("S"))
val LifeOsSpacing = object {
    val xs = S.x1
    val sm = S.x2
    val md = S.x4
    val lg = S.x6
}
