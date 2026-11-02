package com.apexfit.app.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StarterPlanBuilderTest {
    @Test
    fun selectedFrequencyProducesThatManyDistinctTrainingDays() {
        (3..6).forEach { days ->
            val sessions = StarterPlanBuilder.templates(days)
            assertEquals(days, sessions.size)
            assertEquals(days, sessions.map { it.day }.distinct().size)
            assertTrue(sessions.all { it.exercises.isNotEmpty() })
        }
    }

    @Test
    fun planUsesExperienceAndDoesNotOverwriteExistingUserPlans() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val names = (3..6).flatMap { StarterPlanBuilder.templates(it) }.flatMap { it.exercises }.distinct()
            dao.insertExercises(names.mapIndexed { index, name -> Exercise(id = "exercise-$index", name = name,
                category = "Strength", primaryMuscle = "Chest", equipmentRequired = "Dumbbell") })
            val original = dao.insertWorkoutPlan(WorkoutPlan(name = "My saved plan", goal = "Maintain", isActive = true))
            val profile = OnboardingProfile(experience = "Beginner", daysPerWeek = 3)
            StarterPlanBuilder.createIfNeeded(db, profile)
            StarterPlanBuilder.createIfNeeded(db, profile)
            assertEquals(2, dao.getAllPlans().size)
            assertTrue(dao.getAllPlans().any { it.id == original })
            val plan = dao.getActivePlan()!!
            val sessions = dao.getSessionsForPlan(plan.id)
            assertEquals(3, sessions.size)
            sessions.forEach { session ->
                val exercises = dao.getExercisesForSession(session.id)
                assertTrue(exercises.isNotEmpty())
                assertTrue(exercises.all { it.sets == 2 && it.weight == 0.0 && it.exerciseId.isNotEmpty() })
            }
            StarterPlanBuilder.createIfNeeded(db, profile.copy(experience = "Advanced", daysPerWeek = 5))
            assertEquals(2, dao.getAllPlans().size)
            val updated = dao.getActivePlan()!!
            assertEquals(5, dao.getSessionsForPlan(updated.id).size)
        } finally { db.close() }
    }
}
