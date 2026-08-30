package com.lifeos.enforce.vpn

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.lifeos.core.DemoPackages
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.NetworkMode
import com.lifeos.enforce.notify.NotificationChannels
import java.io.FileInputStream
import java.io.IOException
import kotlin.concurrent.thread

class LifeOsVpnService : VpnService() {
    private val gate = Any()
    private var tunFd: ParcelFileDescriptor? = null
    private var blackhole: Thread? = null
    private var dnsFilter: DnsFilter? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationChannels.ensureAll(this)
        if (intent == null) {
            val mode = lastMode
            val packages = lastPackages
            val domains = lastDomains
            if ((mode == null || mode == NetworkMode.OFF) && domains.isEmpty()) {
                LifeOsLog.d("LifeOS/Vpn", "restarted without rules; stop")
                stopSelf()
                return START_NOT_STICKY
            }
            return applyRules(mode ?: NetworkMode.OFF, packages, domains)
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_STOP || intent.action == ACTION_STOP) {
            LifeOsLog.d("LifeOS/Vpn", "ACTION_STOP")
            teardown()
            cancelStatus()
            forgetRules()
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = runCatching {
            NetworkMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrDefault(NetworkMode.OFF)
        val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty()
        val domains = intent.getStringArrayListExtra(EXTRA_DOMAINS).orEmpty()
        return applyRules(mode, packages, domains)
    }

    override fun onRevoke() {
        LifeOsLog.d("LifeOS/Vpn", "onRevoke")
        teardown()
        cancelStatus()
        forgetRules()
        stopSelf()
    }

    private fun forgetRules() {
        lastMode = null
        lastPackages = emptyList()
        lastDomains = emptyList()
    }

    override fun onDestroy() {
        teardown()
        cancelStatus()
        super.onDestroy()
    }

    private fun applyRules(mode: NetworkMode, packages: List<String>, domains: List<String>): Int {
        if (mode == NetworkMode.OFF && domains.isEmpty()) {
            teardown()
            cancelStatus()
            forgetRules()
            stopSelf()
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
            LifeOsLog.d("LifeOS/Vpn", "establish failed mode=$mode domains=${domains.size}")
            stopSelf()
            return START_NOT_STICKY
        }
        postStatus(mode, domains)
        return START_STICKY
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
        blackhole = thread(isDaemon = true, name = "lifeos-dns-filter") { filter.run() }
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

    private fun postStatus(mode: NetworkMode, domains: List<String>) {
        runCatching {
            val n = Notification.Builder(this, NotificationChannels.VPN)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("LifeOS network guard")
                .setContentText(
                    if (domains.isNotEmpty()) {
                        "Blocking ${domains.size} domain${if (domains.size == 1) "" else "s"}"
                    } else {
                        "Mode ${mode.name.lowercase()}"
                    },
                )
                .setOngoing(true)
                .build()
            getSystemService(NotificationManager::class.java)
                ?.notify(NotificationChannels.NOTIF_VPN, n)
        }
    }

    private fun cancelStatus() {
        getSystemService(NotificationManager::class.java)?.cancel(NotificationChannels.NOTIF_VPN)
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PACKAGES = "packages"
        const val EXTRA_DOMAINS = "domains"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"

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
