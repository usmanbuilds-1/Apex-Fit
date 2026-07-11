package com.example.data.repository

import com.example.data.*
import com.example.domain.repository.FitnessRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

/**
 * FitnessRepositoryImpl provides clean, centralized data access delegating to the local database and datastore.
 */
class FitnessRepositoryImpl(
    private val db: AppDatabase,
    private val dao: FitnessDao,
    private val dataStore: DataStoreManager
) : FitnessRepository {

    override suspend fun logWeightEntry(entry: WeightEntry) {
        dao.insertWeightEntry(entry)
    }

    override suspend fun logNutritionEntry(entry: NutritionEntry) {
        dao.insertNutritionEntry(entry)
    }

    override suspend fun logExerciseSet(set: ExerciseSet) {
        dao.insertExerciseSet(set)
    }

    override suspend fun savePlan(plan: WorkoutPlan) {
        dao.insertWorkoutPlan(plan)
    }

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> {
        return dao.getAllPlansFlow()
    }

    override fun getExercisesForSession(sessionId: Long): Flow<List<PlanExercise>> {
        return dao.getExercisesForSessionFlow(sessionId)
    }

    override suspend fun updateWorkoutPlan(plan: WorkoutPlan, sessions: List<PlanSession>, exercises: List<PlanExercise>) = db.withTransaction {
        if (plan.isActive) {
            dao.deactivateAllPlans()
        }
        dao.insertWorkoutPlan(plan)
        // Clean up old sessions and their exercises
        val oldSessions = dao.getSessionsForPlan(plan.id)
        oldSessions.forEach {
            dao.deleteExercisesForPlanSession(it.id)
        }
        dao.deleteSessionsForPlan(plan.id)
        // Insert new ones
        sessions.forEach { dao.insertPlanSession(it) }
        exercises.forEach { dao.insertPlanExercise(it) }
    }

    override fun getWeightHistory(): Flow<List<WeightEntry>> {
        return dao.getAllWeightEntriesFlow()
    }

    override fun getNutritionEntries(date: String): Flow<List<NutritionEntry>> {
        return dao.getNutritionForDateFlow(date)
    }

    override fun getTrainingSessions(): Flow<List<TrainingSession>> {
        return dao.getAllTrainingSessionsFlow()
    }

    override fun getBodyMeasurements(): Flow<List<BodyMeasurement>> {
        return dao.getAllBodyMeasurementsFlow()
    }

    override fun getActivePlan(): Flow<WorkoutPlan?> {
        return dao.getActivePlanFlow()
    }

    override fun getPlanSessions(planId: Long): Flow<List<PlanSession>> {
        return dao.getSessionsForPlanFlow(planId)
    }

    override suspend fun getLastSessionWeights(exerciseName: String): List<Pair<Double, Int>> {
        val sets = dao.getLastSetsForExercise(exerciseName)
        return sets
            .groupBy { it.sessionId }
            .values
            .firstOrNull()
            ?.map { Pair(it.weight, it.reps) }
            ?: emptyList()
    }

    override suspend fun getLastSetForExercise(exerciseId: String): LastSetWithDate? {
        return dao.getLastSetForExercise(exerciseId)
    }

    override suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord> {
        return dao.getPRsForExercise(exerciseId)
    }

    override suspend fun insertSessionAtomic(session: TrainingSession, sets: List<ExerciseSet>) {
        dao.insertSessionAtomic(session, sets)
    }

    override suspend fun insertSessionWithPRsAtomic(session: TrainingSession, sets: List<ExerciseSet>, prs: List<PersonalRecord>) {
        dao.insertSessionWithPRsAtomic(session, sets, prs)
    }

    override suspend fun insertPersonalRecord(record: PersonalRecord) {
        dao.insertPersonalRecord(record)
    }

    override suspend fun getAllWeightEntries(): List<WeightEntry> {
        return dao.getAllWeightEntries()
    }

    override fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>> {
        return dao.getAllNutritionEntriesFlow()
    }

    override suspend fun getSetsForSession(sessionId: String): List<ExerciseSet> {
        return dao.getSetsForSession(sessionId)
    }

    override suspend fun getAllCompletedSessions(): List<TrainingSession> {
        return dao.getAllCompletedSessions()
    }

    override suspend fun getAllExerciseSets(): List<ExerciseSet> {
        return dao.getAllExerciseSets()
    }

    override fun getCalorieTargetFlow(): Flow<Int> {
        return dataStore.calorieTargetValueFlow
    }

    override fun getGoalFlow(): Flow<String> {
        return dataStore.goalFlow
    }

    override fun getCurrentWeightFlow(): Flow<Double> {
        return dataStore.currentWeightFlow
    }

    override suspend fun scanAndSaveWeeklyPatterns() {
        val dbWeights = dao.getAllWeightEntries()
        val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
            com.example.utils.WeightEntry(date, list.map { it.weight }.average())
        }.sortedBy { it.date }
        
        val dbNutrition = dao.getAllNutritionEntriesFlow().first()
        val engineNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
            com.example.utils.NutritionEntry(
                date = date,
                calories = list.sumOf { it.calories },
                protein = list.sumOf { it.protein }.toInt(),
                carbs = list.sumOf { it.carbs }.toInt(),
                fat = list.sumOf { it.fat }.toInt()
            )
        }.sortedBy { it.date }
        
        val dbSessions = dao.getAllCompletedSessions()
        val sessions = dbSessions.map { session ->
            val dbSets = dao.getSetsForSession(session.id)
            val exerciseLogs = dbSets.groupBy { it.exerciseId }.map { (exId, sets) ->
                val firstSet = sets.firstOrNull()
                val name = firstSet?.exerciseName ?: "Exercise"
                val muscle = firstSet?.muscleGroup ?: "General"
                com.example.utils.ExerciseLog(
                    id = exId,
                    name = name,
                    muscleGroup = muscle,
                    sets = sets.map { s ->
                        com.example.utils.ExerciseSet(
                            weight = s.weight,
                            reps = s.reps,
                            rpe = s.rpe,
                            isWarmup = s.isWarmup,
                            completed = s.completed
                        )
                    }
                )
            }
            com.example.utils.TrainingSession(
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = exerciseLogs
            )
        }
        
        val latestWeight = engineWeights.lastOrNull()?.weight ?: com.example.UserDefaults.WEIGHT_KG
        val proteinTarget = (latestWeight * com.example.UserDefaults.PROTEIN_PER_KG).roundToInt().coerceIn(100, 250).toDouble()

        val detected = com.example.utils.PatternDetector.scanAllPatterns(
            weightLog = engineWeights,
            nutritionLog = engineNutrition,
            trainingLog = sessions,
            sleepLog = emptyList(),
            proteinTarget = proteinTarget
        )
        
        val entities = detected.map { p ->
            com.example.data.DetectedPatternEntity(
                id = p.id,
                type = p.type,
                title = p.title,
                description = p.description,
                confidence = p.confidence,
                actionable = p.actionable,
                detectedAt = p.detectedAt
            )
        }
        dao.clearAllDetectedPatterns()
        dao.insertDetectedPatterns(entities)
    }
}
