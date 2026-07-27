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
import com.cpamp.mobile.domain.model.ServerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TrafficWindow(val durationMs: Long) {
    Hour(60 * 60 * 1000L),
    Day(24 * 60 * 60 * 1000L),
    Week(7 * 24 * 60 * 60 * 1000L),
}

data class TrafficFilter(
    val search: String = "",
    val failedOnly: Boolean = false,
    val window: TrafficWindow = TrafficWindow.Day,
) {
    val cacheable: Boolean
        get() = search.isBlank() && !failedOnly && window == TrafficWindow.Day
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

@OptIn(FlowPreview::class)
@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MonitoringUiState())
    val state: StateFlow<MonitoringUiState> = mutableState.asStateFlow()
    private val active = MutableStateFlow(false)
    private val filter = MutableStateFlow(TrafficFilter())

    init {
        viewModelScope.launch {
            val debouncedFilter = filter
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
            combine(sessionRepository.session, active, debouncedFilter) { session, isActive, currentFilter ->
                Triple(session, isActive, currentFilter)
            }.collectLatest { (session, isActive, currentFilter) ->
                if (session == null) {
                    mutableState.value = MonitoringUiState(loading = false)
                    return@collectLatest
                }

                val filterChanged = mutableState.value.filter != currentFilter ||
                    mutableState.value.profile?.id != session.profile.id
                if (filterChanged) {
                    mutableState.value = MonitoringUiState(
                        profile = session.profile,
                        filter = currentFilter,
                        loading = true,
                    )
                    if (currentFilter.cacheable) {
                        monitoringRepository.cached(session.profile.id)?.let { cached ->
                            mutableState.value = mutableState.value.copy(
                                response = cached.response,
                                loading = false,
                                fromCache = true,
                                updatedAt = cached.updatedAt,
                            )
                        }
                    }
                }

                if (!isActive) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    refreshInternal(currentFilter)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        active.value = value
    }

    fun setSearch(value: String) {
        filter.value = filter.value.copy(search = value.take(120))
        mutableState.value = mutableState.value.copy(
            filter = mutableState.value.filter.copy(search = value.take(120)),
        )
    }

    fun setFailedOnly(value: Boolean) {
        filter.value = filter.value.copy(failedOnly = value)
    }

    fun setWindow(value: TrafficWindow) {
        filter.value = filter.value.copy(window = value)
    }

    fun refresh() {
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
            searchQuery = currentFilter.search.trim(),
            filters = MonitoringFiltersDto(failedOnly = currentFilter.failedOnly),
            include = MonitoringIncludeDto(),
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val POLL_INTERVAL_MS = 3_000L
    }
}

private fun Throwable.toMonitoringError(): MonitoringError = when (this) {
    is RemoteFailure.Unauthorized -> MonitoringError.Unauthorized
    is RemoteFailure.RateLimited -> MonitoringError.RateLimited
    is RemoteFailure.Timeout -> MonitoringError.Timeout
    is RemoteFailure.Network, is RemoteFailure.Tls -> MonitoringError.Network
    else -> MonitoringError.Server
}
