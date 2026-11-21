package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise

/** First-exposure load seed. The active set remains user-editable for calibration. */
data class StartingLoadCalibration(
    val initialLoadKg: Double,
    val guidance: String
)

object StartingLoadCalibrationBuilder {
    fun forFirstExposure(exercise: PlanExercise, exerciseCategory: String): StartingLoadCalibration {
        val prescribedLoad = exercise.weight.takeIf { it.isFinite() && it > 0.0 }
        val initialLoadKg = prescribedLoad ?: 0.0
        val effortTarget = "Target: ${exercise.repsMin}–${exercise.repsMax} reps · about 2–3 RIR."
        val guidance = when {
            exerciseCategory.equals("Bodyweight", ignoreCase = true) ->
                "Calibration · bodyweight. Record any added load separately. $effortTarget"
            prescribedLoad != null ->
                "Calibration · adjust the plan load as needed. $effortTarget"
            else ->
                "Calibration · enter a comfortable starting load. $effortTarget"
        }
        return StartingLoadCalibration(initialLoadKg, guidance)
    }
}
