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
    primary = BrandBlue,
    onPrimary = White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = Ink,
    secondary = ColorTokens.Secondary,
    tertiary = Success,
    background = Sky,
    onBackground = Ink,
    surface = White,
    surfaceVariant = ColorTokens.SurfaceVariantLight,
    outlineVariant = ColorTokens.OutlineLight,
    error = ColorTokens.Error,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueBright,
    onPrimary = Navy,
    primaryContainer = ColorTokens.PrimaryContainerDark,
    secondary = ColorTokens.SecondaryDark,
    tertiary = ColorTokens.SuccessDark,
    background = Navy,
    onBackground = ColorTokens.OnDark,
    surface = NavySurface,
    surfaceVariant = ColorTokens.SurfaceVariantDark,
    outlineVariant = ColorTokens.OutlineDark,
    error = ColorTokens.ErrorDark,
)

private object ColorTokens {
    val Secondary = androidx.compose.ui.graphics.Color(0xFF536785)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFFB9C8E5)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF173B78)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFE7EFFC)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF192C4B)
    val OutlineLight = androidx.compose.ui.graphics.Color(0xFFC7D5EA)
    val OutlineDark = androidx.compose.ui.graphics.Color(0xFF344B70)
    val OnDark = androidx.compose.ui.graphics.Color(0xFFEAF1FF)
    val SuccessDark = androidx.compose.ui.graphics.Color(0xFF63D9A8)
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
