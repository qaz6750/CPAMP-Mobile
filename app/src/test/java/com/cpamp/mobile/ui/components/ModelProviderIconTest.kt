package com.cpamp.mobile.ui.components

import com.cpamp.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderIconTest {
    @Test
    fun `credential providers resolve dedicated and fallback visuals`() {
        assertEquals(R.drawable.ic_provider_openai, credentialProviderVisual("codex").icon)
        assertTrue(credentialProviderVisual("codex").useThemeForeground)
        assertEquals(R.drawable.ic_provider_xai, credentialProviderVisual("grok").icon)
        assertTrue(credentialProviderVisual("grok").useThemeForeground)
        assertEquals(R.drawable.ic_provider_anthropic, credentialProviderVisual("claude").icon)
        assertEquals("AI", credentialProviderVisual("future-provider").badgeText)
    }

    @Test
    fun `model provider resolution remains compatible`() {
        assertEquals(R.drawable.ic_provider_openai, modelProviderVisual("gpt-5-codex").icon)
        assertTrue(modelProviderVisual("gpt-5-codex").useThemeForeground)
        assertEquals(R.drawable.ic_provider_xai, modelProviderVisual("grok-4").icon)
        assertTrue(modelProviderVisual("grok-4").useThemeForeground)
        assertEquals(R.drawable.ic_provider_gemini, modelProviderVisual("gemini-3-pro").icon)
    }
}
