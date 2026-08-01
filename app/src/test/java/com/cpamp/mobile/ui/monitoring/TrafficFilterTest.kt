package com.cpamp.mobile.ui.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFilterTest {
    @Test
    fun filterValues_areTrimmedDeduplicatedAndEmptyValuesRemoved() {
        assertEquals(
            listOf("gpt-5", "claude-sonnet"),
            " gpt-5, claude-sonnet, gpt-5, ,".asFilterValues(),
        )
    }
}