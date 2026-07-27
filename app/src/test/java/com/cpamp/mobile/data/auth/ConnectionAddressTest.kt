package com.cpamp.mobile.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionAddressTest {
    @Test
    fun `address without scheme defaults to https`() {
        assertEquals("https://192.168.1.9:18317", ConnectionAddress.normalize("192.168.1.9:18317"))
    }

    @Test
    fun `management suffix and trailing slash are removed`() {
        assertEquals(
            "http://example.test:18317",
            ConnectionAddress.normalize("http://example.test:18317/v0/management/"),
        )
        assertEquals(
            "https://example.test",
            ConnectionAddress.normalize("https://example.test/management.html"),
        )
    }

    @Test
    fun `credentials queries fragments and arbitrary paths are rejected`() {
        listOf(
            "https://user@example.test",
            "https://example.test?token=x",
            "https://example.test/#secret",
            "https://example.test/admin",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { ConnectionAddress.normalize(value) }
        }
    }

    @Test
    fun `only http schemes are accepted`() {
        assertThrows(IllegalArgumentException::class.java) { ConnectionAddress.normalize("ftp://example.test") }
    }
}
