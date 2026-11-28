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
                    (e.groupTransitionRest ?: 0) !in 1..3600 || (e.groupRoundRest ?: 0) !in 1..3600 || e.sets <= 0
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

    /** Applies explicit membership to the same ordered draft snapshot, or refuses a stale/conflicting edit. */
    fun configure(exercises: List<PlanExercise>, sessionId: Long, expectedIds: List<Long>,
                  key: String, memberIds: List<Long>, transition: Int, round: Int): List<PlanExercise>? {
        val session = exercises.filter { it.planSessionId == sessionId }
        if (session.map { it.id } != expectedIds || expectedIds.distinct().size != expectedIds.size ||
            memberIds.size < 2 || memberIds.distinct().size != memberIds.size ||
            !key.matches(Regex("[A-Za-z0-9_-]{1,64}")) || transition !in 1..3600 || round !in 1..3600) return null
        val start = expectedIds.indexOf(memberIds.first())
        if (start < 0 || start + memberIds.size > expectedIds.size ||
            expectedIds.subList(start, start + memberIds.size) != memberIds) return null
        val selected = session.filter { it.id in memberIds }
        if (selected.any { it.groupKey != null && it.groupKey != key }) return null
        val changed = exercises.map { ex ->
            if (ex.planSessionId != sessionId) ex
            else if (ex.id in memberIds) ex.copy(groupKey = key, groupTransitionRest = transition, groupRoundRest = round)
            else if (ex.groupKey == key) ex.copy(groupKey = null, groupTransitionRest = null, groupRoundRest = null)
            else ex
        }
        return changed.takeIf { validationError(it) == null }
    }

    fun remove(exercises: List<PlanExercise>, sessionId: Long, key: String): List<PlanExercise> =
        exercises.map { ex ->
            if (ex.planSessionId == sessionId && ex.groupKey == key)
                ex.copy(groupKey = null, groupTransitionRest = null, groupRoundRest = null) else ex
        }

    /** Deleting from a two-member group dissolves the remaining singleton. Larger groups retain their order. */
    fun deleteOccurrence(exercises: List<PlanExercise>, exerciseId: Long): List<PlanExercise> {
        val deleted = exercises.singleOrNull { it.id == exerciseId } ?: return exercises
        val remaining = exercises.filterNot { it.id == exerciseId }
        val key = deleted.groupKey ?: return remaining
        return if (remaining.count { it.planSessionId == deleted.planSessionId && it.groupKey == key } < 2)
            remove(remaining, deleted.planSessionId, key) else remaining
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
