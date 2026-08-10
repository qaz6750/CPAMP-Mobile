package com.cpamp.mobile.ui.accounts

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthSource
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsViewModelTest {
    @Test
    fun `opened cached detail remains visible when live ids replace placeholders`() {
        val cachedAccount = AccountHealth(
            stableId = "cached:1",
            authIndex = "",
            name = "Credential 1",
            account = "",
            provider = "codex",
            status = AccountStatus.Active,
            planType = "plus",
            windows = emptyList(),
            quotaState = AccountQuotaState.NotRequested,
            source = AccountHealthSource.Cache,
        )
        val previous = AccountDetailUiState(account = cachedAccount, observedAtMs = 1, fromCache = true)

        assertEquals(
            previous,
            retainCachedAccountDetail(previous, AccountDetailUiState(), "cached:1"),
        )
    }

    @Test
    fun `normal detail uses the current snapshot state`() {
        val current = AccountDetailUiState(observedAtMs = 2)

        assertEquals(
            current,
            retainCachedAccountDetail(AccountDetailUiState(), current, "codex\u00001"),
        )
    }
}