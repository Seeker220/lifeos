package com.lifeos.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    val emphasized = spring<Float>(dampingRatio = 0.80f, stiffness = 380f)
    val standard   = tween<Float>(220, easing = FastOutSlowInEasing)
    val enter      = tween<Float>(320, easing = LinearOutSlowInEasing)
    val navFade    = tween<Float>(180)
}
