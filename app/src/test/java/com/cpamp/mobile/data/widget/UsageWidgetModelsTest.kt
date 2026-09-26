package com.cpamp.mobile.data.widget

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthSnapshot
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountQuotaWindow
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.DashboardTodayDto
import com.cpamp.mobile.data.remote.model.RollingSummaryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UsageWidgetModelsTest {
    @Test
    fun `credentials skip disabled and quota-less accounts and sort by lowest remaining`() {
        val credentials = snapshot().toWidgetCredentials()

        assertEquals(listOf(3, 1), credentials.map(UsageWidgetCredential::ordinal))
        assertEquals(listOf(18_000L, 604_800L), credentials[1].windows.map(UsageWidgetQuotaWindow::durationSeconds))
        assertEquals(listOf(40.0, 60.0), credentials[1].windows.map(UsageWidgetQuotaWindow::remainingPercent))
        assertEquals("pro", credentials[1].planType)
    }

    @Test
    fun `serialized widget cache carries no credential identity`() {
        val payload = Json.encodeToString(
            UsageWidgetCache(credentials = snapshot().toWidgetCredentials()),
        )

        listOf("private-index", "private.json", "private@example.com", "private window").forEach { secret ->
            assertFalse(secret in payload)
        }
    }

    @Test
    fun `failed refresh keeps previous values and records the error`() {
        val previous = UsageWidgetCache().withRefreshResult(
            usage = Result.success(summary()),
            quota = Result.success(snapshot()),
            attemptedAtMs = 1_000,
        )

        val failed = previous.withRefreshResult(
            usage = Result.failure(RemoteFailure.Unauthorized()),
            quota = Result.failure(RemoteFailure.Unauthorized()),
            attemptedAtMs = 2_000,
        )

        assertEquals(previous.usage, failed.usage)
        assertEquals(previous.credentials, failed.credentials)
        assertEquals(UsageWidgetError.Unauthorized, failed.lastError)
        assertEquals(2_000L, failed.lastAttemptAtMs)
    }

    @Test
    fun `successful refresh maps dashboard metrics and clears the error`() {
        val refreshed = UsageWidgetCache(lastError = UsageWidgetError.Network).withRefreshResult(
            usage = Result.success(summary()),
            quota = Result.success(snapshot()),
            attemptedAtMs = 5_000,
        )

        assertEquals(UsageWidgetUsage(120, 0.95, 4_000, 1.25, 3.5, 5_000), refreshed.usage)
        assertEquals(42L, refreshed.quotaUpdatedAtMs)
        assertNull(refreshed.lastError)
    }

    private fun summary() = DashboardSummaryDto(
        today = DashboardTodayDto(totalCalls = 120, successRate = 0.95, totalTokens = 4_000, totalCost = 1.25),
        rolling30m = RollingSummaryDto(rpm = 3.5),
    )

    private fun snapshot() = AccountHealthSnapshot(
        observedAtMs = 42,
        accounts = listOf(
            account(
                provider = "codex",
                planType = "pro",
                windows = listOf(window(604_800, 60.0, "private window"), window(18_000, 80.0), window(18_000, 40.0)),
            ),
            account(provider = "claude", status = AccountStatus.Disabled, windows = listOf(window(18_000, 5.0))),
            account(provider = "kimi", windows = listOf(window(18_000, 10.0))),
            account(provider = "xai", windows = listOf(window(18_000, null))),
        ),
    )

    private fun account(
        provider: String,
        status: AccountStatus = AccountStatus.Active,
        planType: String = "",
        windows: List<AccountQuotaWindow>,
    ) = AccountHealth(
        stableId = "$provider:private-index",
        authIndex = "private-index",
        name = "private.json",
        account = "private@example.com",
        provider = provider,
        status = status,
        planType = planType,
        windows = windows,
        quotaState = AccountQuotaState.Available,
    )

    private fun window(durationSeconds: Long, remaining: Double?, label: String = "") =
        AccountQuotaWindow(durationSeconds = durationSeconds, remainingPercent = remaining, label = label)
}
