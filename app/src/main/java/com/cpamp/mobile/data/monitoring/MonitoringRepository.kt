package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.cache.CacheEntity
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.MonitoringRequestDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class CachedMonitoring(
    val response: MonitoringResponseDto,
    val updatedAt: Long,
)

@Singleton
class MonitoringRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    suspend fun cached(profileId: String): CachedMonitoring? {
        val entity = cacheDao.get(profileId, CACHE_KIND) ?: return null
        val response = runCatching { json.decodeFromString<MonitoringResponseDto>(entity.payload) }
            .getOrNull() ?: return null
        return CachedMonitoring(response, entity.updatedAt)
    }

    suspend fun refresh(
        session: AuthenticatedSession,
        request: MonitoringRequestDto,
        cacheResult: Boolean,
    ): MonitoringResponseDto {
        val response = remoteCall { clientFactory.api(session).monitoring(request) }
        if (cacheResult) {
            cacheDao.upsert(
                CacheEntity(
                    profileId = session.profile.id,
                    kind = CACHE_KIND,
                    payload = json.encodeToString(response.cacheSafeCopy()),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        return response
    }

    private fun MonitoringResponseDto.cacheSafeCopy(): MonitoringResponseDto = copy(
        recentFailures = emptyList(),
        events = events?.copy(
            items = events.items.map { event ->
                event.copy(
                    requestId = "",
                    path = "",
                    authIndex = "",
                    source = "",
                    accountSnapshot = "",
                    authLabelSnapshot = "",
                    authProviderSnapshot = "",
                    failSummary = "",
                )
            },
        ),
    )

    private companion object {
        const val CACHE_KIND = "monitoring.v1"
    }
}

