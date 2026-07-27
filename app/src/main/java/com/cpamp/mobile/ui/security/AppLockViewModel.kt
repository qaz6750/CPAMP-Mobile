package com.cpamp.mobile.ui.security

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.security.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppLockUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val timeoutMinutes: Int = 5,
    val mutating: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val repository: AppLockRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> = mutableState.asStateFlow()
    private var backgroundedAt: Long? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                repository.synchronizeRuntimeMode(settings.enabled)
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    enabled = settings.enabled,
                    locked = if (!initialized) settings.enabled else mutableState.value.locked && settings.enabled,
                    timeoutMinutes = settings.timeoutMinutes,
                )
                initialized = true
            }
        }
    }

    fun unlock() {
        mutableState.value = mutableState.value.copy(locked = false, error = false)
        backgroundedAt = null
    }

    fun authenticationFailed() {
        mutableState.value = mutableState.value.copy(error = true)
    }

    fun onBackground() {
        if (mutableState.value.enabled && !mutableState.value.locked) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onForeground() {
        val elapsed = backgroundedAt?.let { SystemClock.elapsedRealtime() - it } ?: return
        if (elapsed >= mutableState.value.timeoutMinutes * 60_000L) {
            mutableState.value = mutableState.value.copy(locked = true)
        }
        backgroundedAt = null
    }

    fun setEnabledAfterAuthentication(enabled: Boolean) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(mutating = true, error = false)
            runCatching { repository.setEnabled(enabled) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        mutating = false,
                        enabled = enabled,
                        locked = false,
                    )
                }
                .onFailure { mutableState.value = mutableState.value.copy(mutating = false, error = true) }
        }
    }

    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { repository.setTimeoutMinutes(minutes) }
                .onFailure { mutableState.value = mutableState.value.copy(error = true) }
        }
    }
}