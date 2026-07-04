package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ApexDarkColorScheme = darkColorScheme(
    primary = AmberAccent,
    onPrimary = DarkBackground,
    primaryContainer = DarkRaised,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryText,
    onSecondary = DarkBackground,
    secondaryContainer = DarkCardSurface,
    onSecondaryContainer = SecondaryText,
    tertiary = GreenAccent,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkRaised,
    onTertiaryContainer = GreenAccent,
    background = DarkBackground,
    onBackground = PrimaryText,
    surface = DarkCardSurface,
    onSurface = PrimaryText,
    surfaceVariant = DarkRaised,
    onSurfaceVariant = SecondaryText,
    error = RedAccent,
    onError = DarkBackground,
    errorContainer = DarkRaised,
    onErrorContainer = RedAccent,
    outline = BorderSubtle,
    outlineVariant = BorderBright
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
