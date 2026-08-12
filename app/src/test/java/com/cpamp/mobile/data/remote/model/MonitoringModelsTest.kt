package com.cpamp.mobile.data.remote.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringModelsTest {
    @Test
    fun `credential auth index accepts a number`() {
        val response = Json.decodeFromString<MonitoringResponseDto>(
            """{"credential_stats":[{"auth_index":42}]}""",
        )

        assertEquals("42", response.credentialStats.single().authIndex)
    }
}
