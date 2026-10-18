package com.apexfit.app.ui.theme

import androidx.compose.ui.unit.dp

// ─── Spacing · v2 Night Signal ────────────────────────────────────────────────
// 4dp grid — unchanged from v1 (Ease Doctrine, rule §2.3 is absolute).
// If a value is not in this table, it does not ship.

object ApexSpacing {
    val xxs  =  2.dp
    val xs   =  4.dp
    val sm   =  8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 24.dp
    val xxl  = 32.dp
    val xxxl = 48.dp

    // Composite tokens
    val screenPadding = 20.dp  // horizontal margin for all screen content
    val cardPadding   = 16.dp  // uniform internal padding for all cards
    val cardGap       = 12.dp  // vertical gap between stacked cards
    val sectionGap    = 24.dp  // gap between named screen sections
    val ritualPad     = 40.dp  // peak-moment screens — generous air (RitualCard, PRBadge, plan reveal)
    val bottomNavHeight = 64.dp
}
