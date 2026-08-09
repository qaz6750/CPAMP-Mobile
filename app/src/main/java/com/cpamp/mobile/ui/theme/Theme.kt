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
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
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
    surface = NavySurface,
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
    val Secondary = Color(0xFF0F766E)
    val SecondaryDark = Color(0xFF7DD3C7)
    val SecondaryContainerLight = Color(0xFFE2F4F1)
    val PrimaryContainerDark = Color(0xFF193A6F)
    val OnPrimaryContainerDark = Color(0xFFDCE7FF)
    val SurfaceVariantLight = Color(0xFFF0F2F5)
    val SurfaceVariantDark = Color(0xFF25282C)
    val OnSurfaceVariantDark = Color(0xFFC5C8CE)
    val OnSurfaceVariantLight = Color(0xFF5F6877)
    val OutlineLight = Color(0xFFD7DCE3)
    val OutlineDark = Color(0xFF353A40)
    val OutlineDarkStrong = Color(0xFF555B63)
    val OnDark = Color(0xFFF1F3F5)
    val TertiaryContainerLight = Color(0xFFE1F5EE)
    val OnTertiaryContainerLight = Color(0xFF075E47)
    val InversePrimaryLight = Color(0xFFA9C7FF)
    val SuccessDark = Color(0xFF6ED8B5)
    val OnTertiaryDark = Color(0xFF06382C)
    val TertiaryContainerDark = Color(0xFF123D32)
    val OnTertiaryContainerDark = Color(0xFFA3ECD3)
    val OnSecondaryDark = Color(0xFF063B37)
    val SecondaryContainerDark = Color(0xFF173E3B)
    val OnSecondaryContainerDark = Color(0xFFA9E6DE)
    val Error = Color(0xFFD93C32)
    val ErrorDark = Color(0xFFFFB4A8)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF5A1B19)
    val OnErrorContainerDark = Color(0xFFFFDAD5)
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
