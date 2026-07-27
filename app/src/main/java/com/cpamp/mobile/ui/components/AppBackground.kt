package com.cpamp.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val tertiaryGlow = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset.Zero,
                    radius = 980f,
                ),
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, tertiaryGlow, background),
                ),
            ),
        content = content,
    )
}

