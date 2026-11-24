package com.apexfit.app.domain.importer

enum class ImportRepTarget { FIXED, RANGE, AMRAP, FAILURE }

enum class ImportWeightUnit(val code: String) {
    KG("kg"),
    LB("lb")
}

enum class MatchConfidence { EXACT, HIGH, MEDIUM, UNRESOLVED }

enum class ImportWarningSeverity { INFO, WARNING, ERROR }

data class ImportWarning(
    val code: String,
    val message: String,
    val severity: ImportWarningSeverity = ImportWarningSeverity.WARNING,
    val lineIndex: Int? = null
) {
    companion object {
        const val INPUT_TOO_LARGE = "input_too_large"
        const val EMPTY_INPUT = "empty_input"
        const val NO_DAYS_FOUND = "no_days_found"
        const val NO_EXERCISES_FOUND = "no_exercises_found"
        const val LINE_NOT_PARSED = "line_not_parsed"
        const val SETS_MISSING = "sets_missing"
        const val EXERCISE_UNRESOLVED = "exercise_unresolved"
        const val EXERCISE_NEEDS_REVIEW = "exercise_needs_review"
        const val SUPERSET_FLATTENED = "superset_flattened"
        const val PRESCRIPTION_LEFTOVER = "prescription_leftover"
    }
}

data class ParsedImportWeight(
    val value: Double,
    val unit: ImportWeightUnit,
    val rawText: String
)

data class ExerciseLibraryEntry(
    val id: String,
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val aliases: List<String> = emptyList()
)

data class ExerciseCandidate(
    val entry: ExerciseLibraryEntry,
    val confidence: MatchConfidence,
    val score: Int,
    val flagged: Boolean
)

data class ParsedImportedExercise(
    val rawName: String,
    val rawPrescription: String?,
    val matchedEntryId: String?,
    val matchedName: String?,
    val matchConfidence: MatchConfidence,
    val matchFlagged: Boolean,
    val candidates: List<ExerciseCandidate>,
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repTarget: ImportRepTarget,
    val repNote: String?,
    val weight: ParsedImportWeight?,
    val restSeconds: Int?,
    val rpe: Double?,
    val rir: Int?,
    val supersetTag: String?,
    val notes: List<String>,
    val warnings: List<ImportWarning>,
    val lineIndex: Int?
) {
    val needsReview: Boolean
        get() = matchConfidence == MatchConfidence.MEDIUM ||
            matchConfidence == MatchConfidence.UNRESOLVED ||
            matchFlagged ||
            sets == null ||
            repsMin == null ||
            repsMax == null
}

data class ParsedImportedDay(
    val label: String,
    val dayOfWeek: String?,
    val focus: String?,
    val exercises: List<ParsedImportedExercise>,
    val lineIndex: Int?
)

data class WorkoutImportProvenance(
    val originalText: String,
    val normalizedText: String,
    val importedAtEpochMs: Long
)

data class ImportedScheduleSlot(val weekday: String, val week: String, val workout: String) {
    val isRest: Boolean get() = workout.trim().lowercase() in setOf("rest", "rest day", "off", "off day", "-")
}

data class ParsedImportedPlan(
    val name: String,
    val nameInferred: Boolean,
    val days: List<ParsedImportedDay>,
    val warnings: List<ImportWarning>,
    val provenance: WorkoutImportProvenance,
    val schedule: List<ImportedScheduleSlot> = emptyList()
)

data class WorkoutImportParseResult(
    val plan: ParsedImportedPlan?,
    val fatalError: ImportWarning? = null
) {
    val isSuccess: Boolean get() = plan != null && fatalError == null
}
