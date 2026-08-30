package com.lifeos.ui.screens.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.Time
import com.lifeos.core.model.CandidateKind
import com.lifeos.core.model.EmailCandidate
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.LifeOsRadius
import com.lifeos.ui.theme.MdDanger
import com.lifeos.ui.theme.MdOnPrimary
import com.lifeos.ui.theme.MdPrimary
import com.lifeos.ui.theme.MdWarn
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InboxScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: InboxViewModel = viewModel { InboxViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(ui.snackbar) {
        val message = ui.snackbar ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = message,
            actionLabel = "View",
        )
        vm.consumeSnackbar()
        if (result == SnackbarResult.ActionPerformed) {
            onNavigate(LifeOsDestination.TODAY)
        }
    }

    Box(Modifier.fillMaxSize()) {
        InboxBody(
            ui = ui,
            onSync = vm::sync,
            onPromote = { id -> vm.promote(id) },
            onDismiss = vm::dismiss,
            onEditPromote = { id, title, start -> vm.promote(id, title, start) },
        )
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun InboxBody(
    ui: InboxUiState,
    onSync: () -> Unit,
    onPromote: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onEditPromote: (String, String?, String?) -> Unit,
) {
    var noiseOpen by remember { mutableStateOf(false) }
    var handledOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmailCandidate?>(null) }

    val pending = remember(ui.candidates) {
        ui.candidates.filter { it.isPendingDecision() }.sortedByDescending { it.confidence }
    }
    val noise = remember(ui.candidates) {
        ui.candidates.filter { it.isPendingNoise() }
    }
    val handled = remember(ui.candidates) {
        ui.candidates.filter { it.isHandled() }
    }

    if (!ui.loading && ui.candidates.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            AccountStrip(label = ui.accountLabel, loading = false, onSync = onSync)
            EmptyState(
                title = "Inbox is empty.",
                subtitle = "Load the sample mailbox to see how LifeOS triages email.",
                actionLabel = "Load sample",
                onAction = onSync,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            AccountStrip(label = ui.accountLabel, loading = ui.loading, onSync = onSync)
        }
        if (ui.loading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        item { SectionHeader("Needs decision (${pending.size})") }
        items(pending, key = { it.id }) { candidate ->
            CandidateCard(
                candidate = candidate,
                onPromote = { onPromote(candidate.id) },
                onDismiss = { onDismiss(candidate.id) },
                onEdit = { editing = candidate },
            )
        }
        item {
            CollapsedGroup(
                title = "Noise (${noise.size})",
                expanded = noiseOpen,
                onToggle = { noiseOpen = !noiseOpen },
            )
        }
        if (noiseOpen) {
            items(noise, key = { it.id }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    onPromote = { onPromote(candidate.id) },
                    onDismiss = { onDismiss(candidate.id) },
                    onEdit = { editing = candidate },
                )
            }
        }
        item {
            CollapsedGroup(
                title = "Handled (${handled.size})",
                expanded = handledOpen,
                onToggle = { handledOpen = !handledOpen },
            )
        }
        if (handledOpen) {
            items(handled, key = { it.id }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    onPromote = { onPromote(candidate.id) },
                    onDismiss = { onDismiss(candidate.id) },
                    onEdit = { editing = candidate },
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    editing?.let { candidate ->
        EditCandidateDialog(
            candidate = candidate,
            onDismiss = { editing = null },
            onConfirm = { title, start ->
                onEditPromote(candidate.id, title, start)
                editing = null
            },
        )
    }
}

@Composable
private fun AccountStrip(label: String, loading: Boolean, onSync: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row {
            TextButton(onClick = onSync, enabled = !loading) { Text("Sync") }
            TextButton(onClick = onSync, enabled = !loading) { Text("Load sample") }
        }
    }
}

@Composable
private fun CollapsedGroup(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (expanded) title else "$title ▸",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun CandidateCard(
    candidate: EmailCandidate,
    onPromote: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(LifeOsRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(candidate.subject, style = MaterialTheme.typography.bodyLarge)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "from: ${candidate.from}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${(candidate.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                proposedLine(candidate.proposedStartIso),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            KindChip(candidate.kind)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPromote) { Text("Add to schedule") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun KindChip(kind: CandidateKind) {
    val (bg, fg) = when (kind) {
        CandidateKind.EXAM -> MdDanger to MdOnPrimary
        CandidateKind.DEADLINE -> MdWarn to MdOnPrimary
        CandidateKind.EVENT -> MdPrimary to MdOnPrimary
        CandidateKind.NOISE -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Text(
            kind.name,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EditCandidateDialog(
    candidate: EmailCandidate,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf(candidate.proposedTitle) }
    var start by remember { mutableStateOf(candidate.proposedStartIso.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Start (yyyy-MM-dd'T'HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.trim().ifBlank { null },
                        start.trim().ifBlank { null },
                    )
                },
            ) { Text("Add to schedule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val proposedTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

internal fun proposedLine(iso: String?): String {
    if (iso == null) return "No date found — tap Edit"
    val dt = Time.parseIsoOrNull(iso) ?: return "Proposed: $iso"
    val day = dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    return "Proposed: $day ${dt.toLocalTime().format(proposedTimeFmt)}"
}
