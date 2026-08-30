package com.lifeos.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.InstalledApp
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Danger
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.theme.Surface3
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.Violet
import com.lifeos.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionChipRow(chips: List<AppliedChange>, onChipClick: (AppliedChange) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(S.x2),
        verticalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        chips.forEach { chip ->
            val tint = chip.kind.chipTint(MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier
                    .pressable { onChipClick(chip) }
                    .clip(RoundedCornerShape(Radius.full))
                    .background(tint.copy(alpha = 0.14f))
                    .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Radius.full))
                    .padding(horizontal = S.x3, vertical = S.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                Icon(
                    imageVector = chip.kind.chipIcon(),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = chip.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun RiskBadge(percent: Int) {
    val (word, color) = riskTone(percent)
    Text(
        text = "$percent% $word",
        color = color,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    val iconTint by animateColorAsState(
        targetValue = if (granted) MaterialTheme.colorScheme.primary else TextTertiary,
        label = "permIcon",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = S.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(S.x3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(S.x3))
        if (granted) {
            Text(
                text = "Granted",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            PrimaryButton(text = "Grant", onClick = onGrant)
        }
    }
}

@Composable
fun TimeoutBar(label: String, usedMinutes: Int, limitMinutes: Int, sourceLabel: String?) {
    val ratio = if (limitMinutes <= 0) 0f else usedMinutes.toFloat() / limitMinutes.toFloat()
    val progressColor = when {
        ratio >= 1f -> Danger
        ratio > 0.80f -> Warn
        else -> AccentVivid
    }
    Column(Modifier.fillMaxWidth().padding(vertical = S.x2)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(S.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(text = label, color = progressColor, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$usedMinutes / $limitMinutes min",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (sourceLabel != null) {
                LineageChip(sourceLabel)
            }
        }
        Spacer(Modifier.height(S.x2))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(Surface3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(progressColor),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(S.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .offset(x = 8.dp, y = 8.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Surface3)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(Radius.md)),
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Surface2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(Radius.md)),
            )
        }
        Spacer(Modifier.height(S.x4))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(S.x2))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(S.x4))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(start = S.x4, end = S.x4, top = S.x5, bottom = S.x2),
    )
}

@Composable
fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName).toImageBitmap() }
            .getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = S.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            MonogramAvatar(text = app.label, color = AccentVivid, size = 36.dp)
        }
        Spacer(Modifier.width(S.x4))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .pressable { onCheckedChange(!checked) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Surface3,
                uncheckedBorderColor = BorderSubtle,
            ),
        )
    }
}

internal fun riskTone(percent: Int): Pair<String, Color> = when {
    percent < 40 -> "On track" to Success
    percent < 70 -> "At risk" to Warn
    else -> "Critical" to Danger
}

internal fun ChangeKind.chipTint(primary: Color): Color = when (this) {
    ChangeKind.FOCUS, ChangeKind.TIMEOUT -> primary
    ChangeKind.EMAIL -> Success
    ChangeKind.MEMORY, ChangeKind.PERSONA -> Violet
    ChangeKind.ALARM -> Warn
    else -> primary
}

internal fun ChangeKind.chipIcon(): ImageVector = when (this) {
    ChangeKind.GOAL -> Icons.Outlined.Flag
    ChangeKind.TASK -> Icons.Outlined.CheckCircle
    ChangeKind.EVENT -> Icons.Outlined.Event
    ChangeKind.HABIT -> Icons.Outlined.Repeat
    ChangeKind.BLOCK -> Icons.Outlined.Schedule
    ChangeKind.ALARM -> Icons.Outlined.Alarm
    ChangeKind.TIMEOUT -> Icons.Outlined.HourglassBottom
    ChangeKind.FOCUS -> Icons.Outlined.Shield
    ChangeKind.NETWORK -> Icons.Outlined.Wifi
    ChangeKind.MEMORY -> Icons.Outlined.Psychology
    ChangeKind.PERSONA -> Icons.Outlined.Face
    ChangeKind.XP -> Icons.Outlined.Star
    ChangeKind.EMAIL -> Icons.Outlined.Mail
    ChangeKind.REVERT -> Icons.Outlined.Undo
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
