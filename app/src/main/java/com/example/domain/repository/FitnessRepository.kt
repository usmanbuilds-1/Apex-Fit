package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * FitnessRepository Interface
 * 
 * Pure Kotlin interface defining what data operations ViewModels can perform.
 * No Room, no Android, no implementation details.
 * Implementations will handle database/network logic.
 */
interface FitnessRepository {
    
    // ========================================================================
    // EXERCISE SET OPERATIONS
    // ========================================================================
    
    suspend fun logExerciseSet(set: DomainExerciseSet)
    suspend fun logExerciseSets(sets: List<DomainExerciseSet>)
    suspend fun getLastSetForExercise(exerciseName: String): DomainExerciseSet?
    fun getAllSetsForSession(sessionId: String): Flow<List<DomainExerciseSet>>
    
    // ========================================================================
    // TRAINING SESSION OPERATIONS
    // ========================================================================
    
    suspend fun createSession(session: DomainTrainingSession)
    suspend fun updateSession(session: DomainTrainingSession)
    suspend fun getSession(sessionId: String): DomainTrainingSession?
    fun getAllSessions(): Flow<List<DomainTrainingSession>>
    fun getSessionsForDateRange(startDate: String, endDate: String): Flow<List<DomainTrainingSession>>
    
    // ========================================================================
    // PERSONAL RECORD OPERATIONS
    // ========================================================================
    
    suspend fun savePR(pr: DomainPersonalRecord)
    suspend fun savePRs(prs: List<DomainPersonalRecord>)
    suspend fun getMaxWeightPR(exerciseName: String): DomainPersonalRecord?
    suspend fun getMax1RMPR(exerciseName: String): DomainPersonalRecord?
    fun getPRHistoryForExercise(exerciseName: String): Flow<List<DomainPersonalRecord>>
    fun getPRsForSession(sessionId: String): Flow<List<DomainPersonalRecord>>
    fun getAllPRs(): Flow<List<DomainPersonalRecord>>
    
    // ========================================================================
    // WEIGHT TRACKING OPERATIONS
    // ========================================================================
    
    suspend fun logWeight(entry: DomainWeightEntry)
    suspend fun getLastWeight(): DomainWeightEntry?
    fun getWeightHistory(days: Int = 30): Flow<List<DomainWeightEntry>>
    fun getWeightTrend(days: Int = 14): Flow<DomainWeightTrend?>
    
    // ========================================================================
    // NUTRITION OPERATIONS
    // ========================================================================
    
    suspend fun logNutrition(entry: DomainNutritionEntry)
    fun getNutritionForDate(date: String): Flow<List<DomainNutritionEntry>>
    fun getNutritionHistory(days: Int = 7): Flow<List<DomainNutritionEntry>>
    
    // ========================================================================
    // BODY MEASUREMENTS
    // ========================================================================
    
    suspend fun logMeasurement(measurement: DomainBodyMeasurement)
    fun getMeasurementsForBodyPart(bodyPart: String): Flow<List<DomainBodyMeasurement>>
    fun getLatestMeasurements(): Flow<List<DomainBodyMeasurement>>
    
    // ========================================================================
    // WORKOUT PLAN OPERATIONS
    // ========================================================================
    
    suspend fun createWorkoutPlan(plan: DomainWorkoutPlan)
    suspend fun setActivePlan(planId: Long)
    fun getActivePlan(): Flow<DomainWorkoutPlan?>
    fun getAllPlans(): Flow<List<DomainWorkoutPlan>>
    
    // ========================================================================
    // PATTERN DETECTION
    // ========================================================================
    
    suspend fun saveDetectedPattern(pattern: DomainDetectedPattern)
    fun getActivePatterns(): Flow<List<DomainDetectedPattern>>
    fun getAllPatterns(): Flow<List<DomainDetectedPattern>>
    suspend fun resolvePattern(patternId: Long)
    
    // ========================================================================
    // COMPOSITE OPERATIONS (For complex queries)
    // ========================================================================
    
    /**
     * Get complete session data: session info + all sets + achieved PRs
     */
    suspend fun getSessionWithSets(sessionId: String): Pair<DomainTrainingSession?, List<DomainExerciseSet>>?
    
    /**
     * Get session readiness data: recent sessions, weight trend, fatigue
     */
    fun getSessionReadinessData(): Flow<SessionReadinessData>
    
    /**
     * Get home screen data: TDEE, macros, weight trend, recent PRs, alerts
     */
    fun getHomeScreenData(): Flow<HomeScreenData>
    
    /**
     * Get training screen data: active plan, upcoming sessions, session PRs
     */
    fun getTrainingScreenData(): Flow<TrainingScreenData>
    
    /**
     * Get progress screen data: weight history, effective sets, all PRs
     */
    fun getProgressScreenData(): Flow<ProgressScreenData>
}

// ============================================================================
// COMPOSITE DATA CLASSES (Return types for complex operations)
// ============================================================================

data class SessionReadinessData(
    val recentSessions: List<DomainTrainingSession>,
    val lastWeight: DomainWeightEntry?,
    val fatigueLevel: String,
    val readinessScore: Int
)

data class HomeScreenData(
    val tdeeResult: DomainTDEEResult?,
    val macroTargets: DomainMacroTargets?,
    val weightTrend: DomainWeightTrend?,
    val recentPRs: List<DomainPersonalRecord>,
    val alerts: List<DomainAlert>
)

data class TrainingScreenData(
    val activePlan: DomainWorkoutPlan?,
    val upcomingSessions: List<DomainPlanSession>,
    val recentSessionPRs: List<DomainPersonalRecord>
)

data class ProgressScreenData(
    val weightHistory: List<DomainWeightEntry>,
    val allTimePRs: List<DomainPersonalRecord>,
    val effectiveSetsData: List<DomainEffectiveSetsData>
)
