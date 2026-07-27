package com.cpamp.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModelProviderIcon(model: String, modifier: Modifier = Modifier) {
    val provider = ModelProvider.fromModel(model)
    Surface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        color = provider.color.copy(alpha = 0.14f),
        contentColor = provider.color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(provider.mark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
    }
}

private enum class ModelProvider(val mark: String, val color: Color) {
    OpenAi("O", Color(0xFF0F766E)),
    Anthropic("A", Color(0xFF7C3AED)),
    Google("G", Color(0xFF2563EB)),
    Xai("X", Color(0xFF111827)),
    DeepSeek("D", Color(0xFF1D4ED8)),
    Qwen("Q", Color(0xFFB45309)),
    Generic("AI", Color(0xFF356AE6));

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