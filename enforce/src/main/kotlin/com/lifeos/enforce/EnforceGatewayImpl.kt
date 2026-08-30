package com.lifeos.enforce

import com.lifeos.core.EnforceGateway
import com.lifeos.core.LifeOsLog
import com.lifeos.core.model.AlarmSpec
import com.lifeos.core.model.EnforcementRules
import com.lifeos.core.model.FocusSession
import com.lifeos.core.model.NetworkRules
import com.lifeos.enforce.alarm.AlarmScheduler
import com.lifeos.enforce.focus.FocusController
import com.lifeos.enforce.vpn.NetworkGuardController

class EnforceGatewayImpl(
    private val focus: FocusController,
    private val alarms: AlarmScheduler,
    private val network: NetworkGuardController,
) : EnforceGateway {
    override fun startFocus(session: FocusSession) = focus.start(session)
    override fun stopFocus() = focus.stop()
    override fun applyRules(rules: EnforcementRules) = focus.applyRules(rules)
    override fun scheduleAlarm(spec: AlarmSpec) = alarms.schedule(spec)
    override fun cancelAlarm(alarmId: String) = alarms.cancel(alarmId)
    override fun startNetworkGuard(rules: NetworkRules) = network.start(rules)
    override fun stopNetworkGuard() = network.stop()
    override fun usageTodayMinutes(packages: List<String>): Map<String, Int> {
        LifeOsLog.d("LifeOS/Focus", "usageTodayMinutes n=${packages.size}")
        return focus.usageTodayMinutes(packages)
    }
}
