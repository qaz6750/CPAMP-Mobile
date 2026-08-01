package com.cpamp.mobile.ui.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficFilterTest {
    @Test
    fun onlyDefaultDayFilter_isCacheable() {
        assertTrue(TrafficFilter().cacheable)
        assertFalse(TrafficFilter(failedOnly = true).cacheable)
        assertFalse(TrafficFilter(models = listOf("gpt-5")).cacheable)
        assertFalse(TrafficFilter(providers = listOf("openai")).cacheable)
        assertFalse(TrafficFilter(window = TrafficWindow.Hour).cacheable)
    }
}