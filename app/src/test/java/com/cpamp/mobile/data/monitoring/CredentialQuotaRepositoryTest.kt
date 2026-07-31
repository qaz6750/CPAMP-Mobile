package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.remote.model.CodexInspectionQuotaWindowDto
import com.cpamp.mobile.data.remote.model.CodexInspectionResultDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialQuotaRepositoryTest {
    @Test
    fun `maps standardized server inspection quota`() {
        val quota = CodexInspectionResultDto(
            fileName = "codex.json",
            displayAccount = "user@example.com",
            provider = "codex",
            planType = "pro",
            quotaWindows = listOf(
                CodexInspectionQuotaWindowDto(
                    usedPercent = 25.0,
                    resetLabel = "08/01 12:00",
                    limitWindowSeconds = 18_000.0,
                ),
            ),
        ).toCredentialQuota()

        assertEquals("user@example.com", quota.account)
        assertEquals("pro", quota.planType)
        assertEquals(CredentialQuotaQueryState.Success, quota.queryState)
        assertEquals(75.0, quota.windows.single().remainingPercent ?: -1.0, 0.001)
        assertEquals("08/01 12:00", quota.windows.single().resetLabel)
    }

    @Test
    fun `maps disabled and failed server inspection results`() {
        val disabled = CodexInspectionResultDto(disabled = true).toCredentialQuota()
        val failed = CodexInspectionResultDto(errorKind = "protocol_changed").toCredentialQuota()

        assertEquals(CredentialQuotaQueryState.NotRequested, disabled.queryState)
        assertEquals(CredentialQuotaQueryState.Failed, failed.queryState)
        assertEquals(CredentialQuotaFailure.ServerResult, failed.failure)
    }
}
