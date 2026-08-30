package com.lifeos.enforce.focus

import com.lifeos.core.DemoPackages
import com.lifeos.core.model.AppTimeout
import com.lifeos.core.model.EnforcementRules
import kotlin.math.min

class TimeoutMonitor {
    fun effectiveLimit(t: AppTimeout, demoStrict: Boolean): Int =
        if (demoStrict) min(t.limitMinutes, 1) else t.limitMinutes

    fun exceeded(pkg: String, usedMinutes: Int, rules: EnforcementRules): AppTimeout? {
        return rules.timeouts.firstOrNull { t ->
            packageMatches(t.packageName, pkg) &&
                usedMinutes >= effectiveLimit(t, rules.demoStrictTimeouts)
        }
    }
}

internal fun packageMatches(rulePkg: String, foregroundPkg: String): Boolean {
    if (rulePkg == foregroundPkg) return true
    return DemoPackages.SUBSTITUTES[rulePkg] == foregroundPkg
}
