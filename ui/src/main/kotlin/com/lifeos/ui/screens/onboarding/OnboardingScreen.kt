package com.lifeos.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifeos.ui.UiPorts
import com.lifeos.ui.components.PermissionRow
import com.lifeos.ui.components.PrimaryButton
import com.lifeos.ui.theme.AccentVivid
import com.lifeos.ui.theme.AccentWash
import com.lifeos.ui.theme.Radius
import com.lifeos.ui.theme.S
import com.lifeos.ui.theme.SuccessWash
import com.lifeos.ui.theme.TextSecondary
import com.lifeos.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val STEP_COUNT = 5

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })

    var status by remember { mutableStateOf(UiPorts.value.system.permissions()) }
    var calendarGranted by remember { mutableStateOf(calendarGranted(context)) }

    fun refreshStatus() {
        if (UiPorts.isReady) status = UiPorts.value.system.permissions()
        calendarGranted = calendarGranted(context)
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

    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshStatus() }

    val leaveAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStatus() }

    val packageName = context.packageName

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

    fun advance() {
        scope.launch {
            if (pagerState.currentPage < STEP_COUNT - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                complete()
            }
        }
    }

    val page = pagerState.currentPage
    val spec = stepSpec(page)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = S.x6),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = ::complete) {
                    Text("Skip", color = TextSecondary)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { step ->
                val stepSpec = stepSpec(step)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlyphHalo(
                        icon = stepSpec.icon,
                        celebrate = step == 4 && pagerState.currentPage == 4,
                    )
                    Spacer(Modifier.height(S.x6))
                    Text(
                        text = stepSpec.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(S.x3))
                    Text(
                        text = stepSpec.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(S.x6))
                    when (step) {
                        0 -> AnimatedGrantRow(
                            granted = status.notifications,
                            content = {
                                PermissionRow(
                                    title = "Notifications",
                                    subtitle = "So focus sessions and alarms can reach you.",
                                    granted = status.notifications,
                                    onGrant = {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                )
                            },
                        )
                        1 -> AnimatedGrantRow(
                            granted = status.exactAlarms,
                            content = {
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
                            },
                        )
                        2 -> AnimatedGrantRow(
                            granted = status.usageAccess,
                            content = {
                                PermissionRow(
                                    title = "Usage access",
                                    subtitle = "So LifeOS can tell which app is in front of you.\nFind LifeOS in the list and turn it on.",
                                    granted = status.usageAccess,
                                    onGrant = {
                                        leaveAppLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    },
                                )
                            },
                        )
                        3 -> AnimatedGrantRow(
                            granted = status.overlay,
                            content = {
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
                            },
                        )
                        else -> {
                            AnimatedGrantRow(
                                granted = status.vpnConsented,
                                content = {
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
                                },
                            )
                            AnimatedGrantRow(
                                granted = calendarGranted,
                                content = {
                                    PermissionRow(
                                        title = "Calendar — Optional",
                                        subtitle = "Mirror LifeOS events into the calendar app on this phone.",
                                        granted = calendarGranted,
                                        onGrant = {
                                            calendarLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_CALENDAR,
                                                    Manifest.permission.WRITE_CALENDAR,
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            PrimaryButton(
                text = spec.cta,
                onClick = {
                    when (page) {
                        0 -> if (!status.notifications) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        1 -> if (!status.exactAlarms) {
                            leaveAppLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                },
                            )
                        }
                        2 -> if (!status.usageAccess) {
                            leaveAppLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        3 -> if (!status.overlay) {
                            leaveAppLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.parse("package:$packageName")
                                },
                            )
                        }
                    }
                    advance()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(S.x4))
            ProgressDots(count = STEP_COUNT, selected = page)
            Spacer(Modifier.height(S.x4))
        }
    }
}

private data class StepSpec(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val cta: String,
)

private fun stepSpec(page: Int): StepSpec = when (page) {
    0 -> StepSpec(
        icon = Icons.Outlined.Notifications,
        title = "Hear from LifeOS",
        body = "Focus sessions and alarms need a way to reach you. Everything stays on this device.",
        cta = "Continue",
    )
    1 -> StepSpec(
        icon = Icons.Outlined.Alarm,
        title = "Wake you on time",
        body = "Exact alarms fire at 7am, not whenever the system feels like it.",
        cta = "Continue",
    )
    2 -> StepSpec(
        icon = Icons.Outlined.QueryStats,
        title = "Know what's in front",
        body = "Usage access lets LifeOS see the app you're in — so a cap can actually enforce.",
        cta = "Continue",
    )
    3 -> StepSpec(
        icon = Icons.Outlined.Layers,
        title = "Step in when it matters",
        body = "The block screen appears over the app you're avoiding. That's the whole product.",
        cta = "Continue",
    )
    else -> StepSpec(
        icon = Icons.Outlined.CheckCircle,
        title = "You're set",
        body = "Optional extras below. Skip anything — nothing here is a gate.",
        cta = "Let's go",
    )
}

@Composable
private fun GlyphHalo(icon: ImageVector, celebrate: Boolean) {
    Box(
        modifier = Modifier.size(168.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (celebrate) {
            ParticleBurst(active = true)
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(AccentWash),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

private data class BurstParticle(
    val angleDeg: Float,
    val dist: Float,
    val radius: Float,
    val delay: Float,
    val vivid: Boolean,
)

@Composable
private fun ParticleBurst(active: Boolean) {
    val particles = remember {
        List(36) { i ->
            BurstParticle(
                angleDeg = i * 10f + (i % 5) * 3f,
                dist = 0.42f + (i % 5) * 0.10f,
                radius = 2.2f + (i % 3).toFloat(),
                delay = (i % 8) * 0.04f,
                vivid = i % 3 == 0,
            )
        }
    }
    val t by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = LinearOutSlowInEasing),
        label = "burst",
    )
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(168.dp)) {
        if (t <= 0f) return@Canvas
        particles.forEach { p ->
            val localT = ((t - p.delay) / (1f - p.delay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEach
            val rad = size.minDimension * 0.5f * p.dist * localT
            val a = Math.toRadians(p.angleDeg.toDouble())
            val alpha = (1f - localT) * 0.90f
            drawCircle(
                color = (if (p.vivid) AccentVivid else primary).copy(alpha = alpha),
                radius = p.radius.dp.toPx(),
                center = center + Offset(cos(a).toFloat() * rad, sin(a).toFloat() * rad),
            )
        }
    }
}

@Composable
private fun AnimatedGrantRow(granted: Boolean, content: @Composable () -> Unit) {
    val wash by animateColorAsState(
        targetValue = if (granted) SuccessWash else Color.Transparent,
        label = "grantWash",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(wash)
            .padding(horizontal = S.x2, vertical = S.x1),
    ) {
        content()
    }
}

@Composable
private fun ProgressDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == selected
            val size by animateDpAsState(if (active) 8.dp else 6.dp, label = "dot$i")
            Box(
                modifier = Modifier
                    .padding(horizontal = S.x1)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else TextTertiary),
            )
        }
    }
}

private fun calendarGranted(context: Context): Boolean {
    val read = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    val write = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    return read && write
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
