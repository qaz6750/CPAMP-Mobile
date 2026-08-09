package com.cpamp.mobile.data.remote

import com.cpamp.mobile.data.remote.model.CodexInspectionRunDetailDto
import com.cpamp.mobile.data.remote.model.CodexInspectionRunsResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexInspectionModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes completed inspection run list contract`() {
        val response = json.decodeFromString<CodexInspectionRunsResponseDto>(
            """
            {
              "items": [{
                "id": 42,
                "status": "completed",
                "finishedAtMs": 1785500000000,
                "updatedAtMs": 1785500001000,
                "unknownFutureField": true
              }]
            }
            """.trimIndent(),
        )

        assertEquals(42L, response.items.single().id)
        assertEquals("completed", response.items.single().status)
    }

    @Test
    fun `decodes standardized codex and xai quota windows`() {
        val detail = json.decodeFromString<CodexInspectionRunDetailDto>(
            """
            {
              "run": {"id": 42, "status": "completed", "finishedAtMs": 1785500000000},
              "results": [
                {
                  "fileName": "codex.json",
                  "displayAccount": "codex@example.com",
                  "provider": "codex",
                  "planType": "pro",
                  "quotaWindows": [{
                    "id": "five-hour",
                    "usedPercent": 20,
                    "resetLabel": "08/01 12:00",
                    "limitWindowSeconds": 18000
                  }]
                },
                {
                  "fileName": "xai.json",
                  "provider": "xai",
                  "quotaWindows": [{
                    "id": "monthly",
                    "usedPercent": 60,
                    "limitWindowSeconds": 2592000
                  }]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("codex", "xai"), detail.results.map { it.provider })
        assertEquals(20.0, detail.results.first().quotaWindows?.single()?.usedPercent ?: -1.0, 0.001)
    }

    @Test
    fun `accepts missing and null quota windows`() {
        val detail = json.decodeFromString<CodexInspectionRunDetailDto>(
            """
            {
              "run": {"id": 43, "status": "completed"},
              "results": [
                {"fileName": "missing.json", "provider": "codex"},
                {"fileName": "null.json", "provider": "codex", "quotaWindows": null}
              ]
            }
            """.trimIndent(),
        )

        assertNull(detail.results[0].quotaWindows)
        assertNull(detail.results[1].quotaWindows)
    }

    @Test
    fun `resolves camel and snake case account identity fields`() {
        val detail = json.decodeFromString<CodexInspectionRunDetailDto>(
            """
            {
              "results": [
                {
                  "fileName": "camel.json",
                  "displayAccount": "camel@example.com",
                  "authIndex": "camel-index",
                  "planType": "pro"
                },
                {
                  "file_name": "snake.json",
                  "display_account": "snake@example.com",
                  "auth_index": "snake-index",
                  "plan_type": "team"
                }
              ]
            }
            """.trimIndent(),
        )

        val camel = detail.results[0]
        assertEquals("camel.json", camel.resolvedFileName)
        assertEquals("camel@example.com", camel.resolvedDisplayAccount)
        assertEquals("camel-index", camel.resolvedAuthIndex)
        assertEquals("pro", camel.resolvedPlanType)

        val snake = detail.results[1]
        assertEquals("snake.json", snake.resolvedFileName)
        assertEquals("snake@example.com", snake.resolvedDisplayAccount)
        assertEquals("snake-index", snake.resolvedAuthIndex)
        assertEquals("team", snake.resolvedPlanType)
    }
}