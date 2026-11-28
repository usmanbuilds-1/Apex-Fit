package com.apexfit.app.utils

import com.apexfit.app.data.ActiveCircuit
import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.ActiveWorkoutGroup

/** Frozen group ranges, scoped to exercise occurrences; legacy initial circuits remain supported. */
internal object CircuitExecution {
    fun groups(session: ActiveSession): List<ActiveWorkoutGroup> =
        session.permanentGroups.orEmpty() + listOfNotNull(session.circuit?.let {
            ActiveWorkoutGroup("session-local", 0, it.memberCount, it.transitionRestSeconds, it.roundRestSeconds)
        })

    fun hasGroups(session: ActiveSession): Boolean = groups(session).isNotEmpty()

    fun groupAt(session: ActiveSession, index: Int): ActiveWorkoutGroup? =
        groups(session).firstOrNull { index >= it.firstIndex && index.toLong() < it.firstIndex.toLong() + it.memberCount }

    fun valid(session: ActiveSession): Boolean {
        if (session.circuit != null && !session.permanentGroups.isNullOrEmpty()) return false
        val groups = groups(session)
        if (groups.isEmpty()) return true
        if (session.cursorIndex == null || session.cursorIndex !in session.exercises.indices ||
            session.skippedOccurrenceIndices.orEmpty().any { it !in session.exercises.indices }) return false
        if (groups.any { g ->
                !g.key.matches(Regex("[A-Za-z0-9_-]{1,64}")) ||
                    g.firstIndex < 0 || g.memberCount < 2 ||
                    g.firstIndex.toLong() + g.memberCount > session.exercises.size ||
                    g.transitionRestSeconds !in 1..3600 || g.roundRestSeconds !in 1..3600
            }) return false
        return groups.map { it.key }.distinct().size == groups.size &&
            groups.zipWithNext().all { (a, b) -> a.firstIndex + a.memberCount <= b.firstIndex } &&
            groups.all { g -> session.exercises.subList(g.firstIndex, g.firstIndex + g.memberCount)
                .all { exercise -> exercise.sets.any { !it.isWarmup } } }
    }

    fun canConfigure(session: ActiveSession): Boolean =
        session.permanentGroups.isNullOrEmpty() &&
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

    fun contains(session: ActiveSession, index: Int): Boolean = groupAt(session, index) != null

    fun pending(session: ActiveSession, index: Int): Boolean =
        index !in session.skippedOccurrenceIndices.orEmpty() &&
            session.exercises.getOrNull(index)?.sets?.any { !it.completed && !it.isWarmup } == true

    /** Rotate within exactly one range, bypassing exhausted/skipped occurrences. */
    fun nextMember(session: ActiveSession, index: Int): Int? {
        val group = requireNotNull(groupAt(session, index))
        return (1..group.memberCount).map {
            group.firstIndex + (index - group.firstIndex + it) % group.memberCount
        }.firstOrNull { pending(session, it) }
    }

    fun advance(session: ActiveSession, index: Int, skipped: Boolean): ActiveSession {
        require(valid(session) && contains(session, index))
        val marked = if (skipped) session.copy(skippedOccurrenceIndices =
            (session.skippedOccurrenceIndices.orEmpty() + index).distinct()) else session
        val group = requireNotNull(groupAt(marked, index))
        val next = nextMember(marked, index) ?:
            (group.firstIndex + group.memberCount).takeIf { it < marked.exercises.size } ?: index
        return marked.copy(cursorIndex = next)
    }

    fun terminal(session: ActiveSession, index: Int): Boolean {
        val group = groupAt(session, index) ?: return index == session.exercises.lastIndex
        val end = group.firstIndex + group.memberCount
        return end == session.exercises.size && (group.firstIndex until end).none { pending(session, it) }
    }

    /** Null means no subsequent work. A wrap or range exit uses configured round rest. */
    fun restAfter(session: ActiveSession, completedIndex: Int, nextIndex: Int): Int? {
        val group = requireNotNull(groupAt(session, completedIndex))
        if (terminal(session, completedIndex)) return null
        return if (groupAt(session, nextIndex) == group && nextIndex > completedIndex)
            group.transitionRestSeconds else group.roundRestSeconds
    }
}
