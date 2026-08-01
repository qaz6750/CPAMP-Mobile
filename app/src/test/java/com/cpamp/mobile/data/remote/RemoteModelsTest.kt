package com.cpamp.mobile.data.remote

import com.cpamp.mobile.data.remote.model.DashboardSummaryDto
import com.cpamp.mobile.data.remote.model.ApiCallRequestDto
import com.cpamp.mobile.data.remote.model.ApiCallResponseDto
import com.cpamp.mobile.data.remote.model.AuthFilesResponseDto
import com.cpamp.mobile.data.remote.model.MonitoringResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `dashboard model accepts upstream additions`() {
        val payload = """
            {
              "generated_at_ms": 10,
              "today": {
                "total_calls": 7,
                "success_calls": 6,
                "success_rate": 0.857,
                "total_tokens": 900,
                "future_field": "ignored"
              },
              "rolling_30m": {"rpm": 1.2},
              "top_models_today": [],
              "recent_failures": [],
              "future_root": true
            }
        """.trimIndent()

        val decoded = json.decodeFromString<DashboardSummaryDto>(payload)
        assertEquals(7, decoded.today.totalCalls)
        assertEquals(900, decoded.today.totalTokens)
    }

    @Test
    fun `monitoring event preserves failure and cursor fields`() {
        val payload = """
            {
              "generated_at_ms": 20,
              "events": {
                "items": [{
                  "event_hash": "evt-1",
                  "timestamp_ms": 19,
                  "model": "gpt-test",
                  "failed": true,
                  "fail_status_code": 429
                }],
                "next_before_ms": 19,
                "next_before_id": 3,
                "has_more": true,
                "total_count": 1
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<MonitoringResponseDto>(payload)
        assertEquals("evt-1", decoded.events?.items?.single()?.stableId)
        assertEquals(true, decoded.events?.hasMore)
    }

    @Test
    fun `monitoring timeline preserves health and token structure`() {
        val payload = """
            {
              "timeline": [{
                "bucket_ms": 100,
                "calls": 10,
                "success": 8,
                "failure": 2,
                "average_latency_ms": 1250.5,
                "input_tokens": 100,
                "output_tokens": 50,
                "cached_tokens": 25,
                "reasoning_tokens": 10
              }]
            }
        """.trimIndent()

        val point = json.decodeFromString<MonitoringResponseDto>(payload).timeline.single()
        assertEquals(1250.5, point.averageLatencyMs ?: 0.0, 0.0)
        assertEquals(100, point.inputTokens)
        assertEquals(50, point.outputTokens)
        assertEquals(25, point.cachedTokens)
        assertEquals(10, point.reasoningTokens)
    }

      @Test
      fun `quota api call serializes manager auth index field`() {
        val request = ApiCallRequestDto(
          authIndex = "12",
          method = "GET",
          url = "https://example.com/quota",
          header = emptyMap(),
        )

        val payload = json.encodeToJsonElement(ApiCallRequestDto.serializer(), request) as JsonObject
        assertEquals(JsonPrimitive("12"), payload["auth_index"])
        assertEquals(null, payload["authIndex"])
      }

      @Test
      fun `auth files accept numeric auth index`() {
        val response = json.decodeFromString<AuthFilesResponseDto>(
          """{"files":[{"name":"account.json","auth_index":12}]}""",
        )

        assertEquals(JsonPrimitive(12), response.files.single().snakeAuthIndex)
      }

      @Test
      fun `quota response may omit provider status code`() {
        val response = json.decodeFromString<ApiCallResponseDto>("""{"body":{"ok":true}}""")

        assertEquals(null, response.resolvedStatusCode)
      }
}
