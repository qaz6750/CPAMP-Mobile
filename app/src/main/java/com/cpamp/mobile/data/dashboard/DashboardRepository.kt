package com.cpamp.mobile.data.dashboard

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class CachedDashboard(
    val summary: DashboardSummaryDto,
    val updatedAt: Long,
)

@Singleton
class DashboardRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    suspend fun cached(profileId: String): CachedDashboard? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        val summary = runCatching { json.decodeFromString<DashboardSummaryDto>(entity.payload) }
            .getOrNull() ?: return null
        return CachedDashboard(summary, entity.updatedAt)
    }

    suspend fun refresh(
        session: AuthenticatedSession,
        todayStartMs: Long,
        nowMs: Long,
    ): DashboardSummaryDto {
        val summary = remoteCall {
            clientFactory.api(session).dashboard(
                todayStartMs = todayStartMs,
                nowMs = nowMs,
            )
        }
        val cacheSafeSummary = summary.copy(recentFailures = emptyList())
        cacheDao.upsert(
            CacheEntity(
                profileId = session.profile.id,
                kind = CACHE_KIND,
                payload = json.encodeToString(cacheSafeSummary),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return summary
    }

    private companion object {
        const val CACHE_KIND = "dashboard.v1"
    }
}

