package com.cpamp.mobile.data.accounts

import com.cpamp.mobile.data.remote.model.CredentialStatDto
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

    @Test
    fun `credential usage matches management stats by auth index`() {
        val usage = listOf(
            CredentialStatDto(
                authIndex = "42",
                authFileSnapshot = "other.json",
                calls = 1_000,
                totalTokens = 97_300_000,
                cost = 181.475,
                successRate = 0.992,
            ),
        ).accountUsage(authIndex = "42", fileName = "credential.json")

        assertEquals(1_000L, usage?.calls)
        assertEquals(97_300_000L, usage?.totalTokens)
        assertEquals(181.475, usage?.cost ?: -1.0, 0.001)
        assertEquals(0.992, usage?.successRate ?: -1.0, 0.0001)
    }

    @Test
    fun `credential usage falls back to a unique file snapshot`() {
        val usage = listOf(
            CredentialStatDto(authFileSnapshot = "/masked/Credential.JSON", calls = 12),
        ).accountUsage(authIndex = "", fileName = "credential.json")

        assertEquals(12L, usage?.calls)
    }

    @Test
    fun `empty credential statistics mean zero usage`() {
        val usage = emptyList<CredentialStatDto>().accountUsage(
            authIndex = "42",
            fileName = "credential.json",
        )

        assertEquals(AccountUsage(), usage)
    }

    @Test
    fun `credential usage stays absent when management returns no match`() {
        val usage = listOf(
            CredentialStatDto(authIndex = "other", calls = 12),
        ).accountUsage(authIndex = "42", fileName = "credential.json")

        assertEquals(null, usage)
    }

    @Test
    fun `credential usage does not match an empty file name`() {
        val usage = listOf(
            CredentialStatDto(calls = 12),
        ).accountUsage(authIndex = "", fileName = "")

        assertEquals(null, usage)
    }

    @Test
    fun `credential usage stays absent when auth index is ambiguous`() {
        val usage = listOf(
            CredentialStatDto(authIndex = "42", calls = 12),
            CredentialStatDto(authIndex = "42", calls = 24),
        ).accountUsage(authIndex = "42", fileName = "credential.json")

        assertEquals(null, usage)
    }

    @Test
    fun `credential usage skips auth index when credential index is ambiguous`() {
        val usage = listOf(
            CredentialStatDto(authIndex = "42", calls = 12),
        ).accountUsage(
            authIndex = "42",
            authIndexIsUnique = false,
            fileName = "credential.json",
        )

        assertEquals(null, usage)
    }

    @Test
    fun `credential usage skips file fallback when credential name is ambiguous`() {
        val usage = listOf(
            CredentialStatDto(authFileSnapshot = "credential.json", calls = 12),
        ).accountUsage(
            authIndex = "",
            fileName = "credential.json",
            fileNameIsUnique = false,
        )

        assertEquals(null, usage)
    }

    @Test
    fun `usage cycle starts at the most recent quota reset`() {
        val account = accountHealth(
            windows = listOf(
                AccountQuotaWindow(durationSeconds = 100, remainingPercent = 50.0, resetAtMs = 2_050_000),
                AccountQuotaWindow(durationSeconds = 10, remainingPercent = 80.0, resetAtMs = 2_005_000),
            ),
        )

        assertEquals(1_995_000L, account.currentQuotaCycleStart(observedAtMs = 2_000_000))
    }

    @Test
    fun `usage cycle ignores windows without a current reset boundary`() {
        val account = accountHealth(
            windows = listOf(
                AccountQuotaWindow(durationSeconds = 0, remainingPercent = 50.0, resetAtMs = 2_050_000),
                AccountQuotaWindow(durationSeconds = 10, remainingPercent = 80.0, resetAtMs = 1_999_000),
            ),
        )

        assertEquals(null, account.currentQuotaCycleStart(observedAtMs = 2_000_000))
    }

    @Test
    fun `projected cost uses remaining quota from the matching usage cycle`() {
        val account = accountHealth(
            windows = listOf(
                AccountQuotaWindow(durationSeconds = 100, remainingPercent = 50.0, resetAtMs = 2_050_000),
                AccountQuotaWindow(durationSeconds = 10, remainingPercent = 80.0, resetAtMs = 2_005_000),
            ),
        ).copy(
            usage = AccountUsage(cost = 2.5),
            usageFromMs = 1_995_000,
            usageToMs = 2_000_000,
        )

        assertEquals(12.5, account.estimatedQuotaCycleCost() ?: -1.0, 0.001)
    }

    @Test
    fun `projected cost requires priced usage and consumed quota`() {
        val account = accountHealth(
            windows = listOf(
                AccountQuotaWindow(durationSeconds = 10, remainingPercent = 100.0, resetAtMs = 2_005_000),
            ),
        ).copy(
            usage = AccountUsage(cost = 2.5),
            usageFromMs = 1_995_000,
            usageToMs = 2_000_000,
        )

        assertEquals(null, account.estimatedQuotaCycleCost())
        assertEquals(null, account.copy(usage = AccountUsage(cost = 0.0)).estimatedQuotaCycleCost())
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
