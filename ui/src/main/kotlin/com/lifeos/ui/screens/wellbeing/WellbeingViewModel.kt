package com.lifeos.ui.screens.wellbeing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.core.Ports
import com.lifeos.core.model.Action
import com.lifeos.core.model.ActionOrigin
import kotlinx.coroutines.launch

class WellbeingViewModel(val ports: Ports) : ViewModel() {
    fun dispatch(action: Action) {
        viewModelScope.launch {
            ports.executor.execute(listOf(action), ActionOrigin.USER)
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            ports.lifeState.mutate { state ->
                state.copy(settings = state.settings.copy(onboardingComplete = false))
            }
        }
    }
}
