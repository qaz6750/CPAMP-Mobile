package com.cpamp.mobile.data.accounts

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.monitoring.loadDirectCredentialQuotas
import com.cpamp.mobile.data.monitoring.resolvedAuthIndex
import com.cpamp.mobile.data.monitoring.stableAccountId
import com.cpamp.mobile.data.monitoring.supportsDirectQuota
import com.cpamp.mobile.data.monitoring.toBaseAccountHealth
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.CredentialStatDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
enum class AccountUsageState { Available, Unavailable }

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
data class AccountUsage(
    val calls: Long = 0,
    val totalTokens: Long = 0,
    val cost: Double = 0.0,
    val successRate: Double = 0.0,
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
    val usage: AccountUsage? = null,
)

@Serializable
data class AccountHealthSnapshot(
    val inspectionRunId: Long? = null,
    val observedAtMs: Long,
    val accounts: List<AccountHealth>,
    val usageState: AccountUsageState = AccountUsageState.Unavailable,
    val usageFromMs: Long = 0,
    val usageToMs: Long = 0,
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
            ?.toCacheSafeSnapshot()
            ?.copy(cachedAtMs = entity.updatedAt, fromCache = true)
        snapshot?.let { publish(profileId, it) }
        return snapshot
    }

    suspend fun load(session: AuthenticatedSession): AccountHealthSnapshot {
        val api = clientFactory.api(session)
        val observedAtMs = System.currentTimeMillis()
        val files = remoteCall { api.authFiles() }.files
        val zoneId = ZoneId.systemDefault()
        val usageFromMs = Instant.ofEpochMilli(observedAtMs)
            .atZone(zoneId)
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val credentialStatsResult = try {
            remoteCall {
                api.monitoring(
                    MonitoringRequestDto(
                        fromMs = usageFromMs,
                        toMs = observedAtMs,
                        nowMs = observedAtMs,
                        timeZone = zoneId.id,
                        include = MonitoringIncludeDto(credentialStats = true),
                    ),
                )
            }.credentialStats.let { stats -> AccountUsageResult(stats, AccountUsageState.Available) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AccountUsageResult(emptyList(), AccountUsageState.Unavailable)
        }
        val credentialAuthIndexCounts = files
            .map { file -> file.resolvedAuthIndex.trim() }
            .filter(String::isNotEmpty)
            .groupingBy { it }
            .eachCount()
        val credentialFileNameCounts = files
            .map { file -> file.name.normalizedCredentialFileName() }
            .filter(String::isNotEmpty)
            .groupingBy { it }
            .eachCount()
        val directTargets = files.filter { file -> file.supportsDirectQuota && !file.disabled }
        val directAccounts = if (directTargets.isEmpty()) {
            emptyMap()
        } else {
            api.loadDirectCredentialQuotas(json, observedAtMs, directTargets)
                .associateBy(AccountHealth::stableId)
        }
        val accounts = files.map { file ->
            val health = directAccounts[file.stableAccountId]
                ?: file.toBaseAccountHealth(
                    quotaState = if (file.disabled) {
                        AccountQuotaState.NotRequested
                    } else if (file.supportsDirectQuota) {
                        AccountQuotaState.NotRequested
                    } else {
                        AccountQuotaState.Unsupported
                    },
                )
            health.copy(
                usage = credentialStatsResult.stats.accountUsage(
                    authIndex = file.resolvedAuthIndex,
                    authIndexIsUnique = credentialAuthIndexCounts[
                        file.resolvedAuthIndex.trim()
                    ] == 1,
                    fileName = file.name,
                    fileNameIsUnique = credentialFileNameCounts[
                        file.name.normalizedCredentialFileName()
                    ] == 1,
                ),
            )
        }
        val snapshot = AccountHealthSnapshot(
            observedAtMs = observedAtMs,
            accounts = accounts,
            usageState = credentialStatsResult.state,
            usageFromMs = usageFromMs,
            usageToMs = observedAtMs,
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

    private companion object {
        const val CACHE_KIND = "account-health.v3"
    }
}

internal fun AccountHealthSnapshot.toCacheSafeSnapshot(): AccountHealthSnapshot = copy(
    accounts = accounts.mapIndexed { index, account ->
        account.copy(
            stableId = "$CACHED_ACCOUNT_ID_PREFIX${index + 1}",
            authIndex = "",
            name = "Credential ${index + 1}",
            account = "",
            windows = account.windows.map { window ->
                window.copy(resetLabel = "", label = "")
            },
            failure = account.failure?.takeUnless { it == AccountHealthFailure.Inspection },
            source = AccountHealthSource.Cache,
        )
    },
)

internal fun AccountHealthSnapshot.accountForDetail(accountId: String): AccountHealth? {
    return accounts.firstOrNull { it.stableId == accountId }
}

internal fun List<CredentialStatDto>.accountUsage(
    authIndex: String,
    authIndexIsUnique: Boolean = true,
    fileName: String,
    fileNameIsUnique: Boolean = true,
): AccountUsage? {
    val normalizedAuthIndex = authIndex.trim()
    val normalizedFileName = fileName.normalizedCredentialFileName()
    if (authIndexIsUnique && normalizedAuthIndex.isNotEmpty()) {
        val indexMatches = filter { item -> item.authIndex.trim() == normalizedAuthIndex }
        if (indexMatches.isNotEmpty()) return indexMatches.singleOrNull()?.toAccountUsage()
    }
    if (!fileNameIsUnique || normalizedFileName.isEmpty()) return null
    return filter { item ->
        item.authIndex.isBlank() &&
            item.authFileSnapshot.normalizedCredentialFileName() == normalizedFileName
    }.singleOrNull()?.toAccountUsage()
}

private fun CredentialStatDto.toAccountUsage(): AccountUsage =
    AccountUsage(
        calls = calls,
        totalTokens = totalTokens,
        cost = cost,
        successRate = successRate,
    )

private data class AccountUsageResult(
    val stats: List<CredentialStatDto>,
    val state: AccountUsageState,
)

private fun String.normalizedCredentialFileName(): String = trim()
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .lowercase()

private const val CACHED_ACCOUNT_ID_PREFIX = "cached:"
