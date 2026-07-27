package com.cpamp.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun `formats compact values at unit boundaries`() {
        assertEquals("999", 999L.compactNumber())
        assertEquals("1K", 1_000L.compactNumber())
        assertEquals("1.5M", 1_500_000L.compactNumber())
    }

    @Test
    fun `formats percent and cost deterministically`() {
        assertEquals("85.7%", 0.857.asPercent())
        assertEquals("$0.0042", 0.0042.asCost())
        assertEquals("$12.35", 12.345.asCost())
    }
}