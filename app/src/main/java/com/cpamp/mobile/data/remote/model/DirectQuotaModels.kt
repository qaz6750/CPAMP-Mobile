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
    val authIndex: JsonElement? = null,
    @SerialName("auth_index") val snakeAuthIndex: JsonElement? = null,
    val disabled: Boolean = false,
    val runtimeOnly: Boolean = false,
    @SerialName("runtime_only") val snakeRuntimeOnly: Boolean = false,
    val projectId: String = "",
    @SerialName("project_id") val snakeProjectId: String = "",
    val account: String = "",
    val email: String = "",
    val label: String = "",
    @SerialName("account_snapshot") val accountSnapshot: String = "",
    @SerialName("account_id") val accountId: String = "",
    val planType: String = "",
    @SerialName("plan_type") val snakePlanType: String = "",
    val metadata: JsonElement? = null,
    @SerialName("id_token") val idToken: JsonElement? = null,
)

@Serializable
data class ApiCallRequestDto(
    @SerialName("auth_index") val authIndex: String,
    val method: String,
    val url: String,
    val header: Map<String, String>,
    val data: String? = null,
)

@Serializable
data class ApiCallResponseDto(
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("statusCode") val camelStatusCode: Int? = null,
    val body: JsonElement? = null,
) {
    val resolvedStatusCode: Int? get() = statusCode ?: camelStatusCode
}