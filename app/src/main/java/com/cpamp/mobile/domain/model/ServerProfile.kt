package com.cpamp.mobile.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lastConnectedAt: Long,
    val serverVersion: String? = null,
) {
    val usesCleartext: Boolean
        get() = baseUrl.startsWith("http://", ignoreCase = true)
}

data class AuthenticatedSession(
    val profile: ServerProfile,
    val adminKey: String,
    val service: String,
)

@Serializable
data class StoredProfiles(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
)
