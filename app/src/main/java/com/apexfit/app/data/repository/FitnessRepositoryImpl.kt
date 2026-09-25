package com.apexfit.app.data.repository

import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * FitnessRepositoryImpl provides clean, centralized data access delegating to the local database and datastore.
 */
/**
 * Implementation of [FitnessRepository].
 *
 * Architecture Note (Fix D-L7):
 * Repository flows return cold Flow instances originating from Room DAOs.
 * In ViewModels that collect the same repository flow multiple times or across multiple UI states,
 * repeated calls should be consolidated into a single stateIn-cached flow at the ViewModel level
 * (using viewModelScope and SharingStarted.WhileSubscribed(5_000)) to prevent redundant Room subscriptions.
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

    override suspend fun savePlan(plan: WorkoutPlan): Long {
        return dao.insertWorkoutPlan(plan)
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
        val newPlanId = dao.insertWorkoutPlan(plan)
        val resolvedPlanId = if (plan.id == 0L) newPlanId else plan.id
        dao.deleteSessionsForPlan(plan.id) // CASCADE removes exercises automatically
        dao.insertPlanSessions(sessions.map { it.copy(planId = resolvedPlanId) })
        dao.insertPlanExercises(exercises)
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
        val slug = com.apexfit.app.utils.exerciseNameToSlug(exerciseName)
        val sets = dao.getLastSetsForExercise(slug)
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

    override suspend fun getLastSetsForExercise(exerciseId: String): List<com.apexfit.app.data.ExerciseSet> =
        dao.getLastSetsForExercise(exerciseId)

    override suspend fun getAllSetsForExercises(exerciseIds: List<String>): List<ExerciseSet> =
        dao.getAllSetsForExercises(exerciseIds)

    override suspend fun getLastSetsForExercises(exerciseIds: List<String>): List<ExerciseSet> =
        dao.getLastSetsForExercises(exerciseIds)

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

    override suspend fun getRecentCompletedSessions(cutoffDate: String): List<TrainingSession> {
        return dao.getRecentCompletedSessions(cutoffDate)
    }

    override suspend fun getRecentExerciseSets(cutoffDate: String): List<ExerciseSet> {
        return dao.getRecentExerciseSets(cutoffDate)
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
        val cutoffDate = LocalDate.now().minusDays(90).toString()

        val dbWeights = dao.getWeightEntriesSince(cutoffDate)
        val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
            com.apexfit.app.utils.WeightEntry(date, list.map { it.weight }.average())
        }.sortedBy { it.date }
        
        val dbNutrition = dao.getNutritionEntriesSince(cutoffDate)
        val engineNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
            com.apexfit.app.utils.NutritionEntry(
                date = date,
                calories = list.sumOf { it.calories },
                protein = list.sumOf { it.protein },
                carbs = list.sumOf { it.carbs },
                fat = list.sumOf { it.fat }
            )
        }.sortedBy { it.date }
        
        val dbSessions = dao.getRecentCompletedSessions(cutoffDate)
        val muscleVolumes = dao.getMuscleGroupVolumesSince(cutoffDate)
        val sessions = dbSessions.map { session ->
            com.apexfit.app.utils.TrainingSession(
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = emptyList()
            )
        }
        
        val latestWeight = engineWeights.lastOrNull()?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        val proteinTarget = (latestWeight * com.apexfit.app.UserDefaults.PROTEIN_PER_KG)
            .coerceIn(100.0, 250.0)

        val detected = com.apexfit.app.utils.PatternDetector.scanAllPatterns(
            weightLog = engineWeights,
            nutritionLog = engineNutrition,
            trainingLog = sessions,
            proteinTarget = proteinTarget
        )
        
        val entities = detected.map { p ->
            com.apexfit.app.data.DetectedPatternEntity(
                id = p.id,
                type = p.type,
                title = p.title,
                description = p.description,
                confidence = p.confidence,
                actionable = p.actionable,
                detectedAt = p.detectedAt
            )
        }
        db.withTransaction {
            dao.clearAllDetectedPatterns()
            dao.insertDetectedPatterns(entities)
        }
    }
}
