package com.cpamp.mobile.data.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountHealthMappingTest {
    @Test
    fun `disabled credential keeps server quota windows available`() {
        assertEquals(
            AccountQuotaState.Available,
            resolvedInspectionQuotaState(disabled = true, failed = false, hasWindows = true),
        )
    }

    @Test
    fun `disabled credential without server quota remains not requested`() {
        assertEquals(
            AccountQuotaState.NotRequested,
            resolvedInspectionQuotaState(disabled = true, failed = false, hasWindows = false),
        )
    }
}