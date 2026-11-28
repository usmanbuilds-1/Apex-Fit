package com.apexfit.app.utils

import com.apexfit.app.data.PlanExercise

/** Group identity belongs to exercise occurrences within a plan session, not catalog IDs.
 * Ordered contiguous runs keep sequential exercises between groups intact.
 */
data class PermanentWorkoutGroup(val key: String, val firstIndex: Int, val memberCount: Int,
                                 val transitionRestSeconds: Int, val roundRestSeconds: Int) {
    val endExclusive: Int get() = firstIndex + memberCount
}

object PermanentWorkoutGroups {
    fun validationError(exercises: List<PlanExercise>): String? {
        for (session in exercises.groupBy { it.planSessionId }.values) {
            val invalid = session.firstOrNull { e ->
                if (e.groupKey == null) e.groupTransitionRest != null || e.groupRoundRest != null
                else !e.groupKey.matches(Regex("[A-Za-z0-9_-]{1,64}")) ||
                    e.groupTransitionRest !in 1..3600 || e.groupRoundRest !in 1..3600 || e.sets <= 0
            }
            if (invalid != null) return "Invalid exercise group configuration: ${invalid.name}"
            val grouped = session.withIndex().filter { it.value.groupKey != null }.groupBy { it.value.groupKey }
            for ((key, members) in grouped) {
                if (members.size < 2) return "Group $key must contain at least two exercises."
                if (members.last().index - members.first().index + 1 != members.size)
                    return "Group $key must contain consecutive exercise occurrences."
                if (members.map { it.value.groupTransitionRest to it.value.groupRoundRest }.distinct().size != 1)
                    return "Group $key must use consistent transition and round rest."
            }
        }
        return null
    }

    /** Caller supplies a single session in its persisted execution order. */
    fun resolve(exercises: List<PlanExercise>): List<PermanentWorkoutGroup> {
        require(exercises.map { it.planSessionId }.distinct().size <= 1)
        require(validationError(exercises) == null) { validationError(exercises).orEmpty() }
        return exercises.withIndex().filter { it.value.groupKey != null }
            .groupBy { requireNotNull(it.value.groupKey) }.map { (key, members) ->
                val first = members.first()
                PermanentWorkoutGroup(key, first.index, members.size,
                    requireNotNull(first.value.groupTransitionRest), requireNotNull(first.value.groupRoundRest))
            }.sortedBy { it.firstIndex }
    }
}
