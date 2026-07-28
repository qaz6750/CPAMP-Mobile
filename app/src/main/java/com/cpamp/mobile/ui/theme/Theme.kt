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
    onSecondary = White,
    secondaryContainer = ColorTokens.SecondaryContainerLight,
    onSecondaryContainer = Ink,
    tertiary = Success,
    onTertiary = White,
    tertiaryContainer = ColorTokens.TertiaryContainerLight,
    onTertiaryContainer = ColorTokens.OnTertiaryContainerLight,
    background = Sky,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = ColorTokens.SurfaceVariantLight,
    onSurfaceVariant = ColorTokens.OnSurfaceVariantLight,
    outline = ColorTokens.OutlineLight,
    outlineVariant = ColorTokens.OutlineLight,
    surfaceTint = BrandBlue,
    inverseSurface = Ink,
    inverseOnSurface = Sky,
    inversePrimary = ColorTokens.InversePrimaryLight,
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
    val Secondary = androidx.compose.ui.graphics.Color(0xFF3F6EB8)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFFB5C8F4)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFE9EFFA)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF173A78)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFF1F3F5)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF292B2E)
    val OnSurfaceVariantLight = androidx.compose.ui.graphics.Color(0x99000000)
    val OutlineLight = androidx.compose.ui.graphics.Color(0x33000000)
    val OutlineDark = androidx.compose.ui.graphics.Color(0x33FFFFFF)
    val OnDark = androidx.compose.ui.graphics.Color(0xE6FFFFFF)
    val TertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFFEAF6E8)
    val OnTertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFF245A20)
    val InversePrimaryLight = androidx.compose.ui.graphics.Color(0xFF7DD3FC)
    val SuccessDark = androidx.compose.ui.graphics.Color(0xFF83D47B)
    val Error = androidx.compose.ui.graphics.Color(0xFFE84026)
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFFB4A8)
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
