package com.cpamp.mobile.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
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
    val provider = ModelProvider.fromModel(model)
    Surface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        color = provider.color.copy(alpha = 0.14f),
        contentColor = provider.color,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            when {
                provider.icon != null -> Icon(
                    painter = painterResource(provider.icon),
                    contentDescription = provider.displayName,
                    modifier = Modifier.size(21.dp),
                )
                provider == ModelProvider.Xai -> Text(
                    text = "xAI",
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

private enum class ModelProvider(
    val displayName: String,
    @DrawableRes val icon: Int?,
    val color: Color,
) {
    OpenAi("OpenAI", R.drawable.ic_provider_openai, Color(0xFF111111)),
    Anthropic("Anthropic", R.drawable.ic_provider_anthropic, Color(0xFF191919)),
    Google("Google Gemini", R.drawable.ic_provider_gemini, Color(0xFF6750A4)),
    Xai("xAI", null, Color(0xFF111111)),
    DeepSeek("DeepSeek", R.drawable.ic_provider_deepseek, Color(0xFF356AE6)),
    Qwen("Qwen", R.drawable.ic_provider_qwen, Color(0xFF6950EF)),
    Generic("AI model", null, Color(0xFF356AE6));

    companion object {
        fun fromModel(model: String): ModelProvider {
            val normalized = model.lowercase()
            return when {
                listOf("gpt", "o1", "o3", "o4", "codex", "chatgpt").any(normalized::contains) -> OpenAi
                listOf("claude", "anthropic").any(normalized::contains) -> Anthropic
                listOf("gemini", "vertex", "palm").any(normalized::contains) -> Google
                listOf("grok", "xai").any(normalized::contains) -> Xai
                "deepseek" in normalized -> DeepSeek
                listOf("qwen", "qwq", "tongyi").any(normalized::contains) -> Qwen
                else -> Generic
            }
        }
    }
}