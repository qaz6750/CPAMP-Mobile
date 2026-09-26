package com.cpamp.mobile.data.widget

import android.content.Context
import androidx.core.content.edit
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.accounts.AccountHealthRepository
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.dashboard.DashboardRepository
import com.cpamp.mobile.data.profile.ServerProfileStore
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.security.AppLockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@Singleton
class UsageWidgetRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val profileStore: ServerProfileStore,
    private val sessionRepository: SessionRepository,
    private val dashboardRepository: DashboardRepository,
    private val accountHealthRepository: AccountHealthRepository,
    private val appLockRepository: AppLockRepository,
    private val cacheDao: CacheDao,
    private val json: Json,
) {
    private val status = context.getSharedPreferences(STATUS_PREFERENCES, Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()

    suspend fun state(nowMs: Long = System.currentTimeMillis()): UsageWidgetState {
        if (appLockRepository.settings.first().enabled) return UsageWidgetState.Locked
        val profileId = profileStore.snapshot().activeProfileId ?: return UsageWidgetState.NotConnected
        val requestedAtMs = status.getLong(KEY_REFRESH_REQUESTED_AT, 0)
        return UsageWidgetState.Ready(
            cache = read(profileId) ?: UsageWidgetCache(),
            refreshing = requestedAtMs > 0 && nowMs - requestedAtMs in 0 until REFRESH_INDICATOR_TIMEOUT_MS,
        )
    }

    fun markRefreshRequested() {
        status.edit { putLong(KEY_REFRESH_REQUESTED_AT, System.currentTimeMillis()) }
    }

    suspend fun refresh() = refreshMutex.withLock {
        try {
            refreshActiveProfile()
        } finally {
            status.edit { remove(KEY_REFRESH_REQUESTED_AT) }
        }
    }

    private suspend fun refreshActiveProfile() {
        // The widget shows nothing while app lock is on, so it must not fetch either.
        if (appLockRepository.settings.first().enabled) return
        val attemptedAtMs = System.currentTimeMillis()
        val session = sessionRepository.backgroundSession().getOrElse { error ->
            val profileId = profileStore.snapshot().activeProfileId ?: return
            write(
                profileId,
                (read(profileId) ?: UsageWidgetCache()).copy(
                    lastError = error.toUsageWidgetError(),
                    lastAttemptAtMs = attemptedAtMs,
                ),
            )
            return
        }
        val todayStartMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val (usage, quota) = coroutineScope {
            val usage = async {
                fetchWithReconnect { dashboardRepository.refresh(session, todayStartMs, attemptedAtMs) }
            }
            val quota = async { fetchWithReconnect { accountHealthRepository.load(session) } }
            usage.await() to quota.await()
        }
        val profileId = session.profile.id
        write(profileId, (read(profileId) ?: UsageWidgetCache()).withRefreshResult(usage, quota, attemptedAtMs))
    }

    // Widget refreshes are minutes apart, so the shared client's pooled connections are often
    // already dropped by the network; the client does not retry those on its own.
    private suspend fun <T> fetchWithReconnect(block: suspend () -> T): Result<T> {
        val first = runSuspendCatching { block() }
        return if (first.exceptionOrNull() is RemoteFailure.Network) runSuspendCatching { block() } else first
    }

    private suspend fun read(profileId: String): UsageWidgetCache? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        return runCatching { json.decodeFromString<UsageWidgetCache>(entity.payload) }.getOrNull()
    }

    private suspend fun write(profileId: String, cache: UsageWidgetCache) {
        cacheDao.upsert(
            CacheEntity(
                profileId = profileId,
                kind = CACHE_KIND,
                payload = json.encodeToString(cache),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val CACHE_KIND = "widget.v1"
        const val STATUS_PREFERENCES = "usage_widget_status"
        const val KEY_REFRESH_REQUESTED_AT = "refresh_requested_at"
        const val REFRESH_INDICATOR_TIMEOUT_MS = 2 * 60 * 1_000L
    }
}
