package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.accounts.AccountHealth
import com.cpamp.mobile.data.accounts.AccountHealthFailure
import com.cpamp.mobile.data.accounts.AccountHealthSource
import com.cpamp.mobile.data.accounts.AccountQuotaState
import com.cpamp.mobile.data.accounts.AccountQuotaWindow
import com.cpamp.mobile.data.accounts.AccountStatus
import com.cpamp.mobile.data.remote.CPAMPApi
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.ApiCallResponseDto
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.remote.remoteCall
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val ACCOUNT_CONCURRENCY = 4
private const val DEFAULT_ANTIGRAVITY_PROJECT = "bamboo-precept-lgxtn"

private val supportedProviders = setOf("antigravity", "claude", "codex", "kimi", "xai")

internal suspend fun CPAMPApi.loadDirectCredentialQuotas(
    json: Json,
    fetchedAtMs: Long,
    targetFiles: List<AuthFileDto>? = null,
): List<AccountHealth> {
    val files = (targetFiles ?: remoteCall { authFiles() }.files)
        .filter { it.resolvedProvider in supportedProviders }
    val semaphore = Semaphore(ACCOUNT_CONCURRENCY)
    return coroutineScope {
        files.map { file ->
            async {
                if (file.disabled) {
                    file.baseQuota(
                        windows = emptyList(),
                        quotaState = AccountQuotaState.NotRequested,
                    )
                } else {
                    semaphore.withPermit {
                        try {
                            file.loadQuota(this@loadDirectCredentialQuotas, json, fetchedAtMs)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            if (error is RemoteFailure.Unauthorized) throw error
                            file.baseQuota(
                                windows = emptyList(),
                                quotaState = AccountQuotaState.Failed,
                                failure = AccountHealthFailure.ProviderRequest,
                            )
                        }
                    }
                }
            }
        }.awaitAll()
    }
}

private suspend fun AuthFileDto.loadQuota(
    api: CPAMPApi,
    json: Json,
    fetchedAtMs: Long,
): AccountHealth = when (resolvedProvider) {
    "codex" -> loadCodexQuota(api, json, fetchedAtMs)
    "claude" -> loadClaudeQuota(api, json, fetchedAtMs)
    "kimi" -> loadKimiQuota(api, json)
    "xai" -> loadXaiQuota(api, json)
    "antigravity" -> loadAntigravityQuota(api, json)
    else -> error("Unsupported quota provider")
}

private suspend fun AuthFileDto.loadCodexQuota(
    api: CPAMPApi,
    json: Json,
    fetchedAtMs: Long,
): AccountHealth {
    val headers = linkedMapOf(
        "Authorization" to "Bearer \$TOKEN\$",
        "Content-Type" to "application/json",
        "User-Agent" to "codex_cli_rs/0.76.0 (Debian 13.0.0; x86_64) WindowsTerminal",
    )
    resolveCodexAccountId(json)?.let { headers["Chatgpt-Account-Id"] = it }
    val payload = api.requestQuota(
        json = json,
        authIndex = requiredAuthIndex,
        method = "GET",
        url = "https://chatgpt.com/backend-api/wham/usage",
        headers = headers,
    ).asObject() ?: error("Invalid Codex quota response")
    val plan = payload.value("plan_type", "planType").asString().orEmpty().ifBlank { resolvedPlanType }
    val windows = buildList {
        payload.value("rate_limit", "rateLimit").asObject()?.let { rateLimit ->
            addAll(rateLimit.codexWindows(fetchedAtMs, ""))
        }
        payload.value("code_review_rate_limit", "codeReviewRateLimit").asObject()?.let { rateLimit ->
            addAll(rateLimit.codexWindows(fetchedAtMs, "Code review"))
        }
    }
    return baseQuota(windows = windows, planType = plan)
}

private fun JsonObject.codexWindows(fetchedAtMs: Long, label: String): List<AccountQuotaWindow> {
    val reached = value("limit_reached", "limitReached").asBoolean() == true
    return listOfNotNull(
        value("primary_window", "primaryWindow").asObject()
            ?.toUsedPercentWindow(fetchedAtMs, label, reached),
        value("secondary_window", "secondaryWindow").asObject()
            ?.toUsedPercentWindow(fetchedAtMs, label, reached),
    )
}

private fun JsonObject.toUsedPercentWindow(
    fetchedAtMs: Long,
    label: String = "",
    reached: Boolean = false,
): AccountQuotaWindow? {
    val usedPercent = value("used_percent", "usedPercent").asDouble() ?: 100.0.takeIf { reached }
    val duration = value("limit_window_seconds", "limitWindowSeconds").asLong()?.coerceAtLeast(0) ?: 0
    val resetAtMs = resolveResetAtMs(this, fetchedAtMs)
    if (usedPercent == null && duration == 0L && resetAtMs == null) return null
    return AccountQuotaWindow(
        durationSeconds = duration,
        remainingPercent = usedPercent?.let(::remainingFromUsed),
        resetAtMs = resetAtMs,
        label = label,
    )
}

private suspend fun AuthFileDto.loadClaudeQuota(
    api: CPAMPApi,
    json: Json,
    fetchedAtMs: Long,
): AccountHealth = coroutineScope {
    val headers = mapOf(
        "Authorization" to "Bearer \$TOKEN\$",
        "Content-Type" to "application/json",
        "anthropic-beta" to "oauth-2025-04-20",
    )
    val usageRequest = async {
        api.requestQuota(
            json,
            requiredAuthIndex,
            "GET",
            "https://api.anthropic.com/api/oauth/usage",
            headers,
        )
    }
    val profileRequest = async {
        try {
            api.requestQuota(
                json,
                requiredAuthIndex,
                "GET",
                "https://api.anthropic.com/api/oauth/profile",
                headers,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is RemoteFailure.Unauthorized) throw error
            null
        }
    }
    val usage = usageRequest.await().asObject() ?: error("Invalid Claude quota response")
    val profile = profileRequest.await().asObject()
    baseQuota(
        windows = usage.claudeWindows(fetchedAtMs),
        planType = profile.claudePlanType().ifBlank { resolvedPlanType },
    )
}

private fun JsonObject.claudeWindows(fetchedAtMs: Long): List<AccountQuotaWindow> {
    val windows = mutableListOf<AccountQuotaWindow>()
    val topLevelWindows = listOf(
        Triple("five_hour", 18_000L, ""),
        Triple("seven_day", 604_800L, ""),
        Triple("seven_day_oauth_apps", 604_800L, "OAuth apps"),
        Triple("seven_day_opus", 604_800L, "Opus"),
        Triple("seven_day_sonnet", 604_800L, "Sonnet"),
        Triple("seven_day_cowork", 604_800L, "Cowork"),
        Triple("iguana_necktie", 0L, "Iguana Necktie"),
    )
    topLevelWindows.forEach { (key, duration, label) ->
        value(key).asObject()?.let { raw ->
            val used = raw.value("utilization", "percent").asDouble()
            val reset = raw.value("resets_at", "resetsAt", "reset_at", "resetAt").asString()
            if (used != null || !reset.isNullOrBlank()) {
                windows += AccountQuotaWindow(
                    durationSeconds = duration,
                    remainingPercent = used?.let(::remainingFromUsed),
                    resetAtMs = reset.toEpochMs(),
                    resetLabel = reset.orEmpty().takeIf { reset.toEpochMs() == null }.orEmpty(),
                    label = label,
                )
            }
        }
    }
    val hasFiveHour = windows.any { it.durationSeconds == 18_000L && it.label.isBlank() }
    val hasSevenDay = windows.any { it.durationSeconds == 604_800L && it.label.isBlank() }
    value("limits").asArray().orEmpty().forEach { element ->
        val limit = element.asObject() ?: return@forEach
        val kind = limit.value("kind").asString()?.lowercase().orEmpty().replace('-', '_')
        val group = limit.value("group").asString()?.lowercase().orEmpty().replace('-', '_')
        val scopedLabel = limit.claudeScopedModelLabel()
        val duration = when {
            kind == "session" && group in setOf("", "session") -> 18_000L
            kind in setOf("weekly", "weekly_all") && group in setOf("", "weekly", "weekly_all") -> 604_800L
            kind in setOf("weekly_scoped", "model_scoped") || scopedLabel != null -> 604_800L
            else -> return@forEach
        }
        if (scopedLabel == null && duration == 18_000L && hasFiveHour) return@forEach
        if (scopedLabel == null && duration == 604_800L && hasSevenDay) return@forEach
        val used = limit.value("percent").asDouble()
        val reset = limit.value("resets_at", "resetsAt", "reset_at", "resetAt").asString()
        if (used != null || !reset.isNullOrBlank()) {
            windows += AccountQuotaWindow(
                durationSeconds = duration,
                remainingPercent = used?.let(::remainingFromUsed),
                resetAtMs = reset.toEpochMs(),
                resetLabel = reset.orEmpty().takeIf { reset.toEpochMs() == null }.orEmpty(),
                label = scopedLabel.orEmpty(),
            )
        }
    }
    return windows.distinctBy { listOf(it.durationSeconds, it.label, it.resetAtMs, it.remainingPercent) }
}

private fun JsonObject.claudeScopedModelLabel(): String? {
    val model = value("scope").asObject()?.value("model").asObject() ?: return null
    return model.value("display_name", "displayName").asString()
        ?: model.value("details").asObject()?.value("display_name", "displayName").asString()
}

private fun JsonObject?.claudePlanType(): String {
    this ?: return ""
    val account = value("account").asObject()
    if (account?.value("has_claude_max").asBoolean() == true) return "Max"
    if (account?.value("has_claude_pro").asBoolean() == true) return "Pro"
    val organization = value("organization").asObject()
    if (
        organization?.value("organization_type").asString().equals("claude_team", true) &&
        organization?.value("subscription_status").asString().equals("active", true)
    ) return "Team"
    return ""
}

private suspend fun AuthFileDto.loadKimiQuota(
    api: CPAMPApi,
    json: Json,
): AccountHealth {
    val payload = api.requestQuota(
        json,
        requiredAuthIndex,
        "GET",
        "https://api.kimi.com/coding/v1/usages",
        mapOf("Authorization" to "Bearer \$TOKEN\$"),
    )
    val records = payload.collectKimiQuotaRecords()
    val windows = records.mapNotNull { record ->
        val used = record.value("used", "usage", "current", "used_amount").asDouble()
        val limit = record.value("total", "limit", "quota", "max").asDouble()
        val remaining = record.value("remaining", "remaining_amount").asDouble()
        if (used == null && remaining == null && limit == null) return@mapNotNull null
        val reset = record.value("reset_time", "resetTime", "reset_at", "resetAt").asString()
        val label = record.value("model", "name", "label", "window", "id").asString().orEmpty()
        AccountQuotaWindow(
            durationSeconds = durationFromText(label),
            remainingPercent = when {
                remaining != null && limit != null && limit > 0 ->
                    (remaining * 100.0 / limit).coerceIn(0.0, 100.0)
                used != null && limit != null && limit > 0 ->
                    (100.0 - used * 100.0 / limit).coerceIn(0.0, 100.0)
                else -> null
            },
            resetAtMs = reset.toEpochMs(),
            resetLabel = reset.orEmpty().takeIf { reset.toEpochMs() == null }.orEmpty(),
            label = label,
        )
    }
    if (windows.isEmpty()) error("Empty Kimi quota response")
    return baseQuota(windows = windows)
}

private suspend fun AuthFileDto.loadXaiQuota(
    api: CPAMPApi,
    json: Json,
): AccountHealth = coroutineScope {
    val headers = linkedMapOf(
        "Authorization" to "Bearer \$TOKEN\$",
        "x-xai-token-auth" to "xai-grok-cli",
        "x-grok-client-version" to "0.2.101",
        "accept" to "*/*",
        "user-agent" to "grok-pager/0.2.101 grok-shell/0.2.101 (macos; aarch64)",
    )
    resolvedXaiUserId?.let { headers["x-userid"] = it }
    val weeklyRequest = async {
        try {
            api.requestQuota(
                json,
                requiredAuthIndex,
                "GET",
                "https://cli-chat-proxy.grok.com/v1/billing?format=credits",
                headers,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is RemoteFailure.Unauthorized) throw error
            null
        }
    }
    val monthlyRequest = async {
        try {
            api.requestQuota(
                json,
                requiredAuthIndex,
                "GET",
                "https://cli-chat-proxy.grok.com/v1/billing",
                headers,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is RemoteFailure.Unauthorized) throw error
            null
        }
    }
    val weekly = weeklyRequest.await()?.asObject()?.value("config").asObject()
    val monthly = monthlyRequest.await()?.asObject()?.value("config").asObject()
    if (weekly == null && monthly == null) {
        api.requestQuota(
            json,
            requiredAuthIndex,
            "GET",
            "https://api.x.ai/v1/me",
            mapOf("Authorization" to "Bearer \$TOKEN\$", "accept" to "application/json"),
        )
        return@coroutineScope baseQuota(windows = emptyList())
    }
    baseQuota(windows = buildXaiWindows(weekly, monthly))
}

private fun buildXaiWindows(weekly: JsonObject?, monthly: JsonObject?): List<AccountQuotaWindow> {
    val windows = mutableListOf<AccountQuotaWindow>()
    weekly?.let { config ->
        val period = config.value("currentPeriod", "current_period").asObject()
        val reset = period?.value("end").asString()
        config.value("creditUsagePercent", "credit_usage_percent").asDouble()?.let { used ->
            windows += AccountQuotaWindow(
                durationSeconds = 604_800,
                remainingPercent = remainingFromUsed(used),
                resetAtMs = reset.toEpochMs(),
                label = "Weekly limit",
            )
        }
        config.value("productUsage", "product_usage").asArray().orEmpty().forEachIndexed { index, item ->
            val product = item.asObject() ?: return@forEachIndexed
            val used = product.value("usagePercent", "usage_percent").asDouble() ?: return@forEachIndexed
            windows += AccountQuotaWindow(
                durationSeconds = 604_800,
                remainingPercent = remainingFromUsed(used),
                resetAtMs = reset.toEpochMs(),
                label = product.value("product").asString() ?: "Product ${index + 1}",
            )
        }
    }
    monthly?.let { config ->
        val monthlyLimit = config.value("monthlyLimit", "monthly_limit").asCentValue()
        val used = config.value("used").asCentValue()
        val reset = config.value("billingPeriodEnd", "billing_period_end").asString()
            ?: config.value("currentPeriod", "current_period").asObject()?.value("end").asString()
        if (monthlyLimit != null && monthlyLimit > 0 && used != null) {
            windows += AccountQuotaWindow(
                durationSeconds = 2_592_000,
                remainingPercent = (100.0 - minOf(used, monthlyLimit) * 100.0 / monthlyLimit).coerceIn(0.0, 100.0),
                resetAtMs = reset.toEpochMs(),
                label = "Monthly credits",
            )
        }
        val onDemandCap = config.value("onDemandCap", "on_demand_cap").asCentValue()
        val onDemandUsed = config.value("onDemandUsed", "on_demand_used").asCentValue()
            ?: if (used != null && monthlyLimit != null) (used - monthlyLimit).coerceAtLeast(0.0) else null
        if (onDemandCap != null && onDemandCap > 0 && onDemandUsed != null) {
            windows += AccountQuotaWindow(
                durationSeconds = 2_592_000,
                remainingPercent = (100.0 - onDemandUsed * 100.0 / onDemandCap).coerceIn(0.0, 100.0),
                resetAtMs = reset.toEpochMs(),
                label = "Pay-as-you-go",
            )
        }
    }
    return windows
}

private suspend fun AuthFileDto.loadAntigravityQuota(
    api: CPAMPApi,
    json: Json,
): AccountHealth {
    val headers = mapOf(
        "Authorization" to "Bearer \$TOKEN\$",
        "Content-Type" to "application/json",
        "User-Agent" to "antigravity/1.11.5 (CPAMP-Mobile; os_type=android; arch=arm64)",
    )
    val urls = listOf(
        "https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary",
        "https://daily-cloudcode-pa.sandbox.googleapis.com/v1internal:retrieveUserQuotaSummary",
        "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary",
        "https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
        "https://daily-cloudcode-pa.sandbox.googleapis.com/v1internal:fetchAvailableModels",
        "https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
    )
    var lastFailure: Exception? = null
    urls.forEach { url ->
        try {
            val payload = api.requestQuota(
                json,
                requiredAuthIndex,
                "POST",
                url,
                headers,
                JsonObject(mapOf("project" to JsonPrimitive(resolvedProjectId))).toString(),
            ).asObject() ?: return@forEach
            val windows = payload.antigravityWindows()
            if (windows.isNotEmpty()) return baseQuota(windows = windows)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is RemoteFailure.Unauthorized) throw error
            lastFailure = error
        }
    }
    throw lastFailure ?: IllegalStateException("Empty Antigravity quota response")
}

private fun JsonObject.antigravityWindows(): List<AccountQuotaWindow> {
    val grouped = value("groups").asArray().orEmpty().flatMap { groupElement ->
        val group = groupElement.asObject() ?: return@flatMap emptyList()
        val groupLabel = group.value("displayName", "display_name").asString().orEmpty()
        group.value("buckets").asArray().orEmpty().mapNotNull { bucketElement ->
            val bucket = bucketElement.asObject() ?: return@mapNotNull null
            val fraction = bucket.value("remainingFraction", "remaining_fraction").asDouble() ?: return@mapNotNull null
            val reset = bucket.value("resetTime", "reset_time").asString()
            val label = bucket.value("displayName", "display_name").asString().orEmpty()
                .ifBlank { groupLabel }
            AccountQuotaWindow(
                durationSeconds = durationFromText(bucket.value("window").asString().orEmpty()),
                remainingPercent = normalizeRemainingFraction(fraction),
                resetAtMs = reset.toEpochMs(),
                resetLabel = reset.orEmpty().takeIf { reset.toEpochMs() == null }.orEmpty(),
                label = label,
            )
        }
    }
    if (grouped.isNotEmpty()) return grouped
    return value("models").asObject().orEmpty().mapNotNull { (modelId, modelElement) ->
        val model = modelElement.asObject() ?: return@mapNotNull null
        val quota = model.value("quotaInfo", "quota_info").asObject() ?: return@mapNotNull null
        val fraction = quota.value("remainingFraction", "remaining_fraction", "remaining").asDouble()
            ?: return@mapNotNull null
        val reset = quota.value("resetTime", "reset_time").asString()
        AccountQuotaWindow(
            durationSeconds = 0,
            remainingPercent = normalizeRemainingFraction(fraction),
            resetAtMs = reset.toEpochMs(),
            resetLabel = reset.orEmpty().takeIf { reset.toEpochMs() == null }.orEmpty(),
            label = model.value("displayName", "display_name").asString() ?: modelId,
        )
    }
}

private suspend fun CPAMPApi.requestQuota(
    json: Json,
    authIndex: String,
    method: String,
    url: String,
    headers: Map<String, String>,
    data: String? = null,
): JsonElement {
    val response = remoteCall {
        apiCall(ApiCallRequestDto(authIndex, method, url, headers, data))
    }
    response.resolvedStatusCode?.let { statusCode ->
        if (statusCode !in 200..299) throw DirectQuotaRequestException(statusCode)
    }
    return response.normalizedBody(json) ?: error("Empty provider response")
}

private fun ApiCallResponseDto.normalizedBody(json: Json): JsonElement? {
    val primitive = body as? JsonPrimitive
    if (primitive?.isString != true) return body
    val content = primitive.content.trim()
    if (content.isEmpty()) return null
    return runCatching { json.parseToJsonElement(content) }.getOrNull()
}

private fun AuthFileDto.baseQuota(
    windows: List<AccountQuotaWindow>,
    quotaState: AccountQuotaState = AccountQuotaState.Available,
    failure: AccountHealthFailure? = null,
    planType: String = resolvedPlanType,
): AccountHealth = AccountHealth(
    stableId = stableAccountId,
    authIndex = resolvedAuthIndex,
    name = name,
    account = resolvedAccount,
    provider = resolvedProvider,
    status = if (disabled) AccountStatus.Disabled else AccountStatus.Active,
    planType = planType,
    windows = windows,
    quotaState = quotaState,
    failure = failure,
    source = AccountHealthSource.Direct,
)

internal val AuthFileDto.resolvedProvider: String
    get() {
        val candidates = sequenceOf(type, provider)
            .map(String::trim)
            .map(String::lowercase)
            .filter(String::isNotEmpty)
            .map { value ->
                when (value) {
                    "anthropic" -> "claude"
                    "grok" -> "xai"
                    "openai" -> "codex"
                    else -> value
                }
            }
            .toList()
        return candidates.firstOrNull { it in supportedProviders } ?: candidates.firstOrNull().orEmpty()
    }

internal val AuthFileDto.resolvedAuthIndex: String
    get() = authIndex.asString().orEmpty().ifBlank { snakeAuthIndex.asString().orEmpty() }

private val AuthFileDto.requiredAuthIndex: String
    get() = resolvedAuthIndex
        .ifBlank { error("Missing auth index") }

private val AuthFileDto.resolvedProjectId: String
    get() = projectId.ifBlank { snakeProjectId }.trim()
        .ifBlank { metadata.asObject()?.value("project_id", "projectId").asString().orEmpty() }
        .ifBlank { DEFAULT_ANTIGRAVITY_PROJECT }

internal val AuthFileDto.resolvedPlanType: String
    get() = planType.ifBlank { snakePlanType }.trim()
        .ifBlank { idToken.asObject()?.value("plan_type", "planType").asString().orEmpty() }

internal fun AuthFileDto.resolveCodexAccountId(json: Json): String? {
    sequenceOf(chatgptAccountId, camelChatgptAccountId, accountId, camelAccountId)
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.let { return it }

    val containers = listOfNotNull(
        metadata.asIdentityObject(json),
        attributes.asIdentityObject(json),
    )
    containers.forEach { container ->
        container.codexAccountId(json)?.let { return it }
    }

    val tokens = buildList {
        add(idToken)
        add(camelIdToken)
        containers.forEach { container ->
            add(container.value("id_token", "idToken"))
        }
    }
    tokens.forEach { token ->
        token.asIdentityObject(json)?.codexAccountId(json)?.let { return it }
    }
    return null
}

private fun JsonObject.codexAccountId(json: Json): String? =
    value("chatgpt_account_id", "chatgptAccountId", "account_id", "accountId").asString()
        ?: value("https://api.openai.com/auth")
            .asIdentityObject(json)
            ?.value("chatgpt_account_id", "chatgptAccountId", "account_id", "accountId")
            .asString()

private fun JsonElement?.asIdentityObject(json: Json): JsonObject? {
    asObject()?.let { return it }
    val content = asString() ?: return null
    runCatching { json.parseToJsonElement(content).asObject() }
        .getOrNull()
        ?.let { return it }
    val payload = content.split('.').getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        val paddedPayload = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = Base64.getUrlDecoder().decode(paddedPayload).toString(Charsets.UTF_8)
        json.parseToJsonElement(decoded).asObject()
    }.getOrNull()
}

internal val AuthFileDto.resolvedAccount: String
    get() = sequenceOf(
        account,
        email,
        accountSnapshot,
        metadata.asObject()?.value("account", "email").asString().orEmpty(),
        label,
        name,
    ).map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()

internal val AuthFileDto.supportsDirectQuota: Boolean
    get() = resolvedProvider in supportedProviders && resolvedAuthIndex.isNotBlank()

internal val AuthFileDto.stableAccountId: String
    get() = "$resolvedProvider\u0000${resolvedAuthIndex.ifBlank { name.trim() }}"

internal fun AuthFileDto.toBaseAccountHealth(
    quotaState: AccountQuotaState,
): AccountHealth = baseQuota(
    windows = emptyList(),
    quotaState = quotaState,
)

private val AuthFileDto.resolvedXaiUserId: String?
    get() {
        val containers = listOfNotNull(metadata.asObject(), attributes.asObject())
        containers.forEach { container ->
            container.xaiUserId()?.let { return it }
            container.value("oauth").asObject()?.xaiUserId()?.let { return it }
        }
        return null
    }

private fun JsonObject.xaiUserId(): String? =
    value("user_id", "userId").asString()
        ?: value("user").asObject()?.value("id", "user_id", "userId").asString()

private fun JsonElement.collectKimiQuotaRecords(): List<JsonObject> {
    val root = asObject() ?: return collectQuotaRecords()
    return buildList {
        root.value("usage").asObject()?.let(::add)
        root.value("limits").asArray().orEmpty().forEach { element ->
            val limit = element.asObject() ?: return@forEach
            val detail = limit.value("detail").asObject()
            add(if (detail == null) limit else JsonObject(limit + detail))
        }
    }.ifEmpty { collectQuotaRecords() }
}

private fun JsonElement.collectQuotaRecords(): List<JsonObject> {
    val root = asObject() ?: return (this as? JsonArray).orEmpty().mapNotNull(JsonElement::asObject)
    val directArrays = listOf("items", "usages", "limits", "details")
        .flatMap { key -> root.value(key).asArray().orEmpty().mapNotNull(JsonElement::asObject) }
    if (directArrays.isNotEmpty()) return directArrays
    root.values.forEach { child ->
        val nested = child.asObject() ?: return@forEach
        val records = listOf("items", "usages", "limits", "details")
            .flatMap { key -> nested.value(key).asArray().orEmpty().mapNotNull(JsonElement::asObject) }
        if (records.isNotEmpty()) return records
    }
    return listOf(root)
}

private fun resolveResetAtMs(window: JsonObject, fetchedAtMs: Long): Long? {
    val resetAt = window.value("reset_at", "resetAt").asLong()
    if (resetAt != null && resetAt > 0) return if (resetAt < 10_000_000_000L) resetAt * 1000 else resetAt
    val resetAfter = window.value("reset_after_seconds", "resetAfterSeconds").asLong()
    return resetAfter?.takeIf { it > 0 }?.let { fetchedAtMs + it * 1000 }
}

private fun String?.toEpochMs(): Long? = this?.trim()?.takeIf(String::isNotEmpty)?.let { value ->
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

private fun durationFromText(value: String): Long = when {
    value.contains("5h", true) || value.contains("5 hour", true) || value.contains("five hour", true) -> 18_000
    value.contains("daily", true) || value.contains("day", true) -> 86_400
    value.contains("weekly", true) || value.contains("week", true) -> 604_800
    value.contains("monthly", true) || value.contains("month", true) -> 2_592_000
    else -> 0
}

private fun remainingFromUsed(usedPercent: Double): Double = (100.0 - usedPercent).coerceIn(0.0, 100.0)

private fun normalizeRemainingFraction(value: Double): Double =
    (if (value <= 1.0) value * 100.0 else value).coerceIn(0.0, 100.0)

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)
    ?.contentOrNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !it.equals("null", true) }

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.let { primitive ->
    primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}

private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.let { primitive ->
    primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
}

private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.let { primitive ->
    primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonElement?.asCentValue(): Double? = asObject()?.value("val").asDouble() ?: asDouble()

private fun JsonObject.value(vararg keys: String): JsonElement? {
    keys.forEach { key -> get(key)?.let { return it } }
    return null
}

private class DirectQuotaRequestException(statusCode: Int) :
    IllegalStateException("Provider quota request failed: $statusCode")