package com.cpamp.mobile.data.accounts

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.monitoring.loadDirectCredentialQuotas
import com.cpamp.mobile.data.monitoring.resolvedAccount
import com.cpamp.mobile.data.monitoring.resolvedAuthIndex
import com.cpamp.mobile.data.monitoring.resolvedPlanType
import com.cpamp.mobile.data.monitoring.resolvedProvider
import com.cpamp.mobile.data.monitoring.stableAccountId
import com.cpamp.mobile.data.monitoring.supportsDirectQuota
import com.cpamp.mobile.data.monitoring.toBaseAccountHealth
import com.cpamp.mobile.data.remote.CPAMPApi
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.remote.model.CodexInspectionResultDto
import com.cpamp.mobile.data.remote.model.CodexInspectionRunDetailDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Serializable
enum class AccountStatus { Active, Disabled }

@Serializable
enum class AccountQuotaState { Available, NotRequested, Unsupported, Failed }

@Serializable
enum class AccountHealthFailure { Inspection, ProviderRequest }

@Serializable
enum class AccountHealthSource { AuthFile, Inspection, Direct, Cache }

@Serializable
data class AccountQuotaWindow(
    val durationSeconds: Long,
    val remainingPercent: Double?,
    val resetAtMs: Long? = null,
    val resetLabel: String = "",
    val label: String = "",
)

@Serializable
data class AccountHealth(
    val stableId: String,
    val authIndex: String,
    val name: String,
    val account: String,
    val provider: String,
    val status: AccountStatus,
    val planType: String,
    val windows: List<AccountQuotaWindow>,
    val quotaState: AccountQuotaState,
    val failure: AccountHealthFailure? = null,
    val source: AccountHealthSource = AccountHealthSource.AuthFile,
)

@Serializable
data class AccountHealthSnapshot(
    val inspectionRunId: Long? = null,
    val observedAtMs: Long,
    val accounts: List<AccountHealth>,
    val cachedAtMs: Long = 0,
    val fromCache: Boolean = false,
)

@Singleton
class AccountHealthRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    private val mutableSnapshots = MutableStateFlow<Map<String, AccountHealthSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, AccountHealthSnapshot>> = mutableSnapshots.asStateFlow()

    suspend fun cached(profileId: String): AccountHealthSnapshot? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        val snapshot = runCatching { json.decodeFromString<AccountHealthSnapshot>(entity.payload) }
            .getOrNull()
            ?.copy(cachedAtMs = entity.updatedAt, fromCache = true)
        snapshot?.let { publish(profileId, it) }
        return snapshot
    }

    suspend fun load(
        session: AuthenticatedSession,
        refreshProviderQuotas: Boolean = false,
    ): AccountHealthSnapshot {
        val api = clientFactory.api(session)
        val observedAtMs = System.currentTimeMillis()
        val files = remoteCall { api.authFiles() }.files
        val inspection = api.loadLatestCompletedInspection()
        val inspectionByAccount = inspection?.results.orEmpty()
            .mapNotNull { result ->
                result.matchKey()?.let { key -> key to result }
            }
            .toMap()
        val inspectionByFile = inspection?.results.orEmpty()
            .associateBy { result -> result.fallbackMatchKey() }

        val inspectionAccounts = mutableMapOf<String, AccountHealth>()
        val directTargets = mutableListOf<AuthFileDto>()
        files.forEach { file ->
            val result = file.resolvedAuthIndex
                .takeIf(String::isNotBlank)
                ?.let { inspectionByAccount[accountMatchKey(file.resolvedProvider, it)] }
                ?: inspectionByFile[file.fallbackMatchKey()]
            val standardized = result?.takeIf {
                it.resolvedQuotaWindows != null || it.error.isNotBlank() || it.resolvedErrorKind.isNotBlank()
            }
            if (standardized != null) {
                inspectionAccounts[file.stableAccountId] = standardized.toAccountHealth(file)
            }
            if (refreshProviderQuotas && file.supportsDirectQuota && !file.disabled) {
                directTargets += file
            }
        }

        val directAccounts = if (directTargets.isEmpty()) {
            emptyMap()
        } else {
            api.loadDirectCredentialQuotas(json, observedAtMs, directTargets)
                .associateBy(AccountHealth::stableId)
        }
        val accounts = files.map { file ->
            directAccounts[file.stableAccountId]
                ?: inspectionAccounts[file.stableAccountId]
                ?: file.toBaseAccountHealth(
                    quotaState = if (file.disabled) {
                        AccountQuotaState.NotRequested
                    } else if (file.supportsDirectQuota) {
                        AccountQuotaState.NotRequested
                    } else {
                        AccountQuotaState.Unsupported
                    },
                )
        }
        val snapshot = AccountHealthSnapshot(
            inspectionRunId = inspection?.run?.id?.takeIf { it > 0 },
            observedAtMs = inspection?.run?.resolvedFinishedAtMs?.takeIf { it > 0 } ?: observedAtMs,
            accounts = accounts,
            cachedAtMs = observedAtMs,
        )
        cacheDao.upsert(
            CacheEntity(
                profileId = session.profile.id,
                kind = CACHE_KIND,
                payload = json.encodeToString(snapshot.toCacheSafeSnapshot()),
                updatedAt = observedAtMs,
            ),
        )
        publish(session.profile.id, snapshot)
        return snapshot
    }

    private fun publish(profileId: String, snapshot: AccountHealthSnapshot) {
        mutableSnapshots.update { current -> current + (profileId to snapshot) }
    }

    private suspend fun CPAMPApi.loadLatestCompletedInspection(): CodexInspectionRunDetailDto? {
        val latest = try {
            remoteCall { codexInspectionRuns(limit = 20) }.items
                .filter { it.status.equals("completed", ignoreCase = true) && it.id > 0 }
                .maxByOrNull { maxOf(it.resolvedFinishedAtMs, it.resolvedUpdatedAtMs) }
        } catch (error: RemoteFailure.NotFound) {
            return null
        } catch (error: RemoteFailure.Unauthorized) {
            throw error
        } catch (_: Exception) {
            return null
        } ?: return null
        return try {
            remoteCall { codexInspectionRun(latest.id) }
        } catch (error: RemoteFailure.NotFound) {
            null
        } catch (error: RemoteFailure.Unauthorized) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val CACHE_KIND = "account-health.v2"
    }
}

internal fun AccountHealthSnapshot.toCacheSafeSnapshot(): AccountHealthSnapshot = copy(
    accounts = accounts.mapIndexed { index, account ->
        account.copy(
            stableId = "cached:${index + 1}",
            authIndex = "",
            name = "Credential ${index + 1}",
            account = "",
            source = AccountHealthSource.Cache,
        )
    },
)

private fun CodexInspectionResultDto.toAccountHealth(file: AuthFileDto): AccountHealth {
    val windows = resolvedQuotaWindows.orEmpty().map { window ->
        AccountQuotaWindow(
            durationSeconds = window.resolvedLimitWindowSeconds?.toLong()?.coerceAtLeast(0) ?: 0,
            remainingPercent = window.resolvedUsedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) },
            resetLabel = window.resolvedResetLabel,
            label = window.resolvedLabel,
        )
    }
    val disabled = disabled || file.disabled
    val failed = windows.isEmpty() && (error.isNotBlank() || resolvedErrorKind.isNotBlank())
    return AccountHealth(
        stableId = file.stableAccountId,
        authIndex = file.resolvedAuthIndex,
        name = resolvedFileName.ifBlank { file.name },
        account = resolvedDisplayAccount.ifBlank { file.resolvedAccount },
        provider = resolvedProvider.ifBlank { file.resolvedProvider },
        status = if (disabled) AccountStatus.Disabled else AccountStatus.Active,
        planType = resolvedPlanType.ifBlank { file.resolvedPlanType },
        windows = windows,
        quotaState = resolvedInspectionQuotaState(
            disabled = disabled,
            failed = failed,
            hasWindows = windows.isNotEmpty(),
        ),
        failure = AccountHealthFailure.Inspection.takeIf { failed },
        source = AccountHealthSource.Inspection,
    )
}

internal fun resolvedInspectionQuotaState(
        disabled: Boolean,
        failed: Boolean,
        hasWindows: Boolean,
    ): AccountQuotaState = when {
        failed -> AccountQuotaState.Failed
        hasWindows -> AccountQuotaState.Available
        disabled -> AccountQuotaState.NotRequested
        else -> AccountQuotaState.Unsupported
}

private fun CodexInspectionResultDto.matchKey(): String? = resolvedAuthIndex
    .takeIf(String::isNotBlank)
    ?.let { accountMatchKey(resolvedProvider, it) }

private fun CodexInspectionResultDto.fallbackMatchKey(): String =
    accountMatchKey(resolvedProvider, resolvedFileName)

private fun AuthFileDto.fallbackMatchKey(): String = accountMatchKey(resolvedProvider, name)

private fun accountMatchKey(provider: String, identity: String): String =
    "${provider.trim().lowercase()}\u0000${identity.trim()}"
