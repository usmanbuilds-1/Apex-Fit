package com.apexfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ─── Material3 Color Scheme ───────────────────────────────────────────────────
// All slots wired to ApexColors semantic tokens.
// APEX is a dark-only product — no light scheme.

private val ApexDarkColorScheme = darkColorScheme(
    primary             = ApexColors.Primary,
    onPrimary           = ApexColors.OnPrimary,
    primaryContainer    = ApexColors.SurfaceElevated,
    onPrimaryContainer  = ApexColors.TextPrimary,

    secondary           = ApexColors.TextSecondary,
    onSecondary         = ApexColors.Background,
    secondaryContainer  = ApexColors.Surface,
    onSecondaryContainer = ApexColors.TextSecondary,

    tertiary            = ApexColors.Warning,
    onTertiary          = ApexColors.Background,
    tertiaryContainer   = ApexColors.SurfaceElevated,
    onTertiaryContainer = ApexColors.Warning,

    background          = ApexColors.Background,
    onBackground        = ApexColors.TextPrimary,

    surface             = ApexColors.Surface,
    onSurface           = ApexColors.TextPrimary,
    surfaceVariant      = ApexColors.SurfaceElevated,
    onSurfaceVariant    = ApexColors.TextSecondary,

    error               = ApexColors.Error,
    onError             = ApexColors.Background,
    errorContainer      = ApexColors.SurfaceElevated,
    onErrorContainer    = ApexColors.Error,

    outline             = ApexColors.BorderSubtle,
    outlineVariant      = ApexColors.BorderDefault,
    scrim               = ApexColors.ScrimBottom
)

@Composable
fun ApexFitTheme(
    darkTheme: Boolean = true,     // APEX is dark-only
    dynamicColor: Boolean = false, // System dynamic color disabled — brand consistency
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography  = Typography,
        shapes      = ApexShapes,
        content     = content
    )
}
