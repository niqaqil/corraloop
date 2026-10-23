package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaLight,
    onPrimary = Color(0xFF381303),
    primaryContainer = Color(0xFF6B2B15),
    onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = EarthSageLight,
    onSecondary = Color(0xFF143729),
    secondaryContainer = Color(0xFF2B4E3E),
    onSecondaryContainer = Color(0xFFC7E6D7),
    tertiary = WarmOchre,
    background = DarkEarthBackground,
    onBackground = DarkEarthText,
    surface = DarkEarthSurface,
    onSurface = DarkEarthText,
    surfaceVariant = DarkEarthSurfaceVariant,
    onSurfaceVariant = DarkMutedText,
    outline = DarkOutlineWarm
)

private val LightColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = EarthSage,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = WarmOchre,
    background = SandBackgroundLight,
    onBackground = DeepEspressoText,
    surface = SandSurfaceLight,
    onSurface = DeepEspressoText,
    surfaceVariant = SandSurfaceVariantLight,
    onSurfaceVariant = MutedEspressoText,
    outline = OutlineWarm
)

@Composable
fun CorraloopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
