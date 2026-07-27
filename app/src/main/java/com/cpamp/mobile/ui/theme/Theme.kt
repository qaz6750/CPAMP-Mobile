package com.cpamp.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Evergreen,
    onPrimary = Mist,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    background = Mist,
    onBackground = Ink,
    surface = ColorTokens.SurfaceLight,
    error = ColorTokens.Error,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Night,
    primaryContainer = Evergreen,
    background = Night,
    onBackground = Mist,
    surface = ColorTokens.SurfaceDark,
    error = ColorTokens.ErrorDark,
)

private object ColorTokens {
    val SurfaceLight = androidx.compose.ui.graphics.Color(0xFFFAFDFC)
    val SurfaceDark = androidx.compose.ui.graphics.Color(0xFF10231F)
    val Error = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
}

@Composable
fun CPAMPMobileTheme(
    darkThemeOverride: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
