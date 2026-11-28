package com.apexfit.app.ui.screens

import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.utils.AdvancedSets

internal fun receiptWorkingReps(sets: List<UiExerciseSet>): Long =
    sets.asSequence().filter { it.completed && !it.isWarmup && it.reps > 0 &&
        it.weight.isFinite() && it.weight >= 0 }.sumOf {
        it.reps.toLong() + (AdvancedSets.decode(it.weight, it.advancedDetails)
            ?.continuations?.sumOf { segment -> segment.reps.toLong() } ?: 0L)
    }

private fun receiptRest(set: UiExerciseSet): Long =
    set.restTaken.coerceAtLeast(0).toLong() +
        (AdvancedSets.decode(set.weight, set.advancedDetails)
            ?.continuations?.sumOf { it.restSeconds.toLong() } ?: 0L)

internal data class ExerciseReceiptSummary(
    val exerciseName: String,
    val workingSets: List<UiExerciseSet>,
    val warmupSets: List<UiExerciseSet>,
    val actualRestSeconds: Long
)

/** Groups the completed sets in the order the exercises first appeared in the session. */
internal fun summarizeCompletedExercises(sets: List<UiExerciseSet>): List<ExerciseReceiptSummary> =
    sets.filter { it.completed }
        .groupBy { set ->
            set.occurrenceIndex?.let { "occurrence:$it" }
                ?: set.exerciseId.takeIf(String::isNotBlank)?.let { "id:$it" }
                ?: "name:${set.exerciseName.trim().lowercase()}"
        }
        .values
        .map { exerciseSets ->
            val first = exerciseSets.first()
            ExerciseReceiptSummary(
                exerciseName = first.exerciseName.ifBlank { "Exercise" },
                workingSets = exerciseSets.filterNot { it.isWarmup },
                warmupSets = exerciseSets.filter { it.isWarmup },
                // Rest is stored on the set that was completed before that rest began.
                actualRestSeconds = exerciseSets.sumOf { receiptRest(it) }
            )
        }

internal fun totalActualRestSeconds(sets: List<UiExerciseSet>): Long =
    sets.asSequence()
        .filter { it.completed }
        .sumOf { receiptRest(it) }

internal fun formatReceiptDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }
}
