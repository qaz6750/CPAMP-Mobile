package com.cpamp.mobile.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.MILLIS_PER_DAY
import com.cpamp.mobile.common.MILLIS_PER_HOUR
import com.cpamp.mobile.common.MILLIS_PER_WEEK
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.MonitoringFiltersDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MonitoringWindow(val durationMs: Long) {
    Hour(MILLIS_PER_HOUR),
    Day(MILLIS_PER_DAY),
    Week(MILLIS_PER_WEEK),
}

data class MonitoringFilter(
    val failedOnly: Boolean = false,
    val window: MonitoringWindow = MonitoringWindow.Day,
) {
    val cacheable: Boolean
        get() = window == MonitoringWindow.Day && !failedOnly
}

data class MonitoringUiState(
    val profile: ServerProfile? = null,
    val response: MonitoringResponseDto? = null,
    val filter: MonitoringFilter = MonitoringFilter(),
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
    private val filter = MutableStateFlow(MonitoringFilter())

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = MonitoringUiState(loading = false)
                    return@collectLatest
                }
                val defaultFilter = MonitoringFilter()
                filter.value = defaultFilter
                mutableState.value = MonitoringUiState(
                    profile = session.profile,
                    filter = defaultFilter,
                    loading = true,
                )
                monitoringRepository.cached(session.profile.id)?.let { cached ->
                    mutableState.update { state ->
                        state.copy(
                            response = cached.response,
                            fromCache = true,
                            updatedAt = cached.updatedAt,
                        )
                    }
                }
                refreshInternal(defaultFilter, session)
            }
        }
    }

    fun setFailedOnly(value: Boolean) {
        filter.update { it.copy(failedOnly = value) }
        mutableState.update { it.copy(filter = filter.value) }
    }

    fun setWindow(value: MonitoringWindow) {
        filter.update { it.copy(window = value) }
        mutableState.update { it.copy(filter = filter.value) }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        val session = sessionRepository.session.value ?: return
        viewModelScope.launch { refreshInternal(filter.value, session) }
    }

    private suspend fun refreshInternal(
        currentFilter: MonitoringFilter,
        session: AuthenticatedSession,
    ) {
        mutableState.update { state ->
            state.copy(
                refreshing = state.response != null,
                loading = state.response == null,
                error = null,
            )
        }
        val now = System.currentTimeMillis()
        val request = MonitoringRequestDto(
            fromMs = now - currentFilter.window.durationMs,
            toMs = now,
            nowMs = now,
            timeZone = ZoneId.systemDefault().id,
            filters = MonitoringFiltersDto(
                failedOnly = currentFilter.failedOnly,
            ),
            include = MonitoringIncludeDto(summary = true),
        )
        runSuspendCatching {
            monitoringRepository.refresh(session, request, cacheResult = currentFilter.cacheable)
        }.onSuccess { response ->
            if (sessionRepository.session.value?.profile?.id != session.profile.id) {
                return@onSuccess
            }
            if (filter.value != currentFilter) {
                mutableState.update { it.copy(loading = false, refreshing = false) }
                return@onSuccess
            }
            mutableState.update { state ->
                state.copy(
                    profile = session.profile,
                    response = response,
                    loading = false,
                    refreshing = false,
                    fromCache = false,
                    updatedAt = System.currentTimeMillis(),
                    error = null,
                )
            }
        }.onFailure { error ->
            if (sessionRepository.session.value?.profile?.id != session.profile.id) {
                return@onFailure
            }
            if (filter.value != currentFilter) {
                mutableState.update { it.copy(loading = false, refreshing = false) }
                return@onFailure
            }
            mutableState.update { state ->
                state.copy(
                    loading = false,
                    refreshing = false,
                    error = error.toMonitoringError(),
                )
            }
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
