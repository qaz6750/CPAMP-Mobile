package com.cpamp.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
    val Secondary = Color(0xFF3F6EB8)
    val SecondaryDark = Color(0xFFB5C8F4)
    val SecondaryContainerLight = Color(0xFFE9EFFA)
    val PrimaryContainerDark = Color(0xFF173A78)
    val SurfaceVariantLight = Color(0xFFF1F3F5)
    val SurfaceVariantDark = Color(0xFF292B2E)
    val OnSurfaceVariantLight = Color(0x99000000)
    val OutlineLight = Color(0x33000000)
    val OutlineDark = Color(0x33FFFFFF)
    val OnDark = Color(0xE6FFFFFF)
    val TertiaryContainerLight = Color(0xFFEAF6E8)
    val OnTertiaryContainerLight = Color(0xFF245A20)
    val InversePrimaryLight = Color(0xFF7DD3FC)
    val SuccessDark = Color(0xFF83D47B)
    val Error = Color(0xFFE84026)
    val ErrorDark = Color(0xFFFFB4A8)
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

    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
