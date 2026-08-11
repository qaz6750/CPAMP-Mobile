package com.cpamp.mobile.data.accounts

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.monitoring.loadDirectCredentialQuotas
import com.cpamp.mobile.data.monitoring.stableAccountId
import com.cpamp.mobile.data.monitoring.supportsDirectQuota
import com.cpamp.mobile.data.monitoring.toBaseAccountHealth
import com.cpamp.mobile.data.remote.SessionApiClientFactory
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
            ?.toCacheSafeSnapshot()
            ?.copy(cachedAtMs = entity.updatedAt, fromCache = true)
        snapshot?.let { publish(profileId, it) }
        return snapshot
    }

    suspend fun load(session: AuthenticatedSession): AccountHealthSnapshot {
        val api = clientFactory.api(session)
        val observedAtMs = System.currentTimeMillis()
        val files = remoteCall { api.authFiles() }.files
        val directTargets = files.filter { file -> file.supportsDirectQuota && !file.disabled }
        val directAccounts = if (directTargets.isEmpty()) {
            emptyMap()
        } else {
            api.loadDirectCredentialQuotas(json, observedAtMs, directTargets)
                .associateBy(AccountHealth::stableId)
        }
        val accounts = files.map { file ->
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

private const val CACHED_ACCOUNT_ID_PREFIX = "cached:"
