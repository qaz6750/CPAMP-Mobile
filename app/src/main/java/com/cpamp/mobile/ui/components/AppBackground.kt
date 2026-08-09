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
import androidx.compose.ui.graphics.luminance
import com.cpamp.mobile.ui.theme.AppGradientCenter
import com.cpamp.mobile.ui.theme.AppGradientDarkCenter
import com.cpamp.mobile.ui.theme.AppGradientDarkEnd
import com.cpamp.mobile.ui.theme.AppGradientDarkStart
import com.cpamp.mobile.ui.theme.AppGradientEnd
import com.cpamp.mobile.ui.theme.AppGradientStart

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val gradientColors = if (background.luminance() < 0.5f) {
        listOf(AppGradientDarkStart, AppGradientDarkCenter, AppGradientDarkEnd)
    } else {
        listOf(AppGradientStart, AppGradientCenter, AppGradientEnd)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset.Zero,
                    end = Offset(1080f, 820f),
                ),
            ),
        content = content,
    )
}
