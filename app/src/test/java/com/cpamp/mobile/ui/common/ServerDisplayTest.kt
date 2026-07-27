package com.cpamp.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDisplayTest {
    @Test
    fun `hides address shaped profile names`() {
        val fallback = "Overview"
        listOf(
            "https://manager.example.com:18317",
            "manager.example.com",
            "manager.example.com:18317",
            "192.168.1.20",
            "[2001:db8::1]:18317",
            "2001:db8::1",
            "localhost",
        ).forEach { name ->
            assertEquals(fallback, safeServerName(name, "https://manager.example.com:18317", true, fallback))
        }
    }

    @Test
    fun `keeps friendly aliases while privacy is enabled`() {
        assertEquals(
            "Home gateway",
            safeServerName("Home gateway", "https://manager.example.com:18317", true, "Overview"),
        )
    }

    @Test
    fun `shows configured value when privacy is disabled`() {
        assertEquals(
            "manager.example.com",
            safeServerName("manager.example.com", "https://manager.example.com:18317", false, "Overview"),
        )
    }
}