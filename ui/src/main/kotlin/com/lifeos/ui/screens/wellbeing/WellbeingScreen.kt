package com.lifeos.ui.screens.wellbeing

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.DemoPackages
import com.lifeos.core.model.Action
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.Goal
import com.lifeos.core.model.InstalledApp
import com.lifeos.core.model.NetworkMode
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.AppToggleRow
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.TimeoutBar
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.theme.LifeOsRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun WellbeingScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: WellbeingViewModel = viewModel { WellbeingViewModel(UiPorts.value) }
    val state by vm.ports.lifeState.state.collectAsState()
    WellbeingContent(
        state = state,
        ports = vm.ports,
        onDispatch = vm::dispatch,
        onNavigate = onNavigate,
    )
}

@Composable
private fun WellbeingContent(
    state: CanonicalLifeState,
    ports: com.lifeos.core.Ports,
    onDispatch: (Action) -> Unit,
    onNavigate: (LifeOsDestination) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(ports.system.permissions()) }
    var launchable by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var usage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var appQuery by remember { mutableStateOf("") }
    var showCapPicker by remember { mutableStateOf(false) }
    var editingTimeout by remember { mutableStateOf<AppTimeout?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = ports.system.permissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissions = ports.system.permissions()
    }

    LaunchedEffect(Unit) {
        launchable = ports.apps.launchableApps()
    }

    val labelsByPackage = remember(launchable) {
        launchable.associate { it.packageName to it.label }
    }

    val timeoutPackages = remember(state.appTimeouts) {
        state.appTimeouts.map { it.packageName }
    }
    LaunchedEffect(timeoutPackages) {
        while (isActive) {
            if (timeoutPackages.isNotEmpty()) {
                usage = withContext(Dispatchers.IO) {
                    ports.enforce.usageTodayMinutes(timeoutPackages)
                }
            }
            delay(15_000)
        }
    }

    LaunchedEffect(state.focus.active, state.focus.endsAtEpochMs) {
        while (isActive && state.focus.active && state.focus.endsAtEpochMs != null) {
            nowEpochMs = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val visibleApps = remember(launchable, appQuery) {
        launchable
            .filter { it.packageName !in DemoPackages.ALWAYS_ALLOW }
            .filter {
                appQuery.isBlank() ||
                    it.label.contains(appQuery, ignoreCase = true) ||
                    it.packageName.contains(appQuery, ignoreCase = true)
            }
    }

    val appsHeader = if (state.focus.mode == FocusMode.WHITELIST) {
        "Apps — checked apps are allowed"
    } else {
        "Apps — checked apps are blocked"
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FocusSection(
                state = state,
                nowEpochMs = nowEpochMs,
                enforcementReady = permissions.enforcementReady,
                onDispatch = onDispatch,
                onOpenMore = { onNavigate(LifeOsDestination.MORE) },
            )
        }
        item { SectionHeader("App daily caps") }
        if (state.appTimeouts.isEmpty()) {
            item {
                Text(
                    "No daily caps yet. Add one, or expand a goal in Chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(state.appTimeouts, key = { "cap:${it.packageName}" }) { timeout ->
            CapRow(
                timeout = timeout,
                label = friendlyAppName(timeout.packageName, labelsByPackage),
                usedMinutes = usage[timeout.packageName] ?: 0,
                sourceLabel = sourceLabelFor(timeout, state.goals),
                onEdit = { editingTimeout = timeout },
                onRemove = { onDispatch(Action.ClearAppTimeout(packageName = timeout.packageName)) },
            )
        }
        item {
            OutlinedButton(
                onClick = { showCapPicker = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Add cap")
            }
        }
        item { SectionHeader(appsHeader) }
        item {
            OutlinedTextField(
                value = appQuery,
                onValueChange = { appQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search apps") },
                singleLine = true,
            )
        }
        items(visibleApps, key = { "app:${it.packageName}" }) { app ->
            val checked = app.packageName in state.focus.packages
            AppToggleRow(
                app = app,
                checked = checked,
                onCheckedChange = { on ->
                    val updated = if (on) {
                        (state.focus.packages + app.packageName).distinct()
                    } else {
                        state.focus.packages - app.packageName
                    }
                    onDispatch(Action.FocusSetApps(state.focus.mode, updated))
                },
            )
        }
        item {
            NetworkSection(
                state = state,
                vpnConsented = permissions.vpnConsented,
                onDispatch = onDispatch,
                onGrantVpn = {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        vpnLauncher.launch(prepare)
                    } else {
                        permissions = ports.system.permissions()
                    }
                },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (showCapPicker) {
        CapPickerDialog(
            apps = launchable.filter {
                it.packageName !in DemoPackages.ALWAYS_ALLOW &&
                    state.appTimeouts.none { t -> t.packageName == it.packageName }
            },
            onPick = { pkg ->
                onDispatch(Action.SetAppTimeout(pkg, 30, null))
                showCapPicker = false
            },
            onDismiss = { showCapPicker = false },
        )
    }

    editingTimeout?.let { timeout ->
        EditLimitDialog(
            timeout = timeout,
            appLabel = friendlyAppName(timeout.packageName, labelsByPackage),
            onConfirm = { minutes ->
                onDispatch(
                    Action.SetAppTimeout(
                        packageName = timeout.packageName,
                        limitMinutes = minutes,
                        sourceGoalId = timeout.sourceGoalId,
                    ),
                )
                editingTimeout = null
            },
            onDismiss = { editingTimeout = null },
        )
    }
}

@Composable
private fun FocusSection(
    state: CanonicalLifeState,
    nowEpochMs: Long,
    enforcementReady: Boolean,
    onDispatch: (Action) -> Unit,
    onOpenMore: () -> Unit,
) {
    val focus = state.focus
    val active = focus.active
    val statusText = if (active) {
        val ends = focus.endsAtEpochMs
        if (ends != null) {
            val leftMin = ((ends - nowEpochMs) / 60_000L).coerceAtLeast(0)
            "Active · ${leftMin}m left"
        } else {
            "Active"
        }
    } else {
        "Off"
    }
    val statusColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Focus session", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape),
            )
            Text(
                statusText,
                color = statusColor,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FocusMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = focus.mode == mode,
                    onClick = { onDispatch(Action.FocusSetApps(mode, focus.packages)) },
                    shape = SegmentedButtonDefaults.itemShape(index, FocusMode.entries.size),
                ) {
                    Text(if (mode == FocusMode.WHITELIST) "Whitelist" else "Blacklist")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!enforcementReady) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(LifeOsRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Usage access and overlay required",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onOpenMore) { Text("Open More") }
                }
            }
        } else if (active) {
            Button(onClick = { onDispatch(Action.FocusStop) }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = {
                    onDispatch(Action.FocusStart(focus.mode, focus.packages, minutes = 50))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
private fun CapRow(
    timeout: AppTimeout,
    label: String,
    usedMinutes: Int,
    sourceLabel: String?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            TimeoutBar(
                label = label,
                usedMinutes = usedMinutes,
                limitMinutes = timeout.limitMinutes,
                sourceLabel = sourceLabel,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Cap options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit limit") },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun NetworkSection(
    state: CanonicalLifeState,
    vpnConsented: Boolean,
    onDispatch: (Action) -> Unit,
    onGrantVpn: () -> Unit,
) {
    val modes = listOf(NetworkMode.OFF, NetworkMode.BLACKLIST, NetworkMode.WHITELIST)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Network guard", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.network.mode == mode,
                    onClick = { onDispatch(Action.NetworkSetMode(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) {
                    Text(
                        when (mode) {
                            NetworkMode.OFF -> "Off"
                            NetworkMode.BLACKLIST -> "Blacklist"
                            NetworkMode.WHITELIST -> "Whitelist"
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Blocks network for selected apps using an on-device VPN. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!vpnConsented) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGrantVpn, modifier = Modifier.fillMaxWidth()) {
                Text("Grant VPN permission")
            }
        }
        TextButton(onClick = { onDispatch(Action.NetworkSetApps(state.focus.packages)) }) {
            Text("Same as focus list")
        }
    }
}

@Composable
private fun CapPickerDialog(
    apps: List<InstalledApp>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(LifeOsRadius),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp).heightIn(max = 480.dp)) {
                Text("Add cap", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = { onPick(app.packageName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(app.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun EditLimitDialog(
    timeout: AppTimeout,
    appLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember {
        mutableIntStateOf(timeout.limitMinutes.coerceIn(5, 120))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit limit") },
        text = {
            Column {
                Text(
                    "$appLabel · ${minutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceIn(5, 120) },
                    valueRange = 5f..120f,
                    steps = 22,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun friendlyAppName(packageName: String, labels: Map<String, String>): String {
    labels[packageName]?.let { return it }
    DemoPackages.ALIASES.entries
        .firstOrNull { it.value.equals(packageName, ignoreCase = true) }
        ?.key
        ?.split(' ')
        ?.joinToString(" ") { part -> part.replaceFirstChar { ch -> ch.uppercase() } }
        ?.let { return it }
    val last = packageName.substringAfterLast('.')
    if (last.equals("android", ignoreCase = true)) {
        return packageName.substringBeforeLast('.').substringAfterLast('.')
            .replaceFirstChar { it.uppercase() }
    }
    return last.replaceFirstChar { it.uppercase() }
}

internal fun sourceLabelFor(timeout: AppTimeout, goals: List<Goal>): String? {
    val goalId = timeout.sourceGoalId ?: return null
    return goals.firstOrNull { it.id == goalId }?.title
}
