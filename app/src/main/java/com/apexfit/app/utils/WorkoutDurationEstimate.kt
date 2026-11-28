package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise
import kotlin.math.roundToInt

/** Planned working time and prescribed rests, not measured workout duration. */
data class PlannedWorkoutDuration(val workingSeconds: Double, val restSeconds: Long,
                                  val groupingValid: Boolean) {
    val minutes: Int get() = ((workingSeconds + restSeconds) / 60.0).roundToInt()
}

object WorkoutDurationEstimate {
    fun estimate(exercises: List<PlanExercise>): PlannedWorkoutDuration {
        val valid = exercises.map { it.planSessionId }.distinct().size <= 1 &&
            PermanentWorkoutGroups.validationError(exercises) == null
        val groups = if (valid) PermanentWorkoutGroups.resolve(exercises).associateBy { it.firstIndex } else emptyMap()
        val counts = exercises.map { it.sets.coerceAtLeast(0) }
        val working = exercises.indices.sumOf { i ->
            counts[i] * AlgorithmEngine.estimateSetDurationMinutes(exercises[i].repsMin, exercises[i].repsMax) * 60
        }
        val last = counts.indexOfLast { it > 0 }
        var rest = 0L
        var index = 0
        while (index <= last) {
            val group = groups[index]
            if (group != null) {
                val memberCounts = counts.subList(index, group.endExclusive)
                val rounds = memberCounts.maxOrNull() ?: 0
                // Each nonempty round has members-1 transitions, regardless of uneven counts.
                // The final group set has round rest only when more work follows.
                val transitions = memberCounts.sumOf { it.toLong() } - rounds
                val roundIntervals = (rounds - 1).coerceAtLeast(0) + if (group.endExclusive <= last) 1 else 0
                rest += transitions * group.transitionRestSeconds + roundIntervals.toLong() * group.roundRestSeconds
                index = group.endExclusive
            } else {
                val count = counts[index]
                val intervals = (count - 1).coerceAtLeast(0) + if (count > 0 && index < last) 1 else 0
                rest += intervals.toLong() * exercises[index].restSeconds.coerceAtLeast(0)
                index++
            }
        }
        return PlannedWorkoutDuration(working, rest, valid)
    }
}
