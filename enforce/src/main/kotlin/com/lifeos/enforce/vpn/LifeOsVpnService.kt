package com.lifeos.enforce.vpn

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.lifeos.core.DemoPackages
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.NetworkMode
import com.lifeos.enforce.EnforceHolder
import com.lifeos.enforce.R
import com.lifeos.enforce.notify.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class LifeOsVpnService : VpnService() {
    private val gate = Any()
    private var tunFd: ParcelFileDescriptor? = null
    private var blackhole: Thread? = null
    private var dnsFilter: DnsFilter? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryJob: Job? = null

    /** Bumped on every teardown so a dying tunnel thread cannot trigger a stale restart. */
    private val generation = AtomicInteger(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationChannels.ensureAll(this)
        // Promote immediately and unconditionally. Without this the process is a background
        // service and gets reclaimed, which is the usual reason the guard "turns itself off".
        promote(NetworkMode.OFF, emptyList(), starting = true)

        if (intent == null || intent.getBooleanExtra(EXTRA_RESUME, false)) {
            resumeFromPersistedRules()
            return START_STICKY
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_STOP || intent.action == ACTION_STOP) {
            LifeOsLog.d("LifeOS/Vpn", "ACTION_STOP")
            retryJob?.cancel()
            teardown()
            forgetRules()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        val mode = runCatching {
            NetworkMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrDefault(NetworkMode.OFF)
        val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty()
        val domains = intent.getStringArrayListExtra(EXTRA_DOMAINS).orEmpty()
        return applyRules(mode, packages, domains)
    }

    /**
     * The statics that cache the active rules die with the process, so a sticky restart has to
     * recover the intent from persisted state instead of giving up.
     */
    private fun resumeFromPersistedRules() {
        val cachedMode = lastMode
        val cachedDomains = lastDomains
        if ((cachedMode != null && cachedMode != NetworkMode.OFF) || cachedDomains.isNotEmpty()) {
            applyRules(cachedMode ?: NetworkMode.OFF, lastPackages, cachedDomains)
            return
        }
        val store = EnforceHolder.lifeState
        if (store == null) {
            LifeOsLog.d("LifeOS/Vpn", "resume: no store yet; waiting for retry")
            scheduleRetry()
            return
        }
        scope.launch {
            store.awaitLoaded()
            val net = store.state.value.network
            if (net.mode == NetworkMode.OFF && net.domains.isEmpty()) {
                LifeOsLog.d("LifeOS/Vpn", "resume: nothing configured; stop")
                withContext(Dispatchers.Main) { stopForegroundAndSelf() }
            } else {
                LifeOsLog.d(
                    "LifeOS/Vpn",
                    "resume from state mode=${net.mode} domains=${net.domains.size}",
                )
                withContext(Dispatchers.Main) {
                    applyRules(net.mode, net.packages, net.domains)
                }
            }
        }
    }

    override fun onRevoke() {
        // Another VPN took over or the user revoked consent. Keep the rules so the guard can
        // come back; erasing them here is what made blocks silently vanish for good.
        LifeOsLog.d("LifeOS/Vpn", "onRevoke; keeping rules for retry")
        teardown()
        postRevoked()
        stopSelf()
    }

    private fun forgetRules() {
        lastMode = null
        lastPackages = emptyList()
        lastDomains = emptyList()
    }

    override fun onDestroy() {
        retryJob?.cancel()
        scope.cancel()
        teardown()
        super.onDestroy()
    }

    private fun applyRules(mode: NetworkMode, packages: List<String>, domains: List<String>): Int {
        if (mode == NetworkMode.OFF && domains.isEmpty()) {
            retryJob?.cancel()
            teardown()
            forgetRules()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        lastMode = mode
        lastPackages = packages
        lastDomains = domains
        teardown()
        // Domain blocking needs real DNS answers, which is incompatible with a blackhole
        // tunnel, so it takes precedence when both are configured.
        val ok = synchronized(gate) {
            if (domains.isNotEmpty()) {
                if (mode != NetworkMode.OFF) {
                    LifeOsLog.d("LifeOS/Vpn", "domains set; DNS filter supersedes app $mode")
                }
                establishDnsFilter(domains)
            } else {
                establishBlackhole(mode, packages)
            }
        }
        if (!ok) {
            // Failures here are often transient (consent not yet regranted, boot not settled),
            // so stay alive and keep trying rather than dropping enforcement for good.
            LifeOsLog.d("LifeOS/Vpn", "establish failed mode=$mode domains=${domains.size}; retrying")
            promote(mode, domains, starting = true)
            scheduleRetry()
            return START_STICKY
        }
        retryJob?.cancel()
        promote(mode, domains, starting = false)
        return START_STICKY
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            var wait = RETRY_MIN_MS
            while (isActive) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
                val mode = lastMode
                val domains = lastDomains
                if ((mode == null || mode == NetworkMode.OFF) && domains.isEmpty()) {
                    LifeOsLog.d("LifeOS/Vpn", "retry: no rules left; give up")
                    return@launch
                }
                if (VpnService.prepare(this@LifeOsVpnService) != null) {
                    LifeOsLog.d("LifeOS/Vpn", "retry: consent still missing")
                    continue
                }
                val ok = synchronized(gate) {
                    if (domains.isNotEmpty()) {
                        establishDnsFilter(domains)
                    } else {
                        establishBlackhole(mode ?: NetworkMode.OFF, lastPackages)
                    }
                }
                if (ok) {
                    LifeOsLog.d("LifeOS/Vpn", "retry: tunnel restored")
                    promote(mode ?: NetworkMode.OFF, domains, starting = false)
                    return@launch
                }
            }
        }
    }

    /** Called when a tunnel worker exits on its own; rebuild if the rules are still wanted. */
    private fun onTunnelDied(bornAt: Int) {
        if (bornAt != generation.get()) return
        val mode = lastMode
        if ((mode == null || mode == NetworkMode.OFF) && lastDomains.isEmpty()) return
        LifeOsLog.d("LifeOS/Vpn", "tunnel thread died while active; scheduling rebuild")
        scheduleRetry()
    }

    private fun establishDnsFilter(domains: List<String>): Boolean {
        val builder = Builder()
            .setSession("LifeOS DNS guard")
            .addAddress("10.7.0.1", 32)
            .addDnsServer(DNS_SINK)
            // Route only the sink resolver: DNS is intercepted, all other traffic is
            // untouched, so the device stays online while blocked names fail to resolve.
            .addRoute(DNS_SINK, 32)
            .setBlocking(true)
            .setMtu(1500)
        // LifeOS itself must never depend on its own filter.
        runCatching { builder.addDisallowedApplication(packageName) }

        val fd = runCatching { builder.establish() }.getOrNull()
        if (fd == null) {
            LifeOsLog.d("LifeOS/Vpn", "establish() returned null (consent revoked)")
            return false
        }
        tunFd = fd
        val filter = DnsFilter(this, fd, domains)
        dnsFilter = filter
        val bornAt = generation.get()
        blackhole = thread(isDaemon = true, name = "lifeos-dns-filter") {
            try {
                filter.run()
            } finally {
                onTunnelDied(bornAt)
            }
        }
        LifeOsLog.d("LifeOS/Vpn", "dns tunnel up domains=$domains")
        return true
    }

    private fun establishBlackhole(mode: NetworkMode, packages: List<String>): Boolean {
        val builder = Builder()
            .setSession("LifeOS Focus")
            .addAddress("10.7.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setBlocking(true)
            .setMtu(1500)

        when (mode) {
            NetworkMode.BLACKLIST -> {
                if (packages.isEmpty()) {
                    LifeOsLog.d("LifeOS/Vpn", "blacklist empty; skip tunnel")
                    return false
                }
                var allowed = 0
                packages.forEach { pkg ->
                    // Blacklist starve X: only X enters the TUN blackhole; everyone else is untouched.
                    runCatching { builder.addAllowedApplication(pkg) }
                        .onSuccess { allowed++ }
                        .onFailure {
                            LifeOsLog.d("LifeOS/Vpn", "skip missing package $pkg: ${it.message}")
                        }
                }
                if (allowed == 0) {
                    LifeOsLog.d("LifeOS/Vpn", "no blacklist packages installed; skip tunnel")
                    return false
                }
            }
            NetworkMode.WHITELIST -> {
                val bypass = (packages + DemoPackages.ALWAYS_ALLOW).distinct()
                var disallowed = 0
                bypass.forEach { pkg ->
                    // Whitelist: allowed + ALWAYS_ALLOW bypass the tunnel; everyone else is starved.
                    runCatching { builder.addDisallowedApplication(pkg) }
                        .onSuccess { disallowed++ }
                        .onFailure {
                            LifeOsLog.d("LifeOS/Vpn", "skip missing package $pkg: ${it.message}")
                        }
                }
                logIfWhitelistCoversAll(bypass)
                LifeOsLog.d("LifeOS/Vpn", "whitelist bypass count=$disallowed")
            }
            NetworkMode.OFF -> return false
        }

        val fd = runCatching { builder.establish() }.getOrNull()
        if (fd == null) {
            LifeOsLog.d("LifeOS/Vpn", "establish() returned null (consent revoked)")
            return false
        }
        tunFd = fd
        val bornAt = generation.get()
        blackhole = thread(isDaemon = true, name = "lifeos-vpn-blackhole") {
            val input = FileInputStream(fd.fileDescriptor)
            val buf = ByteArray(32 * 1024)
            try {
                while (!Thread.interrupted()) {
                    if (input.read(buf) <= 0) break
                }
            } catch (_: IOException) {
                // fd closed on stop
            } finally {
                runCatching { input.close() }
                onTunnelDied(bornAt)
            }
        }
        LifeOsLog.d("LifeOS/Vpn", "tunnel up mode=$mode packages=$packages")
        return true
    }

    private fun logIfWhitelistCoversAll(bypass: List<String>) {
        val installed = runCatching {
            packageManager.getInstalledApplications(0).mapTo(HashSet()) { it.packageName }
        }.getOrDefault(emptySet())
        if (installed.isNotEmpty() && installed.all { it in bypass.toSet() }) {
            LifeOsLog.d("LifeOS/Vpn", "whitelist covers all installed apps; tunnel blocks nothing")
        }
    }

    private fun teardown() {
        generation.incrementAndGet()
        // Drop any pending hit so a reconfigure cannot flash the block screen for a rule
        // that no longer exists.
        BlockedDomainSignal.clear()
        synchronized(gate) {
            dnsFilter?.stop()
            dnsFilter = null
            runCatching { tunFd?.close() }
            tunFd = null
            blackhole?.interrupt()
            blackhole = null
        }
        LifeOsLog.d("LifeOS/Vpn", "teardown")
    }

    /**
     * Keeps the service in the foreground for its whole life. A VPN that is only a background
     * service is a VPN the OS is free to kill, which is exactly what users see as "it turned off".
     */
    private fun promote(mode: NetworkMode, domains: List<String>, starting: Boolean) {
        val text = when {
            starting -> "Starting…"
            domains.isNotEmpty() ->
                "Blocking ${domains.size} domain${if (domains.size == 1) "" else "s"}"
            else -> "Mode ${mode.name.lowercase()}"
        }
        runCatching {
            startForeground(NotificationChannels.NOTIF_VPN, statusNotification(text))
        }.onFailure {
            LifeOsLog.d("LifeOS/Vpn", "startForeground failed: ${it.message}")
        }
    }

    private fun statusNotification(text: String): Notification =
        Notification.Builder(this, NotificationChannels.VPN)
            .setSmallIcon(R.drawable.ic_stat_lifeos)
            .setColor(NotificationChannels.ACCENT)
            .setContentTitle("LifeOS network guard")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun postRevoked() {
        runCatching {
            val n = Notification.Builder(this, NotificationChannels.VPN)
                .setSmallIcon(R.drawable.ic_stat_lifeos)
                .setColor(NotificationChannels.ACCENT)
                .setContentTitle("LifeOS network guard stopped")
                .setContentText("Another VPN took over. Open LifeOS to turn the guard back on.")
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java)
                ?.notify(NotificationChannels.NOTIF_VPN, n)
        }
    }

    private fun stopForegroundAndSelf() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        getSystemService(NotificationManager::class.java)?.cancel(NotificationChannels.NOTIF_VPN)
        stopSelf()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PACKAGES = "packages"
        const val EXTRA_DOMAINS = "domains"
        const val EXTRA_ACTION = "action"
        const val EXTRA_RESUME = "resume"
        const val ACTION_STOP = "stop"

        private const val RETRY_MIN_MS = 3_000L
        private const val RETRY_MAX_MS = 60_000L

        /** Sink resolver address inside the tunnel; the only route we install in DNS mode. */
        private const val DNS_SINK = "10.7.0.2"

        @Volatile
        var lastMode: NetworkMode? = null

        @Volatile
        var lastPackages: List<String> = emptyList()

        @Volatile
        var lastDomains: List<String> = emptyList()
    }
}
