package com.apexfit.app.utils

import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.WeightEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CustomFitnessGoalsTest {
    private val today = LocalDate.parse("2026-11-16")
    private val start = today.plusDays(1)
    private fun point(id: Long, load: Double, reps: Int, date: LocalDate = start) =
        StrengthSessionPoint("s$id", date, false, listOf(StrengthObservation(id, load, reps, "f$id")))
    private fun confirmations(points: List<StrengthSessionPoint>) = points.flatMap { it.observations }
        .associate { it.setId to SetLoadConfirmation(it.setId, it.fingerprint!!, RecordedLoadBasis.TOTAL, "Gym A") }
    private fun spec(kind: FitnessGoalKind, target: Double, minimum: Double) =
        FitnessGoalSpec(kind, target, "press", RecordedLoadBasis.TOTAL, "gym a", minimum)
    private fun observations(spec: FitnessGoalSpec, points: List<StrengthSessionPoint>,
                             c: Map<Long, SetLoadConfirmation> = confirmations(points)) =
        CustomFitnessGoals.observations(spec, points, c, emptyList(), emptyList(), start, start.plusDays(10))

    @Test fun loadAndMinimumRepsMustOccurOnSameSetAndDeletionReopens() {
        val spec = spec(FitnessGoalKind.LOAD, 60.0, 8.0)
        val goal = CustomFitnessGoals.create(spec, today, emptyList())
        val wrong = listOf(point(1, 60.0, 5), point(2, 50.0, 10))
        val valid = point(3, 60.0, 8)
        assertNull(CustomFitnessGoals.progress(goal, start, observations(spec, wrong)).achievement)
        assertEquals("set:3", CustomFitnessGoals.progress(goal, start, observations(spec, wrong + valid)).achievement!!.source)
        assertNull(CustomFitnessGoals.progress(goal, start, observations(spec, wrong)).achievement)
    }

    @Test fun repGoalRequiresMinimumLoadAndMatchingSetup() {
        val spec = spec(FitnessGoalKind.REPS, 10.0, 50.0)
        val points = listOf(point(1, 40.0, 12), point(2, 50.0, 9), point(3, 50.0, 10))
        val c = confirmations(points).toMutableMap()
        c[3] = c.getValue(3).copy(setup = "Gym B")
        assertEquals(9.0, observations(spec, points, c).single().value, 0.0)
    }

    @Test fun sessionVolumeDoesNotSumAcrossSessions() {
        val spec = spec(FitnessGoalKind.SESSION_VOLUME, 600.0, 0.0)
        val points = listOf(point(1, 40.0, 10), point(2, 40.0, 10))
        val goal = CustomFitnessGoals.create(spec, today, emptyList())
        assertNull(CustomFitnessGoals.progress(goal, start, observations(spec, points)).achievement)
    }

    @Test fun trainingDaysCountDistinctCompletedDatesAndMinutesCountSessions() {
        val sessions = listOf(
            TrainingSession("a", start.toString(), "Upper", true, 30, 3),
            TrainingSession("b", start.toString(), "Extra", true, 20, 3),
            TrainingSession("c", start.plusDays(1).toString(), "Lower", false, 25, 3, status = "partial"))
        fun value(kind: FitnessGoalKind) = CustomFitnessGoals.observations(FitnessGoalSpec(kind, 2.0),
            emptyList(), emptyMap(), sessions, emptyList(), start, start.plusDays(2)).last().value
        assertEquals(1.0, value(FitnessGoalKind.TRAINING_DAYS), 0.0)
        assertEquals(50.0, value(FitnessGoalKind.MINUTES), 0.0)
    }

    @Test fun deadlineAndArchiveBoundAchievementWithoutChangingBaseline() {
        val spec = FitnessGoalSpec(FitnessGoalKind.TRAINING_DAYS, 2.0, deadline = start.plusDays(1))
        val goal = CustomFitnessGoals.create(spec, today, emptyList())
        val values = listOf(GoalObservation(1.0, start, "a"), GoalObservation(2.0, start.plusDays(2), "b"))
        val progress = CustomFitnessGoals.progress(goal, start.plusDays(3), values)
        assertTrue(progress.ended)
        assertNull(progress.achievement)
        assertEquals(0.0, goal.baseline!!, 0.0)
        val archived = CustomFitnessGoals.progress(goal.copy(archivedOn = today), start, values)
        assertTrue(archived.ended)
        assertNull(archived.current)
    }

    @Test fun dailyWeightMeansAndPriorBaselineRemainSeparate() {
        val spec = FitnessGoalSpec(FitnessGoalKind.WEIGHT_UP, 70.0)
        val baseline = listOf(GoalObservation(68.0, today.minusDays(1), "mean"))
        val goal = CustomFitnessGoals.create(spec, today, baseline)
        val weights = listOf(WeightEntry(date = start.toString(), weight = 69.0),
            WeightEntry(date = start.toString(), weight = 71.0))
        val obs = CustomFitnessGoals.observations(spec, emptyList(), emptyMap(), emptyList(), weights, start, start)
        assertEquals(70.0, obs.single().value, 0.0)
        assertEquals(68.0, goal.baseline!!, 0.0)
        assertNotNull(CustomFitnessGoals.progress(goal, start, obs).achievement)
    }

    @Test fun codecPreservesDefinitionAndRejectsMalformedAndConflictingRows() {
        val goal = CustomFitnessGoals.create(spec(FitnessGoalKind.LOAD, 60.0, 8.0).copy(setup = "Gym ä"), today, emptyList())
        val encoded = CustomFitnessGoals.encode(goal)
        assertEquals(goal, CustomFitnessGoals.decode(encoded))
        assertNull(CustomFitnessGoals.decode("bad"))
        assertTrue(CustomFitnessGoals.decodeAll(setOf(encoded, CustomFitnessGoals.encode(goal.copy(archivedOn = today)))).isEmpty())
        assertFalse(FitnessGoalSpec(FitnessGoalKind.LOAD, 60.0).valid())
    }
}
