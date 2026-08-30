package com.lifeos.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf

/** Scaffold inset so lists can scroll under the header and nav bar. */
val LocalScreenPadding = staticCompositionLocalOf { PaddingValues() }
