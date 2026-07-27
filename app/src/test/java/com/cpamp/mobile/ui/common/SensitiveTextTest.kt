package com.cpamp.mobile.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextTest {
    @Test
    fun `redacts bearer and known key shapes`() {
        val output = SensitiveText.redact("Bearer abc.def.ghi api_key=sk-secretvalue123456")
        assertFalse(output.contains("abc.def.ghi"))
        assertFalse(output.contains("secretvalue"))
        assertTrue(output.contains("••••"))
    }

    @Test
    fun `bounds returned error text`() {
        assertTrue(SensitiveText.redact("x".repeat(500)).length <= 320)
    }
}
