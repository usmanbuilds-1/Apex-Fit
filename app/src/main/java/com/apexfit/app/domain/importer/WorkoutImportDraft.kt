package com.apexfit.app.domain.importer

import java.util.UUID

data class EditableImportedExercise(
    val key: String = UUID.randomUUID().toString(),
    val sourceName: String,
    val selectedExerciseId: String?,
    val selectedExerciseName: String?,
    val muscleGroup: String?,
    val matchConfidence: MatchConfidence,
    val matchConfirmed: Boolean,
    val candidates: List<ExerciseCandidate>,
    val setsText: String,
    val repsMinText: String,
    val repsMaxText: String,
    val weightText: String,
    val weightUnit: ImportWeightUnit,
    val restSecondsText: String,
    val repTarget: ImportRepTarget,
    val notes: String,
    val sourcePrescription: String?
) {
    val sets: Int? get() = setsText.toIntOrNull()?.takeIf { it in 1..99 }
    val repsMin: Int? get() = repsMinText.toIntOrNull()?.takeIf { it in 1..999 }
    val repsMax: Int? get() = repsMaxText.toIntOrNull()?.takeIf { it in 1..999 }
    val weight: Double? get() = weightText.toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
    val restSeconds: Int? get() = restSecondsText.toIntOrNull()?.takeIf { it in 0..3600 }

    val needsReview: Boolean
        get() = selectedExerciseId == null ||
            !matchConfirmed ||
            sets == null ||
            repsMin == null ||
            repsMax == null ||
            (repsMin != null && repsMax != null && repsMin!! > repsMax!!)
}

data class EditableImportedDay(
    val key: String = UUID.randomUUID().toString(),
    val label: String,
    val dayOfWeek: String?,
    val focus: String?,
    val exercises: List<EditableImportedExercise>
) {
    val needsReview: Boolean get() = dayOfWeek == null || exercises.any { it.needsReview }
}

data class EditableImportedPlan(
    val name: String,
    val days: List<EditableImportedDay>,
    val originalText: String,
    val makeActive: Boolean = true,
    val schedule: List<ImportedScheduleSlot> = emptyList(),
    val selectedScheduleWeek: String? = null
) {
    // Materialize exactly one explicitly selected source week. Repeated templates
    // become separate weekday sessions; fresh keys avoid Compose identity collisions.
    fun selectFixedWeek(week: String): EditableImportedPlan? {
        if (selectedScheduleWeek != null) return null
        val slots = schedule.filter { it.week.equals(week, true) }
        if (slots.isEmpty() || slots.map { it.weekday }.distinct().size != slots.size) return null
        val training = slots.filterNot { it.isRest }
        if (training.isEmpty()) return null
        val resolved = training.map { slot ->
            val template = days.singleOrNull { it.label.equals(slot.workout, true) } ?: return null
            template.copy(key = UUID.randomUUID().toString(), dayOfWeek = slot.weekday,
                exercises = template.exercises.map { it.copy(key = UUID.randomUUID().toString()) })
        }
        // Never silently discard an imported workout template.
        if (days.any { day -> training.none { it.workout.equals(day.label, true) } }) return null
        return copy(days = resolved, selectedScheduleWeek = week)
    }

    fun validationErrors(): List<String> = buildList {
        if (schedule.isNotEmpty() && selectedScheduleWeek == null) add("Choose which fixed week to import. Automatic multi-week rotation is not supported yet.")
        if (name.isBlank()) add("Give the plan a name.")
        if (days.isEmpty()) add("Add at least one training day.")
        if (days.any { it.exercises.isEmpty() }) add("Every training day needs at least one exercise.")
        if (days.any { it.dayOfWeek == null }) add("Choose a weekday for every training day.")
        val selectedDays = days.mapNotNull { it.dayOfWeek }
        if (selectedDays.size != selectedDays.distinct().size) add("Each imported training day needs a different weekday.")
        if (days.flatMap { it.exercises }.any { it.selectedExerciseId == null }) add("Resolve every exercise before saving.")
        if (days.flatMap { it.exercises }.any { !it.matchConfirmed }) add("Confirm every suggested exercise match before saving.")
        if (days.flatMap { it.exercises }.any { it.sets == null }) add("Enter valid sets for every exercise.")
        if (days.flatMap { it.exercises }.any { it.repsMin == null || it.repsMax == null }) add("Enter a numeric rep target for every exercise.")
        if (days.flatMap { it.exercises }.any { ex -> ex.repsMin != null && ex.repsMax != null && ex.repsMin!! > ex.repsMax!! }) {
            add("Minimum reps cannot be higher than maximum reps.")
        }
        if (days.flatMap { it.exercises }.any { it.weightText.isNotBlank() && it.weight == null }) add("Fix invalid weight values.")
        if (days.flatMap { it.exercises }.any { it.restSecondsText.isNotBlank() && it.restSeconds == null }) add("Rest must be between 0 and 3600 seconds.")
    }.distinct()
}

fun ParsedImportedPlan.toEditablePlan(libraryById: Map<String, ExerciseLibraryEntry>): EditableImportedPlan = EditableImportedPlan(
    name = name,
    days = days.map { day ->
        EditableImportedDay(
            label = day.label,
            dayOfWeek = day.dayOfWeek,
            focus = day.focus,
            exercises = day.exercises.map { exercise ->
                EditableImportedExercise(
                    sourceName = exercise.rawName,
                    selectedExerciseId = exercise.matchedEntryId,
                    selectedExerciseName = exercise.matchedName,
                    muscleGroup = exercise.matchedEntryId?.let { libraryById[it]?.primaryMuscle },
                    matchConfidence = exercise.matchConfidence,
                    matchConfirmed = !exercise.matchFlagged && (exercise.matchConfidence == MatchConfidence.EXACT || exercise.matchConfidence == MatchConfidence.HIGH),
                    candidates = exercise.candidates,
                    setsText = exercise.sets?.toString().orEmpty(),
                    repsMinText = exercise.repsMin?.toString().orEmpty(),
                    repsMaxText = exercise.repsMax?.toString().orEmpty(),
                    weightText = exercise.weight?.value?.let(::formatEditableNumber).orEmpty(),
                    weightUnit = exercise.weight?.unit ?: ImportWeightUnit.KG,
                    restSecondsText = exercise.restSeconds?.toString().orEmpty(),
                    repTarget = exercise.repTarget,
                    notes = exercise.notes.joinToString(" · "),
                    sourcePrescription = exercise.rawPrescription
                )
            }
        )
    },
    originalText = provenance.originalText,
    makeActive = true,
    schedule = schedule
)

private fun formatEditableNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
