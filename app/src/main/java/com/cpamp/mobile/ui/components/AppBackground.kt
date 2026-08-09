package com.cpamp.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val primaryTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    val secondaryTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryTint, secondaryTint, background, background),
                    start = Offset.Zero,
                    end = Offset(980f, 1180f),
                ),
            ),
        content = content,
    )
}
