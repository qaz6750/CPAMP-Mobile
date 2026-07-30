package com.cpamp.mobile.data.monitoring

import com.cpamp.mobile.data.remote.model.AuthFileDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialQuotaRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses codex response with null optional limits`() {
        val payload = json.parseToJsonElement(
            """
            {
              "plan_type": "pro",
              "rate_limit": {
                "primary_window": {
                  "used_percent": 12,
                  "limit_window_seconds": 18000
                }
              },
              "code_review_rate_limit": null,
              "additional_rate_limits": null
            }
            """.trimIndent(),
        )

        val parsed = requireNotNull(CredentialQuotaDataParser.parseCodex(json, payload, "", nowMs = 0))

        assertEquals("pro", parsed.planType)
        assertEquals(1, parsed.windows.size)
        assertEquals(88.0, parsed.windows.single().remainingPercent ?: -1.0, 0.001)
    }

    @Test
    fun `codex snake case response becomes remaining quota windows`() {
        val body = json.parseToJsonElement(
            """
            {
              "email": "codex@example.com",
              "plan_type": "pro",
              "rate_limit": {
                "primary_window": {
                  "used_percent": 50,
                  "limit_window_seconds": 18000,
                  "reset_after_seconds": 60
                },
                "secondary_window": {
                  "used_percent": "80%",
                  "limit_window_seconds": 604800,
                  "reset_at": 2000000000
                }
              }
            }
            """.trimIndent(),
        )

        val parsed = requireNotNull(CredentialQuotaDataParser.parseCodex(json, body, "", nowMs = 1_000L))
        assertEquals("codex@example.com", parsed.account)
        assertEquals("pro", parsed.planType)
        assertEquals(listOf(50.0, 20.0), parsed.windows.map(CredentialQuotaWindow::remainingPercent))
        assertEquals(61_000L, parsed.windows.first().resetAtMs)
    }

    @Test
    fun `codex camel case body text and empty windows are accepted`() {
        val parsed = requireNotNull(
            CredentialQuotaDataParser.parseCodex(
                json = json,
                body = null,
                bodyText = """{"planType":"plus","rateLimit":{}}""",
            ),
        )

        assertEquals("plus", parsed.planType)
        assertEquals(emptyList<CredentialQuotaWindow>(), parsed.windows)
    }

    @Test
    fun `codex code review and additional windows are retained`() {
        val parsed = requireNotNull(
            CredentialQuotaDataParser.parseCodex(
                json = json,
                body = json.parseToJsonElement(
                    """
                    {
                      "code_review_rate_limit": {
                        "primary_window": {"used_percent": 10, "limit_window_seconds": 3600}
                      },
                      "additionalRateLimits": [{
                        "rateLimit": {
                          "secondaryWindow": {"usedPercent": 30, "limitWindowSeconds": 7200}
                        }
                      }]
                    }
                    """.trimIndent(),
                ),
                bodyText = "",
            ),
        )

        assertEquals(listOf(3600L, 7200L), parsed.windows.map(CredentialQuotaWindow::durationSeconds))
        assertEquals(listOf(90.0, 70.0), parsed.windows.map(CredentialQuotaWindow::remainingPercent))
    }

    @Test
    fun `xai nested cent values become monthly remaining quota`() {
        val body = json.parseToJsonElement(
            """
            {
              "config": {
                "monthly_limit": {"val": "10000"},
                "used": {"val": 2500},
                "on_demand_cap": {"val": 5000},
                "billing_period_start": "2026-07-01T00:00:00Z",
                "billing_period_end": "2026-08-01T00:00:00Z"
              }
            }
            """.trimIndent(),
        )

        val window = requireNotNull(CredentialQuotaDataParser.parseXai(json, body, ""))
            .windows.single()
        assertEquals(75.0, window.remainingPercent ?: -1.0, 0.0)
        assertEquals(31L * 24 * 60 * 60, window.durationSeconds)
        assertNotNull(window.resetAtMs)
    }

    @Test
    fun `xai camel case numeric variants and unknown ratio are accepted`() {
        val parsed = requireNotNull(
            CredentialQuotaDataParser.parseXai(
                json = json,
                body = null,
                bodyText = """{"config":{"monthlyLimit":0,"used":5,"billingPeriodEnd":"2026-08-01T00:00:00Z"}}""",
            ),
        )

        assertNull(parsed.windows.single().remainingPercent)
    }

    @Test
    fun `xai empty billing config is rejected`() {
        assertNull(CredentialQuotaDataParser.parseXai(json, json.parseToJsonElement("""{"config":{}}"""), ""))
    }

    @Test
    fun `remaining quota preserves threshold boundary values`() {
        val remaining = listOf(50, 20, 0).map { expected ->
            val used = 100 - expected
            requireNotNull(
                CredentialQuotaDataParser.parseXai(
                    json,
                    json.parseToJsonElement("""{"config":{"monthlyLimit":100,"used":$used}}"""),
                    "",
                ),
            ).windows.single().remainingPercent
        }

        assertEquals(listOf(50.0, 20.0, 0.0), remaining)
    }

    @Test
    fun `provider classification distinguishes supported disabled failed and unknown files`() {
        assertEquals(
            CredentialQuotaLoadAction.Xai,
            AuthFileDto(provider = "x-ai", authIndex = JsonPrimitive(7)).quotaLoadAction,
        )
        assertEquals(
            CredentialQuotaLoadAction.Disabled,
            AuthFileDto(provider = "codex", status = "disabled").quotaLoadAction,
        )
        assertEquals(
            CredentialQuotaLoadAction.Failed,
            AuthFileDto(provider = "codex").quotaLoadAction,
        )
        assertEquals(
            CredentialQuotaLoadAction.Unsupported,
            AuthFileDto(provider = "future-provider", authIndex = JsonPrimitive(1)).quotaLoadAction,
        )
        assertEquals("xai", AuthFileDto(type = "grok").normalizedProvider)
        assertEquals("xai", AuthFileDto(typo = "x_ai").normalizedProvider)
        assertEquals("unknown", AuthFileDto().normalizedProvider)
    }
}
