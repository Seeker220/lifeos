package com.lifeos.enforce.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.NetworkMode
import com.lifeos.core.model.NetworkRules

class NetworkGuardController(private val context: Context) {
    fun start(rules: NetworkRules) {
        runCatching {
            if (rules.mode == NetworkMode.OFF) {
                stop()
                return
            }
            if (VpnService.prepare(context) != null) {
                LifeOsLog.d("LifeOS/Vpn", "VPN consent missing; skip start")
                return
            }
            context.startService(
                Intent(context, LifeOsVpnService::class.java)
                    .putExtra(LifeOsVpnService.EXTRA_MODE, rules.mode.name)
                    .putStringArrayListExtra(
                        LifeOsVpnService.EXTRA_PACKAGES,
                        ArrayList(rules.packages),
                    ),
            )
            LifeOsLog.d("LifeOS/Vpn", "startNetworkGuard mode=${rules.mode} n=${rules.packages.size}")
        }.onFailure {
            LifeOsLog.d("LifeOS/Vpn", "start failed: ${it.message}")
        }
    }

    fun stop() {
        runCatching {
            context.startService(
                Intent(context, LifeOsVpnService::class.java)
                    .putExtra(LifeOsVpnService.EXTRA_ACTION, LifeOsVpnService.ACTION_STOP),
            )
            LifeOsLog.d("LifeOS/Vpn", "stopNetworkGuard")
        }.onFailure {
            LifeOsLog.d("LifeOS/Vpn", "stop failed: ${it.message}")
        }
    }
}
