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
    val Secondary = androidx.compose.ui.graphics.Color(0xFF3282A8)
    val SecondaryDark = androidx.compose.ui.graphics.Color(0xFFB9C8E5)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFDDF3FC)
    val PrimaryContainerDark = androidx.compose.ui.graphics.Color(0xFF173B78)
    val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFE8F7FE)
    val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF192C4B)
    val OnSurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFF526D82)
    val OutlineLight = androidx.compose.ui.graphics.Color(0xFFB8DDEE)
    val OutlineDark = androidx.compose.ui.graphics.Color(0xFF344B70)
    val OnDark = androidx.compose.ui.graphics.Color(0xFFEAF1FF)
    val TertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFFD9F8EC)
    val OnTertiaryContainerLight = androidx.compose.ui.graphics.Color(0xFF064E3B)
    val InversePrimaryLight = androidx.compose.ui.graphics.Color(0xFF7DD3FC)
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
