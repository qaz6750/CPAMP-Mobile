package com.cpamp.mobile.data.system

import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.LogsDto
import com.cpamp.mobile.data.remote.model.ManagerInfoDto
import com.cpamp.mobile.data.remote.model.ManagerStatusDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton

data class SystemSnapshot(
    val info: ManagerInfoDto,
    val status: ManagerStatusDto,
)

@Singleton
class SystemRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
) {
    suspend fun status(session: AuthenticatedSession): SystemSnapshot {
        val api = clientFactory.api(session)
        return SystemSnapshot(
            info = remoteCall { api.info() },
            status = remoteCall { api.status() },
        )
    }

    suspend fun logs(session: AuthenticatedSession, cursor: String? = null): LogsDto =
        remoteCall { clientFactory.api(session).logs(cursor = cursor, limit = LOG_PAGE_SIZE) }

    suspend fun clearLogs(session: AuthenticatedSession) {
        remoteCall { clientFactory.api(session).clearLogs() }
    }

    private companion object {
        const val LOG_PAGE_SIZE = 300
    }
}