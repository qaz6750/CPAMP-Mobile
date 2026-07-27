package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardSummaryDto(
    @SerialName("generated_at_ms") val generatedAtMs: Long = 0,
    val today: DashboardTodayDto = DashboardTodayDto(),
    @SerialName("rolling_30m") val rolling30m: RollingSummaryDto = RollingSummaryDto(),
    @SerialName("top_models_today") val topModelsToday: List<TopModelDto> = emptyList(),
    @SerialName("traffic_timeline") val trafficTimeline: List<TrafficPointDto> = emptyList(),
    @SerialName("recent_failures") val recentFailures: List<RecentFailureDto> = emptyList(),
)

@Serializable
data class DashboardTodayDto(
    @SerialName("total_calls") val totalCalls: Long = 0,
    @SerialName("success_calls") val successCalls: Long = 0,
    @SerialName("failure_calls") val failureCalls: Long = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cached_tokens") val cachedTokens: Long = 0,
    @SerialName("reasoning_tokens") val reasoningTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("total_cost") val totalCost: Double = 0.0,
    @SerialName("average_latency_ms") val averageLatencyMs: Double? = null,
)

@Serializable
data class RollingSummaryDto(
    val rpm: Double = 0.0,
    val tpm: Double = 0.0,
    @SerialName("total_calls") val totalCalls: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
)

@Serializable
data class TopModelDto(
    val model: String = "",
    val calls: Long = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
    @SerialName("success_rate") val successRate: Double = 0.0,
)

@Serializable
data class TrafficPointDto(
    @SerialName("bucket_ms") val bucketMs: Long = 0,
    val calls: Long = 0,
    val tokens: Long = 0,
    val success: Long = 0,
    val failure: Long = 0,
    @SerialName("failure_rate") val failureRate: Double = 0.0,
)

@Serializable
data class RecentFailureDto(
    @SerialName("timestamp_ms") val timestampMs: Long = 0,
    val model: String = "",
    val endpoint: String = "",
    @SerialName("auth_index") val authIndex: String = "",
    @SerialName("fail_status_code") val failStatusCode: Long? = null,
    @SerialName("fail_summary") val failSummary: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
)

