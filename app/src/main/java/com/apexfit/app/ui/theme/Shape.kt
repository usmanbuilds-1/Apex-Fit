package com.apexfit.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─── Shape Scale ──────────────────────────────────────────────────────────────
// Deliberately NOT on the 4dp grid. Irregular values read as designed, not
// auto-generated. Each level has one job — don't mix them.
//
//   extraSmall  6dp  → tags, muscle chips, micro-badges
//   small      10dp  → input fields, toggle pills, inline selectors
//   medium     14dp  → nested/inner cards (exercise preview panel)
//   large      18dp  → primary cards (workout card, nutrition card)
//   extraLarge 20dp  → bottom sheets, dialogs, drawers
//
// The gaps are +4, +4, +4, +2 — close but not mechanical.

val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// Circular — avatar, readiness ring, RPE dots
val ApexShapeFull = CircleShape
