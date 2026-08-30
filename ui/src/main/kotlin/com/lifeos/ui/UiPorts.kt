package com.lifeos.ui

import com.lifeos.core.Ports

object UiPorts {
    lateinit var value: Ports
    val isReady: Boolean get() = ::value.isInitialized
}
