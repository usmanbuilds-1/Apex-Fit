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

    val sets = setsText.toIntOrNull()
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.SETS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    if (sets !in 1..99) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.SETS,
            PlanExerciseDraftIssue.OUT_OF_RANGE
        )
    }
    val minReps = minRepsText.toIntOrNull()
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MIN_REPS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    if (minReps !in 1..999) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MIN_REPS,
            PlanExerciseDraftIssue.OUT_OF_RANGE
        )
    }
    val maxReps = maxRepsText.toIntOrNull()
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MAX_REPS,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    if (maxReps !in 1..999) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MAX_REPS,
            PlanExerciseDraftIssue.OUT_OF_RANGE
        )
    }
    if (minReps > maxReps) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.MIN_REPS,
            PlanExerciseDraftIssue.MIN_REPS_EXCEEDS_MAX
        )
    }

    val restSeconds = restText.toIntOrNull()
        ?: return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.REST,
            PlanExerciseDraftIssue.INVALID_NUMBER
        )
    if (restSeconds !in 0..3600) {
        return PlanExerciseDraftValidation.Invalid(
            PlanExerciseDraftField.REST,
            PlanExerciseDraftIssue.OUT_OF_RANGE
        )
    }

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
