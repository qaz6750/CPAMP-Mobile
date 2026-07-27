package com.cpamp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val loading: Boolean = true,
    val settings: AppearanceSettings = AppearanceSettings(),
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val repository: AppearanceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { mutableState.value = AppearanceUiState(false, it) }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { repository.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setAllowScreenshots(enabled: Boolean) {
        viewModelScope.launch { repository.setAllowScreenshots(enabled) }
    }

    fun setHideAddresses(enabled: Boolean) {
        viewModelScope.launch { repository.setHideAddresses(enabled) }
    }
}