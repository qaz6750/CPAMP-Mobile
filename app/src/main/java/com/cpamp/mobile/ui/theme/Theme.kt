package com.cpamp.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    onPrimaryContainer = BrandBlueActive,
    secondary = Slate,
    onSecondary = White,
    secondaryContainer = SlateLight,
    onSecondaryContainer = SlateDark,
    tertiary = Success,
    onTertiary = White,
    tertiaryContainer = ColorTokens.TertiaryContainerLight,
    onTertiaryContainer = ColorTokens.OnTertiaryContainerLight,
    background = Sky,
    onBackground = Ink,
    surface = ColorTokens.SurfaceLight,
    onSurface = Ink,
    surfaceVariant = ColorTokens.SurfaceVariantLight,
    onSurfaceVariant = ColorTokens.OnSurfaceVariantLight,
    outline = ColorTokens.OutlineLightStrong,
    outlineVariant = ColorTokens.OutlineLight,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Sky,
    inversePrimary = ColorTokens.InversePrimaryLight,
    error = Error,
    onError = White,
    errorContainer = ColorTokens.ErrorContainerLight,
    onErrorContainer = ColorTokens.OnErrorContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueBright,
    onPrimary = Navy,
    primaryContainer = ColorTokens.PrimaryContainerDark,
    onPrimaryContainer = ColorTokens.OnPrimaryContainerDark,
    secondary = ColorTokens.SecondaryDark,
    onSecondary = ColorTokens.OnSecondaryDark,
    secondaryContainer = ColorTokens.SecondaryContainerDark,
    onSecondaryContainer = ColorTokens.OnSecondaryContainerDark,
    tertiary = ColorTokens.SuccessDark,
    onTertiary = ColorTokens.OnTertiaryDark,
    tertiaryContainer = ColorTokens.TertiaryContainerDark,
    onTertiaryContainer = ColorTokens.OnTertiaryContainerDark,
    background = Navy,
    onBackground = ColorTokens.OnDark,
    surface = ColorTokens.SurfaceDark,
    onSurface = ColorTokens.OnDark,
    surfaceVariant = ColorTokens.SurfaceVariantDark,
    onSurfaceVariant = ColorTokens.OnSurfaceVariantDark,
    outline = ColorTokens.OutlineDarkStrong,
    outlineVariant = ColorTokens.OutlineDark,
    error = ColorTokens.ErrorDark,
    onError = ColorTokens.OnErrorDark,
    errorContainer = ColorTokens.ErrorContainerDark,
    onErrorContainer = ColorTokens.OnErrorContainerDark,
    surfaceTint = Color.Transparent,
    inverseSurface = ColorTokens.OnDark,
    inverseOnSurface = Navy,
)

private object ColorTokens {
    val SurfaceLight = Color(0xF0FFFFFF)
    val SurfaceDark = NavySurface.copy(alpha = 0.90f)
    val SecondaryDark = Color(0xFF94A3B8)
    val PrimaryContainerDark = Color(0xFF1D3557)
    val OnPrimaryContainerDark = Color(0xFFDBEAFE)
    val SurfaceVariantLight = Color(0xFFF6FAFF)
    val SurfaceVariantDark = Color(0xFF2B2F3A)
    val OnSurfaceVariantDark = Color(0xFFA3A3A3)
    val OnSurfaceVariantLight = Color(0xFF5F6C7B)
    val OutlineLight = Color(0xFFECECEE)
    val OutlineLightStrong = Color(0xFFE1E2E5)
    val OutlineDark = Color(0xFF2B2F39)
    val OutlineDarkStrong = Color(0xFF343743)
    val OnDark = Color(0xFFE5E5E5)
    val TertiaryContainerLight = Color(0xFFF0FDF4)
    val OnTertiaryContainerLight = Color(0xFF166534)
    val InversePrimaryLight = Color(0xFF93C5FD)
    val SuccessDark = Color(0xFF4ADE80)
    val OnTertiaryDark = Color(0xFF052E16)
    val TertiaryContainerDark = Color(0xFF14351F)
    val OnTertiaryContainerDark = Color(0xFF86EFAC)
    val OnSecondaryDark = Color(0xFF0F172A)
    val SecondaryContainerDark = Color(0xFF1E293B)
    val OnSecondaryContainerDark = Color(0xFFCBD5E1)
    val ErrorContainerLight = Color(0xFFFEF2F2)
    val OnErrorContainerLight = Color(0xFF991B1B)
    val ErrorDark = Color(0xFFF87171)
    val OnErrorDark = Color(0xFF450A0A)
    val ErrorContainerDark = Color(0xFF3F171A)
    val OnErrorContainerDark = Color(0xFFFCA5A5)
}

@Composable
fun CPAMPMobileTheme(
    darkThemeOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
