package com.apexfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ApexDarkColorScheme = darkColorScheme(
    primary = ApexColors.Primary,
    onPrimary = ApexColors.OnGlow,
    primaryContainer = ApexColors.SurfaceRaised,
    onPrimaryContainer = ApexColors.TextPrimary,
    secondary = ApexColors.TextSecondary,
    onSecondary = ApexColors.Canvas,
    secondaryContainer = ApexColors.Surface,
    onSecondaryContainer = ApexColors.TextSecondary,
    tertiary = ApexColors.Victory,
    onTertiary = ApexColors.Canvas,
    tertiaryContainer = ApexColors.SurfaceRaised,
    onTertiaryContainer = ApexColors.Victory,
    background = ApexColors.Canvas,
    onBackground = ApexColors.TextPrimary,
    surface = ApexColors.Surface,
    onSurface = ApexColors.TextPrimary,
    surfaceVariant = ApexColors.SurfaceRaised,
    onSurfaceVariant = ApexColors.TextSecondary,
    error = ApexColors.Alert,
    onError = ApexColors.Canvas,
    errorContainer = ApexColors.SurfaceRaised,
    onErrorContainer = ApexColors.Alert,
    outline = ApexColors.BorderSubtle,
    outlineVariant = ApexColors.BorderStrong
)

@Composable
fun ApexFitTheme(
    darkTheme: Boolean = true, // Force Dark Theme always
    dynamicColor: Boolean = false, // Disable dynamic colors to maintain premium vibe
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = Typography,
        content = content
    )
}
