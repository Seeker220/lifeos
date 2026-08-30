package com.lifeos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.surface(
    level: Int = 1,
    radius: Dp = Radius.lg,
    active: Boolean = false,
): Modifier {
    val fill = when (level) {
        0 -> Surface0
        2 -> Surface2
        3 -> Surface3
        else -> Surface1
    }
    val shape = RoundedCornerShape(radius)
    val hairline = if (active) AccentVivid.copy(alpha = 0.40f) else BorderSubtle
    return this
        .then(
            if (active) {
                Modifier.drawBehind {
                    val glow = 8.dp.toPx()
                    drawRoundRect(
                        color = AccentWash,
                        topLeft = Offset(-glow / 2f, -glow / 2f),
                        size = Size(this.size.width + glow, this.size.height + glow),
                        cornerRadius = CornerRadius(radius.toPx() + glow / 2f),
                    )
                }
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(fill)
        .border(1.dp, hairline, shape)
}
