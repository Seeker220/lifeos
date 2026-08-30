package com.lifeos.enforce.vpn

/**
 * Bridges the DNS filter to the block overlay. A browser hitting a blocked name only sees a
 * resolver failure, so the filter records the hit here and FocusService turns it into the
 * LifeOS block screen.
 */
object BlockedDomainSignal {
    @Volatile
    private var domain: String? = null

    @Volatile
    private var atMs: Long = 0L

    fun record(blockedDomain: String) {
        domain = blockedDomain
        atMs = System.currentTimeMillis()
    }

    /** The most recent blocked lookup, or null once it is older than [windowMs]. */
    fun recent(windowMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val name = domain ?: return null
        return if (nowMs - atMs <= windowMs) name else null
    }

    fun clear() {
        domain = null
        atMs = 0L
    }
}
