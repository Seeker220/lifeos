package com.lifeos.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.Time
import com.lifeos.core.model.CandidateKind
import com.lifeos.core.model.EmailCandidate
import com.lifeos.core.model.MailKind
import com.lifeos.core.model.MailMessage
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.ConfidenceMeter
import com.lifeos.ui.components.EmptyState
import com.lifeos.ui.components.GhostButton
import com.lifeos.ui.components.IconGhostButton
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.LifeOsSnackbarHost
import com.lifeos.ui.components.MonogramAvatar
import com.lifeos.ui.components.Pill
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.pressable
import com.lifeos.ui.theme.AccentDeep
import com.lifeos.ui.theme.AccentHigh
import com.lifeos.ui.theme.BorderSubtle
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.nav.LocalScreenPadding
import com.lifeos.ui.theme.Danger
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Success
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.Warn
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InboxScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: InboxViewModel = viewModel { InboxViewModel(UiPorts.value) }
    val ui by vm.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val screenPadding = LocalScreenPadding.current
    var connectOpen by remember { mutableStateOf(false) }
    var connectKind by remember { mutableStateOf(MailKind.GMAIL) }
    var composeOpen by remember { mutableStateOf(false) }
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }

    LaunchedEffect(ui.snackbar) {
        val message = ui.snackbar ?: return@LaunchedEffect
        val action = ui.snackbarAction
        vm.consumeSnackbar()
        val result = snackbarHost.showSnackbar(
            message = message,
            actionLabel = action,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onNavigate(LifeOsDestination.TODAY)
        }
    }

    LaunchedEffect(ui.hasRealAccount) {
        if (ui.hasRealAccount) connectOpen = false
    }

    Box(Modifier.fillMaxSize()) {
        InboxBody(
            ui = ui,
            contentPadding = screenPadding,
            onSync = vm::sync,
            onAddSource = { kind ->
                when (kind) {
                    MailKind.GMAIL, MailKind.IMAP -> {
                        connectKind = kind
                        connectOpen = true
                    }
                    MailKind.CODEFORCES, MailKind.LEETCODE -> vm.addSource(kind)
                    MailKind.SEED -> Unit
                }
            },
            onDisconnect = vm::disconnect,
            onPromote = { id -> vm.promote(id) },
            onDismiss = vm::dismiss,
            onEditPromote = { id, title, start -> vm.promote(id, title, start) },
            onCompose = { composeTo = ""; composeOpen = true },
            onReply = { message ->
                composeTo = message.from
                composeSubject = "Re: ${message.subject}"
                composeOpen = true
            },
            onMarkRead = vm::markRead,
        )
        LifeOsSnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (connectOpen) {
        ConnectAccountDialog(
            initialKind = connectKind,
            onDismiss = { connectOpen = false },
            onConnect = { kind, address, password, host, port, username, smtpHost ->
                vm.connect(kind, address, password, host, port, username, smtpHost)
                connectOpen = false
            },
        )
    }

    if (composeOpen) {
        ComposeDialog(
            initialTo = composeTo,
            initialSubject = composeSubject,
            sending = ui.sending,
            onDismiss = {
                composeOpen = false
                composeSubject = ""
            },
            onSend = { to, subject, body ->
                vm.send(to, subject, body)
                composeOpen = false
                composeSubject = ""
            },
        )
    }
}

@Composable
private fun InboxBody(
    ui: InboxUiState,
    contentPadding: PaddingValues,
    onSync: () -> Unit,
    onAddSource: (MailKind) -> Unit,
    onDisconnect: (MailKind) -> Unit,
    onPromote: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onEditPromote: (String, String?, String?) -> Unit,
    onCompose: () -> Unit,
    onReply: (MailMessage) -> Unit,
    onMarkRead: (String) -> Unit,
) {
    var noiseOpen by remember { mutableStateOf(false) }
    var handledOpen by remember { mutableStateOf(false) }
    var mailOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmailCandidate?>(null) }
    var reading by remember { mutableStateOf<MailMessage?>(null) }

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AccountStrip(
                label = ui.accountLabel,
                loading = false,
                canSend = ui.canSend,
                connectedKinds = ui.connectedKinds,
                onSync = onSync,
                onAddSource = onAddSource,
                onDisconnect = onDisconnect,
                onCompose = onCompose,
            )
            if (!ui.hasRealAccount) {
                ConnectPrompt(
                    onGmail = { onAddSource(MailKind.GMAIL) },
                    onImap = { onAddSource(MailKind.IMAP) },
                )
            } else {
                EmptyState(
                    title = "Inbox is empty.",
                    subtitle = "Sync to fetch mail from ${ui.accountLabel}.",
                    actionLabel = "Sync",
                    onAction = onSync,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            AccountStrip(
                label = ui.accountLabel,
                loading = ui.loading,
                canSend = ui.canSend,
                connectedKinds = ui.connectedKinds,
                onSync = onSync,
                onAddSource = onAddSource,
                onDisconnect = onDisconnect,
                onCompose = onCompose,
            )
        }
        if (ui.loading) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(S.x6),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SectionHeader("Needs decision (${pending.size})") }
        items(pending, key = { it.id }) { candidate ->
            CandidateCard(
                candidate = candidate,
                showActions = true,
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
                    showActions = true,
                    onPromote = { onPromote(candidate.id) },
                    onDismiss = { onDismiss(candidate.id) },
                    onEdit = { editing = candidate },
                )
            }
        }
        item {
            CollapsedGroup(
                title = "All mail (${ui.messages.size})",
                expanded = mailOpen,
                onToggle = { mailOpen = !mailOpen },
            )
        }
        if (mailOpen) {
            items(ui.messages, key = { "msg:${it.id}" }) { message ->
                MailRow(
                    message = message,
                    onOpen = {
                        reading = message
                        if (!message.read) onMarkRead(message.id)
                    },
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
                    showActions = false,
                    onPromote = { onPromote(candidate.id) },
                    onDismiss = { onDismiss(candidate.id) },
                    onEdit = { editing = candidate },
                )
            }
        }
        item { Spacer(Modifier.height(S.x8)) }
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

    reading?.let { message ->
        MailDetailDialog(
            message = message,
            canReply = ui.canSend,
            onDismiss = { reading = null },
            onReply = {
                reading = null
                onReply(message)
            },
        )
    }
}

@Composable
private fun ConnectPrompt(onGmail: () -> Unit, onImap: () -> Unit) {
    LifeOsCard(modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2), level = 1) {
        Column(verticalArrangement = Arrangement.spacedBy(S.x3)) {
            Text(
                "Connect a mailbox",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                "LifeOS reads your real mail to find exams, deadlines, and events, then puts " +
                    "them on your schedule. Nothing is sent anywhere else.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            PrimaryButton(
                text = "Sign in with Google",
                onClick = onGmail,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                text = "Other provider (IMAP)",
                onClick = onImap,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountStrip(
    label: String,
    loading: Boolean,
    canSend: Boolean,
    connectedKinds: Set<MailKind>,
    onSync: () -> Unit,
    onAddSource: (MailKind) -> Unit,
    onDisconnect: (MailKind) -> Unit,
    onCompose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = S.x4, vertical = S.x3),
        verticalArrangement = Arrangement.spacedBy(S.x2),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(S.x2),
            verticalArrangement = Arrangement.spacedBy(S.x2),
        ) {
            SourceChip("Gmail", MailKind.GMAIL, connectedKinds, onAddSource, onDisconnect)
            SourceChip("Codeforces", MailKind.CODEFORCES, connectedKinds, onAddSource, onDisconnect)
            SourceChip("LeetCode", MailKind.LEETCODE, connectedKinds, onAddSource, onDisconnect)
            SourceChip("IMAP", MailKind.IMAP, connectedKinds, onAddSource, onDisconnect)
            GhostButton(text = if (loading) "Syncing…" else "Sync", onClick = onSync)
            if (canSend) {
                PrimaryButton(text = "Compose", onClick = onCompose)
            }
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    kind: MailKind,
    connected: Set<MailKind>,
    onAdd: (MailKind) -> Unit,
    onRemove: (MailKind) -> Unit,
) {
    val on = kind in connected
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(if (on) AccentDeep else Surface2)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else BorderSubtle, RoundedCornerShape(Radius.full))
            .pressable { if (on) onRemove(kind) else onAdd(kind) }
            .padding(horizontal = S.x3, vertical = S.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) AccentHigh else TextPrimary,
        )
        if (on) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove $label",
                tint = AccentHigh,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ConnectAccountDialog(
    initialKind: MailKind,
    onDismiss: () -> Unit,
    onConnect: (MailKind, String, String, String, Int, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(if (initialKind == MailKind.IMAP) MailKind.IMAP else MailKind.GMAIL) }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var username by remember { mutableStateOf("") }
    var smtpHost by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
                    if (kind == MailKind.GMAIL) {
                        PrimaryButton(text = "Gmail", onClick = { })
                    } else {
                        GhostButton(text = "Gmail", onClick = { kind = MailKind.GMAIL })
                    }
                    if (kind == MailKind.IMAP) {
                        PrimaryButton(text = "IMAP", onClick = { })
                    } else {
                        GhostButton(text = "IMAP", onClick = { kind = MailKind.IMAP })
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == MailKind.IMAP) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        placeholder = { Text(address.ifBlank { "Defaults to email" }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP host (for sending)") },
                        placeholder = { Text(guessedSmtp(host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (kind == MailKind.GMAIL) "App password" else "Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == MailKind.GMAIL) {
                    Text(
                        "Gmail needs an App Password, not your normal password. " +
                            "Create one at myaccount.google.com/apppasswords " +
                            "(2-Step Verification must be on).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConnect(
                        kind,
                        address,
                        password,
                        host,
                        port.toIntOrNull() ?: 993,
                        username,
                        smtpHost,
                    )
                },
            ) { Text("Connect and sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MailRow(message: MailMessage, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onOpen)
            .padding(horizontal = S.x4, vertical = S.x3),
        horizontalArrangement = Arrangement.spacedBy(S.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonogramAvatar(
            text = senderName(message.from),
            color = if (message.read) TextTertiary else MaterialTheme.colorScheme.primary,
            size = 36.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S.x1)) {
            Text(
                senderName(message.from),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                message.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold,
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                message.body.take(120),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            receivedLabel(message.receivedAtEpochMs),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

@Composable
private fun MailDetailDialog(
    message: MailMessage,
    canReply: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                message.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                Text(
                    "From ${message.from}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                if (message.to.isNotBlank()) {
                    Text(
                        "To ${message.to}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Text(
                    message.body.ifBlank { "(empty body)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        },
        confirmButton = {
            if (canReply) {
                TextButton(onClick = onReply) { Text("Reply") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canReply) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun ComposeDialog(
    initialTo: String,
    initialSubject: String,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, String, String) -> Unit,
) {
    var to by remember { mutableStateOf(initialTo) }
    var subject by remember { mutableStateOf(initialSubject) }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New mail") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = { onSend(to, subject, body) },
            ) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CollapsedGroup(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (expanded) title.uppercase() else "${title.uppercase()} ▸",
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = S.x4, vertical = S.x3),
    )
}

@Composable
private fun CandidateCard(
    candidate: EmailCandidate,
    showActions: Boolean,
    onPromote: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        level = 1,
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radius.full))
                    .background(Success),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S.x2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(S.x3),
                    verticalAlignment = Alignment.Top,
                ) {
                    MonogramAvatar(
                        text = senderName(candidate.from),
                        color = Success,
                        size = 40.dp,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S.x1)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(S.x2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                candidate.subject,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Pill(text = candidate.kind.name, color = kindColor(candidate.kind))
                        }
                        Text(
                            candidate.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            proposedLine(candidate.proposedStartIso),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
                ConfidenceMeter(candidate.confidence)
                if (showActions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(S.x2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PrimaryButton(
                            text = "Add",
                            onClick = onPromote,
                            icon = Icons.Outlined.Event,
                            modifier = Modifier.weight(1f),
                        )
                        IconGhostButton(
                            icon = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            onClick = onEdit,
                        )
                        IconGhostButton(
                            icon = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
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
                Spacer(Modifier.height(S.x2))
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
            ) { Text("Add to calendar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun kindColor(kind: CandidateKind): Color = when (kind) {
    CandidateKind.EXAM -> Danger
    CandidateKind.DEADLINE -> Warn
    CandidateKind.EVENT -> Success
    CandidateKind.NOISE -> TextTertiary
}

private val proposedTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

internal fun proposedLine(iso: String?): String {
    if (iso == null) return "No date found — tap Edit"
    val dt = Time.parseIsoOrNull(iso) ?: return "Proposed: $iso"
    val day = dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    return "Proposed: $day ${dt.toLocalTime().format(proposedTimeFmt)}"
}

private val mailDateFmt = DateTimeFormatter.ofPattern("d MMM")

internal fun receivedLabel(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    return if (at.toLocalDate() == today) {
        at.toLocalTime().format(proposedTimeFmt)
    } else {
        at.format(mailDateFmt)
    }
}

internal fun guessedSmtp(imapHost: String): String {
    val host = imapHost.trim().lowercase()
    if (host.isBlank()) return "smtp.example.com"
    return if (host.startsWith("imap.")) "smtp." + host.removePrefix("imap.") else host
}

internal fun senderName(from: String): String {
    val trimmed = from.trim()
    val angle = trimmed.indexOf('<')
    if (angle > 0) {
        return trimmed.substring(0, angle).trim().trim('"', '\'')
    }
    val local = trimmed.substringBefore('@')
    return local.replace('.', ' ').replace('_', ' ').ifBlank { trimmed }
}
