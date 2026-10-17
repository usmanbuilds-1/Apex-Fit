package com.apexfit.app.ui.theme

import androidx.annotation.DrawableRes
import com.apexfit.app.R

// ─── Asset References ─────────────────────────────────────────────────────────
// Central registry for all static drawable resources.
// Screens import from here — never hardcode R.drawable.* in composable files.

object ApexAssets {

    // ── Hero Images ───────────────────────────────────────────────────────────
    // Full-bleed background photo for the Today screen header.
    //
    // HOW TO PROVIDE REAL IMAGES:
    //   1. Export or license a dark-toned gym/training photo (16:9, min 1080px wide).
    //   2. Save as JPEG at quality 85: app/src/main/res/drawable/hero_workout_<type>.jpg
    //   3. Replace the R.drawable.hero_workout_placeholder reference below.
    //
    // OR — load from URL via Coil (requires internet permission):
    //   AsyncImage(model = "https://images.unsplash.com/photo-<id>?w=800&q=80")
    //   See HomeScreen.kt Phase 7 implementation notes.

    @DrawableRes val heroPlaceholder = R.drawable.hero_workout_placeholder

    // ── Exercise Silhouettes ──────────────────────────────────────────────────
    // White vector silhouettes for the exercise preview thumbnail in workout cards.
    // One per movement pattern — not per exercise. Map exercises to patterns below.

    @DrawableRes val exerciseSilhouetteSquat  = R.drawable.ic_exercise_squat
    @DrawableRes val exerciseSilhouetteHinge  = R.drawable.ic_exercise_hinge
    @DrawableRes val exerciseSilhouettePress  = R.drawable.ic_exercise_press
    @DrawableRes val exerciseSilhouettePush   = R.drawable.ic_exercise_push

    // Movement pattern → silhouette mapping
    // Used by the exercise card composable in Phase 7.
    fun silhouetteForMovement(movementPattern: String): Int = when (movementPattern.lowercase()) {
        "squat", "leg press", "lunge", "split squat", "hack squat", "goblet squat" ->
            exerciseSilhouetteSquat
        "hip hinge", "deadlift", "rdl", "good morning", "hip thrust", "glute bridge" ->
            exerciseSilhouetteHinge
        "vertical push", "overhead press", "shoulder press", "military press" ->
            exerciseSilhouettePress
        "horizontal push", "bench press", "push-up", "chest press", "dips" ->
            exerciseSilhouettePush
        else ->
            exerciseSilhouetteSquat // default fallback
    }
}
