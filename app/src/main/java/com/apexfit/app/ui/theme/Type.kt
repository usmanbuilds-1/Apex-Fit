package com.apexfit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apexfit.app.R

// ─── Font Families ────────────────────────────────────────────────────────────

val InterFamily = FontFamily(
    Font(R.font.inter_regular,  FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold,     FontWeight.Bold)
)

// Display use only: hero headlines ("Today, we train.")
val SyneFamily = FontFamily(
    Font(R.font.syne_bold, FontWeight.Bold)
)

// Numeric data: scores, weights, macros ("82", "76.4 kg", "1,542 / 2,400")
val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

// ─── Type Scale ───────────────────────────────────────────────────────────────
// 5 levels max, each with a single job. Syne for display, Inter for everything
// else, JetBrains Mono surfaced via ApexTextStyles below.

val Typography = Typography(
    // displayLarge — hero headline: "Today, we train."
    displayLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    // displayMedium — large section headings (Inter, not Syne — Syne is for displayLarge only)
    displayMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    // displaySmall — unused; reserved
    displaySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 24.sp,
        lineHeight = 30.sp
    ),
    // headlineLarge — workout name: "Lower Body A"
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 22.sp,
        lineHeight = 28.sp
    ),
    // headlineMedium — card section headings
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 20.sp,
        lineHeight = 26.sp
    ),
    // headlineSmall — secondary card headings
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        lineHeight = 24.sp
    ),
    // titleLarge — exercise names: "Bodyweight squat"
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        lineHeight = 22.sp
    ),
    // titleMedium — card header labels: "Today's nutrition"
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    // titleSmall — sub-labels, inline metadata
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 18.sp
    ),
    // bodyLarge — primary body copy
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    // bodyMedium — descriptions: "Your recovery, training load..."
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 21.sp
    ),
    // bodySmall — set/rep meta: "3 × 12–15 reps  •  0 kg"
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 18.sp
    ),
    // labelLarge — button text: "Start workout"
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // labelMedium — meta text, section headers
    // Note: do NOT apply text-transform or letter-spacing at this level.
    // Uppercase + tracking is a per-use-site decision, not a system default.
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp
    ),
    // labelSmall — micro meta, captions
    // Same rule: no baked-in tracking. Apply at the use site only when
    // uppercase + wide tracking genuinely serves the content.
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 10.sp,
        lineHeight = 14.sp
    )
)

// ─── Data Text Styles ─────────────────────────────────────────────────────────
// JetBrains Mono for numeric readouts. Use directly where needed.
// Not in the M3 Typography slot — M3 has no "data" role.

object ApexTextStyles {
    // Large score / ring number: "82"
    val dataLarge = TextStyle(
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 36.sp,
        lineHeight    = 40.sp,
        letterSpacing = (-0.5).sp
    )
    // Weight / kcal readout: "76.4 kg", "1,542"
    val dataMedium = TextStyle(
        fontFamily    = JetBrainsMonoFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 22.sp,
        lineHeight    = 28.sp
    )
    // NOTE: there is intentionally no dataSmall.
    // Inline numbers like "108 / 145 g" or "~ 48 min" use bodySmall (Inter).
    // Monospace is a functional choice for primary readouts where fixed-width
    // prevents layout shift as live values update — not a decoration.
}
