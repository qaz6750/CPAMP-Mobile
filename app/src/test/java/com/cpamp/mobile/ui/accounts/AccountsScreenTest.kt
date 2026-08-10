package com.cpamp.mobile.ui.accounts

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsScreenTest {
    @Test
    fun `quota level follows health thresholds`() {
        assertEquals(QuotaLevel.Healthy, quotaLevel(70.0))
        assertEquals(QuotaLevel.Warning, quotaLevel(69.999))
        assertEquals(QuotaLevel.Warning, quotaLevel(30.0))
        assertEquals(QuotaLevel.Critical, quotaLevel(29.999))
        assertEquals(QuotaLevel.Critical, quotaLevel(0.0))
        assertEquals(QuotaLevel.Unknown, quotaLevel(null))
        assertEquals(QuotaLevel.Unknown, quotaLevel(Double.NaN))
    }

    @Test
    fun `remaining percent is finite and bounded`() {
        assertEquals(0.0, normalizedRemainingPercent(-1.0) ?: -1.0, 0.001)
        assertEquals(0.0, normalizedRemainingPercent(0.0) ?: -1.0, 0.001)
        assertEquals(100.0, normalizedRemainingPercent(101.0) ?: -1.0, 0.001)
        assertEquals(null, normalizedRemainingPercent(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `account title prefers account then file then provider`() {
        val base = AccountHealth(
            stableId = "id",
            authIndex = "1",
            name = "credential.json",
            account = "user@example.com",
            provider = "codex",
            status = AccountStatus.Active,
            planType = "",
            windows = emptyList(),
            quotaState = AccountQuotaState.NotRequested,
        )

        assertEquals("user@example.com", base.displayTitle())
        assertEquals("credential.json", base.copy(account = "").displayTitle())
        assertEquals("OpenAI Codex", base.copy(account = "", name = "").displayTitle())
    }
}