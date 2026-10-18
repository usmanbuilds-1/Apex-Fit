package com.apexfit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.apexfit.app.R

// ═══════════════════════════════════════════════════════════════════════════════
// APEX FIT · Design System v2 "Night Signal" · Typography
//
// Three faces, three jobs:
//   Syne        — speaks    (display + headline large — geometric, athletic)
//   Inter       — works     (UI and body — neutral, effortless at every size)
//   JetBrains   — counts    (all data — the signature number face)
//
// FONT FILE STATUS (res/font/):
//   ✓ inter_regular.ttf      (400)
//   ✓ inter_medium.ttf       (500)
//   ✓ inter_semibold.ttf     (600)
//   ✓ inter_bold.ttf         (700)
//   ✗ inter_extrabold.ttf    (800) — MISSING: maps to Bold until added
//   ✓ syne_bold.ttf          (700)
//   ✗ syne_extrabold.ttf     (800) — MISSING: maps to Bold until added
//   ✓ jetbrains_mono_regular.ttf (400)
//   ✗ jetbrains_mono_medium.ttf  (500) — MISSING: maps to Regular
//   ✗ jetbrains_mono_semibold.ttf (600) — MISSING: maps to Regular
//   ✗ jetbrains_mono_bold.ttf (700) — MISSING: maps to Regular
//
// To complete the scale: download the missing weights from fonts.google.com and
// add them to app/src/main/res/font/, then wire them below.
// ═══════════════════════════════════════════════════════════════════════════════

val InterFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold,     FontWeight.Bold),
    Font(R.font.inter_bold,     FontWeight.ExtraBold), // fallback until inter_extrabold.ttf added
    Font(R.font.inter_bold,     FontWeight.Black)
)

val SyneFamily = FontFamily(
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_bold, FontWeight.ExtraBold) // fallback until syne_extrabold.ttf added
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_regular, FontWeight.Medium),   // fallback
    Font(R.font.jetbrains_mono_regular, FontWeight.SemiBold), // fallback
    Font(R.font.jetbrains_mono_regular, FontWeight.Bold)      // fallback
)

// ─── M3 Typography Scale ──────────────────────────────────────────────────────
// Line heights: 1.2 display · 1.45 body · 1.0 data (per design system §4.3)
// labelMedium: always uppercase + 0.8sp tracking — the telemetry eyebrow convention

val Typography = Typography(
    // ── Display (Syne) ─────────────────────────────────────────────────────
    displayLarge = TextStyle(          // "Today, we train." hero — 40sp
        fontFamily    = SyneFamily,
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 40.sp,
        lineHeight    = 48.sp,         // 1.2
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(         // Ritual card headlines, onboarding — 32sp
        fontFamily    = SyneFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        lineHeight    = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(          // Unused — reserved
        fontFamily    = SyneFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 26.sp,
        lineHeight    = 31.sp
    ),

    // ── Headline (Syne for L, Inter for M/S) ───────────────────────────────
    headlineLarge = TextStyle(         // Screen headers, sheet titles — 24sp Syne
        fontFamily    = SyneFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 24.sp,
        lineHeight    = 30.sp
    ),
    headlineMedium = TextStyle(        // Card titles — 20sp Inter ExtraBold
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 20.sp,
        lineHeight    = 26.sp
    ),
    headlineSmall = TextStyle(         // Secondary card headings — 18sp
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 18.sp,
        lineHeight    = 24.sp
    ),

    // ── Title (Inter) ──────────────────────────────────────────────────────
    titleLarge = TextStyle(            // Section titles, exercise names — 17sp
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 17.sp,
        lineHeight    = 24.sp
    ),
    titleMedium = TextStyle(           // Row titles, input labels — 15sp SemiBold
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 15.sp,
        lineHeight    = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 13.sp,
        lineHeight    = 19.sp
    ),

    // ── Body (Inter Regular) ───────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 23.sp
    ),
    bodyMedium = TextStyle(            // Body copy, descriptions — 15sp
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        lineHeight    = 22.sp
    ),
    bodySmall = TextStyle(             // Meta, helper text — 13sp
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 13.sp,
        lineHeight    = 19.sp         // 1.45
    ),

    // ── Labels (Inter) ─────────────────────────────────────────────────────
    labelLarge = TextStyle(            // Buttons, primary actions — 15sp ExtraBold
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 15.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(           // Eyebrow labels, chip text — 12sp CAPS +0.8
        // NOTE: always render as uppercase at the use site (.uppercase() modifier
        // or text.uppercase()). letterSpacing is baked in — this style IS the
        // telemetry eyebrow convention. Do not use for anything that is not an
        // eyebrow label or chip.
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.4.sp
    )
)

// ─── Data Styles (JetBrains Mono) ────────────────────────────────────────────
// All numerics — stats, scores, weights, durations — use these styles.
// Rule §2.6: "Numbers are mono." No exceptions.
// Line height 1.0 — data aligns tight, like telemetry readouts.

object ApexTextStyles {
    val dataXL = TextStyle(            // Hero numbers: readiness score, remaining kcal — 44sp
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 44.sp,
        lineHeight    = 44.sp,         // 1.0
        letterSpacing = (-1.0).sp
    )
    val dataLarge = TextStyle(         // Big stats, summary volumes — 36sp
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 36.sp,
        lineHeight    = 36.sp,
        letterSpacing = (-0.5).sp
    )
    val dataMedium = TextStyle(        // Card stats, weights, countdown — 22sp
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 22.sp,
        lineHeight    = 22.sp,
        letterSpacing = (-0.3).sp
    )
    val dataSmall = TextStyle(         // Inline numbers, meta data — 13sp
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 13.sp,
        lineHeight    = 13.sp,
        letterSpacing = 0.sp
    )
}
