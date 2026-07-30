package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.remote.CPAMPApi
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.ApiCallResponseDto
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.remote.model.CodexQuotaWindowDto
import com.cpamp.mobile.data.remote.model.CodexUsageDto
import com.cpamp.mobile.data.remote.model.XaiBillingDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull

enum class CredentialAccountStatus { Active, Disabled }

enum class CredentialQuotaQueryState { Success, NotRequested, Unsupported, Failed }

enum class CredentialQuotaFailure { MissingAuthIndex, RateLimited, ProviderUnavailable, InvalidResponse, RequestFailed }

data class CredentialQuota(
    val name: String,
    val account: String,
    val provider: String,
    val accountStatus: CredentialAccountStatus,
    val planType: String,
    val windows: List<CredentialQuotaWindow>,
    val queryState: CredentialQuotaQueryState,
    val failure: CredentialQuotaFailure? = null,
    val stale: Boolean = false,
)

data class CredentialQuotaWindow(
    val durationSeconds: Long,
    val remainingPercent: Double?,
    val resetAtMs: Long?,
)

internal data class ParsedCredentialQuota(
    val account: String = "",
    val planType: String = "",
    val windows: List<CredentialQuotaWindow>,
)

internal enum class CredentialQuotaLoadAction { Codex, Xai, Disabled, Unsupported, Failed }

@Singleton
class CredentialQuotaRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    private val memoryCache = mutableMapOf<String, CredentialQuota>()

    suspend fun load(session: AuthenticatedSession): List<CredentialQuota> = coroutineScope {
        val api = clientFactory.api(session)
        val root = remoteCall { api.authFiles() }
        val files = parseAuthFiles(root) ?: error("Invalid authentication file list")
        val semaphore = Semaphore(3)
        files.map { file ->
            async {
                semaphore.withPermit {
                    val provider = file.normalizedProvider
                    val quota = when (file.quotaLoadAction) {
                        CredentialQuotaLoadAction.Disabled -> file.baseQuota(
                            provider = provider,
                            accountStatus = CredentialAccountStatus.Disabled,
                            queryState = CredentialQuotaQueryState.NotRequested,
                        )
                        CredentialQuotaLoadAction.Unsupported -> file.baseQuota(
                            provider = provider,
                            queryState = CredentialQuotaQueryState.Unsupported,
                        )
                        CredentialQuotaLoadAction.Failed -> failedQuota(
                            session.profile.id, file, provider, CredentialQuotaFailure.MissingAuthIndex,
                        )
                        CredentialQuotaLoadAction.Codex -> loadCodexQuota(session.profile.id, file, api)
                        CredentialQuotaLoadAction.Xai -> loadXaiQuota(session.profile.id, file, api)
                    }
                    if (quota.queryState == CredentialQuotaQueryState.Success) {
                        synchronized(memoryCache) { memoryCache[cacheKey(session.profile.id, file, provider)] = quota }
                    }
                    quota
                }
            }
        }.awaitAll()
    }

    private fun parseAuthFiles(root: JsonElement): List<AuthFileDto>? {
        val unwrapped = if (root is JsonPrimitive && root.isString) {
            runCatching { json.parseToJsonElement(root.content) }.getOrDefault(root)
        } else root
        val files = when (unwrapped) {
            is JsonArray -> unwrapped
            is JsonObject -> unwrapped["files"] as? JsonArray
            else -> null
        }
            ?: return null
        return files.mapIndexed { index, element ->
            runCatching { json.decodeFromJsonElement(AuthFileDto.serializer(), element) }.getOrNull()
                ?: (element as? JsonObject)?.toTolerantAuthFile(index)
                ?: AuthFileDto(name = "Credential ${index + 1}")
        }
    }

    private fun JsonObject.toTolerantAuthFile(index: Int) = AuthFileDto(
        name = this["name"].scalarText().orEmpty().ifBlank { "Credential ${index + 1}" },
        type = this["type"].scalarText().orEmpty(),
        provider = this["provider"].scalarText().orEmpty(),
        typo = this["typo"].scalarText().orEmpty(),
        authIndex = this["auth_index"],
        camelAuthIndex = this["authIndex"],
        hyphenAuthIndex = this["auth-index"],
        account = this["account"].scalarText().orEmpty(),
        email = this["email"].scalarText().orEmpty(),
        accountId = this["account_id"],
        camelAccountId = this["accountId"],
        hyphenAccountId = this["account-id"],
        chatgptAccountId = this["chatgpt_account_id"],
        camelChatgptAccountId = this["chatgptAccountId"],
        hyphenChatgptAccountId = this["chatgpt-account-id"],
        metadata = this["metadata"] as? JsonObject,
        attributes = this["attributes"] as? JsonObject,
        idToken = this["id_token"] ?: this["idToken"] ?: this["id-token"],
        disabled = this["disabled"],
        status = this["status"].scalarText().orEmpty(),
        state = this["state"].scalarText().orEmpty(),
    )

    private suspend fun loadCodexQuota(profileId: String, file: AuthFileDto, api: CPAMPApi): CredentialQuota = loadProviderQuota(
        profileId = profileId,
        file = file,
        provider = CODEX_PROVIDER,
        api = api,
        url = CODEX_USAGE_URL,
        headers = buildMap {
            put("Authorization", "Bearer \$TOKEN\$")
            put("Content-Type", "application/json")
            put("User-Agent", CODEX_USER_AGENT)
            file.resolvedAccountId.takeIf(String::isNotEmpty)?.let { put("Chatgpt-Account-Id", it) }
        },
    ) { response ->
        CredentialQuotaDataParser.parseCodex(json, response.body, response.resolvedBodyText)
    }

    private suspend fun loadXaiQuota(profileId: String, file: AuthFileDto, api: CPAMPApi): CredentialQuota = loadProviderQuota(
        profileId = profileId,
        file = file,
        provider = XAI_PROVIDER,
        api = api,
        url = XAI_BILLING_URL,
        headers = mapOf("Authorization" to "Bearer \$TOKEN\$"),
    ) { response ->
        CredentialQuotaDataParser.parseXai(json, response.body, response.resolvedBodyText)
    }

    private suspend fun loadProviderQuota(
        profileId: String,
        file: AuthFileDto,
        provider: String,
        api: CPAMPApi,
        url: String,
        headers: Map<String, String>,
        parse: (ApiCallResponseDto) -> ParsedCredentialQuota?,
    ): CredentialQuota {
        val request = ApiCallRequestDto(
            authIndex = file.resolvedAuthIndex,
            method = "GET",
            url = url,
            header = headers,
        )
        var lastFailure = CredentialQuotaFailure.RequestFailed
        repeat(2) { attempt ->
            val result = runSuspendCatching { remoteCall { api.apiCall(request) } }
            val error = result.exceptionOrNull()
            if (error != null) {
                lastFailure = when (error) {
                    is com.cpamp.mobile.data.remote.RemoteFailure.RateLimited -> CredentialQuotaFailure.RateLimited
                    is com.cpamp.mobile.data.remote.RemoteFailure.Server ->
                        if (error.statusCode in RETRYABLE_STATUS_CODES) CredentialQuotaFailure.ProviderUnavailable
                        else CredentialQuotaFailure.RequestFailed
                    else -> CredentialQuotaFailure.RequestFailed
                }
                if (attempt == 0 && error.isRetryableQuotaFailure()) {
                    delay(RETRY_DELAY_MS)
                    return@repeat
                }
                return failedQuota(profileId, file, provider, lastFailure)
            }
            val response = result.getOrThrow()
            val status = response.resolvedStatusCode
            if (status in 200..299) {
                val parsed = parse(response)
                    ?: return failedQuota(profileId, file, provider, CredentialQuotaFailure.InvalidResponse)
                return file.baseQuota(
                    provider = provider,
                    account = parsed.account.ifBlank { file.displayAccount },
                    planType = parsed.planType,
                    windows = parsed.windows.sortedWith(
                        compareBy { window -> window.durationSeconds.takeIf { it > 0 } ?: Long.MAX_VALUE },
                    ),
                    queryState = CredentialQuotaQueryState.Success,
                )
            }
            lastFailure = when (status) {
                429 -> CredentialQuotaFailure.RateLimited
                502, 503, 504 -> CredentialQuotaFailure.ProviderUnavailable
                else -> CredentialQuotaFailure.RequestFailed
            }
            if (attempt == 0 && status in RETRYABLE_STATUS_CODES) {
                delay(RETRY_DELAY_MS)
            } else {
                return failedQuota(profileId, file, provider, lastFailure)
            }
        }
        return failedQuota(profileId, file, provider, lastFailure)
    }

    private fun failedQuota(
        profileId: String,
        file: AuthFileDto,
        provider: String,
        failure: CredentialQuotaFailure,
    ): CredentialQuota {
        val cached = synchronized(memoryCache) { memoryCache[cacheKey(profileId, file, provider)] }
        return cached?.copy(
            queryState = CredentialQuotaQueryState.Failed,
            failure = failure,
            stale = true,
        ) ?: file.baseQuota(
            provider = provider,
            queryState = CredentialQuotaQueryState.Failed,
            failure = failure,
        )
    }

    private fun cacheKey(profileId: String, file: AuthFileDto, provider: String): String =
        "$profileId:$provider:${file.name}:${file.displayAccount}"

    private fun Throwable.isRetryableQuotaFailure(): Boolean = when (this) {
        is com.cpamp.mobile.data.remote.RemoteFailure.RateLimited -> true
        is com.cpamp.mobile.data.remote.RemoteFailure.Server -> statusCode in RETRYABLE_STATUS_CODES
        else -> false
    }

    private val AuthFileDto.displayAccount: String
        get() = account.ifBlank { email }.ifBlank { name }

    private fun AuthFileDto.baseQuota(
        provider: String,
        account: String = displayAccount,
        accountStatus: CredentialAccountStatus = CredentialAccountStatus.Active,
        planType: String = "",
        windows: List<CredentialQuotaWindow> = emptyList(),
        queryState: CredentialQuotaQueryState,
        failure: CredentialQuotaFailure? = null,
    ) = CredentialQuota(
        name = name,
        account = account,
        provider = provider,
        accountStatus = accountStatus,
        planType = planType,
        windows = windows,
        queryState = queryState,
        failure = failure,
    )

    private val AuthFileDto.resolvedAccountId: String
        get() {
            val direct = sequenceOf(
                chatgptAccountId,
                camelChatgptAccountId,
                hyphenChatgptAccountId,
                accountId,
                camelAccountId,
                hyphenAccountId,
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

    private val ApiCallResponseDto.resolvedStatusCode: Int
        get() = sequenceOf(statusCode, camelStatusCode, hyphenStatusCode, status)
            .mapNotNull { it.scalarText() }
            .mapNotNull(String::toIntOrNull)
            .firstOrNull { it != 0 }
            ?: 0

    private val ApiCallResponseDto.resolvedBodyText: String
        get() = sequenceOf(bodyText, camelBodyText, hyphenBodyText, body)
            .mapNotNull { it.scalarText() }
            .firstOrNull(String::isNotBlank)
            .orEmpty()

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
        const val XAI_PROVIDER = "xai"
        const val CODEX_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        const val CODEX_USER_AGENT = "codex_cli_rs/0.76.0 (Debian 13.0.0; x86_64) WindowsTerminal"
        const val XAI_BILLING_URL = "https://cli-chat-proxy.grok.com/v1/billing"
        const val RETRY_DELAY_MS = 500L
        val RETRYABLE_STATUS_CODES = setOf(429, 502, 503, 504)
    }
}

internal val AuthFileDto.normalizedProvider: String
    get() {
        val candidates = sequenceOf(provider, type, typo)
            .map { it.trim().lowercase().replace('_', '-').replace(' ', '-') }
            .filter(String::isNotBlank)
            .toList()
        return candidates.firstNotNullOfOrNull { candidate ->
            when (candidate) {
                "codex", "openai", "chatgpt", "openai-codex", "chatgpt-codex" -> "codex"
                "xai", "x-ai", "grok", "xai-grok" -> "xai"
                else -> null
            }
        } ?: candidates.firstOrNull() ?: "unknown"
    }

internal val AuthFileDto.isCredentialDisabled: Boolean
    get() {
        val lifecycle = status.ifBlank { state }.trim().lowercase()
        if (lifecycle == "disabled" || lifecycle == "inactive") return true
        return disabled.scalarText()?.lowercase() in setOf("true", "1", "yes", "on")
    }

internal val AuthFileDto.resolvedAuthIndex: String
    get() = sequenceOf(authIndex, camelAuthIndex, hyphenAuthIndex)
        .mapNotNull { it.scalarText() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

internal val AuthFileDto.quotaLoadAction: CredentialQuotaLoadAction
    get() = when {
        isCredentialDisabled -> CredentialQuotaLoadAction.Disabled
        resolvedAuthIndex.isBlank() -> CredentialQuotaLoadAction.Failed
        normalizedProvider !in setOf("codex", "xai") -> CredentialQuotaLoadAction.Unsupported
        normalizedProvider == "codex" -> CredentialQuotaLoadAction.Codex
        else -> CredentialQuotaLoadAction.Xai
    }

internal object CredentialQuotaDataParser {
    fun parseCodex(
        json: Json,
        body: JsonElement?,
        bodyText: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ParsedCredentialQuota? {
        val usage = decode(json, body, bodyText, CodexUsageDto.serializer()) ?: return null
        val rateLimit = usage.rateLimit ?: usage.camelRateLimit
        val codeReviewLimit = usage.codeReviewRateLimit ?: usage.camelCodeReviewRateLimit
        val additionalLimits = usage.additionalRateLimits.orEmpty()
            .ifEmpty { usage.camelAdditionalRateLimits.orEmpty() }
        val windows = buildList {
            addRateLimitWindows(rateLimit, nowMs)
            addRateLimitWindows(codeReviewLimit, nowMs)
            additionalLimits.forEach { item ->
                addRateLimitWindows(item.rateLimit ?: item.camelRateLimit, nowMs)
            }
        }
        return ParsedCredentialQuota(
            account = usage.email,
            planType = usage.planType.ifBlank { usage.camelPlanType },
            windows = windows,
        )
    }

    private fun MutableList<CredentialQuotaWindow>.addRateLimitWindows(
        rateLimit: com.cpamp.mobile.data.remote.model.CodexRateLimitDto?,
        nowMs: Long,
    ) {
        (rateLimit?.primaryWindow ?: rateLimit?.camelPrimaryWindow)?.toWindow(nowMs)?.let(::add)
        (rateLimit?.secondaryWindow ?: rateLimit?.camelSecondaryWindow)?.toWindow(nowMs)?.let(::add)
    }

    fun parseXai(
        json: Json,
        body: JsonElement?,
        bodyText: String,
    ): ParsedCredentialQuota? {
        val billing = decode(json, body, bodyText, XaiBillingDto.serializer())?.config ?: return null
        val monthlyLimit = firstCentNumber(billing.monthlyLimit, billing.snakeMonthlyLimit)
        val used = firstCentNumber(billing.used)
        val remainingPercent = if (monthlyLimit != null && monthlyLimit > 0 && used != null) {
            (100.0 - used / monthlyLimit * 100.0).coerceIn(0.0, 100.0)
        } else {
            null
        }
        val startAtMs = parseTimestampMs(billing.billingPeriodStart.ifBlank { billing.snakeBillingPeriodStart })
        val endAtMs = parseTimestampMs(billing.billingPeriodEnd.ifBlank { billing.snakeBillingPeriodEnd })
        val durationSeconds = if (startAtMs != null && endAtMs != null && endAtMs > startAtMs) {
            (endAtMs - startAtMs) / 1000
        } else {
            0
        }
        val hasBillingData = monthlyLimit != null || used != null || endAtMs != null ||
            firstCentNumber(billing.onDemandCap, billing.snakeOnDemandCap) != null
        if (!hasBillingData) return null
        return ParsedCredentialQuota(
            windows = listOf(
                CredentialQuotaWindow(
                    durationSeconds = durationSeconds,
                    remainingPercent = remainingPercent,
                    resetAtMs = endAtMs,
                ),
            ),
        )
    }

    private fun CodexQuotaWindowDto.toWindow(nowMs: Long): CredentialQuotaWindow {
        val resetAt = firstNumber(resetAt, camelResetAt)?.toLong()
        val resetAfter = firstNumber(resetAfterSeconds, camelResetAfterSeconds)?.toLong()
        val resolvedResetAtMs = resetAt?.let { if (it < EPOCH_MILLISECONDS_THRESHOLD) it * 1000 else it }
            ?: resetAfter?.let { nowMs + it * 1000 }
        val usedPercent = firstNumber(usedPercent, camelUsedPercent)?.coerceIn(0.0, 100.0)
        return CredentialQuotaWindow(
            durationSeconds = firstNumber(limitWindowSeconds, camelLimitWindowSeconds)?.toLong() ?: 0,
            remainingPercent = usedPercent?.let { 100.0 - it },
            resetAtMs = resolvedResetAtMs,
        )
    }

    private fun firstCentNumber(vararg values: JsonElement?): Double? = values.asSequence()
        .mapNotNull { value ->
            val resolved = (value as? JsonObject)?.get("val") ?: value
            firstNumber(resolved)
        }
        .firstOrNull()

    private fun parseTimestampMs(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { return if (it < EPOCH_MILLISECONDS_THRESHOLD) it * 1000 else it }
        return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
    }

    private fun <T> decode(
        json: Json,
        body: JsonElement?,
        bodyText: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? {
        return body?.unwrapBody(json)?.canonicalizeKeys()?.let {
            runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull()
        }
            ?: bodyText.decodeElement(json)?.unwrapBody(json)?.let {
                runCatching { json.decodeFromJsonElement(serializer, it.canonicalizeKeys()) }.getOrNull()
            }
    }

    private fun String.decodeElement(json: Json): JsonElement? = trim().takeIf(String::isNotEmpty)?.let { value ->
        runCatching { json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }
    }

    private fun JsonElement.unwrapBody(json: Json, depth: Int = 0): JsonElement {
        if (depth >= 4) return this
        return when (this) {
            is JsonObject -> sequenceOf("body", "body_text", "bodyText", "body-text")
                .mapNotNull { this[it] }
                .firstOrNull()
                ?.let { nested ->
                    if (nested is JsonPrimitive && nested.isString) {
                        nested.contentOrNull?.decodeElement(json)?.unwrapBody(json, depth + 1) ?: nested
                    } else nested.unwrapBody(json, depth + 1)
                } ?: this
            is JsonPrimitive -> contentOrNull?.decodeElement(json)?.takeUnless { it == this }
                ?.unwrapBody(json, depth + 1) ?: this
            else -> this
        }
    }

    private fun JsonElement.canonicalizeKeys(): JsonElement = when (this) {
        is JsonObject -> JsonObject(buildMap {
            this@canonicalizeKeys.forEach { (key, value) ->
                val normalizedValue = value.canonicalizeKeys()
                put(key, normalizedValue)
                if ('-' in key) putIfAbsent(key.replace('-', '_'), normalizedValue)
            }
        })
        is JsonArray -> JsonArray(map { it.canonicalizeKeys() })
        else -> this
    }

    private const val EPOCH_MILLISECONDS_THRESHOLD = 100_000_000_000L
}

private fun JsonElement?.scalarText(): String? = (this as? JsonPrimitive)
    ?.contentOrNull
    ?.trim()

private fun firstNumber(vararg values: JsonElement?): Double? = values.asSequence()
    .mapNotNull { it.scalarText() }
    .map { it.removeSuffix("%").trim() }
    .mapNotNull(String::toDoubleOrNull)
    .firstOrNull(Double::isFinite)
