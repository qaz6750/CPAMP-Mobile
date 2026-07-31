package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.CodexInspectionResultDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CredentialAccountStatus { Active, Disabled }

enum class CredentialQuotaQueryState { Success, NotRequested, Failed }

enum class CredentialQuotaFailure { ServerResult }

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
)

@Serializable
data class CredentialQuotaSnapshot(
    val runId: Long,
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
    suspend fun startInspection(session: AuthenticatedSession) {
        remoteCall { clientFactory.api(session).startCodexInspection() }
    }

    suspend fun cached(profileId: String): CredentialQuotaSnapshot? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        return runCatching { json.decodeFromString<CredentialQuotaSnapshot>(entity.payload) }
            .getOrNull()
            ?.copy(cachedAtMs = entity.updatedAt, fromCache = true)
    }

    suspend fun load(session: AuthenticatedSession): CredentialQuotaSnapshot {
        val api = clientFactory.api(session)
        val latestRun = remoteCall { api.codexInspectionRuns() }
            .items
            .asSequence()
            .filter { it.id > 0 && it.status.equals(COMPLETED_STATUS, ignoreCase = true) }
            .maxByOrNull { it.finishedAtMs ?: it.updatedAtMs }
            ?: throw NoCompletedInspectionException()
        val detail = remoteCall { api.codexInspectionRun(latestRun.id) }
        val snapshot = CredentialQuotaSnapshot(
            runId = detail.run.id,
            finishedAtMs = detail.run.finishedAtMs ?: detail.run.updatedAtMs,
            quotas = detail.results.map(CodexInspectionResultDto::toCredentialQuota),
            cachedAtMs = System.currentTimeMillis(),
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
        const val COMPLETED_STATUS = "completed"
        const val CACHE_KIND = "credential-quotas.v1"
    }
}

internal class NoCompletedInspectionException : IllegalStateException()

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
