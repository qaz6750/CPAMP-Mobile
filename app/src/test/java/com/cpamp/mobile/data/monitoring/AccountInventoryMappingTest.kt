package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.data.remote.model.AuthFileDto
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountInventoryMappingTest {
    @Test
    fun `stable id prefers auth index and falls back to provider plus file name`() {
        val indexed = AuthFileDto(
            name = "renamed.json",
            type = "anthropic",
            authIndex = JsonPrimitive("auth-42"),
        )
        val fallback = AuthFileDto(name = "account.json", provider = "grok")

        assertEquals("claude\u0000auth-42", indexed.stableAccountId)
        assertEquals("xai\u0000account.json", fallback.stableAccountId)
    }

    @Test
    fun `unsupported and disabled credentials remain visible with degraded quota state`() {
        val unsupported = AuthFileDto(name = "custom.json", provider = "custom-provider")
        val disabled = AuthFileDto(
            name = "codex.json",
            provider = "codex",
            authIndex = JsonPrimitive("7"),
            disabled = true,
        )

        assertFalse(unsupported.supportsDirectQuota)
        val unsupportedHealth = unsupported.toBaseAccountHealth(AccountQuotaState.Unsupported)
        assertEquals("custom-provider", unsupportedHealth.provider)
        assertEquals(AccountStatus.Active, unsupportedHealth.status)
        assertEquals(AccountQuotaState.Unsupported, unsupportedHealth.quotaState)

        assertTrue(disabled.supportsDirectQuota)
        val disabledHealth = disabled.toBaseAccountHealth(AccountQuotaState.NotRequested)
        assertEquals(AccountStatus.Disabled, disabledHealth.status)
        assertEquals(AccountQuotaState.NotRequested, disabledHealth.quotaState)
    }
}