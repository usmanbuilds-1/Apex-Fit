package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSession
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.ScheduleRevision
import com.apexfit.app.data.ScheduleRevisionSlot
import com.apexfit.app.data.ScheduleRevisionSnapshot
import com.google.gson.Gson
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleOccurrenceTest {
    private val monday = LocalDate.parse("2026-11-23")
    private val chosen = PlanSession(12, 9, "Upper A", "Monday", "Upper", 1)
    private val snapshot = ScheduleRevisionSnapshot(
        ScheduleRevision(5, "2026-11-23", 100, 9, "A/B", 2, "2026-11-23"),
        listOf(ScheduleRevisionSlot(5, 12, 1, 1, "Upper A", true),
            ScheduleRevisionSlot(5, 13, 2, 1, "Upper B", true)))

    @Test fun exactOccurrenceRequiresDatePlanSlotAndCycle() {
        val id = "5:2026-11-23:12"
        assertEquals(id, ScheduleLedger.occurrenceForStart(monday, 200, chosen, snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 200, chosen.copy(id = 99), snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 200, chosen.copy(planId = 10), snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 200, chosen.copy(label = "Edited"), snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 200, chosen.copy(cycleWeek = 2), snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday.plusDays(1), 200, chosen, snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday.plusWeeks(1), 200, chosen, snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 99, chosen, snapshot))
        assertNull(ScheduleLedger.occurrenceForStart(monday, 200, chosen, null))
        assertTrue(ScheduleLedger.validOccurrence(id, monday.toString(), 12))
        assertFalse(ScheduleLedger.validOccurrence(id, monday.plusDays(1).toString(), 12))
        assertFalse(ScheduleLedger.validOccurrence(id, monday.toString(), 13))
    }

    @Test fun newDraftRestoresOriginalDateAndOccurrenceAndLegacyRemainsUnknown() {
        val gson = Gson()
        val active = ActiveSession("12", "Upper A", startTime = 1000,
            exercises = mutableListOf(), startedLocalDate = monday.toString(),
            scheduleOccurrenceId = "5:2026-11-23:12")
        val restored = ActiveWorkoutCodec.decode(gson.toJson(active)).session
        assertEquals(active.sessionId, restored.sessionId)
        assertEquals(active.startedLocalDate, restored.startedLocalDate)
        assertEquals(active.scheduleOccurrenceId, restored.scheduleOccurrenceId)
        val legacy = ActiveWorkoutCodec.decode(gson.toJson(active.copy(schemaVersion = 6,
            startedLocalDate = null, scheduleOccurrenceId = null))).session
        assertEquals(active.sessionId, legacy.sessionId)
        assertNull(legacy.startedLocalDate)
        assertNull(legacy.scheduleOccurrenceId)
        assertTrue(runCatching { ActiveWorkoutCodec.decode(gson.toJson(active.copy(
            scheduleOccurrenceId = "5:2026-11-24:12"))) }.isFailure)
    }
}
