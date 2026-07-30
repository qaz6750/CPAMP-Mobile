package com.cpamp.mobile.ui.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialQuotaScreenTest {
    @Test
    fun `remaining quota thresholds include exact boundaries`() {
        assertEquals(CredentialQuotaLevel.Healthy, credentialQuotaLevel(50.0))
        assertEquals(CredentialQuotaLevel.Warning, credentialQuotaLevel(49.999))
        assertEquals(CredentialQuotaLevel.Warning, credentialQuotaLevel(20.0))
        assertEquals(CredentialQuotaLevel.Critical, credentialQuotaLevel(19.999))
        assertEquals(CredentialQuotaLevel.Critical, credentialQuotaLevel(0.0))
        assertEquals(CredentialQuotaLevel.Unknown, credentialQuotaLevel(null))
    }
}
