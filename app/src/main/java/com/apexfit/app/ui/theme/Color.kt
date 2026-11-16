package com.apexfit.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
// APEX FIT · Night Ledger
// The approved charcoal and mineral-blue palette.
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Raw Palette ─────────────────────────────────────────────────────────────
// Internal only. All UI code must reference ApexColors.* semantic tokens below.

private val MineralBlue    = Color(0xFF7192D3) // Primary action
private val BluePressed        = Color(0xFF5B7EC3) // Pressed states
private val BlueSurface        = Color(0xFF1A2638) // Tint backgrounds, selected chips
private val BlueInk    = Color(0xFF101313) // Meta text on glow fills
private val Void            = Color(0xFF101313) // Canvas / ink on glow

private val OnBlue          = Color(0xFF101313) // Ink on mint actions

private val Night1          = Color(0xFF151918) // Surface
private val Night2          = Color(0xFF1B201E) // SurfaceRaised
private val Night0          = Color(0xFF202624) // SurfaceSunken (recessed wells)

private val White           = Color(0xFFF1F3F0)
private val Grey1           = Color(0xFFADB6B0) // Secondary text
private val Grey2           = Color(0xFF7E8983) // Tertiary text

private val GoldAmber       = Color(0xFFC99B5B) // Caution, deload
private val AmberSoftFill   = Color(0xFF3A2F1A)
private val CoralAlert      = Color(0xFFD46D68) // Errors, low readiness
private val AlertSoftFill   = Color(0xFF3A1D1B)
private val SkyFocus        = Color(0xFF7192D3) // Info, learning, charts
private val FocusSoftFill   = Color(0xFF1A2638)

private val Edge1           = Color(0xFF2B3330) // BorderSubtle
private val Edge2           = Color(0xFF38423E) // BorderStrong
private val EdgeGlow        = Color(0xFF38423E) // BorderGlow — signal hairline

// ─── Semantic Tokens — ApexColors ────────────────────────────────────────────
// The only names that may appear in screen code.

object ApexColors {

    // Surfaces — the night stack
    val Canvas          = Void          // Screen background — the deepest void
    val Surface         = Night1        // Cards, sheets
    val SurfaceRaised   = Night2        // Nested panels, exercise preview wells
    val SurfaceSunken   = Night0        // Input fills, progress tracks, recessed wells

    // Brand
    val Primary         = MineralBlue  // CTAs, active states, rings, links, energy
    val PrimaryDeep     = BluePressed      // Pressed states, arc strokes on tinted fills
    val PrimaryMist     = BlueSurface      // Selected chips, tint backgrounds
    val OnGlow          = OnBlue          // Text/icons on Primary fills — the inversion rule
    val OnGlowSecondary = BlueInk  // Meta text on Primary fills

    // Semantic states
    // Semantic success
    val Victory         = Color(0xFF64A57D)
    val VictorySoft     = Color(0xFF1B2720)
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
    val Chart1          = MineralBlue  // = Primary
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
// Resolve aliases on access: eagerly reading ApexColors during palette initialization
// creates a cycle and permanently captures uninitialized (black) color values.
// Do NOT use in new or rebuilt screens — reference ApexColors.* directly.

val DarkBackground  get() = ApexColors.Canvas
val DarkRaised      get() = ApexColors.SurfaceRaised
val DarkCardSurface get() = ApexColors.Surface
val AmberAccent     get() = ApexColors.Amber
val BlueAccent      get() = ApexColors.Focus
val RedAccent       get() = ApexColors.Alert
val GreenAccent     get() = ApexColors.Primary
val IndigoAccent    get() = ApexColors.Focus
val PrimaryText     get() = ApexColors.TextPrimary
val SecondaryText   get() = ApexColors.TextSecondary
val MutedText       get() = ApexColors.TextTertiary
val BorderBright    get() = ApexColors.BorderStrong
val BorderSubtle    get() = ApexColors.BorderSubtle
val OrangeAccent    get() = ApexColors.Amber
val BackgroundDark  get() = ApexColors.Canvas
val SurfaceDark     get() = ApexColors.Surface
val CardDark        get() = ApexColors.Surface
val DividerDark     get() = ApexColors.BorderSubtle
val GreenSuccess    get() = ApexColors.Victory
val RedError        get() = ApexColors.Alert
