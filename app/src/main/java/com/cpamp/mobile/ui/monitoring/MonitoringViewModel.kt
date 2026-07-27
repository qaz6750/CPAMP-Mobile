package com.cpamp.mobile.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.MonitoringFiltersDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.data.remote.model.EventsPageRequestDto
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class TrafficWindow(val durationMs: Long) {
    Hour(60 * 60 * 1000L),
    Day(24 * 60 * 60 * 1000L),
    Week(7 * 24 * 60 * 60 * 1000L),
}

data class TrafficFilter(
    val failedOnly: Boolean = false,
    val window: TrafficWindow = TrafficWindow.Day,
) {
    val cacheable: Boolean
        get() = !failedOnly && window == TrafficWindow.Day
}

data class MonitoringUiState(
    val profile: ServerProfile? = null,
    val response: MonitoringResponseDto? = null,
    val filter: TrafficFilter = TrafficFilter(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val updatedAt: Long? = null,
    val error: MonitoringError? = null,
)

enum class MonitoringError { Unauthorized, RateLimited, Timeout, Network, Server }

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MonitoringUiState())
    val state: StateFlow<MonitoringUiState> = mutableState.asStateFlow()
    private val filter = MutableStateFlow(TrafficFilter())

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = MonitoringUiState(loading = false)
                    return@collectLatest
                }
                mutableState.value = MonitoringUiState(profile = session.profile, loading = false)
                monitoringRepository.cached(session.profile.id)?.let { cached ->
                    mutableState.value = mutableState.value.copy(
                        response = cached.response,
                        fromCache = true,
                        updatedAt = cached.updatedAt,
                    )
                }
            }
        }
    }

    fun setFailedOnly(value: Boolean) {
        filter.value = filter.value.copy(failedOnly = value)
        mutableState.value = mutableState.value.copy(filter = filter.value)
    }

    fun setWindow(value: TrafficWindow) {
        filter.value = filter.value.copy(window = value)
        mutableState.value = mutableState.value.copy(filter = filter.value)
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        viewModelScope.launch { refreshInternal(filter.value) }
    }

    private suspend fun refreshInternal(currentFilter: TrafficFilter) {
        val session = sessionRepository.session.value ?: return
        mutableState.value = mutableState.value.copy(
            refreshing = mutableState.value.response != null,
            loading = mutableState.value.response == null,
            error = null,
        )
        val now = System.currentTimeMillis()
        val request = MonitoringRequestDto(
            fromMs = now - currentFilter.window.durationMs,
            toMs = now,
            nowMs = now,
            timeZone = ZoneId.systemDefault().id,
            filters = MonitoringFiltersDto(failedOnly = currentFilter.failedOnly),
            include = MonitoringIncludeDto(
                summary = true,
                eventsPage = EventsPageRequestDto(limit = 50),
            ),
        )
        runCatching {
            monitoringRepository.refresh(session, request, cacheResult = currentFilter.cacheable)
        }.onSuccess { response ->
            if (filter.value != currentFilter || sessionRepository.session.value?.profile?.id != session.profile.id) {
                return@onSuccess
            }
            mutableState.value = mutableState.value.copy(
                profile = session.profile,
                response = response,
                loading = false,
                refreshing = false,
                fromCache = false,
                updatedAt = System.currentTimeMillis(),
                error = null,
            )
        }.onFailure { error ->
            if (filter.value != currentFilter) return@onFailure
            mutableState.value = mutableState.value.copy(
                loading = false,
                refreshing = false,
                error = error.toMonitoringError(),
            )
        }
    }
}

private fun Throwable.toMonitoringError(): MonitoringError = when (this) {
    is RemoteFailure.Unauthorized -> MonitoringError.Unauthorized
    is RemoteFailure.RateLimited -> MonitoringError.RateLimited
    is RemoteFailure.Timeout -> MonitoringError.Timeout
    is RemoteFailure.Network, is RemoteFailure.Tls -> MonitoringError.Network
    else -> MonitoringError.Server
}
