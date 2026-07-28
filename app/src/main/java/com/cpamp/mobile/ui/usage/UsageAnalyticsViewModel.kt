package com.cpamp.mobile.ui.usage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
    private val shareImageWriter: UsageShareImageWriter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UsageAnalyticsUiState())
    val state: StateFlow<UsageAnalyticsUiState> = mutableState.asStateFlow()

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
        mutableState.value = mutableState.value.copy(window = window)
    }

    fun setRanking(ranking: UsageRanking) {
        mutableState.value = mutableState.value.copy(ranking = ranking)
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.sharing) return
        val session = sessionRepository.session.value ?: return
        val window = mutableState.value.window
        val ranking = mutableState.value.ranking
        viewModelScope.launch { refreshInternal(session, window, ranking) }
    }

    fun share() {
        val current = mutableState.value
        if (current.loading || current.sharing) return
        val session = sessionRepository.session.value ?: return
        val window = current.window
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(sharing = true, shareError = false, shareUri = null)
            val reusable = current.response?.takeIf {
                current.loadedWindow == window && current.loadedRange != null && it.modelStats.isNotEmpty()
            }
            val range = current.loadedRange?.takeIf { reusable != null }
                ?: usageWindowRange(window, System.currentTimeMillis())
            runCatching {
                val response = reusable ?: monitoringRepository.refresh(
                    session = session,
                    request = MonitoringRequestDto(
                        fromMs = range.fromMs,
                        toMs = range.toMs,
                        nowMs = range.toMs,
                        timeZone = ZoneId.systemDefault().id,
                        include = MonitoringIncludeDto(summary = true, timeline = true, modelStats = true),
                    ),
                    cacheResult = false,
                )
                val effectiveRange = if (window == UsageWindow.Month) {
                    effectiveUsageRange(response, range)
                        ?: UsageEffectiveRange(range.fromMs, range.toMs, UsageWindow.Month.days)
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
                mutableState.value = mutableState.value.copy(sharing = false, shareUri = uri)
            }.onFailure {
                mutableState.value = mutableState.value.copy(sharing = false, shareError = true)
            }
        }
    }

    fun consumeShare() {
        mutableState.value = mutableState.value.copy(shareUri = null)
    }

    private suspend fun refreshInternal(
        session: AuthenticatedSession,
        window: UsageWindow,
        ranking: UsageRanking,
    ) {
        mutableState.value = mutableState.value.copy(loading = true, error = false)
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
        runCatching { monitoringRepository.refresh(session, request, cacheResult = false) }
            .onSuccess { response ->
                if (sessionRepository.session.value?.profile?.id == session.profile.id &&
                    mutableState.value.window == window && mutableState.value.ranking == ranking
                ) {
                    mutableState.value = mutableState.value.copy(
                        response = response,
                        loadedWindow = window,
                        loadedRange = range,
                        loading = false,
                    )
                }
            }
            .onFailure {
                if (sessionRepository.session.value?.profile?.id == session.profile.id) {
                    mutableState.value = mutableState.value.copy(loading = false, error = true)
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
            .minusDays((UsageWindow.Month.days - 1).toLong())
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
            .coerceIn(1, UsageWindow.Month.days),
    )
}

private val UsageWindow.days: Int
    get() = when (this) {
        UsageWindow.Day -> 1
        UsageWindow.Week -> 7
        UsageWindow.Month -> 30
    }
