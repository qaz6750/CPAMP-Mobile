package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthFilesResponseDto(
    val files: List<AuthFileDto> = emptyList(),
)

@Serializable
data class AuthFileDto(
    val name: String = "",
    val type: String = "",
    val provider: String = "",
    @SerialName("auth_index") val authIndex: String = "",
    val account: String = "",
    @SerialName("account_id") val accountId: String = "",
    val disabled: Boolean = false,
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
    @SerialName("status_code") val statusCode: Int = 0,
    val body: JsonElement? = null,
    @SerialName("body_text") val bodyText: String = "",
)

@Serializable
data class CodexUsageDto(
    val email: String = "",
    @SerialName("plan_type") val planType: String = "",
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto? = null,
)

@Serializable
data class CodexRateLimitDto(
    @SerialName("primary_window") val primaryWindow: CodexQuotaWindowDto? = null,
    @SerialName("secondary_window") val secondaryWindow: CodexQuotaWindowDto? = null,
)

@Serializable
data class CodexQuotaWindowDto(
    @SerialName("used_percent") val usedPercent: Double? = null,
    @SerialName("limit_window_seconds") val limitWindowSeconds: Long? = null,
    @SerialName("reset_after_seconds") val resetAfterSeconds: Long? = null,
    @SerialName("reset_at") val resetAt: Long? = null,
)