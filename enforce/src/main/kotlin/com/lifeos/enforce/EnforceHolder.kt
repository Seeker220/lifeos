package com.lifeos.enforce

import com.lifeos.core.LifeStateStore
import com.lifeos.core.model.EnforcementRules
import com.lifeos.enforce.alarm.AlarmScheduler
import com.lifeos.enforce.focus.FocusController
import com.lifeos.enforce.vpn.NetworkGuardController

object EnforceHolder {
    @Volatile var lifeState: LifeStateStore? = null
    @Volatile var alarms: AlarmScheduler? = null
    @Volatile var focus: FocusController? = null
    @Volatile var network: NetworkGuardController? = null
    @Volatile var rules: EnforcementRules? = null
}
