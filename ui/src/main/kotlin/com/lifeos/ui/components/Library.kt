package com.lifeos.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.ScrimBottom
import com.lifeos.ui.theme.ScrimTop
import com.lifeos.ui.theme.Surface3
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.TimeNumeric
import com.lifeos.ui.theme.Violet
import com.lifeos.ui.theme.surface

@Composable
fun LifeOsCard(
    modifier: Modifier = Modifier,
    level: Int = 1,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.pressable(onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .surface(level = level, radius = Radius.lg, active = active)
            .padding(S.x4),
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = S.x3, vertical = S.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x1),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LineageChip(sourceLabel: String) {
    Pill(text = sourceLabel, color = Violet)
}

@Composable
fun RiskRing(percent: Int, size: Dp = 56.dp, strokeWidth: Dp = 5.dp) {
    val (_, color) = riskTone(percent)
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = Motion.standard,
        label = "riskRing",
    )
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        RingCanvas(progress = animated, color = color, strokeWidth = strokeWidth)
        Text(
            text = "$percent",
            style = TimeNumeric,
            color = color,
        )
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
    color: Color = AccentVivid,
    content: @Composable (() -> Unit)? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = Motion.standard,
        label = "progressRing",
    )
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        RingCanvas(progress = animated, color = color, strokeWidth = strokeWidth)
        content?.invoke()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (enabled) scheme.primary else Surface3
    val fg = if (enabled) scheme.onPrimary else TextTertiary
    Row(
        modifier = modifier
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .clip(RoundedCornerShape(Radius.full))
            .background(bg)
            .padding(horizontal = S.x5, vertical = S.x3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(S.x2))
        }
        Text(text = text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .pressable(onClick)
            .clip(RoundedCornerShape(Radius.full))
            .border(1.dp, primary.copy(alpha = 0.40f), RoundedCornerShape(Radius.full))
            .padding(horizontal = S.x5, vertical = S.x3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(S.x2))
        }
        Text(text = text, color = primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surface(level = 3, radius = Radius.full)
            .padding(S.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressable { onSelect(index) }
                    .clip(RoundedCornerShape(Radius.full))
                    .background(if (selected) scheme.secondaryContainer else Color.Transparent)
                    .padding(vertical = S.x2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.primary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AnimatedCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val fill = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Motion.emphasized,
        label = "checkbox",
    )
    Canvas(
        modifier = Modifier
            .size(22.dp)
            .pressable { onCheckedChange(!checked) },
    ) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color = Surface3)
        drawCircle(color = TextTertiary.copy(alpha = 1f - progress), style = Stroke(stroke))
        if (progress > 0f) {
            drawCircle(color = fill, radius = size.minDimension / 2f * progress)
        }
        if (progress > 0.45f) {
            val alpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val path = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.70f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
            }
            drawPath(
                path = path,
                color = ink.copy(alpha = alpha),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
fun MonogramAvatar(text: String, color: Color = AccentVivid, size: Dp = 36.dp) {
    val initials = text.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = color,
            style = if (size >= 36.dp) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
        )
    }
}

@Composable
fun ConfidenceMeter(confidence: Double) {
    val target = confidence.toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.standard,
        label = "confidence",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(Surface3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(AccentVivid),
            )
        }
        Text(
            text = "${(target * 100).toInt()}%",
            style = TimeNumeric,
            color = TextSecondary,
        )
    }
}

@Composable
fun ScrimEdge(top: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(if (top) ScrimTop else ScrimBottom),
    )
}

@Composable
private fun RingCanvas(progress: Float, color: Color, strokeWidth: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2f
        val arcSize = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx())
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Surface3,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}
