package com.apexfit.app.domain.repository

import com.apexfit.app.data.*
import com.apexfit.app.ui.models.UiComplianceResult
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.utils.StreakResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FitnessRepository {
    val appScope: CoroutineScope
    val recent90DaySessionsFlow: StateFlow<List<TrainingSession>>
    val recent90DaySetsFlow: StateFlow<List<ExerciseSet>>
    val richSessionsFlow: StateFlow<List<RichTrainingSession>>
    val weightEntriesFlow: StateFlow<List<WeightEntry>>
    val nutritionEntriesFlow: StateFlow<List<NutritionEntry>>
    val sessionReadinessFlow: StateFlow<UiSessionReadiness?>
    val sharedTargetsFlow: Flow<NutritionTargets>
    val todayExercisesFlow: StateFlow<List<PlanExercise>>
    val complianceScoresFlow: StateFlow<UiComplianceResult>
    val streakResultFlow: StateFlow<StreakResult>

    suspend fun logWeightEntry(entry: WeightEntry)
    suspend fun logNutritionEntry(entry: NutritionEntry)
    suspend fun logExerciseSet(set: ExerciseSet)
    suspend fun savePlan(plan: WorkoutPlan): Long
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
    suspend fun getLastSetsForExercise(exerciseId: String): List<com.apexfit.app.data.ExerciseSet>
    suspend fun getAllSetsForExercises(exerciseIds: List<String>): List<ExerciseSet>
    suspend fun getLastSetsForExercises(exerciseIds: List<String>): List<ExerciseSet>
    suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord>
    suspend fun insertSessionAtomic(session: TrainingSession, sets: List<ExerciseSet>)
    suspend fun insertSessionWithPRsAtomic(session: TrainingSession, sets: List<ExerciseSet>, prs: List<PersonalRecord>)
    suspend fun insertPersonalRecord(record: PersonalRecord)
    suspend fun getAllWeightEntries(): List<WeightEntry>
    fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>>
    suspend fun getSetsForSession(sessionId: String): List<ExerciseSet>
    suspend fun getAllCompletedSessions(): List<TrainingSession>
    suspend fun getAllExerciseSets(): List<ExerciseSet>
    suspend fun getRecentCompletedSessions(cutoffDate: String): List<TrainingSession>
    suspend fun getRecentExerciseSets(cutoffDate: String): List<ExerciseSet>
    fun getCalorieTargetFlow(): Flow<Int>
    fun getGoalFlow(): Flow<String>
    fun getCurrentWeightFlow(): Flow<Double>
    suspend fun scanAndSaveWeeklyPatterns()

    // Weight mutations
    suspend fun insertWeightEntry(entry: WeightEntry)
    suspend fun deleteWeightEntryById(id: Long)

    // Plan exercises (reactive)
    fun getAllPlanExercisesFlow(): Flow<List<PlanExercise>>

    // Export / bulk queries
    suspend fun getWeightEntriesSince(cutoffDate: String): List<WeightEntry>
    suspend fun getNutritionEntriesSince(cutoffDate: String): List<NutritionEntry>
    suspend fun getAllWorkoutSessions(): List<com.apexfit.app.data.TrainingSession>
    suspend fun getRecentPersonalRecordsWithNames(limit: Int): List<com.apexfit.app.data.PersonalRecordWithName>

    // Nuclear reset — wipes all Room tables
    suspend fun clearAllData()

    // Atomic plan activation — deactivates all plans then activates the specified one
    suspend fun activatePlan(planId: Long)
}
