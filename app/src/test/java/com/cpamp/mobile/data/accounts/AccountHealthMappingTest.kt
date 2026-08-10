package com.cpamp.mobile.data.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountHealthMappingTest {
    @Test
    fun `disabled credential keeps server quota windows available`() {
        assertEquals(
            AccountQuotaState.Available,
            resolvedInspectionQuotaState(disabled = true, failed = false, hasWindows = true),
        )
    }

    @Test
    fun `disabled credential without server quota remains not requested`() {
        assertEquals(
            AccountQuotaState.NotRequested,
            resolvedInspectionQuotaState(disabled = true, failed = false, hasWindows = false),
        )
    }

    @Test
    fun `server windows survive a failed direct refresh`() {
        val inspection = accountHealth(
            windows = listOf(AccountQuotaWindow(durationSeconds = 18_000, remainingPercent = 42.0)),
            quotaState = AccountQuotaState.Available,
            source = AccountHealthSource.Inspection,
        )
        val failedDirect = accountHealth(
            quotaState = AccountQuotaState.Failed,
            failure = AccountHealthFailure.ProviderRequest,
            source = AccountHealthSource.Direct,
        )

        assertEquals(
            inspection.copy(failure = AccountHealthFailure.ProviderRequest),
            preferredAccountHealth(failedDirect, inspection),
        )
    }

    @Test
    fun `direct windows take precedence over inspection windows`() {
        val direct = accountHealth(
            windows = listOf(AccountQuotaWindow(durationSeconds = 18_000, remainingPercent = 75.0)),
            quotaState = AccountQuotaState.Available,
            source = AccountHealthSource.Direct,
        )
        val inspection = accountHealth(
            windows = listOf(AccountQuotaWindow(durationSeconds = 18_000, remainingPercent = 42.0)),
            quotaState = AccountQuotaState.Available,
            source = AccountHealthSource.Inspection,
        )

        assertEquals(direct, preferredAccountHealth(direct, inspection))
    }

    @Test
    fun `detail lookup never guesses a live account for a cached placeholder`() {
        val first = accountHealth(stableId = "codex\u00001")
        val second = accountHealth(stableId = "claude\u00002")
        val snapshot = AccountHealthSnapshot(observedAtMs = 1, accounts = listOf(second, first))

        assertEquals(null, snapshot.accountForDetail("cached:2"))
    }

    @Test
    fun `provider aliases use the same inspection match key`() {
        assertEquals(accountMatchKey("codex", "42"), accountMatchKey("openai", "42"))
        assertEquals(accountMatchKey("claude", "42"), accountMatchKey("anthropic", "42"))
        assertEquals(accountMatchKey("xai", "42"), accountMatchKey("grok", "42"))
    }

    private fun accountHealth(
        stableId: String = "codex\u00001",
        windows: List<AccountQuotaWindow> = emptyList(),
        quotaState: AccountQuotaState = AccountQuotaState.NotRequested,
        failure: AccountHealthFailure? = null,
        source: AccountHealthSource = AccountHealthSource.AuthFile,
    ) = AccountHealth(
        stableId = stableId,
        authIndex = "1",
        name = "credential.json",
        account = "user@example.com",
        provider = "codex",
        status = AccountStatus.Active,
        planType = "plus",
        windows = windows,
        quotaState = quotaState,
        failure = failure,
        source = source,
    )
}