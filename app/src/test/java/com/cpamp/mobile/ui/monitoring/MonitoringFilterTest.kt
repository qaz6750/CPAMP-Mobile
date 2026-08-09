package com.cpamp.mobile.ui.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringFilterTest {
    @Test
    fun onlyDefaultDayFilter_isCacheable() {
        assertTrue(MonitoringFilter().cacheable)
        assertFalse(MonitoringFilter(failedOnly = true).cacheable)
        assertFalse(MonitoringFilter(window = MonitoringWindow.Hour).cacheable)
    }
}