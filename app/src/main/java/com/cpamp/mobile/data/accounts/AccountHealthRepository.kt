package com.cpamp.mobile.data.accounts

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.monitoring.loadDirectCredentialQuotas
import com.cpamp.mobile.data.monitoring.resolvedAuthIndex
import com.cpamp.mobile.data.monitoring.stableAccountId
import com.cpamp.mobile.data.monitoring.supportsDirectQuota
import com.cpamp.mobile.data.monitoring.toBaseAccountHealth
import com.cpamp.mobile.data.remote.CPAMPApi
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.CredentialStatDto
import com.cpamp.mobile.data.remote.model.MonitoringIncludeDto
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val USAGE_REQUEST_CONCURRENCY = 4

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
    val usageState: AccountUsageState = AccountUsageState.Unavailable,
    val usageFromMs: Long = 0,
    val usageToMs: Long = 0,
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
        val directTargets = files.filter { file -> file.supportsDirectQuota }
        val directAccounts = if (directTargets.isEmpty()) {
            emptyMap()
        } else {
            api.loadDirectCredentialQuotas(json, observedAtMs, directTargets)
                .associateBy(AccountHealth::stableId)
        }
        val baseAccounts = files.map { file ->
            directAccounts[file.stableAccountId]
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
        val usageResults = loadUsageByQuotaCycle(api, baseAccounts, observedAtMs, zoneId)
        val accounts = files.mapIndexed { index, file ->
            val health = baseAccounts[index]
            val usageFromMs = health.currentQuotaCycleStart(observedAtMs)
            val usageResult = usageFromMs?.let(usageResults::get)
            val usage = usageResult
                ?.takeIf { it.state == AccountUsageState.Available }
                ?.stats
                ?.accountUsage(
                    authIndex = file.resolvedAuthIndex,
                    authIndexIsUnique = credentialAuthIndexCounts[
                        file.resolvedAuthIndex.trim()
                    ] == 1,
                    fileName = file.name,
                    fileNameIsUnique = credentialFileNameCounts[
                        file.name.normalizedCredentialFileName()
                    ] == 1,
                )
            val usageState = if (usage != null) {
                AccountUsageState.Available
            } else {
                AccountUsageState.Unavailable
            }
            health.copy(
                usage = usage,
                usageState = usageState,
                usageFromMs = usageFromMs ?: 0,
                usageToMs = observedAtMs.takeIf {
                    usageState == AccountUsageState.Available
                } ?: 0,
            )
        }
        val snapshot = AccountHealthSnapshot(
            observedAtMs = observedAtMs,
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

    private companion object {
        const val CACHE_KIND = "account-health.v4"
    }
}

private suspend fun loadUsageByQuotaCycle(
    api: CPAMPApi,
    accounts: List<AccountHealth>,
    observedAtMs: Long,
    zoneId: ZoneId,
): Map<Long, AccountUsageResult> {
    val periodStarts = accounts.mapNotNull { it.currentQuotaCycleStart(observedAtMs) }.distinct()
    if (periodStarts.isEmpty()) return emptyMap()
    val semaphore = Semaphore(USAGE_REQUEST_CONCURRENCY)
    return coroutineScope {
        periodStarts.map { usageFromMs ->
            async {
                usageFromMs to semaphore.withPermit {
                    try {
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
                        }.credentialStats.let { stats ->
                            AccountUsageResult(stats, AccountUsageState.Available)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        AccountUsageResult(emptyList(), AccountUsageState.Unavailable)
                    }
                }
            }
        }.awaitAll().toMap()
    }
}

internal fun AccountHealth.currentQuotaCycleStart(observedAtMs: Long): Long? = windows
    .mapNotNull { window -> window.currentQuotaCycleStart(observedAtMs) }
    .maxOrNull()

internal fun AccountHealth.estimatedQuotaCycleCost(): Double? {
    val currentUsage = usage ?: return null
    val usageCost = currentUsage.cost.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val cycleStartMs = usageFromMs.takeIf { it > 0 } ?: return null
    val observedAtMs = usageToMs.takeIf { it >= cycleStartMs } ?: return null
    val remainingPercent = windows
        .asSequence()
        .filter { window -> window.currentQuotaCycleStart(observedAtMs) == cycleStartMs }
        .mapNotNull { window ->
            window.remainingPercent?.takeIf(Double::isFinite)?.coerceIn(0.0, 100.0)
        }
        .minOrNull()
        ?: return null
    val usedFraction = (100.0 - remainingPercent) / 100.0
    if (usedFraction <= 0.0) return null
    return (usageCost / usedFraction).takeIf(Double::isFinite)
}

private fun AccountQuotaWindow.currentQuotaCycleStart(observedAtMs: Long): Long? {
    val resetAtMs = resetAtMs ?: return null
    if (durationSeconds <= 0 || durationSeconds > Long.MAX_VALUE / 1_000) return null
    if (resetAtMs < observedAtMs) return null
    val startedAtMs = resetAtMs - durationSeconds * 1_000
    return startedAtMs.takeIf { it > 0 && it <= observedAtMs }
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
    if (isEmpty()) return AccountUsage()
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
