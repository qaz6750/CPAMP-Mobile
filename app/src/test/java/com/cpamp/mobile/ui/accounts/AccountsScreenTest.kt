package com.cpamp.mobile.ui.accounts

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsScreenTest {
    @Test
    fun `quota level follows health thresholds`() {
        assertEquals(QuotaLevel.Healthy, quotaLevel(50.0))
        assertEquals(QuotaLevel.Warning, quotaLevel(49.999))
        assertEquals(QuotaLevel.Warning, quotaLevel(20.0))
        assertEquals(QuotaLevel.Critical, quotaLevel(19.999))
        assertEquals(QuotaLevel.Unknown, quotaLevel(null))
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