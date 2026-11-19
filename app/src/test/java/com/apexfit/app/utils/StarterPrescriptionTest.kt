package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import com.apexfit.app.data.OnboardingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterPrescriptionTest {
    private val primaryMuscles = mapOf(
        "Flat Dumbbell Press" to "Chest",
        "Chest-Supported Dumbbell Row" to "Back",
        "Dumbbell Lateral Raise" to "Shoulders",
        "Hammer Bicep Curl" to "Biceps",
        "Leg Press (High & Wide)" to "Quads",
        "Romanian Deadlift" to "Hamstrings",
        "Seated Leg Curl" to "Hamstrings",
        "Standing Calf Raise" to "Calves",
        "Barbell Hip Thrust" to "Glutes",
        "Standing Overhead Press" to "Shoulders",
        "Dual Rope Tricep Pushdown" to "Triceps",
        "Lat Pulldown (Neutral Grip)" to "Back"
    )

    private val library = primaryMuscles.mapValues { (name, muscle) ->
        Exercise(id = name, name = name, category = "Strength", primaryMuscle = muscle, equipmentRequired = "")
    }

    @Test
    fun beginnerPlansForTwoThroughSixDaysCoverMajorMusclesWithinDirectSetBudgets() {
        for (days in 2..6) {
            val sessions = StarterPlanBuilder.templates(days)
            val plans = StarterPlanBuilder.prescribe(sessions, library, "Gain Muscle", "Beginner")
            val exercises = sessions.zip(plans).flatMap { (session, plan) -> session.exercises.zip(plan) }
            val directSets = exercises.groupBy { primaryMuscles.getValue(it.first) }
                .mapValues { (_, entries) -> entries.sumOf { it.second.sets } }
            assertEquals(primaryMuscles.values.toSet(), directSets.keys)
            assertTrue(directSets.values.all { it in 2..5 })
            assertTrue(exercises.all { (_, target) -> target.sets in 1..3 })
        }
    }

    @Test
    fun twoThroughSixDayTemplatesStayWithinConservativeWeeklyBudgetsAcrossProfiles() {
        val experiences = listOf("Beginner", "Intermediate", "Advanced")
        for (days in 2..6) {
            val sessions = StarterPlanBuilder.templates(days)
            for (goal in OnboardingProfile.GOALS) {
                for (experience in experiences) {
                    val prescriptions = StarterPlanBuilder.prescribe(sessions, library, goal, experience)
                    val scheduled = sessions.zip(prescriptions).flatMap { (session, plan) ->
                        session.exercises.zip(plan)
                    }
                    val directSetsByMuscle = scheduled.groupBy { primaryMuscles.getValue(it.first) }
                        .mapValues { (_, entries) -> entries.sumOf { it.second.sets } }
                    val baseBudget = when (experience) {
                        "Beginner" -> 4
                        "Intermediate" -> 5
                        else -> 6
                    }

                    assertEquals("Every major muscle must appear ($days days, $goal, $experience)",
                        primaryMuscles.values.toSet(), directSetsByMuscle.keys)
                    assertTrue("Weekly work is distributed across exposures ($days days, $goal, $experience)",
                        scheduled.all { (_, prescription) -> prescription.sets in 1..3 })
                    assertTrue("Weekly direct work stays bounded ($days days, $goal, $experience): $directSetsByMuscle",
                        directSetsByMuscle.values.all { it in 2..(baseBudget + 1) })
                }
            }
        }
    }

    @Test
    fun prescribedRepRangesFollowExerciseRoleAndGoal() {
        val session = StarterPlanBuilder.SessionTemplate(
            "Upper", "Monday", listOf("Flat Dumbbell Press", "Dumbbell Lateral Raise")
        )
        val gain = StarterPlanBuilder.prescribe(listOf(session), library, "Gain Muscle", "Beginner").single()
        val maintain = StarterPlanBuilder.prescribe(listOf(session), library, "Maintain", "Beginner").single()
        assertEquals(6..10, gain.first().reps)
        assertEquals(5..8, maintain.first().reps)
        assertEquals(10..15, gain.last().reps)
        assertEquals(10..15, maintain.last().reps)
    }
}
