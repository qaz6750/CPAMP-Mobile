package com.cpamp.mobile.data.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountHealthMappingTest {
    @Test
    fun `detail lookup never guesses a live account for a cached placeholder`() {
        val first = accountHealth(stableId = "codex\u00001")
        val second = accountHealth(stableId = "claude\u00002")
        val snapshot = AccountHealthSnapshot(observedAtMs = 1, accounts = listOf(second, first))

        assertEquals(null, snapshot.accountForDetail("cached:2"))
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