package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
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
    @SerialName("finished_at_ms") val snakeFinishedAtMs: Long? = null,
    val updatedAtMs: Long = 0,
    @SerialName("updated_at_ms") val snakeUpdatedAtMs: Long = 0,
) {
    val resolvedFinishedAtMs: Long get() = finishedAtMs ?: snakeFinishedAtMs ?: 0
    val resolvedUpdatedAtMs: Long get() = updatedAtMs.takeIf { it > 0 } ?: snakeUpdatedAtMs
}

@Serializable
data class CodexInspectionRunDetailDto(
    val run: CodexInspectionRunDto = CodexInspectionRunDto(),
    val results: List<CodexInspectionResultDto> = emptyList(),
)

@Serializable
data class CodexInspectionResultDto(
    val fileName: String = "",
    @SerialName("file_name") val snakeFileName: String = "",
    val displayAccount: String = "",
    @SerialName("display_account") val snakeDisplayAccount: String = "",
    val accountSnapshot: String = "",
    @SerialName("account_snapshot") val snakeAccountSnapshot: String = "",
    val authIndex: String = "",
    @SerialName("auth_index") val snakeAuthIndex: String = "",
    val provider: String = "",
    val disabled: Boolean = false,
    val planType: String? = null,
    @SerialName("plan_type") val snakePlanType: String? = null,
    val quotaWindows: List<CodexInspectionQuotaWindowDto>? = null,
    @SerialName("quota_windows") val snakeQuotaWindows: List<CodexInspectionQuotaWindowDto>? = null,
    val error: String = "",
    val errorKind: String = "",
    @SerialName("error_kind") val snakeErrorKind: String = "",
) {
    val resolvedFileName: String get() = fileName.ifBlank { snakeFileName }
    val resolvedDisplayAccount: String
        get() = displayAccount.ifBlank { snakeDisplayAccount }
            .ifBlank { accountSnapshot }
            .ifBlank { snakeAccountSnapshot }
    val resolvedAuthIndex: String get() = authIndex.ifBlank { snakeAuthIndex }
    val resolvedProvider: String get() = provider.trim().lowercase()
    val resolvedPlanType: String get() = planType.orEmpty().ifBlank { snakePlanType.orEmpty() }
    val resolvedQuotaWindows: List<CodexInspectionQuotaWindowDto>?
        get() = quotaWindows ?: snakeQuotaWindows
    val resolvedErrorKind: String get() = errorKind.ifBlank { snakeErrorKind }
}

@Serializable
data class CodexInspectionQuotaWindowDto(
    val usedPercent: Double? = null,
    @SerialName("used_percent") val snakeUsedPercent: Double? = null,
    val resetLabel: String = "",
    @SerialName("reset_label") val snakeResetLabel: String = "",
    val limitWindowSeconds: Double? = null,
    @SerialName("limit_window_seconds") val snakeLimitWindowSeconds: Double? = null,
    val label: String = "",
) {
    val resolvedUsedPercent: Double? get() = usedPercent ?: snakeUsedPercent
    val resolvedResetLabel: String get() = resetLabel.ifBlank { snakeResetLabel }
    val resolvedLimitWindowSeconds: Double?
        get() = limitWindowSeconds ?: snakeLimitWindowSeconds
    val resolvedLabel: String get() = label
}
