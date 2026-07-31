package com.cpamp.mobile.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R

@Composable
fun ModelProviderIcon(model: String, modifier: Modifier = Modifier) {
    ProviderIcon(modelProviderVisual(model), modifier)
}

@Composable
fun CredentialProviderIcon(provider: String, modifier: Modifier = Modifier) {
    ProviderIcon(credentialProviderVisual(provider), modifier)
}

@Composable
private fun ProviderIcon(provider: ModelProviderVisual, modifier: Modifier) {
    val iconColor = if (provider.useThemeForeground) {
        MaterialTheme.colorScheme.onSurface
    } else {
        provider.color
    }
    Surface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        color = iconColor.copy(alpha = 0.14f),
        contentColor = iconColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                provider.icon != null -> Icon(
                    painter = painterResource(provider.icon),
                    contentDescription = provider.displayName,
                    modifier = Modifier.size(21.dp),
                )
                provider.badgeText != null -> Text(
                    text = provider.badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = provider.displayName,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

internal data class ModelProviderVisual(
    val displayName: String,
    @DrawableRes val icon: Int?,
    val color: Color,
    val badgeText: String? = null,
    val useThemeForeground: Boolean = false,
)

internal fun modelProviderVisual(model: String): ModelProviderVisual {
    val normalized = model.lowercase()
    return when {
        listOf("gpt", "o1", "o3", "o4", "codex", "chatgpt").any(normalized::contains) ->
            ModelProviderVisual(
                "OpenAI",
                R.drawable.ic_provider_openai,
                Color(0xFF111111),
                useThemeForeground = true,
            )
        listOf("claude", "anthropic").any(normalized::contains) ->
            ModelProviderVisual("Claude", R.drawable.ic_provider_anthropic, Color(0xFFD97757))
        listOf("gemini", "vertex", "palm").any(normalized::contains) ->
            ModelProviderVisual("Google Gemini", R.drawable.ic_provider_gemini, Color(0xFF6750A4))
        listOf("grok", "xai").any(normalized::contains) ->
            ModelProviderVisual("xAI", null, Color(0xFF111111), "xAI", useThemeForeground = true)
        "deepseek" in normalized ->
            ModelProviderVisual("DeepSeek", R.drawable.ic_provider_deepseek, Color(0xFF356AE6))
        listOf("qwen", "qwq", "tongyi").any(normalized::contains) ->
            ModelProviderVisual("Qwen", R.drawable.ic_provider_qwen, Color(0xFF6950EF))
        else -> ModelProviderVisual("AI model", null, Color(0xFF356AE6), "AI")
    }
}

internal fun credentialProviderVisual(provider: String): ModelProviderVisual {
    val normalized = provider.trim().lowercase().replace('_', '-')
    return when (normalized) {
        "codex", "openai", "chatgpt" ->
            ModelProviderVisual(
                "OpenAI",
                R.drawable.ic_provider_openai,
                Color(0xFF111111),
                useThemeForeground = true,
            )
        "claude", "anthropic" ->
            ModelProviderVisual("Anthropic", R.drawable.ic_provider_anthropic, Color(0xFFD97757))
        "gemini", "gemini-cli", "aistudio", "vertex" ->
            ModelProviderVisual("Google Gemini", R.drawable.ic_provider_gemini, Color(0xFF6750A4))
        "xai", "x-ai", "grok" ->
            ModelProviderVisual("xAI", null, Color(0xFF111111), "xAI", useThemeForeground = true)
        "deepseek" ->
            ModelProviderVisual("DeepSeek", R.drawable.ic_provider_deepseek, Color(0xFF356AE6))
        "qwen", "qwq", "tongyi" ->
            ModelProviderVisual("Qwen", R.drawable.ic_provider_qwen, Color(0xFF6950EF))
        else -> ModelProviderVisual("AI provider", null, Color(0xFF356AE6), "AI")
    }
}
