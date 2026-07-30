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
    val Secondary = Color(0xFF3F6EB8)
    val SecondaryDark = Color(0xFFB5C8F4)
    val SecondaryContainerLight = Color(0xFFE9EFFA)
    val PrimaryContainerDark = Color(0xFF173A78)
    val OnPrimaryContainerDark = Color(0xFFDCE7FF)
    val SurfaceVariantLight = Color(0xFFF1F3F5)
    val SurfaceVariantDark = Color(0xFF292B2E)
    val OnSurfaceVariantDark = Color(0xFFC2C6CE)
    val OnSurfaceVariantLight = Color(0x99000000)
    val OutlineLight = Color(0x33000000)
    val OutlineDark = Color(0x33FFFFFF)
    val OutlineDarkStrong = Color(0x5CFFFFFF)
    val OnDark = Color(0xE6FFFFFF)
    val TertiaryContainerLight = Color(0xFFEAF6E8)
    val OnTertiaryContainerLight = Color(0xFF245A20)
    val InversePrimaryLight = Color(0xFF7DD3FC)
    val SuccessDark = Color(0xFF83D47B)
    val OnTertiaryDark = Color(0xFF082A08)
    val TertiaryContainerDark = Color(0xFF173F19)
    val OnTertiaryContainerDark = Color(0xFFB6F1AD)
    val OnSecondaryDark = Color(0xFF14213B)
    val SecondaryContainerDark = Color(0xFF293750)
    val OnSecondaryContainerDark = Color(0xFFD9E3FF)
    val Error = Color(0xFFE84026)
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
