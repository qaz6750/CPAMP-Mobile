package com.cpamp.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.dashboard.DashboardRepository
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DashboardUiState(
    val profile: ServerProfile? = null,
    val summary: DashboardSummaryDto? = null,
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
                mutableState.value = DashboardUiState(profile = session.profile, loading = false)
                dashboardRepository.cached(session.profile.id)?.let { cached ->
                    mutableState.value = mutableState.value.copy(
                        summary = cached.summary,
                        loading = false,
                        fromCache = true,
                        updatedAt = cached.updatedAt,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val session = sessionRepository.session.value ?: return
        refreshMutex.withLock {
            mutableState.value = mutableState.value.copy(
                refreshing = mutableState.value.summary != null,
                loading = mutableState.value.summary == null,
                error = null,
            )
            val now = System.currentTimeMillis()
            val start = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            runCatching { dashboardRepository.refresh(session, start, now) }
                .onSuccess { summary ->
                    mutableState.value = mutableState.value.copy(
                        profile = session.profile,
                        summary = summary,
                        loading = false,
                        refreshing = false,
                        fromCache = false,
                        updatedAt = System.currentTimeMillis(),
                        error = null,
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = error.toDashboardError(),
                    )
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

