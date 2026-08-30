package com.lifeos.enforce.focus

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lifeos.core.DemoPackages
import com.lifeos.core.LifeOsLog
import com.lifeos.enforce.EnforceHolder
import com.lifeos.enforce.R
import com.lifeos.enforce.notify.NotificationChannels
import com.lifeos.enforce.usage.UsageStatsHelper
import com.lifeos.enforce.vpn.BlockedDomainSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timeoutMonitor = TimeoutMonitor()
    private val overrides = mutableMapOf<String, Long>()
    private val labelCache = mutableMapOf<String, String>()

    private lateinit var overlay: OverlayController
    private lateinit var usage: UsageStatsHelper

    private var loopJob: Job? = null
    private var usageCached: Map<String, Int> = emptyMap()
    private var usageCachedAtMs: Long = 0L
    private var browsersCache: Set<String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usage = UsageStatsHelper(this)
        overlay = OverlayController(applicationContext) { pkg ->
            overrides[pkg] = System.currentTimeMillis() + OVERRIDE_GRACE_MS
            LifeOsLog.d("LifeOS/Focus", "override grace 10m for $pkg")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        overlay.hide()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NotificationChannels.NOTIF_FOCUS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationChannels.NOTIF_FOCUS, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        NotificationChannels.ensureAll(this)
        val launch = Intent().setClassName(packageName, "$packageName.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = runCatching {
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }.getOrNull()
        val state = EnforceHolder.lifeState?.state?.value
        val snap = EnforceHolder.rules
        val caps = snap?.timeouts?.size ?: state?.appTimeouts?.size ?: 0
        val focusOn = snap?.focus?.active == true || state?.focus?.active == true
        val text = when {
            focusOn && caps > 0 -> "Focus session · $caps daily caps"
            focusOn -> "Focus session is on"
            caps > 0 -> "$caps daily app caps"
            else -> "Watching your apps"
        }
        return NotificationCompat.Builder(this, NotificationChannels.FOCUS)
            .setContentTitle(getString(R.string.focus_watching_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_lifeos)
            .setColor(NotificationChannels.ACCENT)
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val keepGoing = runCatching { tick() }.getOrElse { err ->
                LifeOsLog.d("LifeOS/Focus", "tick failed: ${err.message}")
                true
            }
            if (!keepGoing) return
            delay(TICK_MS)
        }
    }

    private fun tick(): Boolean {
        val store = EnforceHolder.lifeState
        if (store == null) {
            overlay.hide()
            stopSelf()
            return false
        }
        val state = store.state.value
        if (!shouldRun(state, EnforceHolder.rules)) {
            overlay.hide()
            stopSelf()
            return false
        }
        val rules = resolveRules(state, EnforceHolder.rules)
        val effective = effectiveFocus(rules.focus)
        val fg = usage.foregroundPackage() ?: return true
        if (fg == packageName || fg in DemoPackages.ALWAYS_ALLOW) {
            overlay.hide()
            return true
        }
        // An override buys time out of a focus session only. Daily caps keep applying, or
        // one tap during a focus block would quietly buy 10 minutes past every cap too.
        val overridden = overrideActiveFor(fg)
        val focusViolation = !overridden && effective.active && violates(fg, effective)
        val used = usedMinutes(fg, rules.timeouts.map { it.packageName })
        val timeoutHit = timeoutMonitor.exceeded(fg, used, rules)

        val blockedDomain = blockedDomainToShow(fg, state.network.domains.isNotEmpty())

        when {
            focusViolation -> {
                val copy = FocusCopy.forFocus(appLabel(fg), effective, rules)
                overlay.show(BlockReason.FOCUS, copy.title, copy.subtitle, copy.sourceLabel, fg)
            }
            timeoutHit != null -> {
                val copy = FocusCopy.forTimeout(appLabel(fg), timeoutHit.limitMinutes, rules)
                overlay.show(BlockReason.TIMEOUT, copy.title, copy.subtitle, copy.sourceLabel, fg)
            }
            blockedDomain != null -> {
                val copy = FocusCopy.forDomain(blockedDomain, rules)
                overlay.show(BlockReason.DOMAIN, copy.title, copy.subtitle, copy.sourceLabel, fg)
            }
            else -> overlay.hide()
        }
        return true
    }

    /**
     * Only browsers get the domain block screen. Background apps also trigger blocked lookups,
     * and covering the screen for those would be indistinguishable from a random overlay.
     */
    private fun blockedDomainToShow(fg: String, domainsActive: Boolean): String? {
        if (!domainsActive || fg !in browserPackages()) return null
        return BlockedDomainSignal.recent(DOMAIN_BLOCK_WINDOW_MS)
    }

    private fun browserPackages(): Set<String> {
        browsersCache?.let { return it }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved: Set<String> = runCatching {
            packageManager
                .queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
                .mapTo(mutableSetOf()) { it.activityInfo.packageName }
        }.getOrDefault(mutableSetOf())
        browsersCache = resolved
        LifeOsLog.d("LifeOS/Focus", "browsers=$resolved")
        return resolved
    }

    private fun overrideActiveFor(pkg: String): Boolean {
        val expiry = overrides[pkg] ?: return false
        if (System.currentTimeMillis() >= expiry) {
            overrides.remove(pkg)
            return false
        }
        return true
    }

    private fun usedMinutes(pkg: String, timeoutPkgs: List<String>): Int {
        val now = System.currentTimeMillis()
        if (now - usageCachedAtMs >= USAGE_CACHE_MS || pkg !in usageCached) {
            usageCached = usage.usageTodayMinutes((timeoutPkgs + pkg).distinct())
            usageCachedAtMs = now
        }
        return usageCached[pkg] ?: 0
    }

    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            val info = packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
    }

    companion object {
        private const val TICK_MS = 800L
        private const val USAGE_CACHE_MS = 10_000L
        private const val OVERRIDE_GRACE_MS = 10 * 60_000L
        private const val DOMAIN_BLOCK_WINDOW_MS = 6_000L
    }
}
