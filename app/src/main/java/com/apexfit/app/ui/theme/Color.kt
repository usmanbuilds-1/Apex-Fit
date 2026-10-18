package com.apexfit.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
// APEX FIT · Design System v2 "Night Signal"
// Every value in this file is pixel-sampled from the approved Home mockup or
// directly specified in the v2 design system document. Zero ad-hoc hex values.
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Raw Palette ─────────────────────────────────────────────────────────────
// Internal only. All UI code must reference ApexColors.* semantic tokens below.

private val ElectricMint    = Color(0xFF47E1C2) // Primary glow — sampled from mockup
private val MintDeep        = Color(0xFF3CBFA5) // Pressed states
private val MintMist        = Color(0xFF0F3537) // Tint backgrounds, selected chips
private val MintMistDeep    = Color(0xFF174D4B) // Meta text on glow fills
private val Void            = Color(0xFF04141D) // Canvas / ink on glow

private val Night1          = Color(0xFF151C24) // Surface
private val Night2          = Color(0xFF1A232D) // SurfaceRaised
private val Night0          = Color(0xFF0E141B) // SurfaceSunken (recessed wells)

private val White           = Color(0xFFFFFFFF)
private val Grey1           = Color(0xFF8B98A5) // Secondary text
private val Grey2           = Color(0xFF5C6873) // Tertiary text

private val GoldAmber       = Color(0xFFF2B33D) // Caution, deload
private val AmberSoftFill   = Color(0xFF3A2F1A)
private val CoralAlert      = Color(0xFFFF5240) // Errors, low readiness
private val AlertSoftFill   = Color(0xFF3A1D1B)
private val SkyFocus        = Color(0xFF5B9DFF) // Info, learning, charts
private val FocusSoftFill   = Color(0xFF152A41)

private val Edge1           = Color(0xFF242F3A) // BorderSubtle
private val Edge2           = Color(0xFF33414D) // BorderStrong
private val EdgeGlow        = Color(0xFF2A5F58) // BorderGlow — signal hairline

// ─── Semantic Tokens — ApexColors ────────────────────────────────────────────
// The only names that may appear in screen code.

object ApexColors {

    // Surfaces — the night stack
    val Canvas          = Void          // Screen background — the deepest void
    val Surface         = Night1        // Cards, sheets
    val SurfaceRaised   = Night2        // Nested panels, exercise preview wells
    val SurfaceSunken   = Night0        // Input fills, progress tracks, recessed wells

    // Brand — the single glow
    val Primary         = ElectricMint  // CTAs, active states, rings, links, energy
    val PrimaryDeep     = MintDeep      // Pressed states, arc strokes on tinted fills
    val PrimaryMist     = MintMist      // Selected chips, tint backgrounds
    val OnGlow          = Void          // Text/icons on Primary fills — the inversion rule
    val OnGlowSecondary = MintMistDeep  // Meta text on Primary fills

    // Semantic states
    // Victory aliases Primary — in Night Signal, achievement shares the glow
    val Victory         = ElectricMint
    val VictorySoft     = MintMist
    val Amber           = GoldAmber     // Deload, caution, over-target
    val AmberSoft       = AmberSoftFill
    val Alert           = CoralAlert    // Errors, destructive, low readiness
    val AlertSoft       = AlertSoftFill
    val Focus           = SkyFocus      // Information, learning, charts
    val FocusSoft       = FocusSoftFill

    // Text
    val TextPrimary     = White         // Headlines, body, data on dark surfaces
    val TextSecondary   = Grey1         // Descriptions, meta
    val TextTertiary    = Grey2         // Eyebrow labels, timestamps (bold caps ≥12sp only)

    // Borders — hairlines, not heavy strokes
    val BorderSubtle    = Edge1         // Card edges, dividers, input resting
    val BorderStrong    = Edge2         // Raised surfaces, sheet handles
    val BorderGlow      = EdgeGlow      // Editorial emphasis, active focus — the signal hairline

    // Data visualization (used in charts, heatmap, macro bars)
    val Chart1          = ElectricMint  // = Primary
    val Chart2          = SkyFocus
    val Chart3          = Color(0xFFA78BFA)
    val Chart4          = GoldAmber
    val Chart5          = Color(0xFFFF8A5C)

    // Readiness band map — use in ReadinessRing and Home ring arc
    fun readinessBandColor(score: Int, hasData: Boolean): Color = when {
        !hasData     -> Focus    // Learning state (first 3 days)
        score < 40   -> Alert
        score < 75   -> Amber
        else         -> Victory
    }
}

// ─── Legacy Aliases ───────────────────────────────────────────────────────────
// Kept so existing screens compile through Phase 7 rebuilds.
// Do NOT use in new or rebuilt screens — reference ApexColors.* directly.

val DarkBackground  = ApexColors.Canvas
val DarkRaised      = ApexColors.SurfaceRaised
val DarkCardSurface = ApexColors.Surface
val AmberAccent     = ApexColors.Amber
val BlueAccent      = ApexColors.Focus
val RedAccent       = ApexColors.Alert
val GreenAccent     = ApexColors.Primary
val IndigoAccent    = ApexColors.Focus
val PrimaryText     = ApexColors.TextPrimary
val SecondaryText   = ApexColors.TextSecondary
val MutedText       = ApexColors.TextTertiary
val BorderBright    = ApexColors.BorderStrong
val BorderSubtle    = ApexColors.BorderSubtle
val OrangeAccent    = ApexColors.Amber
val BackgroundDark  = ApexColors.Canvas
val SurfaceDark     = ApexColors.Surface
val CardDark        = ApexColors.Surface
val DividerDark     = ApexColors.BorderSubtle
val GreenSuccess    = ApexColors.Victory
val RedError        = ApexColors.Alert
