package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthFailure
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.CodexInspectionResultDto
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CredentialAccountStatus { Active, Disabled }

enum class CredentialQuotaQueryState { Success, NotRequested, Failed }

enum class CredentialQuotaFailure { ServerResult, ProviderRequest }

@Serializable
data class CredentialQuota(
    val name: String,
    val account: String,
    val provider: String,
    val accountStatus: CredentialAccountStatus,
    val planType: String,
    val windows: List<CredentialQuotaWindow>,
    val queryState: CredentialQuotaQueryState,
    val failure: CredentialQuotaFailure? = null,
)

@Serializable
data class CredentialQuotaWindow(
    val durationSeconds: Long,
    val remainingPercent: Double?,
    val resetAtMs: Long? = null,
    val resetLabel: String = "",
    val label: String = "",
)

@Serializable
data class CredentialQuotaSnapshot(
    val runId: Long? = null,
    val finishedAtMs: Long,
    val quotas: List<CredentialQuota>,
    val cachedAtMs: Long = 0,
    val fromCache: Boolean = false,
)

@Singleton
class CredentialQuotaRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    suspend fun cached(profileId: String): CredentialQuotaSnapshot? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        return runCatching { json.decodeFromString<CredentialQuotaSnapshot>(entity.payload) }
            .getOrNull()
            ?.copy(cachedAtMs = entity.updatedAt, fromCache = true)
    }

    suspend fun load(session: AuthenticatedSession): CredentialQuotaSnapshot {
        val api = clientFactory.api(session)
        val fetchedAtMs = System.currentTimeMillis()
        val snapshot = CredentialQuotaSnapshot(
            finishedAtMs = fetchedAtMs,
            quotas = api.loadDirectCredentialQuotas(json, fetchedAtMs).map(AccountHealth::toCredentialQuota),
            cachedAtMs = fetchedAtMs,
        )
        // Persist quota aggregates only; account identifiers remain memory-only.
        cacheDao.upsert(
            CacheEntity(
                profileId = session.profile.id,
                kind = CACHE_KIND,
                payload = json.encodeToString(snapshot.cacheSafe()),
                updatedAt = snapshot.cachedAtMs,
            ),
        )
        return snapshot
    }

    private fun CredentialQuotaSnapshot.cacheSafe(): CredentialQuotaSnapshot = copy(
        quotas = quotas.mapIndexed { index, quota ->
            quota.copy(
                name = "Credential ${index + 1}",
                account = "",
            )
        },
    )

    private companion object {
        const val CACHE_KIND = "credential-quotas.v1"
    }
}

private fun AccountHealth.toCredentialQuota(): CredentialQuota = CredentialQuota(
    name = name,
    account = account,
    provider = provider,
    accountStatus = when (status) {
        AccountStatus.Active -> CredentialAccountStatus.Active
        AccountStatus.Disabled -> CredentialAccountStatus.Disabled
    },
    planType = planType,
    windows = windows.map { window ->
        CredentialQuotaWindow(
            durationSeconds = window.durationSeconds,
            remainingPercent = window.remainingPercent,
            resetAtMs = window.resetAtMs,
            resetLabel = window.resetLabel,
            label = window.label,
        )
    },
    queryState = when (quotaState) {
        AccountQuotaState.Available -> CredentialQuotaQueryState.Success
        AccountQuotaState.NotRequested,
        AccountQuotaState.Unsupported,
        -> CredentialQuotaQueryState.NotRequested
        AccountQuotaState.Failed -> CredentialQuotaQueryState.Failed
    },
    failure = when (failure) {
        AccountHealthFailure.Inspection -> CredentialQuotaFailure.ServerResult
        AccountHealthFailure.ProviderRequest -> CredentialQuotaFailure.ProviderRequest
        null -> null
    },
)

internal fun CodexInspectionResultDto.toCredentialQuota(): CredentialQuota {
    val windows = quotaWindows.orEmpty().map { window ->
        CredentialQuotaWindow(
            durationSeconds = window.limitWindowSeconds?.toLong()?.coerceAtLeast(0) ?: 0,
            remainingPercent = window.usedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) },
            resetLabel = window.resetLabel,
        )
    }
    val failed = windows.isEmpty() && (error.isNotBlank() || errorKind.isNotBlank())
    return CredentialQuota(
        name = fileName,
        account = displayAccount.ifBlank { accountSnapshot.ifBlank { fileName } },
        provider = provider.trim().lowercase().ifBlank { "unknown" },
        accountStatus = if (disabled) CredentialAccountStatus.Disabled else CredentialAccountStatus.Active,
        planType = planType.orEmpty(),
        windows = windows,
        queryState = when {
            disabled -> CredentialQuotaQueryState.NotRequested
            failed -> CredentialQuotaQueryState.Failed
            else -> CredentialQuotaQueryState.Success
        },
        failure = CredentialQuotaFailure.ServerResult.takeIf { failed },
    )
}
