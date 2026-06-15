package com.example.domain.repository

import com.example.data.*
import kotlinx.coroutines.flow.Flow

interface FitnessRepository {
    suspend fun logWeightEntry(entry: WeightEntry)
    suspend fun logNutritionEntry(entry: NutritionEntry)
    suspend fun logExerciseSet(set: ExerciseSet)
    suspend fun savePlan(plan: WorkoutPlan)
    fun getWorkoutPlans(): Flow<List<WorkoutPlan>>
    fun getExercisesForSession(sessionId: Long): Flow<List<PlanExercise>>
    suspend fun updateWorkoutPlan(plan: WorkoutPlan, sessions: List<PlanSession>, exercises: List<PlanExercise>)
    
    fun getWeightHistory(): Flow<List<WeightEntry>>
    fun getNutritionEntries(date: String): Flow<List<NutritionEntry>>
    fun getTrainingSessions(): Flow<List<TrainingSession>>
    fun getBodyMeasurements(): Flow<List<BodyMeasurement>>
    fun getActivePlan(): Flow<WorkoutPlan?>
    fun getPlanSessions(planId: Long): Flow<List<PlanSession>>
    suspend fun getLastSessionWeights(exerciseName: String): List<Pair<Double, Int>>

    // SessionManager & GeminiService methods
    suspend fun getLastSetForExercise(exerciseId: String): LastSetWithDate?
    suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord>
    suspend fun insertSessionAtomic(session: TrainingSession, sets: List<ExerciseSet>)
    suspend fun insertPersonalRecord(record: PersonalRecord)
    suspend fun getAllWeightEntries(): List<WeightEntry>
    fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>>
    suspend fun getSetsForSession(sessionId: String): List<ExerciseSet>
    suspend fun insertWeeklyReport(report: WeeklyReport)
}
