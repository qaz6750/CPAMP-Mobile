package com.cpamp.mobile.ui.usage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.ui.share.UsageShareImageWriter
import com.cpamp.mobile.ui.share.toUsageShareReport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class UsageWindow(val durationMs: Long) {
    Day(24 * 60 * 60 * 1000L),
    Week(7 * 24 * 60 * 60 * 1000L),
    Month(30 * 24 * 60 * 60 * 1000L),
}

data class UsageRange(val fromMs: Long, val toMs: Long)

data class UsageEffectiveRange(
    val fromMs: Long,
    val toMs: Long,
    val actualDays: Int,
)

enum class UsageRanking { Models, ApiKeys, Credentials }

data class UsageAnalyticsUiState(
    val response: MonitoringResponseDto? = null,
    val window: UsageWindow = UsageWindow.Week,
    val ranking: UsageRanking = UsageRanking.Models,
    val loadedWindow: UsageWindow? = null,
    val loadedRange: UsageRange? = null,
    val loading: Boolean = false,
    val sharing: Boolean = false,
    val shareUri: Uri? = null,
    val shareError: Boolean = false,
    val error: Boolean = false,
) {
    val effectiveMonthRange: UsageEffectiveRange?
        get() = if (window == UsageWindow.Month && loadedWindow == UsageWindow.Month) {
            response?.let { loadedRange?.let { range -> effectiveUsageRange(it, range) } }
        } else {
            null
        }

    val partialMonthRange: UsageEffectiveRange?
        get() = effectiveMonthRange?.takeIf { effective ->
            loadedRange?.let { effective.fromMs > it.fromMs } == true
        }
}

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
    private val shareImageWriter: UsageShareImageWriter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UsageAnalyticsUiState())
    val state: StateFlow<UsageAnalyticsUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                if (session == null) {
                    mutableState.value = UsageAnalyticsUiState()
                    return@collectLatest
                }
                val window = UsageWindow.Week
                val ranking = UsageRanking.Models
                mutableState.value = UsageAnalyticsUiState(
                    window = window,
                    ranking = ranking,
                    loading = true,
                )
                refreshInternal(session, window, ranking)
            }
        }
    }

    fun setWindow(window: UsageWindow) {
        if (mutableState.value.window == window) return
        mutableState.update { it.copy(window = window) }
        val session = sessionRepository.session.value ?: return
        scheduleRefresh(session, window, mutableState.value.ranking)
    }

    fun setRanking(ranking: UsageRanking) {
        mutableState.update { it.copy(ranking = ranking) }
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.sharing) return
        val session = sessionRepository.session.value ?: return
        val window = mutableState.value.window
        val ranking = mutableState.value.ranking
        scheduleRefresh(session, window, ranking)
    }

    private fun scheduleRefresh(
        session: AuthenticatedSession,
        window: UsageWindow,
        ranking: UsageRanking,
    ) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch { refreshInternal(session, window, ranking, generation) }
    }

    fun share() {
        val current = mutableState.value
        if (current.loading || current.sharing) return
        val session = sessionRepository.session.value ?: return
        val window = current.window
        viewModelScope.launch {
            mutableState.update { it.copy(sharing = true, shareError = false, shareUri = null) }
            val reusable = current.response?.takeIf {
                current.loadedWindow == window && current.loadedRange != null && it.modelStats.isNotEmpty() &&
                    it.timeline.usesHourlyBuckets()
            }
            val range = current.loadedRange?.takeIf { reusable != null }
                ?: usageWindowRange(window, System.currentTimeMillis())
            runSuspendCatching {
                val response = reusable ?: monitoringRepository.refresh(
                    session = session,
                    request = MonitoringRequestDto(
                        fromMs = range.fromMs,
                        toMs = range.toMs,
                        nowMs = range.toMs,
                        timeZone = ZoneId.systemDefault().id,
                        include = MonitoringIncludeDto(
                            summary = true,
                            timeline = true,
                            modelStats = true,
                            granularity = "hour",
                        ),
                    ),
                    cacheResult = false,
                )
                val effectiveRange = if (window == UsageWindow.Month) {
                    effectiveUsageRange(response, range)
                        ?: UsageEffectiveRange(range.fromMs, range.toMs, usageRangeDayCount(range))
                } else {
                    UsageEffectiveRange(range.fromMs, range.toMs, window.days)
                }
                val report = requireNotNull(
                    response.toUsageShareReport(
                        fromMs = effectiveRange.fromMs,
                        toMs = effectiveRange.toMs,
                        actualDays = effectiveRange.actualDays,
                    ),
                )
                shareImageWriter.write(report)
            }.onSuccess { uri ->
                mutableState.update { it.copy(sharing = false, shareUri = uri) }
            }.onFailure {
                mutableState.update { it.copy(sharing = false, shareError = true) }
            }
        }
    }

    fun consumeShare() {
        mutableState.update { it.copy(shareUri = null) }
    }

    private suspend fun refreshInternal(
        session: AuthenticatedSession,
        window: UsageWindow,
        ranking: UsageRanking,
        generation: Long = ++refreshGeneration,
    ) {
        mutableState.update { it.copy(loading = true, error = false) }
        val now = System.currentTimeMillis()
        val range = usageWindowRange(window, now)
        val request = MonitoringRequestDto(
            fromMs = range.fromMs,
            toMs = range.toMs,
            nowMs = now,
            timeZone = ZoneId.systemDefault().id,
            include = MonitoringIncludeDto(
                summary = true,
                timeline = true,
                modelStats = ranking == UsageRanking.Models,
                credentialStats = ranking == UsageRanking.Credentials,
                apiKeyStats = ranking == UsageRanking.ApiKeys,
            ),
        )
        runSuspendCatching { monitoringRepository.refresh(session, request, cacheResult = false) }
            .onSuccess { response ->
                if (sessionRepository.session.value?.profile?.id == session.profile.id &&
                    mutableState.value.window == window && mutableState.value.ranking == ranking &&
                    refreshGeneration == generation
                ) {
                    mutableState.update { state ->
                        state.copy(
                            response = response,
                            loadedWindow = window,
                            loadedRange = range,
                            loading = false,
                        )
                    }
                }
            }
            .onFailure {
                if (sessionRepository.session.value?.profile?.id == session.profile.id &&
                    mutableState.value.window == window && mutableState.value.ranking == ranking &&
                    refreshGeneration == generation
                ) {
                    mutableState.update { it.copy(loading = false, error = true) }
                }
            }
    }
}

internal fun usageWindowRange(
    window: UsageWindow,
    nowMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): UsageRange {
    val fromMs = when (window) {
        UsageWindow.Day -> Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        UsageWindow.Week -> nowMs - window.durationMs
        UsageWindow.Month -> Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    return UsageRange(fromMs, nowMs)
}

internal fun effectiveUsageRange(
    response: MonitoringResponseDto,
    requestedRange: UsageRange,
    zoneId: ZoneId = ZoneId.systemDefault(),
): UsageEffectiveRange? {
    val firstPoint = response.timeline
        .asSequence()
        .filter { it.calls > 0 || it.totalTokens > 0 || it.tokens > 0 }
        .minByOrNull { it.bucketMs }
        ?: return null
    val firstDate = Instant.ofEpochMilli(firstPoint.bucketMs).atZone(zoneId).toLocalDate()
    val endDate = Instant.ofEpochMilli(requestedRange.toMs).atZone(zoneId).toLocalDate()
    if (firstDate.isAfter(endDate)) return null
    val effectiveFrom = maxOf(
        requestedRange.fromMs,
        firstDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
    )
    return UsageEffectiveRange(
        fromMs = effectiveFrom,
        toMs = requestedRange.toMs,
        actualDays = (ChronoUnit.DAYS.between(firstDate, endDate) + 1).toInt()
            .coerceIn(1, usageRangeDayCount(requestedRange, zoneId)),
    )
}

private fun usageRangeDayCount(
    range: UsageRange,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int {
    val fromDate = Instant.ofEpochMilli(range.fromMs).atZone(zoneId).toLocalDate()
    val toDate = Instant.ofEpochMilli(range.toMs).atZone(zoneId).toLocalDate()
    return (ChronoUnit.DAYS.between(fromDate, toDate) + 1).toInt().coerceAtLeast(1)
}

private fun List<com.cpamp.mobile.data.remote.model.MonitoringTimelineDto>.usesHourlyBuckets(): Boolean =
    size <= 1 || zipWithNext().any { (previous, current) ->
        current.bucketMs - previous.bucketMs in 1 until 24 * 60 * 60 * 1000L
    }

private val UsageWindow.days: Int
    get() = when (this) {
        UsageWindow.Day -> 1
        UsageWindow.Week -> 7
        UsageWindow.Month -> 31
    }
