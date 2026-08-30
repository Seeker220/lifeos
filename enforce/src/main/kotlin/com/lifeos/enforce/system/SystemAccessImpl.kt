package com.lifeos.enforce.system

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.lifeos.core.SystemAccess
import com.lifeos.core.model.PermissionStatus

class SystemAccessImpl(private val context: Context) : SystemAccess {
    override fun permissions(): PermissionStatus {
        val notifications = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        val exactAlarms = runCatching {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        }.getOrDefault(false)
        val usageAccess = runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            ops?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val overlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        val vpnConsented = runCatching { VpnService.prepare(context) == null }.getOrDefault(false)
        val fullScreenIntent = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                context.getSystemService(NotificationManager::class.java)
                    ?.canUseFullScreenIntent() == true
            } else {
                true
            }
        }.getOrDefault(false)
        return PermissionStatus(
            notifications = notifications,
            exactAlarms = exactAlarms,
            usageAccess = usageAccess,
            overlay = overlay,
            vpnConsented = vpnConsented,
            fullScreenIntent = fullScreenIntent,
        )
    }
}
