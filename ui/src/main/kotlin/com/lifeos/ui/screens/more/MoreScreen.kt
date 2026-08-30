package com.lifeos.ui.screens.more

import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.Persona
import com.lifeos.core.Personas
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.GhostButton
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.nav.LocalScreenPadding
import com.lifeos.ui.theme.Accent
import com.lifeos.ui.theme.AccentDeep
import com.lifeos.ui.theme.AccentHigh
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Danger
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.SuccessWash
import com.lifeos.ui.theme.Surface3
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import kotlinx.coroutines.yield

@Composable
fun MoreScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: MoreViewModel = viewModel { MoreViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
        }.getOrNull() ?: "0.1.0"
    }
    val calendarName = remember(ui.calendarId, ui.calendarName) {
        resolveCalendarName(context, ui.calendarId) ?: ui.calendarName
    }
    val screenPadding = LocalScreenPadding.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
    ) {
        item { SectionHeader("You") }
        item { GamificationCard(xp = ui.xp, streakDays = ui.streakDays) }

        item { SectionHeader("Life state") }
        item {
            CompactProofCard(
                counts = ui.lifeCounts,
                chatMessages = ui.chatMessages,
                summaryLength = ui.summaryLength,
                proof = ui.compactProof,
                onCompact = vm::compactChat,
            )
        }

        item { SectionHeader("Persona") }
        item {
            PersonaCarousel(
                personas = ui.personas.ifEmpty { Personas.ALL },
                selectedId = ui.personaId,
                onSelect = vm::setPersona,
            )
        }

        item { SectionHeader("Appearance") }
        item {
            MaterialYouCard(
                enabled = ui.dynamicColor,
                onEnabledChange = vm::setDynamicColor,
            )
        }

        if (ui.hasCalendar) {
            item { SectionHeader("Calendar") }
            item {
                CalendarCard(
                    enabled = ui.calendarSyncEnabled,
                    name = calendarName,
                    hint = ui.calendarHint,
                    status = ui.calendarStatus,
                    onEnabledChange = vm::setCalendarSyncEnabled,
                    onSync = vm::syncCalendar,
                    onOpen = { openCalendar(context) },
                )
            }
        }

        item { SectionHeader("Settings") }
        item {
            SettingsCard(
                chatWindowK = ui.chatWindowK,
                autoSchedule = ui.autoScheduleHighConfidence,
                strictTimeouts = ui.demoStrictTimeouts,
                showStrictTimeouts = ui.isDebug,
                onChatWindow = vm::setChatWindowK,
                onAutoSchedule = vm::setAutoScheduleHighConfidence,
                onStrict = vm::setDemoStrictTimeouts,
            )
        }

        item { SectionHeader("Memory") }
        item { MemoryCard(ui.memoryFacts) }

        item { SectionHeader("Permissions") }
        item {
            PermissionsCard(
                rows = ui.permissions,
                onReview = vm::reviewPermissions,
            )
        }

        if (ui.isDebug) {
            item { SectionHeader("Debug") }
            item {
                DebugCard(
                    expanded = debugOpen,
                    onToggle = { debugOpen = !debugOpen },
                    onTestAlarm = vm::testAlarm,
                    onReset = { confirmReset = true },
                )
            }
        }

        item { SectionHeader("About") }
        item {
            LifeOsCard(
                modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
                level = 1,
            ) {
                IconRow(
                    icon = Icons.Outlined.Info,
                    title = "Version $version",
                    subtitle = "LifeOS — plans that enforce themselves.",
                    showDivider = false,
                )
            }
        }
        item { Spacer(Modifier.height(S.x6)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data") },
            text = { Text("This clears life state and chat. You will start over.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetDemo()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GamificationCard(xp: Int, streakDays: Int) {
    val xpAnim by animateIntAsState(targetValue = xp, label = "xp")
    val streakAnim by animateIntAsState(targetValue = streakDays, label = "streak")
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        IconRow(
            icon = Icons.Outlined.Star,
            title = "$xpAnim XP",
            subtitle = "Points from completed work.",
            showDivider = true,
        )
        IconRow(
            icon = Icons.Outlined.LocalFireDepartment,
            title = "$streakAnim day streak",
            subtitle = "Consecutive days you showed up.",
            showDivider = false,
        )
    }
}

@Composable
private fun CompactProofCard(
    counts: LifeStateCounts,
    chatMessages: Int,
    summaryLength: Int,
    proof: CompactProof?,
    onCompact: () -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
        onClick = onCompact,
    ) {
        IconRow(
            icon = Icons.Outlined.Compress,
            title = "Compact chat",
            subtitle = "Fold old turns. Goals, caps, and alarms stay.",
            showDivider = true,
        )
        Text(
            text = "Chat  $chatMessages messages · summary $summaryLength chars",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = S.x2, bottom = S.x2),
        )
        CountGrid(counts)
        CompactCountRow(proof = proof, liveCount = chatMessages)
        if (proof != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = S.x3)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SuccessWash)
                    .padding(horizontal = S.x3, vertical = S.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = proof.lostLine(counts),
                    style = MaterialTheme.typography.labelMedium,
                    color = Success,
                )
            }
        }
    }
}

@Composable
private fun CompactCountRow(proof: CompactProof?, liveCount: Int) {
    key(proof?.chatBefore, proof?.chatAfter) {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(proof) {
            if (proof != null) {
                started = false
                yield()
                started = true
            }
        }
        val animated by animateIntAsState(
            targetValue = when {
                proof == null -> liveCount
                !started -> proof.chatBefore
                else -> proof.chatAfter
            },
            label = "compactCount",
        )
        val line = if (proof == null) {
            "Tap to compact · $animated messages"
        } else {
            "Chat ${proof.chatBefore} → $animated messages"
        }
        Text(
            text = line,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(top = S.x3),
        )
    }
}

@Composable
private fun CountGrid(counts: LifeStateCounts) {
    val cells = listOf(
        "Goals" to counts.goals,
        "Tasks" to counts.tasks,
        "Events" to counts.events,
        "Habits" to counts.habits,
        "Blocks" to counts.blocks,
        "Alarms" to counts.alarms,
        "Timeouts" to counts.timeouts,
        "Memory" to counts.memoryFacts,
    )
    Column(verticalArrangement = Arrangement.spacedBy(S.x1)) {
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(S.x3)) {
                pair.forEach { (label, value) ->
                    Text(
                        "$label  $value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaCarousel(
    personas: List<Persona>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = S.x4),
        horizontalArrangement = Arrangement.spacedBy(S.x3),
    ) {
        items(personas, key = { it.id }) { persona ->
            LifeOsCard(
                modifier = Modifier.width(220.dp),
                level = 1,
                active = persona.id == selectedId,
                onClick = { onSelect(persona.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(S.x2))
                    Text(persona.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Text(
                    text = persona.voice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = S.x2),
                )
            }
        }
    }
}

@Composable
private fun MaterialYouCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        SwitchRow(
            icon = Icons.Outlined.Palette,
            title = "Use wallpaper colours",
            subtitle = "Only the accent changes; dark surfaces stay.",
            checked = enabled,
            onCheckedChange = onEnabledChange,
            showDivider = true,
        )
        Text(
            text = "Live accent",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(top = S.x3, bottom = S.x2),
        )
        AccentSwatches(dynamic = enabled)
    }
}

@Composable
private fun AccentSwatches(dynamic: Boolean) {
    val context = LocalContext.current
    val branded = listOf(Accent, AccentVivid, AccentDeep, AccentHigh)
    val colors = if (dynamic) {
        val dyn = dynamicDarkColorScheme(context)
        listOf(dyn.primary, dyn.secondary, dyn.primaryContainer, dyn.tertiary)
    } else {
        branded
    }
    Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, BorderSubtle, CircleShape),
            )
        }
    }
}

@Composable
private fun CalendarCard(
    enabled: Boolean,
    name: String,
    hint: String,
    status: String?,
    onEnabledChange: (Boolean) -> Unit,
    onSync: () -> Unit,
    onOpen: () -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        SwitchRow(
            icon = Icons.Outlined.CalendarMonth,
            title = "Sync to Calendar",
            subtitle = name,
            checked = enabled,
            onCheckedChange = onEnabledChange,
            showDivider = true,
        )
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = S.x2),
            )
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = S.x2),
            )
        }
        Row(
            modifier = Modifier.padding(top = S.x3),
            horizontalArrangement = Arrangement.spacedBy(S.x2),
        ) {
            PrimaryButton(text = "Sync now", onClick = onSync)
            GhostButton(text = "Open in Calendar", onClick = onOpen)
        }
    }
}

@Composable
private fun SettingsCard(
    chatWindowK: Int,
    autoSchedule: Boolean,
    strictTimeouts: Boolean,
    showStrictTimeouts: Boolean,
    onChatWindow: (Boolean) -> Unit,
    onAutoSchedule: (Boolean) -> Unit,
    onStrict: (Boolean) -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        SwitchRow(
            icon = Icons.AutoMirrored.Outlined.Chat,
            title = "Compact chat window",
            subtitle = "Keep the last $chatWindowK messages; older turns fold into a summary.",
            checked = chatWindowK <= 12,
            onCheckedChange = onChatWindow,
            showDivider = true,
        )
        SwitchRow(
            icon = Icons.Outlined.AutoAwesome,
            title = "Auto-schedule high confidence",
            subtitle = "Promote exam and deadline emails when confidence is high.",
            checked = autoSchedule,
            onCheckedChange = onAutoSchedule,
            showDivider = showStrictTimeouts,
        )
        if (showStrictTimeouts) {
            SwitchRow(
                icon = Icons.Outlined.Schedule,
                title = "Strict demo timeouts",
                subtitle = "Clamps every app cap to 1 minute so blocking is demoable.",
                checked = strictTimeouts,
                onCheckedChange = onStrict,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun MemoryCard(facts: List<String>) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        if (facts.isEmpty()) {
            IconRow(
                icon = Icons.Outlined.Psychology,
                title = "No memory facts yet",
                subtitle = "Ask LifeOS to remember something.",
                showDivider = false,
            )
        } else {
            facts.forEachIndexed { index, fact ->
                IconRow(
                    icon = Icons.Outlined.Psychology,
                    title = fact,
                    subtitle = null,
                    showDivider = index != facts.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(rows: List<PermissionRowUi>, onReview: () -> Unit) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        rows.forEachIndexed { index, row ->
            IconRow(
                icon = Icons.Outlined.VerifiedUser,
                title = row.title,
                subtitle = if (row.granted) "Granted" else "Missing",
                subtitleColor = if (row.granted) Success else Danger,
                showDivider = index != rows.lastIndex,
            )
        }
        PrimaryButton(
            text = "Review permissions",
            onClick = onReview,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = S.x3),
        )
    }
}

@Composable
private fun DebugCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onTestAlarm: () -> Unit,
    onReset: () -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
        level = 1,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(S.x3))
            Column(Modifier.weight(1f)) {
                Text("Debug", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Test alarm and reset demo data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextTertiary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = S.x3), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                GhostButton(text = "Test alarm in 60s", onClick = onTestAlarm, modifier = Modifier.fillMaxWidth())
                GhostButton(text = "Reset demo data", onClick = onReset, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = S.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(S.x3))
            Column(Modifier.weight(1f).padding(end = S.x2)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            ThemedSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) Hairline()
    }
}

@Composable
private fun IconRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    subtitleColor: Color = TextSecondary,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = S.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(S.x3))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                }
            }
        }
        if (showDivider) Hairline()
    }
}

@Composable
private fun Hairline() {
    HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
}

@Composable
private fun ThemedSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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

private fun resolveCalendarName(context: android.content.Context, id: Long?): String? {
    if (id == null) return null
    val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

private fun openCalendar(context: android.content.Context) {
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(System.currentTimeMillis().toString())
        .build()
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
