package com.apexfit.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─── Shape Scale ──────────────────────────────────────────────────────────────
// Minimal, intentional radius system. Five levels + Full.
// Never use arbitrary radius values in screen code — pick the nearest level.

val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // tags, chips, small badges
    small      = RoundedCornerShape(8.dp),   // input fields, inline pills
    medium     = RoundedCornerShape(12.dp),  // exercise preview cards, inner panels
    large      = RoundedCornerShape(16.dp),  // primary cards (workout, nutrition)
    extraLarge = RoundedCornerShape(24.dp)   // bottom sheets, dialogs
)

// Circular — avatars, readiness ring, RPE selector dots
val ApexShapeFull = CircleShape
