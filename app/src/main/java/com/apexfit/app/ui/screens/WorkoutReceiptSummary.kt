package com.apexfit.app.ui.screens

import com.apexfit.app.ui.models.UiExerciseSet

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
            set.exerciseId.takeIf(String::isNotBlank)?.let { "id:$it" }
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
                actualRestSeconds = exerciseSets.sumOf { it.restTaken.coerceAtLeast(0).toLong() }
            )
        }

internal fun totalActualRestSeconds(sets: List<UiExerciseSet>): Long =
    sets.asSequence()
        .filter { it.completed }
        .sumOf { it.restTaken.coerceAtLeast(0).toLong() }

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
