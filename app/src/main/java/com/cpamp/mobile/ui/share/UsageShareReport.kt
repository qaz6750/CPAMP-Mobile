package com.cpamp.mobile.ui.share

import com.cpamp.mobile.data.remote.model.MonitoringResponseDto

data class UsageShareReport(
    val fromMs: Long,
    val toMs: Long,
    val actualDays: Int,
    val requests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val successRate: Double,
    val tokens: Long,
    val cost: Double,
    val generatedAtMs: Long,
    val timeline: List<UsageSharePoint>,
    val topModels: List<UsageShareModel>,
)

data class UsageSharePoint(
    val timestampMs: Long,
    val requests: Long,
    val tokens: Long,
)

data class UsageShareModel(
    val name: String,
    val requests: Long,
    val tokens: Long,
)

fun MonitoringResponseDto.toUsageShareReport(
    fromMs: Long,
    toMs: Long,
    actualDays: Int,
): UsageShareReport? {
    val summary = summary ?: return null
    return UsageShareReport(
        fromMs = fromMs,
        toMs = toMs,
        actualDays = actualDays,
        requests = summary.totalCalls,
        successfulRequests = summary.successCalls,
        failedRequests = summary.failureCalls,
        successRate = summary.successRate,
        tokens = summary.totalTokens,
        cost = summary.totalCost,
        generatedAtMs = generatedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
        timeline = timeline.filter { it.bucketMs in fromMs..toMs }.map { point ->
            UsageSharePoint(
                timestampMs = point.bucketMs,
                requests = point.calls,
                tokens = point.totalTokens.takeIf { it > 0 } ?: point.tokens,
            )
        },
        topModels = modelStats.sortedByDescending { it.calls }.take(5).map { model ->
            UsageShareModel(model.model, model.calls, model.totalTokens)
        },
    )
}
