package com.lifeos.ui.screens.wellbeing

import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeos.core.DemoPackages
import com.lifeos.core.Ports
import com.lifeos.core.model.Action
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.CanonicalLifeState
import com.lifeos.core.model.FocusMode
import com.lifeos.core.model.Goal
import com.lifeos.core.model.InstalledApp
import com.lifeos.core.model.NetworkMode
import com.lifeos.core.model.PermissionStatus
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.AppToggleRow
import com.lifeos.ui.components.GhostButton
import com.lifeos.ui.components.LifeOsCard
import com.lifeos.ui.components.MonogramAvatar
import com.lifeos.ui.components.Pill
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.components.ProgressRing
import com.lifeos.ui.components.SectionHeader
import com.lifeos.ui.components.SegmentedControl
import com.lifeos.ui.components.TimeoutBar
import com.lifeos.ui.components.pressable
import com.lifeos.ui.nav.LifeOsDestination
import com.lifeos.ui.nav.LocalScreenPadding
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.Motion
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.Surface2
import com.lifeos.ui.theme.TextPrimary
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import com.lifeos.ui.theme.TimeNumeric
import com.lifeos.ui.theme.Warn
import com.lifeos.ui.theme.WarnWash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DurationPresets = listOf(25, 50, 90)

private enum class AppPickerKind { FOCUS, CAP, NETWORK }

@Composable
fun WellbeingScreen(onNavigate: (LifeOsDestination) -> Unit) {
    val vm: WellbeingViewModel = viewModel { WellbeingViewModel(UiPorts.value) }
    val state by vm.ports.lifeState.state.collectAsState()
    WellbeingContent(
        state = state,
        ports = vm.ports,
        onDispatch = vm::dispatch,
        onReopenOnboarding = vm::reopenOnboarding,
        onNavigate = onNavigate,
    )
}

@Composable
private fun WellbeingContent(
    state: CanonicalLifeState,
    ports: Ports,
    onDispatch: (Action) -> Unit,
    onReopenOnboarding: () -> Unit,
    onNavigate: (LifeOsDestination) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val screenPadding = LocalScreenPadding.current
    var permissions by remember { mutableStateOf(ports.system.permissions()) }
    var launchable by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var usage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var allUsage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickerKind by remember { mutableStateOf<AppPickerKind?>(null) }
    var editingTimeout by remember { mutableStateOf<AppTimeout?>(null) }
    var durationMin by rememberSaveable { mutableIntStateOf(50) }

    val uniqueApps = remember(launchable) { launchable.distinctBy { it.packageName } }
    val labelsByPackage = remember(uniqueApps) {
        uniqueApps.associate { it.packageName to it.label }
    }
    val timeouts = remember(state.appTimeouts) {
        state.appTimeouts.distinctBy { it.packageName }
    }
    val timeoutPackages = remember(timeouts) { timeouts.map { it.packageName } }

    fun refreshUsage(packages: List<String>) {
        scope.launch {
            val all = withContext(Dispatchers.IO) { ports.enforce.usageTodayAll() }
            allUsage = all
            usage = packages.associateWith { all[it] ?: 0 }
        }
    }

    DisposableEffect(lifecycleOwner, timeoutPackages) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = ports.system.permissions()
                refreshUsage(timeoutPackages)
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
        launchable = ports.apps.launchableApps().distinctBy { it.packageName }
    }

    LaunchedEffect(timeoutPackages) {
        refreshUsage(timeoutPackages)
    }

    LaunchedEffect(state.focus.active) {
        while (isActive && state.focus.active) {
            nowEpochMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val pickerApps = remember(uniqueApps) {
        uniqueApps.filter { it.packageName !in DemoPackages.ALWAYS_ALLOW }
    }

    // Restricted to launchable apps so system services do not dominate the list.
    val rankedUsage = remember(allUsage, labelsByPackage) {
        allUsage.entries
            .filter { it.value > 0 && it.key in labelsByPackage && it.key != context.packageName }
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }
    val screenTime = remember(rankedUsage) { rankedUsage.take(10) }
    // Totalled over every app, not just the ten shown, so it matches what the agent reports.
    val screenTimeTotal = remember(rankedUsage) { rankedUsage.sumOf { it.second } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding,
    ) {
        if (!permissions.enforcementReady) {
            item {
                PermissionBanner(
                    permissions = permissions,
                    onFix = onReopenOnboarding,
                    onOpenMore = { onNavigate(LifeOsDestination.MORE) },
                )
            }
        }
        item {
            FocusHero(
                state = state,
                nowEpochMs = nowEpochMs,
                durationMin = durationMin,
                labelsByPackage = labelsByPackage,
                enforcementReady = permissions.enforcementReady,
                onDuration = { durationMin = it },
                onDispatch = onDispatch,
                onChooseApps = { pickerKind = AppPickerKind.FOCUS },
                onNeedPermissions = onReopenOnboarding,
            )
        }
        item {
            ScreenTimeHeader(
                totalMinutes = screenTimeTotal,
                usageGranted = permissions.usageAccess,
                measured = allUsage.isNotEmpty(),
            )
        }
        items(screenTime, key = { "usage:${it.first}" }) { (pkg, minutes) ->
            UsageRow(
                label = friendlyAppName(pkg, labelsByPackage),
                minutes = minutes,
                peakMinutes = screenTime.firstOrNull()?.second ?: minutes,
                capped = pkg in timeoutPackages,
                onCap = { onDispatch(Action.SetAppTimeout(pkg, 30, null)) },
            )
        }
        item {
            TimeoutHeader(
                count = timeouts.size,
                onAdd = { pickerKind = AppPickerKind.CAP },
            )
        }
        if (timeouts.isEmpty()) {
            item {
                Text(
                    "No daily caps yet. Add one, or expand a goal in Chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = S.x4, vertical = S.x1),
                )
            }
        }
        items(timeouts, key = { "cap:${it.packageName}" }) { timeout ->
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
            NetworkSection(
                state = state,
                labelsByPackage = labelsByPackage,
                vpnConsented = permissions.vpnConsented,
                onDispatch = onDispatch,
                onPickApps = { pickerKind = AppPickerKind.NETWORK },
                onGrantVpn = {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        vpnLauncher.launch(prepare)
                    } else {
                        permissions = ports.system.permissions()
                    }
                },
                onOpenVpnSettings = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_VPN_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
            )
        }
        item { Spacer(Modifier.height(S.x8)) }
    }

    pickerKind?.let { kind ->
        val initial = when (kind) {
            AppPickerKind.FOCUS -> state.focus.packages.toSet()
            AppPickerKind.NETWORK -> state.network.packages.toSet()
            AppPickerKind.CAP -> emptySet()
        }
        val available = when (kind) {
            AppPickerKind.CAP -> pickerApps.filter { app ->
                timeouts.none { it.packageName == app.packageName }
            }
            else -> pickerApps
        }
        AppPickerSheet(
            kind = kind,
            apps = available.distinctBy { it.packageName },
            initiallySelected = initial,
            onConfirm = { selected ->
                when (kind) {
                    AppPickerKind.FOCUS -> onDispatch(Action.FocusSetApps(state.focus.mode, selected))
                    AppPickerKind.NETWORK -> onDispatch(Action.NetworkSetApps(selected))
                    AppPickerKind.CAP -> selected.forEach { pkg ->
                        onDispatch(Action.SetAppTimeout(pkg, 30, null))
                    }
                }
                pickerKind = null
            },
            onDismiss = { pickerKind = null },
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
private fun PermissionBanner(
    permissions: PermissionStatus,
    onFix: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val missing = buildList {
        if (!permissions.usageAccess) add("Usage access")
        if (!permissions.overlay) add("Display over other apps")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S.x4, vertical = S.x2)
            .clip(RoundedCornerShape(Radius.lg))
            .background(WarnWash)
            .border(1.dp, Warn.copy(alpha = 0.35f), RoundedCornerShape(Radius.lg))
            .pressable(onFix)
            .padding(S.x4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(S.x2)) {
            Text(
                "Focus cannot enforce yet",
                style = MaterialTheme.typography.titleSmall,
                color = Warn,
            )
            Text(
                "Missing: ${missing.joinToString(" · ")}. Sessions and caps will silently do nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
                PrimaryButton(text = "Open onboarding", onClick = onFix)
                GhostButton(text = "More", onClick = onOpenMore)
            }
        }
    }
}

@Composable
private fun FocusHero(
    state: CanonicalLifeState,
    nowEpochMs: Long,
    durationMin: Int,
    labelsByPackage: Map<String, String>,
    enforcementReady: Boolean,
    onDuration: (Int) -> Unit,
    onDispatch: (Action) -> Unit,
    onChooseApps: () -> Unit,
    onNeedPermissions: () -> Unit,
) {
    val focus = state.focus
    val active = focus.active
    val modeLabel = if (focus.mode == FocusMode.WHITELIST) "Whitelist" else "Blacklist"
    val selectedDuration = DurationPresets.indexOf(durationMin).coerceAtLeast(0)
    val ends = focus.endsAtEpochMs
    val started = focus.startedAtEpochMs
    val remainingMs = if (active && ends != null) (ends - nowEpochMs).coerceAtLeast(0L) else 0L
    val totalMs = if (ends != null && started != null && ends > started) {
        ends - started
    } else {
        durationMin * 60_000L
    }
    val ringProgress = if (active && totalMs > 0L) {
        remainingMs.toFloat() / totalMs.toFloat()
    } else {
        0f
    }
    val avatarPkgs = focus.packages.distinct().take(6)
    val overflow = (focus.packages.distinct().size - avatarPkgs.size).coerceAtLeast(0)

    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x2),
        level = 2,
        active = active,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Focus", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Pill(
                text = modeLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (focus.mode == FocusMode.WHITELIST) {
                "Checked apps stay allowed. Everything else is blocked."
            } else {
                "Checked apps are blocked."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = S.x1),
        )
        Spacer(Modifier.height(S.x5))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = ringProgress,
                size = 168.dp,
                strokeWidth = 10.dp,
                color = AccentVivid,
            ) {
                if (active) {
                    Text(
                        text = formatCountdown(remainingMs),
                        style = MaterialTheme.typography.displayMedium.merge(TimeNumeric),
                        color = TextPrimary,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                if (enforcementReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .pressable {
                                if (!enforcementReady) {
                                    onNeedPermissions()
                                } else {
                                    onDispatch(
                                        Action.FocusStart(focus.mode, focus.packages, minutes = durationMin),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Start",
                            color = if (enforcementReady) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                TextTertiary
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(S.x5))
        if (!active) {
            SegmentedControl(
                options = DurationPresets.map { "${it} min" },
                selectedIndex = selectedDuration,
                onSelect = { onDuration(DurationPresets[it]) },
            )
            Spacer(Modifier.height(S.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                FocusAvatars(packages = avatarPkgs, overflow = overflow, labels = labelsByPackage)
                Spacer(Modifier.weight(1f))
                GhostButton(text = "Choose apps", onClick = onChooseApps)
            }
            Row(
                modifier = Modifier.padding(top = S.x3),
                horizontalArrangement = Arrangement.spacedBy(S.x2),
            ) {
                FocusMode.entries.forEach { mode ->
                    val selected = focus.mode == mode
                    val label = if (mode == FocusMode.WHITELIST) "Whitelist" else "Blacklist"
                    val color = if (selected) MaterialTheme.colorScheme.primary else TextTertiary
                    Box(Modifier.pressable { onDispatch(Action.FocusSetApps(mode, focus.packages)) }) {
                        Pill(text = label, color = color)
                    }
                }
            }
        } else {
            FocusAvatars(packages = avatarPkgs, overflow = overflow, labels = labelsByPackage)
            Spacer(Modifier.height(S.x4))
            PrimaryButton(
                text = "End",
                onClick = { onDispatch(Action.FocusStop) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FocusAvatars(
    packages: List<String>,
    overflow: Int,
    labels: Map<String, String>,
) {
    if (packages.isEmpty()) {
        Text("No apps selected", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy((-S.x2)), verticalAlignment = Alignment.CenterVertically) {
        packages.forEach { pkg ->
            MonogramAvatar(
                text = friendlyAppName(pkg, labels),
                color = AccentVivid,
                size = 32.dp,
            )
        }
        if (overflow > 0) {
            Text("+$overflow", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun TimeoutHeader(count: Int, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = S.x4, end = S.x4, top = S.x5, bottom = S.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "DAILY CAPS · $count",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        GhostButton(text = "Add cap", onClick = onAdd)
    }
}

@Composable
private fun ScreenTimeHeader(totalMinutes: Int, usageGranted: Boolean, measured: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = S.x4, end = S.x4, top = S.x5, bottom = S.x2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SCREEN TIME TODAY",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                text = if (totalMinutes >= 60) {
                    "${totalMinutes / 60}h ${totalMinutes % 60}m"
                } else {
                    "${totalMinutes}m"
                },
                style = TimeNumeric,
                color = TextPrimary,
            )
        }
        if (!measured) {
            Text(
                text = if (usageGranted) {
                    "No usage recorded yet today."
                } else {
                    "Usage access is off, so caps cannot measure anything. Grant it in More."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (usageGranted) TextSecondary else Warn,
                modifier = Modifier.padding(top = S.x2),
            )
        }
    }
}

@Composable
private fun UsageRow(
    label: String,
    minutes: Int,
    peakMinutes: Int,
    capped: Boolean,
    onCap: () -> Unit,
) {
    val ratio = if (peakMinutes <= 0) 0f else minutes.toFloat() / peakMinutes.toFloat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S.x4, vertical = S.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S.x3),
    ) {
        MonogramAvatar(text = label, color = AccentVivid, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(S.x1))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(Surface2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(AccentVivid),
                )
            }
        }
        Text(
            text = "${minutes}m",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        if (capped) {
            Pill(text = "capped", color = AccentVivid)
        } else {
            GhostButton(text = "Cap", onClick = onCap)
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
        Modifier
            .fillMaxWidth()
            .padding(end = S.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(horizontal = S.x4)) {
            TimeoutBar(
                label = label,
                usedMinutes = usedMinutes,
                limitMinutes = timeout.limitMinutes,
                sourceLabel = sourceLabel,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Cap options", tint = TextSecondary)
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
    labelsByPackage: Map<String, String>,
    vpnConsented: Boolean,
    onDispatch: (Action) -> Unit,
    onPickApps: () -> Unit,
    onGrantVpn: () -> Unit,
    onOpenVpnSettings: () -> Unit,
) {
    val modes = listOf(NetworkMode.OFF, NetworkMode.BLACKLIST, NetworkMode.WHITELIST)
    val selected = modes.indexOf(state.network.mode).coerceAtLeast(0)
    val shieldTarget = when (state.network.mode) {
        NetworkMode.OFF -> TextTertiary
        NetworkMode.BLACKLIST -> Warn
        NetworkMode.WHITELIST -> MaterialTheme.colorScheme.primary
    }
    val shieldColor by animateColorAsState(shieldTarget, label = "shieldTint")
    val shieldScale by animateFloatAsState(
        targetValue = if (state.network.mode == NetworkMode.OFF) 0.92f else 1.12f,
        animationSpec = Motion.emphasized,
        label = "shieldScale",
    )
    val netPkgs = state.network.packages.distinct()

    LifeOsCard(
        modifier = Modifier.padding(horizontal = S.x4, vertical = S.x3),
        level = 1,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S.x3),
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = shieldColor,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = shieldScale
                        scaleY = shieldScale
                    },
            )
            Text("Network guard", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Spacer(Modifier.height(S.x3))
        SegmentedControl(
            options = listOf("Off", "Blacklist", "Whitelist"),
            selectedIndex = selected,
            onSelect = { onDispatch(Action.NetworkSetMode(modes[it])) },
        )
        Spacer(Modifier.height(S.x2))
        Text(
            "On-device VPN. Selected apps lose network; listed domains are DNS-filtered. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        if (netPkgs.isNotEmpty()) {
            Spacer(Modifier.height(S.x3))
            FocusAvatars(
                packages = netPkgs.take(6),
                overflow = (netPkgs.size - 6).coerceAtLeast(0),
                labels = labelsByPackage,
            )
        } else {
            Spacer(Modifier.height(S.x2))
            Text("No apps selected.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        Spacer(Modifier.height(S.x3))
        Row(horizontalArrangement = Arrangement.spacedBy(S.x2)) {
            GhostButton(text = "Choose apps", onClick = onPickApps)
            GhostButton(
                text = "Same as focus",
                onClick = { onDispatch(Action.NetworkSetApps(state.focus.packages)) },
            )
        }
        if (!vpnConsented && state.network.mode != NetworkMode.OFF) {
            Spacer(Modifier.height(S.x3))
            PrimaryButton(
                text = "Grant VPN permission",
                onClick = onGrantVpn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.network.domains.isNotEmpty()) {
            Spacer(Modifier.height(S.x4))
            Text("DNS filtered domains", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(S.x1))
            Text(
                state.network.domains.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            TextButton(onClick = { onDispatch(Action.NetworkSetDomains(emptyList())) }) {
                Text("Unblock all domains")
            }
        }
        Spacer(Modifier.height(S.x4))
        AlwaysOnRow(onOpenVpnSettings = onOpenVpnSettings)
    }
}

/**
 * LifeOS restarts its own tunnel, but only Android's own always-on setting survives the user
 * (or the ROM) killing the app outright, and only lockdown blocks traffic while it is down.
 */
@Composable
private fun AlwaysOnRow(onOpenVpnSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(S.x2)) {
        Text("Keep it always on", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            "LifeOS reconnects the guard by itself, but Android can still stop it when the phone " +
                "is low on memory. In VPN settings, open the gear next to LifeOS and turn on " +
                "\"Always-on VPN\" plus \"Block connections without VPN\" to make it unskippable.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        GhostButton(
            text = "Open VPN settings",
            onClick = onOpenVpnSettings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    kind: AppPickerKind,
    apps: List<InstalledApp>,
    initiallySelected: Set<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initiallySelected) }
    val unique = remember(apps) { apps.distinctBy { it.packageName } }
    val filtered = remember(unique, query) {
        unique.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }.distinctBy { it.packageName }
    }
    val title = when (kind) {
        AppPickerKind.FOCUS -> "Focus apps"
        AppPickerKind.CAP -> "Add daily cap"
        AppPickerKind.NETWORK -> "Network apps"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = Surface2,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = S.x2),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S.x3, vertical = S.x2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S.x3, vertical = S.x2),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.xl),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { "app:${it.packageName}" }) { app ->
                    AppToggleRow(
                        app = app,
                        checked = app.packageName in selected,
                        onCheckedChange = { on ->
                            selected = if (on) selected + app.packageName else selected - app.packageName
                        },
                    )
                }
            }
            PrimaryButton(
                text = "Confirm",
                onClick = { onConfirm(selected.distinct()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(S.x4),
                enabled = kind != AppPickerKind.CAP || selected.isNotEmpty(),
            )
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

internal fun formatCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
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
