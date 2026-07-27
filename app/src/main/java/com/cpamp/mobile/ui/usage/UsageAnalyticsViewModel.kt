package com.cpamp.mobile.ui.usage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.monitoring.MonitoringRepository
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.ui.share.UsageShareImageWriter
import com.cpamp.mobile.ui.share.toUsageShareReport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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

data class UsageRange(val fromMs: Long, val toMs: Long)

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
)

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val monitoringRepository: MonitoringRepository,
    private val shareImageWriter: UsageShareImageWriter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UsageAnalyticsUiState())
    val state: StateFlow<UsageAnalyticsUiState> = mutableState.asStateFlow()

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
        viewModelScope.launch {
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
                    if (mutableState.value.window == window && mutableState.value.ranking == ranking) {
                        mutableState.value = mutableState.value.copy(
                            response = response,
                            loadedWindow = window,
                            loadedRange = range,
                            loading = false,
                        )
                    }
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(loading = false, error = true)
                }
        }
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
                val report = requireNotNull(response.toUsageShareReport(range.fromMs, range.toMs))
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
}

internal fun usageWindowRange(
    window: UsageWindow,
    nowMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): UsageRange {
    val fromMs = when (window) {
        UsageWindow.Day -> Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        UsageWindow.Week, UsageWindow.Month -> nowMs - window.durationMs
    }
    return UsageRange(fromMs, nowMs)
}