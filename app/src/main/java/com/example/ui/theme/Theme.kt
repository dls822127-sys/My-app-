package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnantDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = MidnightAbyss,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = SolarAmber,
    onSecondary = MidnightAbyss,
    secondaryContainer = DeepAmberContainer,
    onSecondaryContainer = SolarAmber,
    tertiary = AuroraEmerald,
    onTertiary = MidnightAbyss,
    background = MidnightAbyss,
    onBackground = StarWhite,
    surface = DeepIndigoSurface,
    onSurface = StarWhite,
    surfaceVariant = ElevatedIndigo,
    onSurfaceVariant = MistBlue,
    outline = GlassStroke,
    error = CoralAlert,
    onError = StarWhite
)

@Composable
fun AnantDriftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AnantDarkColorScheme,
        typography = Typography,
        content = content
    )
}
