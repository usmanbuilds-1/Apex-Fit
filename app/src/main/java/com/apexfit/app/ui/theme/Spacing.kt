package com.apexfit.app.ui.theme

import androidx.compose.ui.unit.dp

// ─── Spacing Scale ────────────────────────────────────────────────────────────
// 4dp base grid. Every layout measurement in the app must snap to one of these.
// Never use arbitrary dp values in screen code — pick the nearest token.

object ApexSpacing {

    // ── Atomic steps ──────────────────────────────────────────────────────────
    val xxs  =  2.dp   // micro gaps inside tightly packed elements
    val xs   =  4.dp   // icon-to-label gaps, bullet spacers
    val sm   =  8.dp   // between list items, inline element gaps
    val md   = 12.dp   // between groups within a card
    val lg   = 16.dp   // card internal padding, between rows
    val xl   = 24.dp   // between major sections on a screen
    val xxl  = 32.dp   // hero area padding, large section gaps
    val xxxl = 48.dp   // screen top/bottom breathing room

    // ── Semantic layout ───────────────────────────────────────────────────────
    val screenPadding = 20.dp  // horizontal content inset from screen edge
    val cardPadding   = 16.dp  // uniform padding inside all primary cards
    val cardGap       = 12.dp  // vertical gap between stacked cards
    val sectionGap    = 24.dp  // vertical gap between named screen sections
    val bottomNavHeight = 64.dp // bottom navigation bar height (+ safe area below)
}
