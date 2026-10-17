package com.apexfit.app.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Base Palette ─────────────────────────────────────────────────────────────
// Raw hex values. Never reference these in UI code — use ApexColors.* tokens.

private val Teal500  = Color(0xFF00D4A0)   // Brand teal-mint
private val Teal300  = Color(0xFF33DDB4)   // Pressed / lighter teal
private val Navy900  = Color(0xFF080F0E)   // App background
private val Navy800  = Color(0xFF0E1C19)   // Card surface
private val Navy700  = Color(0xFF142421)   // Elevated surface (nested panels)
private val White    = Color(0xFFFFFFFF)
private val Grey400  = Color(0xFF8A9B98)   // Secondary text
private val Grey600  = Color(0xFF4D6360)   // Tertiary / eyebrow text
private val Amber500 = Color(0xFFF59E0B)   // Warnings / deload
private val Red500   = Color(0xFFEF4444)   // Errors / destructive
private val Blue500  = Color(0xFF3B82F6)   // Charts / fatigue gauge (kept)

// ─── Semantic Tokens ──────────────────────────────────────────────────────────
// All new UI code must use these. Named by purpose, never by raw color value.

object ApexColors {

    // Surfaces
    val Background      = Navy900        // Screen background
    val Surface         = Navy800        // First-level cards, bottom nav
    val SurfaceElevated = Navy700        // Nested cards, inner panels

    // Brand
    val Primary         = Teal500        // CTA, active nav, accent rings, links
    val PrimaryVariant  = Teal300        // Pressed / hover states on Primary
    val OnPrimary       = Navy900        // Text / icons on solid Primary fill

    // Text
    val TextPrimary     = White          // Headlines, primary body
    val TextSecondary   = Grey400        // Supporting copy, muscle groups, meta
    val TextTertiary    = Grey600        // Eyebrow labels, captions
    // Semantic status
    // Note: teal inline text ("Ready", link labels) uses Primary directly.
    // No TextBrand alias — teal text is a sparse signal, not decoration to scatter.
    val Success         = Teal500        // Maps to Primary in this design
    val Warning         = Amber500       // Deload suggestions, caution states
    val Error           = Red500         // Errors, destructive actions
    val Info            = Blue500        // Progress charts, fatigue gauge

    // Borders
    val BorderSubtle    = Color(0x14FFFFFF)  //  8% white — between sections
    val BorderDefault   = Color(0x29FFFFFF)  // 16% white — card outlines

    // Overlays
    val ScrimHero       = Color(0xCC080F0E)  // 80% Navy — hero image gradient
    val ScrimBottom     = Color(0xE6080F0E)  // 90% Navy — bottom sheet scrim
}

// ─── Legacy Aliases ───────────────────────────────────────────────────────────
// Kept so existing screens compile while they are rebuilt in Phase 7.
// Do not use in new code — reference ApexColors.* directly.

val DarkBackground  = ApexColors.Background
val DarkRaised      = ApexColors.SurfaceElevated
val DarkCardSurface = ApexColors.Surface
val AmberAccent     = ApexColors.Warning
val BlueAccent      = ApexColors.Info
val RedAccent       = ApexColors.Error
val GreenAccent     = ApexColors.Primary
val IndigoAccent    = Teal300            // remapped: indigo → brand teal
val PrimaryText     = ApexColors.TextPrimary
val SecondaryText   = ApexColors.TextSecondary
val MutedText       = Grey400            // slightly lighter than Tertiary
val BorderBright    = ApexColors.BorderDefault
val BorderSubtle    = ApexColors.BorderSubtle
val OrangeAccent    = ApexColors.Warning // remapped: orange → amber warning
val BackgroundDark  = ApexColors.Background
val SurfaceDark     = ApexColors.Surface
val CardDark        = ApexColors.Surface
val DividerDark     = ApexColors.BorderSubtle
val GreenSuccess    = ApexColors.Primary
val RedError        = ApexColors.Error
