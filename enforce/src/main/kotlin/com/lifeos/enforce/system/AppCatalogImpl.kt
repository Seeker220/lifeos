package com.lifeos.enforce.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.lifeos.core.AppCatalog
import com.lifeos.core.DemoPackages
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCatalogImpl(private val context: Context) : AppCatalog {
    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                .map { info ->
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(pm).toString(),
                    )
                }
                .filter { it.packageName != context.packageName && it.packageName != DemoPackages.SELF }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    override suspend fun resolveOrSubstitute(nameOrPackage: String): String? {
        val raw = nameOrPackage.trim().lowercase()
        val pkg = DemoPackages.ALIASES[raw] ?: nameOrPackage.trim()
        if (isInstalled(pkg)) return pkg
        if (isDebuggable()) {
            val sub = DemoPackages.SUBSTITUTES[pkg]
            if (sub != null && isInstalled(sub)) {
                LifeOsLog.d("LifeOS/Focus", "substitute $pkg -> $sub")
                return sub
            }
        }
        return null
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    }.getOrDefault(false)
}
