package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ApexDarkColorScheme = darkColorScheme(
    primary = AmberAccent,
    secondary = SecondaryText,
    tertiary = GreenAccent,
    background = DarkBackground,
    surface = DarkCardSurface,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    error = RedAccent
)

@Composable
fun MyApplicationTheme(
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
