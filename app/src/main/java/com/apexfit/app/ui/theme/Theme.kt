package com.apexfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ─── M3 Color Scheme — Night Signal ──────────────────────────────────────────
// All slots wired to ApexColors.* tokens. APEX is dark-only — no light scheme.

private val NightSignalColorScheme = darkColorScheme(
    primary              = ApexColors.Primary,
    onPrimary            = ApexColors.OnGlow,
    primaryContainer     = ApexColors.PrimaryMist,
    onPrimaryContainer   = ApexColors.Primary,

    secondary            = ApexColors.TextSecondary,
    onSecondary          = ApexColors.Canvas,
    secondaryContainer   = ApexColors.SurfaceRaised,
    onSecondaryContainer = ApexColors.TextSecondary,

    tertiary             = ApexColors.Amber,
    onTertiary           = ApexColors.Canvas,
    tertiaryContainer    = ApexColors.AmberSoft,
    onTertiaryContainer  = ApexColors.Amber,

    background           = ApexColors.Canvas,
    onBackground         = ApexColors.TextPrimary,

    surface              = ApexColors.Surface,
    onSurface            = ApexColors.TextPrimary,
    surfaceVariant       = ApexColors.SurfaceRaised,
    onSurfaceVariant     = ApexColors.TextSecondary,

    error                = ApexColors.Alert,
    onError              = ApexColors.Canvas,
    errorContainer       = ApexColors.AlertSoft,
    onErrorContainer     = ApexColors.Alert,

    outline              = ApexColors.BorderSubtle,
    outlineVariant       = ApexColors.BorderStrong,
    scrim                = ApexColors.Canvas
)

@Composable
fun ApexFitTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NightSignalColorScheme,
        typography  = Typography,
        shapes      = ApexShapes,
        content     = content
    )
}
