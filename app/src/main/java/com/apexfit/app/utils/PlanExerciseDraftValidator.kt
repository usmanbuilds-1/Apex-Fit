package com.apexfit.app.utils

internal enum class PlanExerciseDraftField {
    NAME,
    SETS,
    MIN_REPS,
    MAX_REPS,
    REST,
    WEIGHT
}

internal enum class PlanExerciseDraftIssue {
    REQUIRED,
    INVALID_NUMBER,
    MIN_REPS_EXCEEDS_MAX,
    OUT_OF_RANGE
}

internal sealed interface PlanExerciseDraftValidation {
    data class Valid(
        val name: String,
        val sets: Int,
        val minReps: Int,
        val maxReps: Int,
        val weightKg: Double,
        val restSeconds: Int
    ) : PlanExerciseDraftValidation

    data class Invalid(
        val field: PlanExerciseDraftField,
        val issue: PlanExerciseDraftIssue
    ) : PlanExerciseDraftValidation
}

/** Validates the exercise editor without silently substituting defaults for bad user input. */
internal fun validatePlanExerciseDraft(
    name: String,
    setsText: String,
    minRepsText: String,
    maxRepsText: String,
    weightText: String,
    weightUnit: String,
    restText: String
): PlanExerciseDraftValidation {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.NAME,
            PlanExerciseDraftIssue.REQUIRED
        )
    }

    val sets = setsText.toIntOrNull()?.takeIf { it > 0 }
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.SETS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    val minReps = minRepsText.toIntOrNull()?.takeIf { it > 0 }
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MIN_REPS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    val maxReps = maxRepsText.toIntOrNull()?.takeIf { it > 0 }
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MAX_REPS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    if (minReps > maxReps) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MIN_REPS,
            PlanExerciseDraftIssue.MIN_REPS_EXCEEDS_MAX
        )
    }

    val restSeconds = restText.toIntOrNull()?.takeIf { it >= 0 }
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.REST,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )

    val isPounds = weightUnit.lowercase() in listOf("lb", "lbs")
    val maxWeight = if (isPounds) 660.0 else 300.0
    val enteredWeight = if (weightText.isBlank()) 0.0 else {
        weightText.replace(',', '.').toDoubleOrNull()
            ?: return PlanExerciseDraftValidation.Invalid(
                PlanExerciseDraftField.WEIGHT,
                PlanExerciseDraftIssue.INVALID_NUMBER
            )
    }
    if (!enteredWeight.isFinite() || enteredWeight !in 0.0..maxWeight) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.WEIGHT,
            PlanExerciseDraftIssue.OUT_OF_RANGE
        )
    }

    val weightKg = if (isPounds) enteredWeight / AppConstants.KG_TO_LBS else enteredWeight
    return PlanExerciseDraftValidation.Valid(
        name = cleanName,
        sets = sets,
        minReps = minReps,
        maxReps = maxReps,
        weightKg = weightKg,
        restSeconds = restSeconds
    )
}
