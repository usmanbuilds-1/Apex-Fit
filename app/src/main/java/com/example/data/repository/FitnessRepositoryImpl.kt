package com.example.data.repository

import com.example.data.*
import com.example.domain.repository.FitnessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * FitnessRepositoryImpl provides clean, centralized data access delegating to the local database and datastore.
 */
class FitnessRepositoryImpl(
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
        val allSets = dao.getAllExerciseSets()
        return allSets
            .filter { (it.exerciseName.equals(exerciseName, ignoreCase = true) || it.exerciseId.equals(exerciseName, ignoreCase = true)) && it.completed }
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

    override suspend fun insertWeeklyReport(report: WeeklyReport) {
        dao.insertWeeklyReport(report)
    }
}
