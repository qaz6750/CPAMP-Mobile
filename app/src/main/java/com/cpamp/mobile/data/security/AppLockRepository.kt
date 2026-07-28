package com.cpamp.mobile.data.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpamp.mobile.data.profile.ServerProfileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.securityDataStore by preferencesDataStore(name = "security_settings")

data class AppLockSettings(
    val enabled: Boolean = false,
    val timeoutMinutes: Int = 5,
)

@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileStore: ServerProfileStore,
    private val secretStore: SecretStore,
) {
    val settings: Flow<AppLockSettings> = context.securityDataStore.data.map { preferences ->
        AppLockSettings(
            enabled = preferences[LOCK_ENABLED] ?: false,
            timeoutMinutes = preferences[LOCK_TIMEOUT_MINUTES] ?: 5,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        val previousEnabled = settings.first().enabled
        if (previousEnabled == enabled) return

        val profileIds = profileStore.snapshot().profiles.map { it.id }
        try {
            secretStore.migrate(profileIds, requireAuthentication = enabled)
            context.securityDataStore.edit { preferences -> preferences[LOCK_ENABLED] = enabled }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching {
                    val persistedEnabled = settings.first().enabled
                    secretStore.migrate(profileIds, requireAuthentication = persistedEnabled)
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    suspend fun setTimeoutMinutes(minutes: Int) {
        require(minutes in SUPPORTED_TIMEOUTS) { "INVALID_LOCK_TIMEOUT" }
        context.securityDataStore.edit { preferences -> preferences[LOCK_TIMEOUT_MINUTES] = minutes }
    }

    suspend fun synchronizeRuntimeMode(enabled: Boolean) {
        secretStore.setAuthenticationRequired(enabled)
    }

    companion object {
        val SUPPORTED_TIMEOUTS = setOf(1, 5, 15, 60)
        private val LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
    }
}
