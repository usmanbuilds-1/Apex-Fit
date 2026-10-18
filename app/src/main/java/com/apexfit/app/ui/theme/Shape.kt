package com.apexfit.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─── Shape Scale · v2 Night Signal ───────────────────────────────────────────
// Matches the approved Home mockup: soft-rect tiles, never playful pills.
// Values are deliberately off the pure 4dp grid — 8/12/14/18/24 — so the
// rhythm reads as designed, not generated.
//
//   XS  8dp   — tags, small chips, RPE dots
//   S  12dp   — buttons, inputs (the CTA silhouette in the mockup)
//   M  14dp   — inner cards, nested panels, sunken wells
//   L  18dp   — primary cards (workout card, nutrition card, session rows)
//   XL 24dp   — sheets, dialogs, bottom drawers
//
// shapeFull (pill) is reserved for the rest-timer pill only — one small
// personality moment, never used as a default card radius.

val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val ApexShapeFull = CircleShape               // avatar, readiness ring center
val ApexShapePill = RoundedCornerShape(999.dp) // rest-timer pill — reserved
