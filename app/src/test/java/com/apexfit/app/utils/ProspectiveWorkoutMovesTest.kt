package com.apexfit.app.utils

import com.apexfit.app.data.ScheduleRevision
import com.apexfit.app.data.ScheduleRevisionSlot
import com.apexfit.app.data.ScheduleRevisionSnapshot
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProspectiveWorkoutMovesTest {
    private val today = LocalDate.parse("2026-11-29")
    private val monday = today.plusDays(1)
    private val tuesday = monday.plusDays(1)
    private val revision = ScheduleRevisionSnapshot(
        ScheduleRevision(1, "2026-11-30", 100, 9, "Upper/Lower", 1, null),
        listOf(ScheduleRevisionSlot(1, 12, 1, 1, "Upper", true),
            ScheduleRevisionSlot(1, 13, 1, 3, "Lower", true)))
    private val revisions = listOf(revision)

    @Test fun deferralKeepsOriginalOccurrenceAndRequiresRestDestination() {
        val allowed = ProspectiveWorkoutMoves.propose(monday, tuesday, today, revisions) as MoveValidation.Allowed
        assertEquals("1:2026-11-30:12", allowed.move.occurrenceId)
        assertEquals(monday, allowed.move.originalDate)
        assertEquals(tuesday, allowed.move.targetDate)
        assertTrue(ProspectiveWorkoutMoves.stillMatches(allowed.move, revisions))
        assertEquals(MoveValidation.Rejected(MoveRejection.DESTINATION_NOT_REST),
            ProspectiveWorkoutMoves.propose(monday, monday.plusDays(2), today, revisions))
        assertEquals(MoveValidation.Rejected(MoveRejection.ORIGINAL_NOT_FUTURE),
            ProspectiveWorkoutMoves.propose(today, tuesday, today, revisions))
        assertEquals(MoveValidation.Rejected(MoveRejection.TARGET_NOT_LATER),
            ProspectiveWorkoutMoves.propose(monday, monday, today, revisions))
        assertEquals(MoveValidation.Rejected(MoveRejection.SOURCE_NOT_TRAINING),
            ProspectiveWorkoutMoves.propose(tuesday, tuesday.plusDays(2), today, revisions))
    }

    @Test fun duplicateSourceAndDestinationRejected() {
        val destination = monday.plusDays(3)
        val move = (ProspectiveWorkoutMoves.propose(monday, destination, today, revisions)
            as MoveValidation.Allowed).move
        assertEquals(MoveValidation.Rejected(MoveRejection.SOURCE_ALREADY_MOVED),
            ProspectiveWorkoutMoves.propose(monday, destination.plusDays(1), today, revisions, listOf(move)))
        assertEquals(MoveValidation.Rejected(MoveRejection.OCCUPIED_DESTINATION),
            ProspectiveWorkoutMoves.propose(monday.plusDays(2), destination, today, revisions, listOf(move)))
    }

    @Test fun laterPlanRevisionInvalidatesProjectionWithoutSilentlyRebinding() {
        val move = (ProspectiveWorkoutMoves.propose(monday, tuesday, today, revisions)
            as MoveValidation.Allowed).move
        val replacement = revision.copy(revision = revision.revision.copy(id = 2, effectiveFrom = tuesday.toString()),
            slots = revision.slots.map { it.copy(revisionId = 2) })
        assertFalse(ProspectiveWorkoutMoves.stillMatches(move, revisions + replacement))
        assertEquals(MoveValidation.Rejected(MoveRejection.DIFFERENT_REVISION),
            ProspectiveWorkoutMoves.propose(monday, tuesday, today, revisions + replacement))
        assertFalse(ProspectiveWorkoutMoves.stillMatches(move.copy(slotId = 99), revisions))
    }
}
