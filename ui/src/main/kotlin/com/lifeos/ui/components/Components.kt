package com.lifeos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.InstalledApp
import com.lifeos.ui.theme.LifeOsRadius
import com.lifeos.ui.theme.MdDanger
import com.lifeos.ui.theme.MdPrimary
import com.lifeos.ui.theme.MdWarn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionChipRow(chips: List<AppliedChange>, onChipClick: (AppliedChange) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val label = chip.label.truncated(28)
            AssistChip(
                onClick = { onChipClick(chip) },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = chip.kind.chipIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun RiskBadge(percent: Int) {
    val (word, color) = when {
        percent < 40 -> "On track" to MdPrimary
        percent < 70 -> "At risk" to MdWarn
        else -> "Critical" to MdDanger
    }
    Text(
        text = "$percent% $word",
        color = color,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun PermissionRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.70f),
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text(
                text = "Granted",
                color = scheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
fun TimeoutBar(label: String, usedMinutes: Int, limitMinutes: Int, sourceLabel: String?) {
    val scheme = MaterialTheme.colorScheme
    val ratio = if (limitMinutes <= 0) 0f else usedMinutes.toFloat() / limitMinutes.toFloat()
    val progressColor = when {
        ratio >= 1f -> MdDanger
        ratio > 0.80f -> MdWarn
        else -> scheme.primary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$usedMinutes / $limitMinutes",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LifeOsRadius)),
            color = progressColor,
            trackColor = scheme.onSurface.copy(alpha = 0.12f),
        )
        if (sourceLabel != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "From: $sourceLabel",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
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
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface.copy(alpha = 0.70f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val monogram = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scheme.onSurfaceVariant.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = monogram,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun String.truncated(max: Int): String =
    if (length <= max) this else take(max)

private fun ChangeKind.chipIcon(): ImageVector = when (this) {
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
