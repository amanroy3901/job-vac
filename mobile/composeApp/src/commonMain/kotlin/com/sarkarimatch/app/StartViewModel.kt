package com.sarkarimatch.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.core.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** S01: decides the start graph before the first frame (no splash route). Force update / outage: FE-11. */
sealed interface StartState {
    data object Deciding : StartState
    data object Onboarding : StartState
    data object Main : StartState
}

class StartViewModel(private val prefs: UserPreferencesRepository) : ViewModel() {
    val state: StateFlow<StartState> = prefs.onboardingDone
        .map { done -> if (done) StartState.Main else StartState.Onboarding }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Deciding)

    fun onOnboardingFinished() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }
}
