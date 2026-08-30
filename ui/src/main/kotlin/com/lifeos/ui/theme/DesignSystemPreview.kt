package com.lifeos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lifeos.core.model.AppliedChange
import com.lifeos.core.model.ChangeKind
import com.lifeos.core.model.InstalledApp
import com.lifeos.ui.components.ActionChipRow
import com.lifeos.ui.components.AnimatedCheckbox
import com.lifeos.ui.components.AppToggleRow
import com.lifeos.ui.components.ConfidenceMeter
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.GhostButton
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.LineageChip
import com.lifeos.ui.components.MonogramAvatar
import com.lifeos.ui.components.PermissionRow
import com.lifeos.ui.components.Pill
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.ProgressRing
import com.lifeos.ui.components.RiskBadge
import com.lifeos.ui.components.RiskRing
import com.lifeos.ui.components.ScrimEdge
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.SegmentedControl
import com.lifeos.ui.components.TimeoutBar

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, backgroundColor = 0xFF0B0E13, widthDp = 400, heightDp = 2400)
@Composable
fun DesignSystemPreview() {
    LifeOsTheme {
        DesignSystemContent()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, backgroundColor = 0xFF0B0E13, widthDp = 400, heightDp = 400)
@Composable
fun DesignSystemDynamicPreview() {
    LifeOsTheme(dynamicColor = true) {
        Column(
            modifier = Modifier
                .background(Surface0)
                .padding(S.x4),
            verticalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            Text("Dynamic accent harvest", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Surfaces stay Surface0–3; only accent roles come from wallpaper.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Swatch("Surface0", Surface0)
                Swatch("Surface1", Surface1)
                Swatch("Surface2", Surface2)
                Swatch("Surface3", Surface3)
                Swatch("primary", MaterialTheme.colorScheme.primary)
                Swatch("onPrimary", MaterialTheme.colorScheme.onPrimary)
            }
            PrimaryButton("Primary action", onClick = {})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignSystemContent() {
        var checked by remember { mutableStateOf(true) }
        var segment by remember { mutableIntStateOf(1) }
        Column(
            modifier = Modifier
                .background(Surface0)
                .verticalScroll(rememberScrollState())
                .padding(S.x4),
            verticalArrangement = Arrangement.spacedBy(S.x4),
        ) {
            Text("Calm Dark OS", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Design system preview", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            PreviewLabel("Surfaces")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Swatch("Backdrop", Backdrop)
                Swatch("Surface0", Surface0)
                Swatch("Surface1", Surface1)
                Swatch("Surface2", Surface2)
                Swatch("Surface3", Surface3)
            }

            PreviewLabel("Hairlines + accent")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Swatch("BorderSubtle", BorderSubtle)
                Swatch("BorderStrong", BorderStrong)
                Swatch("AccentHigh", AccentHigh)
                Swatch("Accent", Accent)
                Swatch("AccentVivid", AccentVivid)
                Swatch("AccentDeep", AccentDeep)
                Swatch("AccentInk", AccentInk)
                Swatch("AccentWash", AccentWash)
            }

            PreviewLabel("Semantic + text")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Swatch("Warn", Warn)
                Swatch("Danger", Danger)
                Swatch("Success", Success)
                Swatch("Violet", Violet)
                Swatch("TextPrimary", TextPrimary)
                Swatch("TextSecondary", TextSecondary)
                Swatch("TextTertiary", TextTertiary)
            }

            PreviewLabel("Washes + gradients")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Swatch("WarnWash", WarnWash)
                Swatch("DangerWash", DangerWash)
                Swatch("SuccessWash", SuccessWash)
                Swatch("VioletWash", VioletWash)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(AgentGradient),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(AgentGradientEdge),
            )
            ScrimEdge(top = true)
            ScrimEdge(top = false)

            PreviewLabel("Type")
            Text("displayMedium 30", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Text("headlineMedium 24", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("titleLarge 20", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text("titleMedium 17", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("bodyLarge 16 — chat and body", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text("bodyMedium 14 — snippets", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("labelLarge 14 — buttons", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("SECTIONHEADER", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text("12:08  09:30  88%", style = TimeNumeric, color = TextPrimary)

            PreviewLabel("Radius / spacing")
            Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
                listOf(Radius.xs, Radius.sm, Radius.md, Radius.lg, Radius.xl).forEach { rad ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .surface(level = 2, radius = rad),
                    )
                }
            }

            PreviewLabel("Elevation helper")
            LifeOsCard(level = 1) { Text("Level 1 card", color = TextPrimary) }
            LifeOsCard(level = 2, active = true) { Text("Level 2 active", color = TextPrimary) }
            LifeOsCard(level = 3, onClick = {}) { Text("Level 3 pressable", color = TextPrimary) }

            PreviewLabel("Existing components")
            ActionChipRow(
                chips = listOf(
                    AppliedChange("Focus 50m", ChangeKind.FOCUS),
                    AppliedChange("Cap YouTube", ChangeKind.TIMEOUT),
                    AppliedChange("Triage inbox", ChangeKind.EMAIL),
                    AppliedChange("Remembered", ChangeKind.MEMORY),
                    AppliedChange("Persona Strict", ChangeKind.PERSONA),
                    AppliedChange("Alarm 06:30", ChangeKind.ALARM),
                ),
                onChipClick = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(S.x2), verticalAlignment = Alignment.CenterVertically) {
                RiskBadge(28)
                RiskBadge(55)
                RiskBadge(88)
            }
            PermissionRow("Usage access", "Needed to count app time", granted = false, onGrant = {})
            PermissionRow("Notifications", "Alarms and nudges", granted = true, onGrant = {})
            TimeoutBar("YouTube", usedMinutes = 38, limitMinutes = 45, sourceLabel = "Interview prep")
            SectionHeader("Morning")
            AppToggleRow(
                app = InstalledApp("com.android.chrome", "Chrome"),
                checked = checked,
                onCheckedChange = { checked = it },
            )
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                EmptyState("Nothing here", "Try asking the agent to plan the day.", "Plan day", onAction = {})
            }

            PreviewLabel("New components")
            Row(horizontalArrangement = Arrangement.spacedBy(S.x3), verticalAlignment = Alignment.CenterVertically) {
                Pill("HARD", Danger)
                Pill("SOFT", MaterialTheme.colorScheme.primary, Icons.Outlined.AutoAwesome)
                LineageChip("Caps: YouTube 45m")
                MonogramAvatar("Ada Lovelace")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(S.x4), verticalAlignment = Alignment.CenterVertically) {
                RiskRing(28)
                RiskRing(62)
                ProgressRing(progress = 0.72f, color = AccentVivid) {
                    Text("18m", style = TimeNumeric, color = TextPrimary)
                }
                AnimatedCheckbox(checked = checked, onCheckedChange = { checked = it })
            }
            ConfidenceMeter(0.86)
            SegmentedControl(
                options = listOf("OFF", "BLACKLIST", "WHITELIST"),
                selectedIndex = segment,
                onSelect = { segment = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(S.x3)) {
                PrimaryButton("Add to calendar", onClick = {}, icon = Icons.Outlined.AutoAwesome)
                GhostButton("Dismiss", onClick = {}, icon = Icons.Outlined.Undo)
            }
        }
}

@Composable
private fun PreviewLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier.padding(top = S.x2),
    )
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, RoundedCornerShape(Radius.sm))
                .border(1.dp, BorderSubtle, RoundedCornerShape(Radius.sm)),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
