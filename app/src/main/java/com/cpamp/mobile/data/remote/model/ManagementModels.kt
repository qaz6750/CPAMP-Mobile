package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class LogsDto(
    val lines: List<String> = emptyList(),
    @SerialName("line-count") val lineCount: Int = lines.size,
    @SerialName("latest-timestamp") val latestTimestamp: Long = 0,
    @SerialName("latest-after") val latestAfter: Long? = null,
    @SerialName("next-cursor") val nextCursor: String? = null,
    @SerialName("cursor-reset") val cursorReset: Boolean = false,
)
