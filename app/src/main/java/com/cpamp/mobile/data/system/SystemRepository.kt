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
    val cpaVersion: String? = null,
    val cpampVersion: String? = null,
)

@Singleton
class SystemRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
    private val versionObserver: ServerVersionObserver,
) {
    suspend fun status(session: AuthenticatedSession): SystemSnapshot {
        val api = clientFactory.api(session)
        val info = remoteCall { api.info() }
        val status = remoteCall { api.status() }
        val observed = versionObserver.snapshot(session.profile.id)
        return SystemSnapshot(
            info = info,
            status = status,
            cpaVersion = observed.cpaVersion
                ?: status.cpaVersion.ifBlank { status.snakeCpaVersion }.trim().takeIf(String::isNotEmpty),
            cpampVersion = observed.cpampVersion
                ?: sequenceOf(info.cpampVersion, info.managerVersion, info.version, info.appVersion)
                    .firstOrNull { it.isNotBlank() }?.trim(),
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
