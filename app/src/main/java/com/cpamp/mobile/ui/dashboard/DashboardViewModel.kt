package com.cpamp.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.dashboard.DashboardRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringTimelineDto
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.domain.model.AuthenticatedSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DashboardUiState(
    val profile: ServerProfile? = null,
    val summary: DashboardSummaryDto? = null,
    val analyticsTimeline: List<MonitoringTimelineDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val fromCache: Boolean = false,
    val updatedAt: Long? = null,
    val error: DashboardError? = null,
)

enum class DashboardError { Unauthorized, RateLimited, Timeout, Network, Server }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val dashboardRepository: DashboardRepository,
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = mutableState.asStateFlow()
    private val refreshMutex = Mutex()

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = DashboardUiState(loading = false)
                    return@collectLatest
                }
                mutableState.value = DashboardUiState(profile = session.profile, loading = true)
                dashboardRepository.cached(session.profile.id)?.let { cached ->
                    mutableState.update { state ->
                        state.copy(
                            summary = cached.summary,
                            loading = false,
                            fromCache = true,
                            updatedAt = cached.updatedAt,
                        )
                    }
                }
                refreshInternal(session)
            }
        }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.refreshing) return
        val session = sessionRepository.session.value ?: return
        viewModelScope.launch { refreshInternal(session) }
    }

    private suspend fun refreshInternal(session: AuthenticatedSession) {
        refreshMutex.withLock {
            mutableState.update { state ->
                state.copy(
                    refreshing = state.summary != null,
                    loading = state.summary == null,
                    error = null,
                )
            }
            val now = System.currentTimeMillis()
            val start = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            runCatching {
                coroutineScope {
                    val summary = async { dashboardRepository.refresh(session, start, now) }
                    val timeline = async {
                        runCatching {
                            monitoringRepository.refresh(
                                session = session,
                                request = MonitoringRequestDto(
                                    fromMs = start,
                                    toMs = now,
                                    nowMs = now,
                                    timeZone = ZoneId.systemDefault().id,
                                    include = MonitoringIncludeDto(timeline = true),
                                ),
                                cacheResult = false,
                            ).timeline
                        }.getOrDefault(emptyList())
                    }
                    summary.await() to timeline.await()
                }
            }
                .onSuccess { (summary, timeline) ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onSuccess
                    mutableState.update { state ->
                        state.copy(
                            profile = session.profile,
                            summary = summary,
                            analyticsTimeline = timeline,
                            loading = false,
                            refreshing = false,
                            fromCache = false,
                            updatedAt = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (sessionRepository.session.value?.profile?.id != session.profile.id) return@onFailure
                    mutableState.update { state ->
                        state.copy(
                            loading = false,
                            refreshing = false,
                            error = error.toDashboardError(),
                        )
                    }
                }
        }
    }

}

private fun Throwable.toDashboardError(): DashboardError = when (this) {
    is RemoteFailure.Unauthorized -> DashboardError.Unauthorized
    is RemoteFailure.RateLimited -> DashboardError.RateLimited
    is RemoteFailure.Timeout -> DashboardError.Timeout
    is RemoteFailure.Network, is RemoteFailure.Tls -> DashboardError.Network
    else -> DashboardError.Server
}
