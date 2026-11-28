package com.apexfit.app.utils

import com.apexfit.app.data.ActiveCircuit
import com.apexfit.app.data.ActiveSession

/** Session-local execution only. Does not mutate the plan, exercise identity or actual saved sets. */
internal object CircuitExecution {
    fun valid(session: ActiveSession): Boolean {
        val group = session.circuit ?: return true
        return group.memberCount in 2..session.exercises.size &&
            group.transitionRestSeconds in 1..3600 && group.roundRestSeconds in 1..3600 &&
            session.cursorIndex != null && session.cursorIndex in session.exercises.indices &&
            session.skippedOccurrenceIndices.orEmpty().all { it in session.exercises.indices }
    }

    fun canConfigure(session: ActiveSession): Boolean =
        session.exercises.size >= 2 && WorkoutResumeState.cursor(session) == 0 &&
            session.restCheckpoint == null && session.skippedOccurrenceIndices.orEmpty().isEmpty() &&
            session.exercises.none { exercise -> exercise.sets.any { it.completed } }

    fun configure(session: ActiveSession, group: ActiveCircuit?): ActiveSession? {
        if (!canConfigure(session)) return null
        val candidate = session.copy(circuit = group, cursorIndex = 0)
        return candidate.takeIf(::valid)?.takeIf {
            group == null || it.exercises.take(group.memberCount).all { ex ->
                ex.sets.any { set -> !set.isWarmup && !set.completed }
            }
        }
    }

    fun contains(session: ActiveSession, index: Int): Boolean =
        session.circuit?.let { index in 0 until it.memberCount } == true

    fun pending(session: ActiveSession, index: Int): Boolean =
        index !in session.skippedOccurrenceIndices.orEmpty() &&
            session.exercises.getOrNull(index)?.sets?.any { !it.completed && !it.isWarmup } == true

    /** The next still-pending occurrence in circular order; an exhausted member is bypassed. */
    fun nextMember(session: ActiveSession, index: Int): Int? {
        val group = requireNotNull(session.circuit)
        require(index in 0 until group.memberCount)
        return (1..group.memberCount).map { (index + it) % group.memberCount }.firstOrNull { pending(session, it) }
    }

    fun advance(session: ActiveSession, index: Int, skipped: Boolean): ActiveSession {
        require(valid(session) && contains(session, index))
        val marked = if (skipped) session.copy(skippedOccurrenceIndices =
            (session.skippedOccurrenceIndices.orEmpty() + index).distinct()) else session
        val group = requireNotNull(marked.circuit)
        val next = nextMember(marked, index) ?: group.memberCount.takeIf { it < marked.exercises.size } ?: index
        return marked.copy(cursorIndex = next)
    }

    fun terminal(session: ActiveSession, index: Int): Boolean {
        val group = session.circuit ?: return index == session.exercises.lastIndex
        if (!contains(session, index)) return index == session.exercises.lastIndex
        return group.memberCount == session.exercises.size &&
            (0 until group.memberCount).none { pending(session, it) }
    }

    /** Null means the workout has no subsequent work. A wrap or group exit uses round rest. */
    fun restAfter(session: ActiveSession, completedIndex: Int, nextIndex: Int): Int? {
        val group = requireNotNull(session.circuit)
        if (terminal(session, completedIndex)) return null
        return if (contains(session, nextIndex) && nextIndex > completedIndex)
            group.transitionRestSeconds else group.roundRestSeconds
    }
}
