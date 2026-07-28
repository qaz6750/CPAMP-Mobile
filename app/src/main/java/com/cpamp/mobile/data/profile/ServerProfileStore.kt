package com.cpamp.mobile.data.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpamp.mobile.data.security.SecretStore
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.domain.model.StoredProfiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDataStore by preferencesDataStore(name = "server_profiles")

@Singleton
class ServerProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val secretStore: SecretStore,
) {
    val profiles: Flow<StoredProfiles> = context.profileDataStore.data.map { preferences ->
        preferences[PROFILES_KEY]
            ?.let { encoded -> runCatching { json.decodeFromString<StoredProfiles>(encoded) }.getOrNull() }
            ?: StoredProfiles()
    }

    suspend fun snapshot(): StoredProfiles = profiles.first()

    suspend fun upsert(profile: ServerProfile, adminKey: String) {
        val previousSecret = secretStore.get(profile.id)
        secretStore.put(profile.id, adminKey)
        try {
            context.profileDataStore.edit { preferences ->
                val current = preferences[PROFILES_KEY]
                    ?.let { json.decodeFromString<StoredProfiles>(it) }
                    ?: StoredProfiles()
                val nextProfiles = (current.profiles.filterNot { it.id == profile.id } + profile)
                    .sortedByDescending(ServerProfile::lastConnectedAt)
                preferences[PROFILES_KEY] = json.encodeToString(
                    StoredProfiles(nextProfiles, activeProfileId = profile.id),
                )
            }
        } catch (error: Throwable) {
            runCatching {
                if (previousSecret == null) {
                    secretStore.remove(profile.id)
                } else {
                    secretStore.put(profile.id, previousSecret)
                }
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    suspend fun markActive(profileId: String) {
        context.profileDataStore.edit { preferences ->
            val current = preferences[PROFILES_KEY]
                ?.let { json.decodeFromString<StoredProfiles>(it) }
                ?: StoredProfiles()
            if (current.profiles.any { it.id == profileId }) {
                preferences[PROFILES_KEY] = json.encodeToString(current.copy(activeProfileId = profileId))
            }
        }
    }

    suspend fun delete(profileId: String) {
        context.profileDataStore.edit { preferences ->
            val current = preferences[PROFILES_KEY]
                ?.let { json.decodeFromString<StoredProfiles>(it) }
                ?: StoredProfiles()
            val remaining = current.profiles.filterNot { it.id == profileId }
            preferences[PROFILES_KEY] = json.encodeToString(
                StoredProfiles(
                    profiles = remaining,
                    activeProfileId = current.activeProfileId
                        ?.takeUnless { it == profileId }
                        ?: remaining.firstOrNull()?.id,
                ),
            )
        }
        secretStore.remove(profileId)
    }

    fun secret(profileId: String): String? = secretStore.get(profileId)

    private companion object {
        val PROFILES_KEY = stringPreferencesKey("profiles_json_v1")
    }
}
