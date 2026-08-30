package com.lifeos.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifeos.core.Personas
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.PermissionRow
import com.lifeos.ui.theme.MdWarn
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val life by UiPorts.value.lifeState.state.collectAsState()

    var selectedPersonaId by remember { mutableStateOf(life.personaId) }
    LaunchedEffect(life.personaId) { selectedPersonaId = life.personaId }

    var status by remember { mutableStateOf(UiPorts.value.system.permissions()) }

    fun refreshStatus() {
        if (UiPorts.isReady) status = UiPorts.value.system.permissions()
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    val leaveAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStatus() }

    val packageName = context.packageName
    val scheme = MaterialTheme.colorScheme
    val allGranted = status.notifications &&
        status.exactAlarms &&
        status.usageAccess &&
        status.overlay &&
        status.vpnConsented
    val warnAlarms = !status.notifications || !status.exactAlarms

    fun complete() {
        scope.launch {
            if (UiPorts.isReady) {
                UiPorts.value.lifeState.mutate {
                    it.copy(settings = it.settings.copy(onboardingComplete = true))
                }
            }
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "LifeOS",
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Plans that enforce themselves.",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Personality",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Personas.ALL.forEach { persona ->
                FilterChip(
                    selected = selectedPersonaId == persona.id,
                    onClick = {
                        selectedPersonaId = persona.id
                        if (UiPorts.isReady) {
                            scope.launch {
                                UiPorts.value.executor.execute(
                                    listOf(Action.SetPersona(persona.id)),
                                    ActionOrigin.USER,
                                )
                            }
                        }
                    },
                    label = { Text(persona.name) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "LifeOS needs these to actually block apps and wake you up. Everything stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface.copy(alpha = 0.80f),
        )

        Spacer(Modifier.height(16.dp))
        PermissionRow(
            title = "Notifications",
            subtitle = "So focus sessions and alarms can reach you.",
            granted = status.notifications,
            onGrant = {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        PermissionRow(
            title = "Exact alarms",
            subtitle = "So a 7am wake-up fires at 7am, not whenever.",
            granted = status.exactAlarms,
            onGrant = {
                leaveAppLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", packageName, null)
                    },
                )
            },
        )
        PermissionRow(
            title = "Usage access",
            subtitle = "So LifeOS can tell which app is in front of you.\nFind LifeOS in the list and turn it on.",
            granted = status.usageAccess,
            onGrant = {
                leaveAppLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
        PermissionRow(
            title = "Display over other apps",
            subtitle = "So the block screen can appear over the app you're avoiding.",
            granted = status.overlay,
            onGrant = {
                leaveAppLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            },
        )
        PermissionRow(
            title = "Network control (VPN) — Optional",
            subtitle = "Optional. Filters traffic on your device — no remote server, no traffic leaves.",
            granted = status.vpnConsented,
            onGrant = {
                val activity = context.findActivity() ?: context
                val prepare = VpnService.prepare(activity)
                if (prepare != null) {
                    leaveAppLauncher.launch(prepare)
                } else {
                    refreshStatus()
                }
            },
        )

        if (warnAlarms) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Notifications or exact alarms are still off — alerts may be late or silent.",
                style = MaterialTheme.typography.bodySmall,
                color = MdWarn,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = ::complete,
            enabled = status.enforcementReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allGranted) "Done" else "Continue")
        }
        TextButton(
            onClick = ::complete,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        ) {
            Text("Skip for now")
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
