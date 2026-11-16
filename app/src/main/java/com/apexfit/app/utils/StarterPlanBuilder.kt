package com.apexfit.app.utils

import androidx.room.withTransaction
import com.apexfit.app.data.*

/** Creates only an onboarding-owned plan. Existing user plans and history are preserved. */
object StarterPlanBuilder {
    data class SessionTemplate(val label: String, val day: String, val exercises: List<String>)

    private val upper = listOf("Flat Dumbbell Press", "Chest-Supported Dumbbell Row", "Dumbbell Lateral Raise", "Hammer Bicep Curl")
    private val lower = listOf("Leg Press (High & Wide)", "Romanian Deadlift", "Seated Leg Curl", "Standing Calf Raise", "Barbell Hip Thrust")
    private val push = listOf("Flat Dumbbell Press", "Standing Overhead Press", "Dumbbell Lateral Raise", "Dual Rope Tricep Pushdown")
    private val pull = listOf("Lat Pulldown (Neutral Grip)", "Chest-Supported Dumbbell Row", "Hammer Bicep Curl")
    private val full = listOf(lower[0], upper[0], upper[1], lower[2], upper[2], push[3], lower[4])
    private val fullB = listOf(lower[1], upper[0], pull[0], lower[3], lower[4], upper[3])

    fun templates(days: Int): List<SessionTemplate> = when (days) {
        2 -> listOf(
            SessionTemplate("Full Body A", "Monday", full),
            SessionTemplate("Full Body B", "Thursday", fullB)
        )
        3 -> listOf(SessionTemplate("Full Body A", "Monday", full), SessionTemplate("Full Body B", "Wednesday", fullB), SessionTemplate("Full Body C", "Friday", full))
        4 -> listOf(SessionTemplate("Upper A", "Monday", upper), SessionTemplate("Lower A", "Tuesday", lower), SessionTemplate("Upper B", "Thursday", push.take(2) + pull.take(2) + push[3]), SessionTemplate("Lower B", "Friday", lower))
        5 -> listOf(SessionTemplate("Upper", "Monday", upper), SessionTemplate("Lower", "Tuesday", lower), SessionTemplate("Push", "Thursday", push), SessionTemplate("Pull", "Friday", pull), SessionTemplate("Legs", "Saturday", lower))
        6 -> listOf(SessionTemplate("Push A", "Monday", push), SessionTemplate("Pull A", "Tuesday", pull), SessionTemplate("Legs A", "Wednesday", lower), SessionTemplate("Push B", "Thursday", push), SessionTemplate("Pull B", "Friday", pull), SessionTemplate("Legs B", "Saturday", lower))
        else -> error("Training frequency must be 2–6 days")
    }

    data class Prescription(val sets: Int, val reps: IntRange, val restSeconds: Int)

    private val accessoryExercises = setOf(upper[2], upper[3], lower[2], lower[3], push[3])
    private val compoundExercises = setOf(upper[0], upper[1], lower[0], lower[1], push[1], pull[0])
    private val accessoryMuscles = setOf("biceps", "triceps", "calves", "shoulders")

    /** Distributes a conservative weekly direct-set budget across scheduled exposures. */
    fun prescribe(
        sessions: List<SessionTemplate>,
        library: Map<String, Exercise>,
        goal: String,
        experience: String
    ): List<List<Prescription>> {
        val baseBudget = when (experience) {
            "Beginner" -> 4
            "Intermediate" -> 5
            "Advanced" -> 6
            else -> error("Unknown training experience")
        }
        val byMuscle = sessions.flatMap { it.exercises }.groupingBy { name ->
            MuscleAliases.getCanonical(library.getValue(name).primaryMuscle)
        }.eachCount()
        val assigned = mutableMapOf<String, Int>()
        return sessions.map { session ->
            session.exercises.map { name ->
                val muscle = MuscleAliases.getCanonical(library.getValue(name).primaryMuscle)
                val exposures = byMuscle.getValue(muscle)
                val goalAdjustment = if (goal == "Gain Muscle" && muscle !in accessoryMuscles) 1 else 0
                val budget = (baseBudget + goalAdjustment).coerceIn(exposures, exposures * 3)
                val exposure = assigned.getOrDefault(muscle, 0)
                assigned[muscle] = exposure + 1
                val sets = budget / exposures + if (exposure < budget % exposures) 1 else 0
                val reps = when (name) {
                    in accessoryExercises -> 10..15
                    in compoundExercises -> if (goal == "Gain Muscle") 6..10 else 5..8
                    else -> 8..12
                }
                Prescription(sets, reps, if (name in compoundExercises) 120 else 90)
            }
        }
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
        val prescription = prescribe(sessions, library, profile.goal, profile.experience)
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
        sessions.forEachIndexed { sessionIndex, template ->
            val sessionId = dao.insertPlanSession(PlanSession(
                planId = planId, label = template.label, day = template.day,
                focus = template.exercises.map { library.getValue(it).primaryMuscle }.distinct().joinToString(", ")
            ))
            dao.insertPlanExercises(template.exercises.mapIndexed { index, name ->
                val exercise = library.getValue(name)
                val target = prescription[sessionIndex][index]
                PlanExercise(planSessionId = sessionId, exerciseId = exercise.id,
                    name = exercise.name, muscleGroup = exercise.primaryMuscle,
                    sets = target.sets, repsMin = target.reps.first, repsMax = target.reps.last,
                    weight = 0.0, weightUnit = "kg", restSeconds = target.restSeconds,
                    notes = "Choose a comfortable load and finish the set with about 2–3 reps in reserve.")
            })
        }
    }
}
