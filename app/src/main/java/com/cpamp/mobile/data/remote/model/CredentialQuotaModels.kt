package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthFilesResponseDto(
    val files: List<AuthFileDto> = emptyList(),
)

@Serializable
data class AuthFileDto(
    val name: String = "",
    val type: String = "",
    val provider: String = "",
    val typo: String = "",
    @SerialName("auth_index") val authIndex: JsonElement? = null,
    @SerialName("authIndex") val camelAuthIndex: JsonElement? = null,
    @SerialName("auth-index") val hyphenAuthIndex: JsonElement? = null,
    val account: String = "",
    val email: String = "",
    @SerialName("account_id") val accountId: JsonElement? = null,
    @SerialName("accountId") val camelAccountId: JsonElement? = null,
    @SerialName("account-id") val hyphenAccountId: JsonElement? = null,
    @SerialName("chatgpt_account_id") val chatgptAccountId: JsonElement? = null,
    @SerialName("chatgptAccountId") val camelChatgptAccountId: JsonElement? = null,
    @SerialName("chatgpt-account-id") val hyphenChatgptAccountId: JsonElement? = null,
    val metadata: JsonObject? = null,
    val attributes: JsonObject? = null,
    @SerialName("id_token") val idToken: JsonElement? = null,
    val disabled: JsonElement? = null,
    val status: String = "",
    val state: String = "",
)

@Serializable
data class ApiCallRequestDto(
    val authIndex: String,
    val method: String,
    val url: String,
    val header: Map<String, String>,
)

@Serializable
data class ApiCallResponseDto(
    @SerialName("status_code") val statusCode: JsonElement? = null,
    @SerialName("statusCode") val camelStatusCode: JsonElement? = null,
    @SerialName("status-code") val hyphenStatusCode: JsonElement? = null,
    val status: JsonElement? = null,
    val body: JsonElement? = null,
    @SerialName("body_text") val bodyText: JsonElement? = null,
    @SerialName("bodyText") val camelBodyText: JsonElement? = null,
    @SerialName("body-text") val hyphenBodyText: JsonElement? = null,
)

@Serializable
data class CodexUsageDto(
    val email: String = "",
    @SerialName("plan_type") val planType: String = "",
    @SerialName("planType") val camelPlanType: String = "",
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto? = null,
    @SerialName("rateLimit") val camelRateLimit: CodexRateLimitDto? = null,
    @SerialName("code_review_rate_limit") val codeReviewRateLimit: CodexRateLimitDto? = null,
    @SerialName("codeReviewRateLimit") val camelCodeReviewRateLimit: CodexRateLimitDto? = null,
    @SerialName("additional_rate_limits") val additionalRateLimits: List<CodexAdditionalRateLimitDto> = emptyList(),
    @SerialName("additionalRateLimits") val camelAdditionalRateLimits: List<CodexAdditionalRateLimitDto> = emptyList(),
)

@Serializable
data class CodexAdditionalRateLimitDto(
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto? = null,
    @SerialName("rateLimit") val camelRateLimit: CodexRateLimitDto? = null,
)

@Serializable
data class CodexRateLimitDto(
    @SerialName("primary_window") val primaryWindow: CodexQuotaWindowDto? = null,
    @SerialName("primaryWindow") val camelPrimaryWindow: CodexQuotaWindowDto? = null,
    @SerialName("secondary_window") val secondaryWindow: CodexQuotaWindowDto? = null,
    @SerialName("secondaryWindow") val camelSecondaryWindow: CodexQuotaWindowDto? = null,
)

@Serializable
data class CodexQuotaWindowDto(
    @SerialName("used_percent") val usedPercent: JsonElement? = null,
    @SerialName("usedPercent") val camelUsedPercent: JsonElement? = null,
    @SerialName("limit_window_seconds") val limitWindowSeconds: JsonElement? = null,
    @SerialName("limitWindowSeconds") val camelLimitWindowSeconds: JsonElement? = null,
    @SerialName("reset_after_seconds") val resetAfterSeconds: JsonElement? = null,
    @SerialName("resetAfterSeconds") val camelResetAfterSeconds: JsonElement? = null,
    @SerialName("reset_at") val resetAt: JsonElement? = null,
    @SerialName("resetAt") val camelResetAt: JsonElement? = null,
)

@Serializable
data class XaiBillingDto(
    val config: XaiBillingConfigDto? = null,
)

@Serializable
data class XaiBillingConfigDto(
    val monthlyLimit: JsonElement? = null,
    @SerialName("monthly_limit") val snakeMonthlyLimit: JsonElement? = null,
    val used: JsonElement? = null,
    val onDemandCap: JsonElement? = null,
    @SerialName("on_demand_cap") val snakeOnDemandCap: JsonElement? = null,
    val billingPeriodStart: String = "",
    @SerialName("billing_period_start") val snakeBillingPeriodStart: String = "",
    val billingPeriodEnd: String = "",
    @SerialName("billing_period_end") val snakeBillingPeriodEnd: String = "",
)
