package com.sarkarimatch.feature.onboarding.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.core.data.prefs.LanguageController
import com.sarkarimatch.core.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LanguageUiState(
    val selected: AppLanguage,
    val enabled: List<AppLanguage> = AppLanguage.launched,
    val comingSoon: List<AppLanguage> = AppLanguage.entries.filterNot { it.launch },
)

/** S02 / S40. Hindi is preselected for every new user (i18n A1). */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languageController: LanguageController,
) : ViewModel() {
    private val _state = MutableStateFlow(LanguageUiState(selected = languageController.current() ?: AppLanguage.DEFAULT))
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()

    fun select(language: AppLanguage) = _state.update { it.copy(selected = language) }

    /** Applies the per-app locale; the activity is recreated and the back stack restored. */
    fun confirm(then: () -> Unit) {
        val chosen = _state.value.selected
        then()
        viewModelScope.launch { languageController.set(chosen) }
    }
}
