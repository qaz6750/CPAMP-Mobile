package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class CodexInspectionRunsResponseDto(
    val items: List<CodexInspectionRunDto> = emptyList(),
)

@Serializable
data class CodexInspectionRunDto(
    val id: Long = 0,
    val status: String = "",
    val finishedAtMs: Long? = null,
    val updatedAtMs: Long = 0,
)

@Serializable
data class CodexInspectionRunDetailDto(
    val run: CodexInspectionRunDto = CodexInspectionRunDto(),
    val results: List<CodexInspectionResultDto> = emptyList(),
)

@Serializable
data class CodexInspectionResultDto(
    val fileName: String = "",
    val displayAccount: String = "",
    val accountSnapshot: String = "",
    val provider: String = "",
    val disabled: Boolean = false,
    val planType: String? = null,
    val quotaWindows: List<CodexInspectionQuotaWindowDto> = emptyList(),
    val error: String = "",
    val errorKind: String = "",
)

@Serializable
data class CodexInspectionQuotaWindowDto(
    val usedPercent: Double? = null,
    val resetLabel: String = "",
    val limitWindowSeconds: Double? = null,
)
