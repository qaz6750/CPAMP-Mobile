package com.cpamp.mobile.data.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessRepositoryTest {
    @Test
    fun `client key mask never returns the complete value`() {
        val secret = "sk-client-secret-123456"
        val masked = maskClientApiKey(secret)

        assertEquals("sk-c••••3456", masked)
        assertFalse(masked.contains(secret))
    }

    @Test
    fun `short values reveal no characters`() {
        assertEquals("••••••••", maskClientApiKey("short"))
    }
}