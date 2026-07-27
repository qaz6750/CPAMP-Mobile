package com.cpamp.mobile.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UsageWindow(val durationMs: Long) {
    Day(24 * 60 * 60 * 1000L),
    Week(7 * 24 * 60 * 60 * 1000L),
    Month(30 * 24 * 60 * 60 * 1000L),
}

data class UsageAnalyticsUiState(
    val response: MonitoringResponseDto? = null,
    val window: UsageWindow = UsageWindow.Week,
    val loading: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UsageAnalyticsUiState())
    val state: StateFlow<UsageAnalyticsUiState> = mutableState.asStateFlow()

    fun setWindow(window: UsageWindow) {
        mutableState.value = mutableState.value.copy(window = window)
    }

    fun refresh() {
        val session = sessionRepository.session.value ?: return
        val window = mutableState.value.window
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = false)
            val now = System.currentTimeMillis()
            val request = MonitoringRequestDto(
                fromMs = now - window.durationMs,
                toMs = now,
                nowMs = now,
                timeZone = ZoneId.systemDefault().id,
                include = MonitoringIncludeDto(
                    modelShare = false,
                    modelStats = true,
                    credentialStats = true,
                    apiKeyStats = true,
                    recentFailures = 0,
                    eventsPage = null,
                ),
            )
            runCatching { monitoringRepository.refresh(session, request, cacheResult = false) }
                .onSuccess { response ->
                    if (mutableState.value.window == window) {
                        mutableState.value = mutableState.value.copy(response = response, loading = false)
                    }
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(loading = false, error = true)
                }
        }
    }
}