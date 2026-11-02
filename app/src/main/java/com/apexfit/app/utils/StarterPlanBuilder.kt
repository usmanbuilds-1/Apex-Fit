package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.*

/** Creates only an onboarding-owned plan. Existing user plans and history are preserved. */
object StarterPlanBuilder {
    data class SessionTemplate(val label: String, val day: String, val exercises: List<String>)

    private val upper = listOf("Flat Dumbbell Press", "Chest-Supported Dumbbell Row", "Dumbbell Lateral Raise", "Hammer Bicep Curl")
    private val lower = listOf("Leg Press (High & Wide)", "Romanian Deadlift", "Seated Leg Curl", "Standing Calf Raise")
    private val push = listOf("Flat Dumbbell Press", "Standing Overhead Press", "Dumbbell Lateral Raise", "Dual Rope Tricep Pushdown")
    private val pull = listOf("Lat Pulldown (Neutral Grip)", "Chest-Supported Dumbbell Row", "Hammer Bicep Curl")
    private val full = listOf(lower[0], upper[0], upper[1], lower[2], upper[2])

    fun templates(days: Int): List<SessionTemplate> = when (days) {
        3 -> listOf(SessionTemplate("Full Body A", "Monday", full), SessionTemplate("Full Body B", "Wednesday", listOf(lower[1], pull[0], push[1], lower[3], upper[3])), SessionTemplate("Full Body C", "Friday", full))
        4 -> listOf(SessionTemplate("Upper A", "Monday", upper), SessionTemplate("Lower A", "Tuesday", lower), SessionTemplate("Upper B", "Thursday", push.take(2) + pull.take(2)), SessionTemplate("Lower B", "Friday", lower))
        5 -> listOf(SessionTemplate("Upper", "Monday", upper), SessionTemplate("Lower", "Tuesday", lower), SessionTemplate("Push", "Thursday", push), SessionTemplate("Pull", "Friday", pull), SessionTemplate("Legs", "Saturday", lower))
        6 -> listOf(SessionTemplate("Push A", "Monday", push), SessionTemplate("Pull A", "Tuesday", pull), SessionTemplate("Legs A", "Wednesday", lower), SessionTemplate("Push B", "Thursday", push), SessionTemplate("Pull B", "Friday", pull), SessionTemplate("Legs B", "Saturday", lower))
        else -> error("Training frequency must be 3–6 days")
    }

    fun setsFor(experience: String): Int = when (experience) {
        "Beginner" -> 2
        "Intermediate" -> 3
        "Advanced" -> 4
        else -> error("Unknown training experience")
    }

    suspend fun createIfNeeded(db: AppDatabase, profile: OnboardingProfile) = db.withTransaction {
        require(profile.validationError() == null)
        val dao = db.fitnessDao()
        // Retry safely if saving preferences failed after the database transaction committed.
        val existing = dao.getAllPlans().firstOrNull { it.source == "onboarding" }
        val planName = "${profile.daysPerWeek}-Day ${profile.experience} Plan"
        if (existing != null && existing.name == planName && existing.goal == profile.goal) return@withTransaction
        val sessions = templates(profile.daysPerWeek)
        val library = sessions.flatMap { it.exercises }.distinct().associateWith { name ->
            requireNotNull(dao.getExerciseByName(name)) { "Exercise library is not ready. Please try again." }
        }
        dao.deactivateAllPlans()
        val plan = WorkoutPlan(
            id = existing?.id ?: 0,
            name = planName,
            goal = profile.goal,
            isActive = true,
            daysPerWeek = profile.daysPerWeek,
            source = "onboarding",
            description = "Starter program for ${profile.goal.lowercase()}. Choose a comfortable starting load; progress is based on your logged sets."
        )
        val planId = if (existing == null) dao.insertWorkoutPlan(plan) else {
            dao.deleteSessionsForPlan(existing.id)
            dao.updateWorkoutPlan(plan)
            existing.id
        }
        sessions.forEach { template ->
            val sessionId = dao.insertPlanSession(PlanSession(
                planId = planId, label = template.label, day = template.day,
                focus = template.exercises.map { library.getValue(it).primaryMuscle }.distinct().joinToString(", ")
            ))
            dao.insertPlanExercises(template.exercises.map { name ->
                val exercise = library.getValue(name)
                PlanExercise(planSessionId = sessionId, exerciseId = exercise.id,
                    name = exercise.name, muscleGroup = exercise.primaryMuscle,
                    sets = setsFor(profile.experience), repsMin = 8, repsMax = 12,
                    weight = 0.0, weightUnit = "kg", restSeconds = 90,
                    notes = "Choose a comfortable starting load. Log your actual weight and reps.")
            })
        }
    }
}
