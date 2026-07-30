package com.cpamp.mobile.ui.components

import com.cpamp.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelProviderIconTest {
    @Test
    fun `credential providers resolve dedicated and fallback visuals`() {
        assertEquals(R.drawable.ic_provider_openai, credentialProviderVisual("codex").icon)
        assertEquals("xAI", credentialProviderVisual("grok").badgeText)
        assertEquals(R.drawable.ic_provider_anthropic, credentialProviderVisual("claude").icon)
        assertEquals("AI", credentialProviderVisual("future-provider").badgeText)
    }

    @Test
    fun `model provider resolution remains compatible`() {
        assertEquals(R.drawable.ic_provider_openai, modelProviderVisual("gpt-5-codex").icon)
        assertNull(modelProviderVisual("grok-4").icon)
        assertEquals(R.drawable.ic_provider_gemini, modelProviderVisual("gemini-3-pro").icon)
    }
}
