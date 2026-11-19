package com.sarkarimatch.feature.onboarding.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarkarimatch.core.data.prefs.LanguageController
import com.sarkarimatch.core.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LanguageUiState(
    val selected: AppLanguage,
    val enabled: List<AppLanguage> = AppLanguage.launched,
    val comingSoon: List<AppLanguage> = AppLanguage.entries.filterNot { it.launch },
)

/** S02 / S40. Hindi is preselected for every new user (i18n A1). */
class LanguageViewModel(private val languageController: LanguageController) : ViewModel() {
    private val _state = MutableStateFlow(LanguageUiState(selected = AppLanguage.DEFAULT))
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = languageController.language.first()
            _state.update { it.copy(selected = current) }
        }
    }

    fun select(language: AppLanguage) = _state.update { it.copy(selected = language) }

    /**
     * Applies the language. Android: the Activity is recreated by AppCompat and the back stack restored.
     * iOS: the Compose root re-keys on the new language.
     */
    fun confirm(then: () -> Unit) {
        val chosen = _state.value.selected
        then()
        viewModelScope.launch { languageController.set(chosen) }
    }
}
