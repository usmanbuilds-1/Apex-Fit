package com.apexfit.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// APEX FIT · Design System v2 "Night Signal" · Motion Token System
//
// Motion is where premium is *felt*. These tokens enforce the Five Motion Laws:
//   1. Acknowledge everything    ≤100ms press state on every tap
//   2. One peak per journey      celebrations never stack
//   3. Every end is a summary    flows close on a ritual card, never a dead stop
//   4. Motion carries data       rings fill to value, numbers count up
//   5. Respect rest              reduced-motion collapses all to quick crossfades
//
// If it moves, it moves on a token. No ad-hoc durations or easing curves.
// ═══════════════════════════════════════════════════════════════════════════════

object ApexMotion {

    // ── Duration tokens (ms) ──────────────────────────────────────────────────
    const val instant =  100  // Press states, toggles, haptic sync
    const val quick   =  180  // Chip select, tab indicator, stepper tick
    const val flow    =  280  // Card reveals, sheet slide, page transitions
    const val moment  =  420  // Ring fills, number count-ups, bar settles
    const val ritual  =  600  // Ritual card reveal, PR badge, plan reveal
    const val hold    = 1200  // Celebration linger before auto-dismiss

    // ── Easing tokens ─────────────────────────────────────────────────────────
    // Settle — entrances: arrive and come to rest
    val Settle  = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    // Eager — exits and dismissals: leave promptly, no lingering
    val Eager   = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
    // Coach — guidance moves: timer bars, progress fills, smooth data
    val Coach   = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    // Victory — peaks only: badge pop, count-up finish, confetti
    // Spring: stiffness 380, damping ratio ~0.5 (bouncy, deliberate)
    // WARNING: never use Victory on routine interactions — it stops feeling like a win.
    val Victory = spring<Float>(
        stiffness    = 380f,
        dampingRatio = Spring.DampingRatioMediumBouncy  // 0.5 — controlled overshoot
    )

    // ── Haptic tokens (descriptive — call HapticFeedbackType at use site) ─────
    // tick    — light   : selector taps, steppers, toggles
    // thud    — medium  : set logged, workout finished
    // fanfare — double  : PR moment, plan saved
    //
    // Every haptic pairs with a visible change at `instant` — never haptic alone.

    // ── Stagger rule ──────────────────────────────────────────────────────────
    const val staggerStep = 40  // ms between list children; cap at 6 visible items

    // ── Named AnimationSpec builders ──────────────────────────────────────────
    // Use these in animateXAsState / updateTransition rather than raw tween/spring.

    fun <T> quickTween(): TweenSpec<T>   = tween(quick,  easing = Eager)
    fun <T> flowSettle(): TweenSpec<T>   = tween(flow,   easing = Settle)
    fun <T> momentCoach(): TweenSpec<T>  = tween(moment, easing = Coach)
    fun <T> ritualSettle(): TweenSpec<T> = tween(ritual, easing = Settle)

    // Sheet enter/exit standard specs
    val sheetEnterDuration  = flow
    val sheetExitDuration   = quick

    // Reduced-motion override — collapse to quick crossfades
    // Usage: if (isReducedMotion) ApexMotion.instant else ApexMotion.moment
}
