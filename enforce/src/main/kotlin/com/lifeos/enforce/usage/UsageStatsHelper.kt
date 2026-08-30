package com.lifeos.enforce.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.lifeos.core.Time

class UsageStatsHelper(private val context: Context) {
    private var lastKnownForeground: String? = null

    fun foregroundPackage(lookbackMs: Long = 5_000): String? {
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java)
                ?: return lastKnownForeground
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - lookbackMs, now)
            var last: String? = null
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
            }
            if (last != null) lastKnownForeground = last
            last ?: lastKnownForeground
        }.getOrElse { lastKnownForeground }
    }

    fun usageTodayMinutes(packages: Collection<String>): Map<String, Int> {
        val stats = aggregateToday()
        return packages.associateWith { (stats[it] ?: 0) }
    }

    fun usageTodayAll(): Map<String, Int> = aggregateToday().filterValues { it > 0 }

    private fun aggregateToday(): Map<String, Int> {
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
            usm.queryAndAggregateUsageStats(
                Time.startOfTodayEpochMs(),
                System.currentTimeMillis(),
            ).mapValues { (_, stat) -> (stat.totalTimeInForeground / 60_000L).toInt() }
        }.getOrDefault(emptyMap())
    }
}
