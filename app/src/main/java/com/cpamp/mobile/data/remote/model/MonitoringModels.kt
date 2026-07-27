package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MonitoringRequestDto(
    @SerialName("from_ms") val fromMs: Long,
    @SerialName("to_ms") val toMs: Long,
    @SerialName("now_ms") val nowMs: Long,
    @SerialName("time_zone") val timeZone: String,
    @SerialName("search_query") val searchQuery: String = "",
    val filters: MonitoringFiltersDto = MonitoringFiltersDto(),
    val include: MonitoringIncludeDto = MonitoringIncludeDto(),
)

@Serializable
data class MonitoringFiltersDto(
    val models: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    @SerialName("failed_only") val failedOnly: Boolean = false,
    @SerialName("include_failed") val includeFailed: Boolean? = null,
    @SerialName("min_latency_ms") val minLatencyMs: Long = 0,
)

@Serializable
data class MonitoringIncludeDto(
    val summary: Boolean = true,
    @SerialName("summary_profile") val summaryProfile: String = "compact",
    val timeline: Boolean = true,
    @SerialName("model_share") val modelShare: Boolean = true,
    @SerialName("model_stats") val modelStats: Boolean = true,
    @SerialName("recent_failures") val recentFailures: Int = 20,
    @SerialName("events_page") val eventsPage: EventsPageRequestDto = EventsPageRequestDto(),
    val granularity: String = "auto",
)

@Serializable
data class EventsPageRequestDto(
    val limit: Int = 100,
    @SerialName("before_ms") val beforeMs: Long? = null,
    @SerialName("before_id") val beforeId: Long? = null,
)

@Serializable
data class MonitoringResponseDto(
    @SerialName("generated_at_ms") val generatedAtMs: Long = 0,
    val summary: MonitoringSummaryDto? = null,
    val timeline: List<MonitoringTimelineDto> = emptyList(),
    @SerialName("model_share") val modelShare: List<ModelShareDto> = emptyList(),
    @SerialName("recent_failures") val recentFailures: List<RecentFailureDto> = emptyList(),
    val events: EventsResponseDto? = null,
)

@Serializable
data class MonitoringSummaryDto(
    @SerialName("total_calls") val totalCalls: Long = 0,
    @SerialName("success_calls") val successCalls: Long = 0,
    @SerialName("failure_calls") val failureCalls: Long = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("total_cost") val totalCost: Double = 0.0,
    @SerialName("average_latency_ms") val averageLatencyMs: Double? = null,
    @SerialName("p95_latency_ms") val p95LatencyMs: Double? = null,
)

@Serializable
data class MonitoringTimelineDto(
    @SerialName("bucket_ms") val bucketMs: Long = 0,
    val label: String = "",
    val calls: Long = 0,
    val success: Long = 0,
    val failure: Long = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
)

@Serializable
data class ModelShareDto(
    val model: String = "",
    val calls: Long = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val share: Double = 0.0,
)

@Serializable
data class EventsResponseDto(
    val items: List<RequestEventDto> = emptyList(),
    @SerialName("next_before_ms") val nextBeforeMs: Long = 0,
    @SerialName("next_before_id") val nextBeforeId: Long = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("total_count") val totalCount: Long = 0,
)

@Serializable
data class RequestEventDto(
    @SerialName("request_id") val requestId: String = "",
    @SerialName("event_hash") val eventHash: String = "",
    @SerialName("timestamp_ms") val timestampMs: Long = 0,
    val model: String = "",
    @SerialName("resolved_model") val resolvedModel: String = "",
    val endpoint: String = "",
    val method: String = "",
    val path: String = "",
    @SerialName("auth_index") val authIndex: String = "",
    val source: String = "",
    @SerialName("account_snapshot") val accountSnapshot: String = "",
    @SerialName("auth_label_snapshot") val authLabelSnapshot: String = "",
    @SerialName("auth_provider_snapshot") val authProviderSnapshot: String = "",
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cached_tokens") val cachedTokens: Long = 0,
    @SerialName("reasoning_tokens") val reasoningTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    val failed: Boolean = false,
    @SerialName("fail_status_code") val failStatusCode: Long? = null,
    @SerialName("fail_summary") val failSummary: String = "",
) {
    val stableId: String
        get() = eventHash.ifBlank { requestId.ifBlank { "$timestampMs:$model:$endpoint" } }
}

