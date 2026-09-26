package com.cpamp.mobile.data.widget

import com.cpamp.mobile.data.accounts.AccountHealthSnapshot
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.data.auth.SessionException
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import kotlinx.serialization.Serializable

/**
 * Compact, privacy-safe widget payload. It intentionally carries no account labels,
 * credential file names, authentication indexes, or raw provider responses.
 */
@Serializable
data class UsageWidgetCache(
    val usage: UsageWidgetUsage? = null,
    val credentials: List<UsageWidgetCredential>? = null,
    val quotaUpdatedAtMs: Long = 0,
    val lastError: UsageWidgetError? = null,
    val lastAttemptAtMs: Long = 0,
)

@Serializable
data class UsageWidgetUsage(
    val calls: Long,
    val successRate: Double,
    val tokens: Long,
    val cost: Double,
    val rpm: Double,
    val updatedAtMs: Long,
)

@Serializable
data class UsageWidgetCredential(
    val ordinal: Int,
    val provider: String,
    val planType: String,
    val windows: List<UsageWidgetQuotaWindow>,
)

@Serializable
data class UsageWidgetQuotaWindow(
    val durationSeconds: Long,
    val remainingPercent: Double,
)

@Serializable
enum class UsageWidgetError { NotConnected, KeyUnavailable, Unauthorized, RateLimited, Network, Server }

sealed interface UsageWidgetState {
    data object Locked : UsageWidgetState
    data object NotConnected : UsageWidgetState
    data class Ready(val cache: UsageWidgetCache, val refreshing: Boolean) : UsageWidgetState
}

internal fun DashboardSummaryDto.toWidgetUsage(updatedAtMs: Long): UsageWidgetUsage = UsageWidgetUsage(
    calls = today.totalCalls,
    successRate = today.successRate,
    tokens = today.totalTokens,
    cost = today.totalCost,
    rpm = rolling30m.rpm,
    updatedAtMs = updatedAtMs,
)

internal fun AccountHealthSnapshot.toWidgetCredentials(): List<UsageWidgetCredential> =
    accounts.mapIndexedNotNull { index, account ->
        if (account.status == AccountStatus.Disabled) return@mapIndexedNotNull null
        val windows = account.windows
            .mapNotNull { window ->
                window.remainingPercent?.let { UsageWidgetQuotaWindow(window.durationSeconds, it) }
            }
            .groupBy(UsageWidgetQuotaWindow::durationSeconds)
            .map { (_, sameDuration) -> sameDuration.minBy(UsageWidgetQuotaWindow::remainingPercent) }
            .sortedBy(UsageWidgetQuotaWindow::durationSeconds)
        if (windows.isEmpty()) return@mapIndexedNotNull null
        UsageWidgetCredential(
            ordinal = index + 1,
            provider = account.provider,
            planType = account.planType.trim(),
            windows = windows,
        )
    }.sortedBy { credential -> credential.windows.minOf(UsageWidgetQuotaWindow::remainingPercent) }

internal fun UsageWidgetCache.withRefreshResult(
    usage: Result<DashboardSummaryDto>,
    quota: Result<AccountHealthSnapshot>,
    attemptedAtMs: Long,
): UsageWidgetCache {
    val snapshot = quota.getOrNull()
    return copy(
        usage = usage.getOrNull()?.toWidgetUsage(attemptedAtMs) ?: this.usage,
        credentials = snapshot?.toWidgetCredentials() ?: credentials,
        quotaUpdatedAtMs = snapshot?.observedAtMs ?: quotaUpdatedAtMs,
        lastError = (usage.exceptionOrNull() ?: quota.exceptionOrNull())?.toUsageWidgetError(),
        lastAttemptAtMs = attemptedAtMs,
    )
}

internal fun Throwable.toUsageWidgetError(): UsageWidgetError = when (this) {
    is SessionException -> when (reason) {
        SessionException.Reason.NoActiveProfile,
        SessionException.Reason.ProfileNotFound,
        -> UsageWidgetError.NotConnected
        else -> UsageWidgetError.KeyUnavailable
    }
    is RemoteFailure.Unauthorized -> UsageWidgetError.Unauthorized
    is RemoteFailure.RateLimited -> UsageWidgetError.RateLimited
    is RemoteFailure.Timeout, is RemoteFailure.Network, is RemoteFailure.Tls -> UsageWidgetError.Network
    else -> UsageWidgetError.Server
}
