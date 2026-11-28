package com.apexfit.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.*
import com.apexfit.app.utils.MuscleVolumePeriod
import com.apexfit.app.utils.MuscleVolumeSummary
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MuscleAnalyticsRepositoryTest {
    @Test
    fun storedSetIdentityAndDeletedLibraryAttributionReachVolumeScreen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dao = db.fitnessDao()
            val today = LocalDate.now()
            dao.insertExercise(Exercise(id = "analytics-row", name = "Analytics Row", category = "Cable",
                primaryMuscle = "Back", secondaryMuscles = listOf("Biceps"),
                equipmentRequired = "Cable", isDeleted = 1))
            dao.insertTrainingSession(TrainingSession("analytics-session", today.toString(), "Pull", true, 20, 3))
            dao.insertExerciseSet(ExerciseSet(id = 701, sessionId = "analytics-session",
                exerciseId = "analytics-row", exerciseName = "Analytics Row", muscleGroup = "Back",
                weight = 20.0, reps = 8, rpe = 8, isWarmup = false, completed = true))
            val repository = FitnessRepositoryImpl(db, dao, DataStoreManager.getInstance(context), scope)
            val rich = withTimeout(15000) {
                repository.richSessionsFlow.first { sessions ->
                    sessions.any { session -> session.exercises.any { it.sets.any { set -> set.id == 701L } && "Biceps" in it.secondaryMuscles } }
                }
            }
            val rows = MuscleVolumeSummary.calculate(rich, today, MuscleVolumePeriod.WEEK)
            assertEquals(1, rows.first { it.muscle == "back" }.directSets)
            assertEquals(1, rows.first { it.muscle == "biceps" }.secondarySets)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }
    @Test
    fun boundedHistoricalQueryIncludesSavedPartialWorkAndRefreshesAfterDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.fitnessDao()
            val old = LocalDate.now().minusDays(400)
            val start = old.minusDays(1)
            val end = old.plusDays(1)
            dao.insertExercise(Exercise(id = "history-row", name = "History Row", category = "Cable",
                primaryMuscle = "Back", secondaryMuscles = listOf("Biceps"), equipmentRequired = "Cable"))
            val sessions = listOf(
                TrainingSession("full", old.toString(), "Pull", true, 20, 3),
                TrainingSession("partial", old.plusDays(1).toString(), "Pull", false, 10, 3, status = "partial"),
                TrainingSession("outside", old.minusDays(2).toString(), "Pull", true, 20, 3),
                TrainingSession("abandoned", old.toString(), "Pull", false, 10, 3, status = "cancelled")
            )
            for ((index, session) in sessions.withIndex()) {
                dao.insertTrainingSession(session)
                dao.insertExerciseSet(ExerciseSet(id = 800L + index, sessionId = session.id,
                    exerciseId = "history-row", exerciseName = "History Row", muscleGroup = "Back",
                    weight = 20.0, reps = 8, rpe = 8, isWarmup = false, completed = true))
            }
            dao.insertExerciseSet(ExerciseSet(id = 899, sessionId = "partial",
                exerciseId = "history-row", exerciseName = "History Row", muscleGroup = "Back",
                weight = 20.0, reps = 0, rpe = 8, isWarmup = false, completed = true))
            val flow = dao.observeAnalyticsWorkingSets(start.toString(), end.toString())
            val facts = withTimeout(10000) { flow.first() }
            assertEquals(setOf(800L, 801L), facts.map { it.set.id }.toSet())
            val rich = com.apexfit.app.utils.AnalyticsHistory.sessions(facts)
            val back = MuscleVolumeSummary.calculateWindow(rich, start, end).first { it.muscle == "back" }
            assertEquals(2, back.directSets)
            assertEquals(2, back.directTrainingDays)
            assertEquals(1.0, back.directMeanGapDays!!, 0.0)
            dao.deleteExerciseSet(801)
            val remaining = withTimeout(10000) { flow.first { it.size == 1 } }
            assertEquals(800L, remaining.single().set.id)
        } finally {
            db.close()
        }
    }

}
