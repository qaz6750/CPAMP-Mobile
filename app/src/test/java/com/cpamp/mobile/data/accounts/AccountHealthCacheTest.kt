package com.cpamp.mobile.data.accounts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountHealthCacheTest {
    @Test
    fun `legacy cache defaults usage fields`() {
        val snapshot = Json.decodeFromString<AccountHealthSnapshot>(
            """{"observedAtMs":1,"accounts":[]}""",
        )

        assertEquals(AccountUsageState.Unavailable, snapshot.usageState)
        assertEquals(0L, snapshot.usageFromMs)
        assertEquals(0L, snapshot.usageToMs)
    }

    @Test
    fun `cache snapshot removes identity and preserves health summary`() {
        val snapshot = AccountHealthSnapshot(
            inspectionRunId = 42,
            observedAtMs = 1_785_500_000_000,
            accounts = listOf(
                AccountHealth(
                    stableId = "codex:private-index",
                    authIndex = "private-index",
                    name = "private-account.json",
                    account = "private@example.com",
                    provider = "codex",
                    status = AccountStatus.Active,
                    planType = "pro",
                    windows = listOf(
                        AccountQuotaWindow(
                            durationSeconds = 18_000,
                            remainingPercent = 80.0,
                            resetAtMs = 1_785_500_100_000,
                            resetLabel = "private reset",
                            label = "private window",
                        ),
                    ),
                    quotaState = AccountQuotaState.Available,
                    failure = AccountHealthFailure.Inspection,
                    source = AccountHealthSource.Inspection,
                    usage = AccountUsage(
                        calls = 1_000,
                        totalTokens = 97_300_000,
                        cost = 181.475,
                        successRate = 0.992,
                    ),
                ),
            ),
            usageState = AccountUsageState.Available,
            usageFromMs = 1_785_000_000_000,
            usageToMs = 1_785_500_000_000,
        )

        val cached = snapshot.toCacheSafeSnapshot()
        val account = cached.accounts.single()
        assertEquals("cached:1", account.stableId)
        assertEquals("", account.authIndex)
        assertEquals("Credential 1", account.name)
        assertEquals("", account.account)
        assertEquals(AccountHealthSource.Cache, account.source)
        assertEquals(null, account.failure)
        assertEquals("codex", account.provider)
        assertEquals(AccountQuotaState.Available, account.quotaState)
        assertEquals(AccountUsageState.Available, cached.usageState)
        assertEquals(1_000L, account.usage?.calls)
        assertEquals(97_300_000L, account.usage?.totalTokens)
        assertEquals(1_785_000_000_000, cached.usageFromMs)
        assertEquals(1_785_500_000_000, cached.usageToMs)
        assertEquals(80.0, account.windows.single().remainingPercent ?: -1.0, 0.001)
        assertEquals("", account.windows.single().resetLabel)
        assertEquals("", account.windows.single().label)

        val payload = Json.encodeToString(cached)
        assertFalse(payload.contains("private-index"))
        assertFalse(payload.contains("private-account.json"))
        assertFalse(payload.contains("private@example.com"))
        assertFalse(payload.contains("private reset"))
        assertFalse(payload.contains("private window"))
    }
}
