package com.cpamp.mobile.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.MILLIS_PER_DAY
import com.cpamp.mobile.common.MILLIS_PER_HOUR
import com.cpamp.mobile.common.MILLIS_PER_WEEK
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.CredentialQuota
import com.cpamp.mobile.data.monitoring.CredentialQuotaRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.EventsPageRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.data.system.ServerVersionObserver
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

enum class TrafficWindow(val durationMs: Long) {
    Hour(MILLIS_PER_HOUR),
    Day(MILLIS_PER_DAY),
    Week(MILLIS_PER_WEEK),
}

data class TrafficFilter(
    val failedOnly: Boolean = false,
    val window: TrafficWindow = TrafficWindow.Day,
) {
    val cacheable: Boolean
        get() = window == TrafficWindow.Day
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
    val credentialQuotas: List<CredentialQuota> = emptyList(),
    val credentialQuotaRunId: Long? = null,
    val credentialQuotaFinishedAtMs: Long? = null,
    val credentialQuotaServerVersion: String? = null,
    val credentialQuotasLoaded: Boolean = false,
    val credentialQuotasFromCache: Boolean = false,
    val credentialQuotasLoading: Boolean = false,
    val credentialQuotasError: CredentialQuotaError? = null,
) {
    val visibleEvents
        get() = response?.events?.items.orEmpty().let { events ->
            if (filter.failedOnly) events.filter { it.failed } else events
        }

    val visibleEventCount: Int
        get() = visibleEvents.size
}

enum class MonitoringError { Unauthorized, RateLimited, Timeout, Network, Server }

enum class CredentialQuotaError { NoCompletedInspection, Unauthorized, ServerUnsupported, InvalidResponse, Network, Server }

@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
    private val credentialQuotaRepository: CredentialQuotaRepository,
    private val serverVersionObserver: ServerVersionObserver,
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
                val defaultFilter = TrafficFilter()
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

    fun setWindow(value: TrafficWindow) {
        filter.update { it.copy(window = value) }
        mutableState.update { it.copy(filter = filter.value) }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        val session = sessionRepository.session.value ?: return
        viewModelScope.launch { refreshInternal(filter.value, session) }
    }

    fun refreshCredentialQuotas() {
        if (mutableState.value.credentialQuotasLoading) return
        val session = sessionRepository.session.value ?: return
        loadCredentialQuotas(session)
    }

    fun loadCredentialQuotasIfNeeded() {
        val current = mutableState.value
        if (current.credentialQuotasLoaded || current.credentialQuotasLoading) return
        val session = sessionRepository.session.value ?: return
        loadCredentialQuotas(session)
    }

    private fun loadCredentialQuotas(session: AuthenticatedSession) {
        mutableState.update {
            it.copy(
                credentialQuotasLoading = true,
                credentialQuotasError = null,
            )
        }
        viewModelScope.launch {
            runSuspendCatching { credentialQuotaRepository.load(session) }
                .onSuccess { snapshot ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    mutableState.update {
                        it.copy(
                            credentialQuotas = snapshot.quotas,
                            credentialQuotaRunId = snapshot.runId,
                            credentialQuotaFinishedAtMs = snapshot.finishedAtMs,
                            credentialQuotasLoading = false,
                            credentialQuotasLoaded = true,
                            credentialQuotasFromCache = snapshot.fromCache,
                            credentialQuotasError = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                    val cached = credentialQuotaRepository.cached(session.profile.id)
                    mutableState.update { state ->
                        cached?.let { snapshot ->
                            state.copy(
                                credentialQuotas = snapshot.quotas,
                                credentialQuotaRunId = snapshot.runId,
                                credentialQuotaFinishedAtMs = snapshot.finishedAtMs,
                                credentialQuotasLoading = false,
                                credentialQuotasError = error.toCredentialQuotaError(),
                                credentialQuotasLoaded = true,
                                credentialQuotasFromCache = true,
                                credentialQuotaServerVersion = serverVersionObserver
                                    .snapshot(session.profile.id)
                                    .cpampVersion,
                            )
                        } ?: state.copy(
                            credentialQuotasLoading = false,
                            credentialQuotasError = error.toCredentialQuotaError(),
                            credentialQuotasLoaded = true,
                            credentialQuotaServerVersion = serverVersionObserver
                                .snapshot(session.profile.id)
                                .cpampVersion,
                        )
                    }
                }
        }
    }

    private suspend fun refreshInternal(
        currentFilter: TrafficFilter,
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
            include = MonitoringIncludeDto(
                summary = true,
                eventsPage = EventsPageRequestDto(limit = 50),
            ),
        )
        runSuspendCatching {
            monitoringRepository.refresh(session, request, cacheResult = currentFilter.cacheable)
        }.onSuccess { response ->
            if (filter.value.window != currentFilter.window || sessionRepository.session.value?.profile?.id != session.profile.id) {
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
            if (filter.value.window != currentFilter.window || sessionRepository.session.value?.profile?.id != session.profile.id) {
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

private fun Throwable.toCredentialQuotaError(): CredentialQuotaError = when (this) {
    is com.cpamp.mobile.data.monitoring.NoCompletedInspectionException -> CredentialQuotaError.NoCompletedInspection
    is RemoteFailure.Unauthorized -> CredentialQuotaError.Unauthorized
    is RemoteFailure.NotFound -> CredentialQuotaError.ServerUnsupported
    is RemoteFailure.InvalidResponse -> CredentialQuotaError.InvalidResponse
    is RemoteFailure.Network, is RemoteFailure.Timeout, is RemoteFailure.Tls -> CredentialQuotaError.Network
    else -> CredentialQuotaError.Server
}

private fun Throwable.toMonitoringError(): MonitoringError = when (this) {
    is RemoteFailure.Unauthorized -> MonitoringError.Unauthorized
    is RemoteFailure.RateLimited -> MonitoringError.RateLimited
    is RemoteFailure.Timeout -> MonitoringError.Timeout
    is RemoteFailure.Network, is RemoteFailure.Tls -> MonitoringError.Network
    else -> MonitoringError.Server
}
