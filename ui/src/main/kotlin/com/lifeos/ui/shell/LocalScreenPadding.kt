package com.lifeos.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.ProvidableCompositionLocal
import com.lifeos.ui.nav.LocalScreenPadding as NavLocalScreenPadding

/**
 * Scaffold inset for lists that scroll under the chrome.
 * Same instance as [com.lifeos.ui.nav.LocalScreenPadding].
 */
val LocalScreenPadding: ProvidableCompositionLocal<PaddingValues> = NavLocalScreenPadding
