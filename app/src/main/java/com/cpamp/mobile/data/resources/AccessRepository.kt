package com.cpamp.mobile.data.resources

import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.QuotaCooldownDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton

data class ClientApiKey(
    val index: Int,
    val maskedValue: String,
)

@Singleton
class AccessRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
) {
    suspend fun loadQuotas(session: AuthenticatedSession): List<QuotaCooldownDto> =
        remoteCall { clientFactory.api(session).quotaCooldowns().items }

    suspend fun loadApiKeys(session: AuthenticatedSession): List<ClientApiKey> =
        remoteCall { clientFactory.api(session).apiKeys().apiKeys }
            .mapIndexed { index, value -> ClientApiKey(index, maskClientApiKey(value)) }

    suspend fun addApiKey(session: AuthenticatedSession, value: String) {
        val normalized = value.trim()
        require(normalized.length in 8..4096) { "API_KEY_INVALID" }
        val api = clientFactory.api(session)
        val current = remoteCall { api.apiKeys().apiKeys }
        require(normalized !in current) { "API_KEY_DUPLICATE" }
        remoteCall { api.replaceApiKeys(current + normalized) }
    }

    suspend fun deleteApiKey(session: AuthenticatedSession, index: Int) {
        require(index >= 0) { "API_KEY_CHANGED" }
        remoteCall { clientFactory.api(session).deleteApiKey(index) }
    }

}

internal fun maskClientApiKey(value: String): String = when {
    value.isEmpty() -> "empty"
    value.length <= 8 -> "••••••••"
    else -> "${value.take(4)}••••${value.takeLast(4)}"
}