package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ManagerInfoDto(
    val service: String = "",
    val mode: String = "",
    val configured: Boolean = false,
    @SerialName("adminReady") val adminReady: Boolean = false,
    @SerialName("setupRequired") val setupRequired: Boolean = false,
    @SerialName("dataKeyReady") val dataKeyReady: Boolean = false,
    @SerialName("hasHistoricalData") val hasHistoricalData: Boolean = false,
)

@Serializable
data class ManagerStatusDto(
    val service: String = "",
    val events: Long = 0,
    @SerialName("deadLetters") val deadLetters: Long = 0,
    val collector: CollectorStatusDto = CollectorStatusDto(),
)

@Serializable
data class CollectorStatusDto(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val mode: String = "",
    val error: String = "",
    @SerialName("lastEventAt") val lastEventAt: Long = 0,
)

@Serializable
data class AuthFilesDto(
    val files: List<AuthFileDto> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class AuthFileDto(
    val id: String? = null,
    val name: String,
    val type: String = "unknown",
    val provider: String = "",
    val size: Long? = null,
    @SerialName("authIndex") val authIndex: JsonElement? = null,
    val disabled: Boolean = false,
    val unavailable: Boolean = false,
    val status: String = "",
    @SerialName("statusMessage") val statusMessage: String = "",
    val modified: Long? = null,
)

@Serializable
data class AuthFileStatusPatchDto(
    val name: String,
    val disabled: Boolean,
)

@Serializable
data class AuthFileDeleteResultDto(
    val status: String = "",
    val deleted: Int = 0,
    val files: List<String> = emptyList(),
)

@Serializable
data class ApiKeysDto(
    @SerialName("api-keys") val apiKeys: List<String> = emptyList(),
)

@Serializable
data class ApiKeyPatchDto(
    val index: Int,
    val value: String,
)

@Serializable
data class LogsDto(
    val lines: List<String> = emptyList(),
    @SerialName("line-count") val lineCount: Int = lines.size,
    @SerialName("latest-timestamp") val latestTimestamp: Long = 0,
    @SerialName("latest-after") val latestAfter: Long? = null,
    @SerialName("next-cursor") val nextCursor: String? = null,
    @SerialName("cursor-reset") val cursorReset: Boolean = false,
)

@Serializable
data class QuotaCooldownsDto(
    val items: List<QuotaCooldownDto> = emptyList(),
)

@Serializable
data class QuotaCooldownDto(
    @SerialName("authFileName") val authFileName: String = "",
    @SerialName("authIndex") val authIndex: String = "",
    val provider: String = "",
    val owner: String = "",
    @SerialName("reasonCode") val reasonCode: String = "",
    @SerialName("windowKind") val windowKind: String = "",
    @SerialName("recoverAtMs") val recoverAtMs: Long = 0,
)

