package com.lifeos.ui.screens.more

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.LifeOsRadius
import com.lifeos.ui.theme.MdDanger
import com.lifeos.ui.theme.MdPrimary
import com.lifeos.ui.theme.MdSurface

@Composable
fun MoreScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: MoreViewModel = viewModel { MoreViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
        }.getOrNull() ?: "0.1.0"
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { GamificationStrip(xp = ui.xp, streakDays = ui.streakDays) }
        item {
            LifeStateCard(
                counts = ui.lifeCounts,
                chatMessages = ui.chatMessages,
                summaryLength = ui.summaryLength,
                proof = ui.compactProof,
                onCompact = vm::compactChat,
            )
        }
        item { SectionHeader("Persona") }
        item { PersonaRow(personas = ui.personas, selectedId = ui.personaId, onSelect = vm::setPersona) }
        item { SectionHeader("Memory") }
        item { MemoryList(ui.memoryFacts) }
        item { SectionHeader("Permissions") }
        item {
            PermissionsBlock(
                rows = ui.permissions,
                onReview = vm::reviewPermissions,
            )
        }
        item { SectionHeader("Demo") }
        item {
            DemoControls(
                strict = ui.demoStrictTimeouts,
                onStrictChange = vm::setDemoStrictTimeouts,
                onTestAlarm = vm::testAlarm,
                onReset = { confirmReset = true },
            )
        }
        item { SectionHeader("About") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Version $version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "LifeOS — plans that enforce themselves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
private fun GamificationStrip(xp: Int, streakDays: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCell(Modifier.weight(1f), value = "$xp", label = "XP")
        StatCell(Modifier.weight(1f), value = "$streakDays", label = "day streak")
    }
}

@Composable
private fun StatCell(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MdSurface),
        shape = RoundedCornerShape(LifeOsRadius),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MdPrimary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LifeStateCard(
    counts: LifeStateCounts,
    chatMessages: Int,
    summaryLength: Int,
    proof: CompactProof?,
    onCompact: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MdSurface),
        shape = RoundedCornerShape(LifeOsRadius),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Life state", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CountGrid(counts)
            Text(
                "Chat: $chatMessages messages · summary ${summaryLength} chars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(onClick = onCompact, modifier = Modifier.padding(top = 12.dp)) {
                Text("Compact chat")
            }
            if (proof != null) {
                val lifeLine = if (proof.lifeUnchanged) "Life state unchanged." else "Life state changed."
                Text(
                    "Chat ${proof.chatBefore} → ${proof.chatAfter} messages. $lifeLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (proof.lifeUnchanged) MdPrimary else MdDanger,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Text(
                        "$label  $value",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaRow(
    personas: List<com.lifeos.core.Persona>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        personas.forEach { persona ->
            FilterChip(
                selected = persona.id == selectedId,
                onClick = { onSelect(persona.id) },
                label = { Text(persona.name) },
            )
        }
    }
}

@Composable
private fun MemoryList(facts: List<String>) {
    if (facts.isEmpty()) {
        Text(
            "No memory facts yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        facts.forEach { fact ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MdSurface),
                shape = RoundedCornerShape(LifeOsRadius),
            ) {
                Text(fact, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PermissionsBlock(rows: List<PermissionRowUi>, onReview: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (row.granted) "Granted" else "Missing",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.granted) MdPrimary else MdDanger,
                )
            }
        }
        Button(onClick = onReview, modifier = Modifier.padding(top = 8.dp)) {
            Text("Review permissions")
        }
    }
}

@Composable
private fun DemoControls(
    strict: Boolean,
    onStrictChange: (Boolean) -> Unit,
    onTestAlarm: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Strict demo timeouts", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Clamps every app cap to 1 minute so blocking is demoable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = strict, onCheckedChange = onStrictChange)
        }
        TextButton(onClick = onTestAlarm, modifier = Modifier.padding(top = 4.dp)) {
            Text("Test alarm in 60s")
        }
        TextButton(onClick = onReset) {
            Text("Reset demo data")
        }
    }
}
