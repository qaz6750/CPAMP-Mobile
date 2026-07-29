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
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
            .filter { it.providerName == CODEX_PROVIDER && !it.isDisabled }

        files.map { file ->
            async {
                if (file.resolvedAuthIndex.isBlank()) file.missingAuthIndexQuota() else loadCodexQuota(file, api)
            }
        }.awaitAll()
    }

    private suspend fun loadCodexQuota(
        file: AuthFileDto,
        api: com.cpamp.mobile.data.remote.CPAMPApi,
    ): CredentialQuota = runSuspendCatching {
        val accountId = file.resolvedAccountId
        val headers = buildMap {
            put("Authorization", "Bearer \$TOKEN\$")
            put("Content-Type", "application/json")
            put("User-Agent", CODEX_USER_AGENT)
            accountId.takeIf(String::isNotEmpty)?.let { put("Chatgpt-Account-Id", it) }
        }
        val response = remoteCall {
            api.apiCall(
                ApiCallRequestDto(
                    authIndex = file.resolvedAuthIndex,
                    method = "GET",
                    url = CODEX_USAGE_URL,
                    header = headers,
                ),
            )
        }
        check(response.resolvedStatusCode in 200..299)
        val usage = response.body?.decodeCodexUsage()
            ?: response.resolvedBodyText.decodeCodexUsage()
            ?: error("Empty Codex quota response")
        CredentialQuota(
            name = file.name,
            account = usage.email.ifBlank { file.account.ifBlank { file.email } },
            provider = CODEX_PROVIDER,
            planType = usage.planType.ifBlank { usage.camelPlanType },
            windows = listOfNotNull(
                usage.resolvedRateLimit?.resolvedPrimaryWindow?.toWindow(),
                usage.resolvedRateLimit?.resolvedSecondaryWindow?.toWindow(),
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
        val resetAt = firstNumber(resetAt, camelResetAt)?.toLong()
        val resetAfterSeconds = firstNumber(resetAfterSeconds, camelResetAfterSeconds)?.toLong()
        val resetAtMs = resetAt?.let { if (it < EPOCH_MILLISECONDS_THRESHOLD) it * 1000 else it }
            ?: resetAfterSeconds?.let { now + it * 1000 }
        return CredentialQuotaWindow(
            durationSeconds = firstNumber(limitWindowSeconds, camelLimitWindowSeconds)?.toLong() ?: 0,
            usedPercent = firstNumber(usedPercent, camelUsedPercent)?.coerceIn(0.0, 100.0),
            resetAtMs = resetAtMs,
        )
    }

    private val AuthFileDto.providerName: String
        get() = provider.ifBlank { type }.trim().lowercase()

    private val AuthFileDto.isDisabled: Boolean
        get() {
            val lifecycle = status.ifBlank { state }.trim().lowercase()
            if (lifecycle == "disabled" || lifecycle == "inactive") return true
            return when (disabled.scalarText()?.lowercase()) {
                "true", "1", "yes", "on" -> true
                else -> false
            }
        }

        private fun AuthFileDto.missingAuthIndexQuota(): CredentialQuota = CredentialQuota(
            name = name,
            account = account.ifBlank { email },
            provider = CODEX_PROVIDER,
            planType = "",
            windows = emptyList(),
            error = true,
        )

    private val AuthFileDto.resolvedAuthIndex: String
        get() = sequenceOf(authIndex, camelAuthIndex, hyphenAuthIndex)
            .mapNotNull { it.scalarText() }
            .firstOrNull(String::isNotBlank)
            .orEmpty()

    private val AuthFileDto.resolvedAccountId: String
        get() {
            val direct = sequenceOf(
                chatgptAccountId,
                camelChatgptAccountId,
                accountId,
                camelAccountId,
                metadata?.get("chatgpt_account_id"),
                metadata?.get("chatgptAccountId"),
                metadata?.get("account_id"),
                metadata?.get("accountId"),
                attributes?.get("chatgpt_account_id"),
                attributes?.get("chatgptAccountId"),
                attributes?.get("account_id"),
                attributes?.get("accountId"),
            ).mapNotNull { it.scalarText() }.firstOrNull(String::isNotBlank)
            if (!direct.isNullOrBlank()) return direct
            return sequenceOf(idToken, metadata?.get("id_token"), attributes?.get("id_token"))
                .mapNotNull(::decodeIdToken)
                .mapNotNull(::accountIdFromObject)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
        }

    private val com.cpamp.mobile.data.remote.model.ApiCallResponseDto.resolvedStatusCode: Int
        get() = sequenceOf(statusCode, camelStatusCode)
            .mapNotNull { it.scalarText() }
            .mapNotNull(String::toIntOrNull)
            .firstOrNull { it != 0 }
            ?: 0

    private val com.cpamp.mobile.data.remote.model.ApiCallResponseDto.resolvedBodyText: String
        get() = bodyText.ifBlank { camelBodyText }

    private val CodexUsageDto.resolvedRateLimit: com.cpamp.mobile.data.remote.model.CodexRateLimitDto?
        get() = rateLimit ?: camelRateLimit

    private val com.cpamp.mobile.data.remote.model.CodexRateLimitDto.resolvedPrimaryWindow: CodexQuotaWindowDto?
        get() = primaryWindow ?: camelPrimaryWindow

    private val com.cpamp.mobile.data.remote.model.CodexRateLimitDto.resolvedSecondaryWindow: CodexQuotaWindowDto?
        get() = secondaryWindow ?: camelSecondaryWindow

    private fun JsonElement.decodeCodexUsage(): CodexUsageDto? = when (this) {
        is JsonObject -> runCatching { json.decodeFromJsonElement(CodexUsageDto.serializer(), this) }.getOrNull()
        is JsonPrimitive -> contentOrNull?.decodeCodexUsage()
        else -> null
    }

    private fun String.decodeCodexUsage(): CodexUsageDto? = trim().takeIf(String::isNotEmpty)?.let { value ->
        runCatching { json.decodeFromString<CodexUsageDto>(value) }.getOrNull()
    }

    private fun JsonElement?.scalarText(): String? = (this as? JsonPrimitive)
        ?.takeIf { it.isString || it.contentOrNull != null }
        ?.contentOrNull
        ?.trim()

    private fun firstNumber(vararg values: JsonElement?): Double? = values.asSequence()
        .mapNotNull { it.scalarText() }
        .map { it.removeSuffix("%").trim() }
        .mapNotNull(String::toDoubleOrNull)
        .firstOrNull(Double::isFinite)

    private fun decodeIdToken(value: JsonElement?): JsonObject? {
        if (value is JsonObject) return value
        val text = value.scalarText()?.takeIf(String::isNotEmpty) ?: return null
        runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()?.let { return it }
        val payload = text.split('.').getOrNull(1) ?: return null
        return runCatching {
            val decoded = Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))
            json.parseToJsonElement(decoded.decodeToString()) as? JsonObject
        }.getOrNull()
    }

    private fun accountIdFromObject(value: JsonObject): String? = sequenceOf(
        value["chatgpt_account_id"],
        value["chatgptAccountId"],
        value["account_id"],
        value["accountId"],
    ).mapNotNull { it.scalarText() }.firstOrNull(String::isNotBlank)

    private companion object {
        const val CODEX_PROVIDER = "codex"
        const val CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        const val CODEX_USER_AGENT = "codex_cli_rs/0.76.0 (Debian 13.0.0; x86_64) WindowsTerminal"
        const val EPOCH_MILLISECONDS_THRESHOLD = 100_000_000_000L
    }
}
