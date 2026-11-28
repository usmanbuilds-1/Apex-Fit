package com.apexfit.app.utils

import com.apexfit.app.data.ScheduleRevisionSnapshot
import java.time.LocalDate

/** A prospective reservation is not completion and never changes the recurring plan.
 * Initial policy: defer a future training slot to a later recorded rest projection
 * under the same revision. The original occurrence remains the accounting identity.
 */
data class ProspectiveWorkoutMove(
    val occurrenceId: String,
    val originalDate: LocalDate,
    val targetDate: LocalDate,
    val revisionId: Long,
    val slotId: Long
)

enum class MoveRejection {
    ORIGINAL_NOT_FUTURE, TARGET_NOT_LATER, SOURCE_NOT_TRAINING,
    DESTINATION_NOT_REST, DIFFERENT_REVISION, OCCUPIED_DESTINATION, SOURCE_ALREADY_MOVED,
    STALE_RESERVATION
}

sealed interface MoveValidation {
    data class Allowed(val move: ProspectiveWorkoutMove) : MoveValidation
    data class Rejected(val reason: MoveRejection) : MoveValidation
}

object ProspectiveWorkoutMoves {
    fun propose(original: LocalDate, target: LocalDate, today: LocalDate,
                revisions: List<ScheduleRevisionSnapshot>,
                existing: List<ProspectiveWorkoutMove> = emptyList()): MoveValidation {
        fun rejected(reason: MoveRejection) = MoveValidation.Rejected(reason)
        if (original <= today) return rejected(MoveRejection.ORIGINAL_NOT_FUTURE)
        if (target <= original) return rejected(MoveRejection.TARGET_NOT_LATER)
        val source = ScheduleLedger.resolve(original, revisions)
        val occurrence = source.occurrenceId
        val revision = source.revisionId
        val slot = source.slotId
        if (source.kind != CalendarScheduleKind.TRAINING || occurrence == null || revision == null || slot == null)
            return rejected(MoveRejection.SOURCE_NOT_TRAINING)
        val destination = ScheduleLedger.resolve(target, revisions)
        if (destination.kind != CalendarScheduleKind.REST)
            return rejected(MoveRejection.DESTINATION_NOT_REST)
        if (destination.revisionId != revision) return rejected(MoveRejection.DIFFERENT_REVISION)
        if (existing.any { it.occurrenceId == occurrence }) return rejected(MoveRejection.SOURCE_ALREADY_MOVED)
        if (existing.any { it.targetDate == target }) return rejected(MoveRejection.OCCUPIED_DESTINATION)
        return MoveValidation.Allowed(ProspectiveWorkoutMove(occurrence, original, target, revision, slot))
    }

    /** Must be checked before presenting a reservation as actionable and again on Start.
     * A newer revision can invalidate a future projection; do not silently rebind it.
     */
    fun stillMatches(move: ProspectiveWorkoutMove, revisions: List<ScheduleRevisionSnapshot>): Boolean {
        if (move.targetDate <= move.originalDate ||
            !ScheduleLedger.validOccurrence(move.occurrenceId, move.originalDate.toString(), move.slotId)) return false
        val source = ScheduleLedger.resolve(move.originalDate, revisions)
        val destination = ScheduleLedger.resolve(move.targetDate, revisions)
        return source.kind == CalendarScheduleKind.TRAINING && source.occurrenceId == move.occurrenceId &&
            source.revisionId == move.revisionId && source.slotId == move.slotId &&
            destination.kind == CalendarScheduleKind.REST && destination.revisionId == move.revisionId
    }
}
