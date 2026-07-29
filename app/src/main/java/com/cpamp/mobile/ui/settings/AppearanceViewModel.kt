package com.cpamp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.settings.AppLanguage
import com.cpamp.mobile.data.settings.AppTheme
import com.cpamp.mobile.data.settings.AppearanceRepository
import com.cpamp.mobile.data.settings.AppearanceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val loading: Boolean = true,
    val settings: AppearanceSettings = AppearanceSettings(),
    val error: Boolean = false,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val repository: AppearanceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                mutableState.value = AppearanceUiState(
                    loading = false,
                    settings = settings,
                )
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        updateSettings { repository.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        updateSettings { repository.setLanguage(language) }
    }

    fun setDynamicColor(enabled: Boolean) {
        updateSettings { repository.setDynamicColor(enabled) }
    }

    fun setAllowScreenshots(enabled: Boolean) {
        updateSettings { repository.setAllowScreenshots(enabled) }
    }

    fun setHideAddresses(enabled: Boolean) {
        updateSettings { repository.setHideAddresses(enabled) }
    }

    private fun updateSettings(operation: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(error = false) }
            runSuspendCatching { operation() }
                .onFailure { mutableState.update { it.copy(error = true) } }
        }
    }
}
