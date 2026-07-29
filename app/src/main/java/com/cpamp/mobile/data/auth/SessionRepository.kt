package com.cpamp.mobile.data.auth

import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.cache.CacheDao
import com.cpamp.mobile.data.profile.ServerProfileStore
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.domain.model.AuthenticatedSession
import com.cpamp.mobile.domain.model.ServerProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SessionRepository @Inject constructor(
    private val profileStore: ServerProfileStore,
    private val connectionTester: ConnectionTester,
    private val apiClientFactory: SessionApiClientFactory,
    private val cacheDao: CacheDao,
) {
    private val mutableSession = MutableStateFlow<AuthenticatedSession?>(null)
    val session: StateFlow<AuthenticatedSession?> = mutableSession.asStateFlow()
    val profiles = profileStore.profiles

    suspend fun restore(): Result<AuthenticatedSession> = runSuspendCatching {
        val stored = profileStore.snapshot()
        val profile = stored.profiles.firstOrNull { it.id == stored.activeProfileId }
            ?: throw SessionException(SessionException.Reason.NoActiveProfile)
        connect(profile)
    }

    suspend fun login(
        name: String,
        rawAddress: String,
        adminKey: String,
        allowCleartext: Boolean,
    ): AuthenticatedSession {
        val baseUrl = ConnectionAddress.normalize(rawAddress)
        if (baseUrl.startsWith("http://") && !allowCleartext) {
            throw SessionException(SessionException.Reason.CleartextConfirmationRequired)
        }
        val trimmedKey = adminKey.trim()
        if (trimmedKey.isEmpty()) {
            throw SessionException(SessionException.Reason.MissingAdminKey)
        }
        val probe = connectionTester.test(baseUrl, trimmedKey)
        val stored = profileStore.snapshot()
        val existing = stored.profiles.firstOrNull { it.baseUrl.equals(baseUrl, ignoreCase = true) }
        val profile = ServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim().ifBlank { existing?.name ?: ConnectionAddress.defaultLabel(baseUrl) },
            baseUrl = baseUrl,
            lastConnectedAt = System.currentTimeMillis(),
            serverVersion = probe.serverVersion ?: existing?.serverVersion,
        )
        profileStore.upsert(profile, trimmedKey)
        return AuthenticatedSession(profile, trimmedKey, probe.service).also { mutableSession.value = it }
    }

    suspend fun switchTo(profileId: String): AuthenticatedSession {
        val profile = profileStore.snapshot().profiles.firstOrNull { it.id == profileId }
            ?: throw SessionException(SessionException.Reason.ProfileNotFound)
        return connect(profile)
    }

    suspend fun delete(profileId: String) {
        profileStore.delete(profileId)
        if (mutableSession.value?.profile?.id == profileId) {
            apiClientFactory.invalidate()
            mutableSession.value = null
        }
        cacheDao.deleteProfile(profileId)
    }

    fun disconnect() {
        apiClientFactory.invalidate()
        mutableSession.value = null
    }

    private suspend fun connect(profile: ServerProfile): AuthenticatedSession {
        disconnect()
        val adminKey = profileStore.secret(profile.id)?.takeIf(String::isNotBlank)
            ?: throw SessionException(SessionException.Reason.SavedKeyUnavailable)
        val probe = connectionTester.test(profile.baseUrl, adminKey)
        val refreshed = profile.copy(
            lastConnectedAt = System.currentTimeMillis(),
            serverVersion = probe.serverVersion ?: profile.serverVersion,
        )
        profileStore.upsert(refreshed, adminKey)
        profileStore.markActive(refreshed.id)
        return AuthenticatedSession(refreshed, adminKey, probe.service).also { mutableSession.value = it }
    }
}
