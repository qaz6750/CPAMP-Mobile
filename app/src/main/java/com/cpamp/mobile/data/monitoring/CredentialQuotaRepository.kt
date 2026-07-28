package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.remote.model.CodexQuotaWindowDto
import com.cpamp.mobile.data.remote.model.CodexUsageDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

data class CredentialQuota(
    val name: String,
    val account: String,
    val provider: String,
    val planType: String,
    val windows: List<CredentialQuotaWindow>,
    val error: Boolean = false,
)

data class CredentialQuotaWindow(
    val durationSeconds: Long,
    val usedPercent: Double?,
    val resetAtMs: Long?,
)

@Singleton
class CredentialQuotaRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    suspend fun load(session: AuthenticatedSession): List<CredentialQuota> = coroutineScope {
        val api = clientFactory.api(session)
        val files = remoteCall { api.authFiles() }.files
            .filter { it.providerName == CODEX_PROVIDER && !it.disabled && it.authIndex.isNotBlank() }

        files.map { file -> async { loadCodexQuota(file, api) } }.awaitAll()
    }

    private suspend fun loadCodexQuota(
        file: AuthFileDto,
        api: com.cpamp.mobile.data.remote.CPAMPApi,
    ): CredentialQuota = runSuspendCatching {
        val headers = buildMap {
            put("Authorization", "Bearer \$TOKEN\$")
            put("Content-Type", "application/json")
            file.accountId.trim().takeIf(String::isNotEmpty)?.let { put("Chatgpt-Account-Id", it) }
        }
        val response = remoteCall {
            api.apiCall(
                ApiCallRequestDto(
                    authIndex = file.authIndex,
                    method = "GET",
                    url = CODEX_USAGE_URL,
                    header = headers,
                ),
            )
        }
        check(response.statusCode in 200..299)
        val usage = response.body?.let { json.decodeFromJsonElement(CodexUsageDto.serializer(), it) }
            ?: response.bodyText.takeIf(String::isNotBlank)?.let { json.decodeFromString<CodexUsageDto>(it) }
            ?: error("Empty Codex quota response")
        CredentialQuota(
            name = file.name,
            account = usage.email.ifBlank { file.account },
            provider = CODEX_PROVIDER,
            planType = usage.planType,
            windows = listOfNotNull(
                usage.rateLimit?.primaryWindow?.toWindow(),
                usage.rateLimit?.secondaryWindow?.toWindow(),
            ).sortedBy(CredentialQuotaWindow::durationSeconds),
        )
    }.getOrElse {
        CredentialQuota(
            name = file.name,
            account = file.account,
            provider = CODEX_PROVIDER,
            planType = "",
            windows = emptyList(),
            error = true,
        )
    }

    private fun CodexQuotaWindowDto.toWindow(): CredentialQuotaWindow {
        val now = System.currentTimeMillis()
        val resetAtMs = resetAt?.let { if (it < EPOCH_MILLISECONDS_THRESHOLD) it * 1000 else it }
            ?: resetAfterSeconds?.let { now + it * 1000 }
        return CredentialQuotaWindow(
            durationSeconds = limitWindowSeconds ?: 0,
            usedPercent = usedPercent?.coerceIn(0.0, 100.0),
            resetAtMs = resetAtMs,
        )
    }

    private val AuthFileDto.providerName: String
        get() = provider.ifBlank { type }.trim().lowercase()

    private companion object {
        const val CODEX_PROVIDER = "codex"
        const val CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        const val EPOCH_MILLISECONDS_THRESHOLD = 100_000_000_000L
    }
}
