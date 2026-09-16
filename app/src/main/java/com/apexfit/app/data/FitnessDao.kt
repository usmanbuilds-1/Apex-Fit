package com.apexfit.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {

    // Weight Entry Queries
    @Query("SELECT * FROM body_weights ORDER BY date DESC, time DESC")
    fun getAllWeightEntriesFlow(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM body_weights WHERE date >= :since ORDER BY date DESC, time DESC")
    fun getWeightEntriesSinceFlow(since: String): Flow<List<WeightEntry>>

    @Query("SELECT * FROM body_weights ORDER BY date DESC, time DESC")
    suspend fun getAllWeightEntries(): List<WeightEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntry(entry: WeightEntry)

    @Query("DELETE FROM body_weights WHERE id = :id")
    suspend fun deleteWeightEntryById(id: Long)

    @Query("DELETE FROM body_weights WHERE date = :date")
    suspend fun deleteWeightEntry(date: String)

    // Nutrition Queries
    @Query("SELECT * FROM nutrition_logs ORDER BY date DESC, time DESC")
    fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>>

    @Query("""
        SELECT * FROM nutrition_logs
        WHERE date >= :since
        ORDER BY date DESC
    """)
    fun getNutritionEntriesSince(since: String): Flow<List<NutritionEntry>>

    @Query("SELECT * FROM nutrition_logs WHERE date = :date ORDER BY time DESC")
    fun getNutritionForDateFlow(date: String): Flow<List<NutritionEntry>>

    @Query("SELECT * FROM nutrition_logs WHERE date = :date ORDER BY time DESC")
    suspend fun getNutritionForDate(date: String): List<NutritionEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutritionEntry(entry: NutritionEntry)

    @Query("DELETE FROM nutrition_logs WHERE id = :id")
    suspend fun deleteNutritionEntryById(id: Long)

    @Query("DELETE FROM nutrition_logs WHERE date = :date")
    suspend fun deleteNutritionEntry(date: String)

    // Training Sessions
    @Query("SELECT * FROM workout_sessions ORDER BY date DESC")
    fun getAllTrainingSessionsFlow(): Flow<List<TrainingSession>>

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC")
    suspend fun getAllTrainingSessions(): List<TrainingSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainingSession(session: TrainingSession)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteTrainingSession(id: String)

    // Exercise Sets
    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getSetsForSessionFlow(sessionId: String): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getSetsForSession(sessionId: String): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets ORDER BY id DESC")
    suspend fun getAllExerciseSets(): List<ExerciseSet>

    @Query("""
        SELECT es.* FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.exerciseId = :exerciseId AND es.completed = 1
        ORDER BY ts.date DESC, es.id ASC
    """)
    suspend fun getLastSetsForExercise(exerciseId: String): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets WHERE exerciseId IN (:exerciseIds) ORDER BY exerciseId, id DESC")
    suspend fun getLastSetsForExercises(exerciseIds: List<String>): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId")
    fun getSetsForExerciseFlow(exerciseId: String): Flow<List<ExerciseSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSets(sets: List<ExerciseSet>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSet(set: ExerciseSet)

    @Query("DELETE FROM exercise_sets WHERE id = :id")
    suspend fun deleteExerciseSet(id: Long)

    @Query("DELETE FROM exercise_sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsForSession(sessionId: String)

    // Workout Plans
    @Query("SELECT * FROM workout_programs ORDER BY createdAt DESC")
    fun getAllPlansFlow(): Flow<List<WorkoutPlan>>

    @Query("SELECT * FROM workout_programs ORDER BY createdAt DESC")
    suspend fun getAllPlans(): List<WorkoutPlan>

    @Query("SELECT * FROM workout_programs WHERE isActive = 1 LIMIT 1")
    fun getActivePlanFlow(): Flow<WorkoutPlan?>

    @Query("SELECT * FROM workout_programs WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): WorkoutPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlan(plan: WorkoutPlan): Long

    @Query("UPDATE workout_programs SET isActive = 0")
    suspend fun deactivateAllPlans()

    @Query("UPDATE workout_programs SET isActive = 1 WHERE id = :planId")
    suspend fun activatePlan(planId: Long)



    @Query("DELETE FROM workout_programs")
    suspend fun deleteAllPlans()

    // Plan Sessions
    @Query("SELECT * FROM plan_sessions WHERE planId = :planId ORDER BY id ASC")
    fun getSessionsForPlanFlow(planId: Long): Flow<List<PlanSession>>

    @Query("SELECT * FROM plan_sessions WHERE planId = :planId ORDER BY id ASC")
    suspend fun getSessionsForPlan(planId: Long): List<PlanSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanSessions(sessions: List<PlanSession>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanSession(session: PlanSession): Long

    @Query("DELETE FROM plan_sessions WHERE planId = :planId")
    suspend fun deleteSessionsForPlan(planId: Long)



    // Exercise Library
    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllExercisesFlow(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    suspend fun getAllExercises(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchExercisesByName(query: String): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExerciseById(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getExercisesByIds(ids: List<String>): List<Exercise>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Query("SELECT * FROM exercise_metadata WHERE exercise_id = :exerciseId LIMIT 1")
    suspend fun getMetadataForExercise(exerciseId: String): ExerciseMetadata?

    @Query("SELECT * FROM exercise_metadata WHERE exercise_id IN (:exerciseIds)")
    suspend fun getMetadataForExercises(exerciseIds: List<String>): List<ExerciseMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseMetadata(metadata: ExerciseMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseMetadataList(metadataList: List<ExerciseMetadata>)

    @Query("SELECT COALESCE(stalledSessions, 0) FROM exercise_metadata WHERE exercise_id = :exerciseId")
    suspend fun getStalledCountForExercise(exerciseId: String): Int

    @Query("UPDATE exercise_metadata SET stalledSessions = :count WHERE exercise_id = :exerciseId")
    suspend fun updateStalledCount(exerciseId: String, count: Int)



    // Plan Exercises
    @Query("SELECT * FROM plan_exercises WHERE planSessionId = :planSessionId ORDER BY id ASC")
    fun getExercisesForSessionFlow(planSessionId: Long): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises ORDER BY id ASC")
    fun getAllPlanExercisesFlow(): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises WHERE planSessionId = :planSessionId ORDER BY id ASC")
    suspend fun getExercisesForSession(planSessionId: Long): List<PlanExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercises(exercises: List<PlanExercise>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercise(exercise: PlanExercise)

    @Query("DELETE FROM plan_exercises WHERE planSessionId = :planSessionId")
    suspend fun deleteExercisesForPlanSession(planSessionId: Long)

    // FIX (§9 item 5): canonicalize plan_exercises weight for imperial users on v22 upgrade
    @Query("UPDATE plan_exercises SET weight = weight / 2.20462, weight_unit = 'kg' WHERE weight_unit IN ('lb', 'lbs')")
    suspend fun convertAllPlanExerciseWeightsToKg()



    // Personal Records
    @Query("SELECT * FROM personal_records ORDER BY date DESC")
    fun getAllPRsFlow(): Flow<List<PersonalRecord>>

    @Query("""
        SELECT pr.*, e.name as exerciseName
        FROM personal_records pr
        LEFT JOIN exercises e ON pr.exerciseId = e.id
        ORDER BY pr.date DESC
    """)
    fun getPersonalRecordsWithNames(): Flow<List<PersonalRecordWithName>>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord>

    @Query("UPDATE exercise_sets SET weight = weight / 2.20462, weight_unit = 'kg' WHERE weight_unit IN ('lb', 'lbs')")
    suspend fun convertAllExerciseSetWeightsToKg()

    @Query("UPDATE personal_records SET value = value / 2.20462 WHERE type IN ('max_weight', 'estimated_1rm')")
    suspend fun convertAllPersonalRecordValuesToKg()



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalRecord(record: PersonalRecord)

    // Body Measurements
    @Query("SELECT * FROM body_measurements ORDER BY date DESC, id DESC")
    fun getAllBodyMeasurementsFlow(): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurements WHERE bodyPart = :bodyPart ORDER BY date ASC, id ASC")
    fun getBodyMeasurementsForPartFlow(bodyPart: String): Flow<List<BodyMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurement(measurement: BodyMeasurement)

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteBodyMeasurement(id: Long)



    // Detected Patterns
    @Query("SELECT * FROM detected_patterns ORDER BY confidence DESC")
    fun getAllDetectedPatternsFlow(): Flow<List<DetectedPatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedPatterns(patterns: List<DetectedPatternEntity>)

    @Query("DELETE FROM detected_patterns")
    suspend fun clearAllDetectedPatterns()

    // --- Required queries for Algorithm Engine ---

    @Query("SELECT * FROM workout_sessions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<TrainingSession>

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId ORDER BY id DESC")
    suspend fun getExerciseSetsByExerciseId(exerciseId: String): List<ExerciseSet>

    @Query("""
        SELECT weight FROM exercise_sets 
        WHERE exerciseId = :exerciseId AND isWarmup = 0 AND completed = 1
        ORDER BY id DESC LIMIT 1
    """)
    suspend fun getLastWeightForExercise(exerciseId: String): Double?

    @Query("""
        SELECT es.id, es.sessionId, es.exerciseId, es.exerciseName,
               es.muscleGroup, es.weight, es.reps, es.rpe, es.isWarmup,
               es.restTaken, es.completed, es.repsInReserve,
               ts.date AS date
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.exerciseId IN (:exerciseIds)
          AND es.completed = 1
        ORDER BY es.id DESC
    """)
    suspend fun getLastSetsForExercises(
        exerciseIds: List<String>
    ): List<LastSetWithDate>

    @Query("""
        SELECT es.id, es.sessionId, es.exerciseId, es.exerciseName,
               es.muscleGroup, es.weight, es.reps, es.rpe, es.isWarmup,
               es.restTaken, es.completed, es.repsInReserve,
               ts.date AS date 
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.exerciseId = :exerciseId AND es.completed = 1
        ORDER BY es.id DESC LIMIT 1
    """)
    suspend fun getLastSetForExercise(exerciseId: String): LastSetWithDate?

    @Query("""
        SELECT COALESCE(SUM(es.weight * es.reps), 0.0)
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.muscleGroup = :muscleGroup
          AND ts.date >= :startDate
          AND ts.date <= :endDate
          AND es.isWarmup = 0
          AND es.completed = 1
    """)
    suspend fun getMuscleGroupVolumeByDateRange(
        muscleGroup: String,
        startDate: String,
        endDate: String
    ): Double

    @Query("""
        SELECT * FROM workout_sessions 
        WHERE sessionType = :sessionType AND completed = 1
        ORDER BY date DESC LIMIT 1
    """)
    suspend fun getLatestSessionByType(sessionType: String): TrainingSession?

    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY date DESC")
    suspend fun getAllCompletedSessions(): List<TrainingSession>

    @Query("""  
        SELECT es.* FROM exercise_sets es  
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id  
        WHERE ws.completed = 1  
        ORDER BY es.id ASC  
    """)  
    suspend fun getAllExerciseSetsForCompletedSessions(): List<ExerciseSet>

    @Query("""
        SELECT * FROM body_measurements 
        WHERE bodyPart = :bodyPart 
        ORDER BY date ASC
    """)
    suspend fun getBodyMeasurementsByBodyPart(bodyPart: String): List<BodyMeasurement>

    // Flow versions for reactive UI
    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY date DESC")
    fun getAllCompletedSessionsFlow(): Flow<List<TrainingSession>>

    @Query("""
        SELECT * FROM workout_sessions
        WHERE completed = 1 AND date >= :cutoffDate
        ORDER BY date DESC
    """)
    fun getRecentCompletedSessionsFlow(cutoffDate: String): Flow<List<TrainingSession>>

    @Query("""
        SELECT * FROM workout_sessions
        WHERE completed = 1 AND date >= :cutoffDate
        ORDER BY date DESC
    """)
    suspend fun getRecentCompletedSessions(cutoffDate: String): List<TrainingSession>

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId AND isWarmup = 0 AND completed = 1 ORDER BY id DESC")
    fun getExerciseSetsByExerciseIdFlow(exerciseId: String): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM exercise_sets WHERE completed = 1 ORDER BY id DESC")
    fun getAllExerciseSetsFlow(): Flow<List<ExerciseSet>>

    @Query("""  
        SELECT es.* FROM exercise_sets es  
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id  
        WHERE ws.date >= :cutoffDate AND ws.completed = 1  
        ORDER BY es.id DESC  
    """)  
    fun getRecentExerciseSetsFlow(cutoffDate: String): Flow<List<ExerciseSet>>

    @Query("""  
        SELECT es.* FROM exercise_sets es  
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id  
        WHERE ws.date >= :cutoffDate AND ws.completed = 1  
        ORDER BY es.id DESC  
    """)  
    suspend fun getRecentExerciseSets(cutoffDate: String): List<ExerciseSet>

    @Query("""
        SELECT es.id, es.sessionId, es.exerciseId, es.exerciseName,
               es.muscleGroup, es.weight, es.reps, es.rpe, es.isWarmup,
               es.restTaken, es.completed, es.repsInReserve,
               ts.date AS date 
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.exerciseId = :exerciseId AND es.rpe = :rpe AND es.completed = 1
        ORDER BY es.id DESC LIMIT :limit
    """)
    suspend fun getExerciseSetsByExerciseIdAndRPE(exerciseId: String, rpe: Int, limit: Int): List<LastSetWithDate>

    /**
     * Batched progression data query for workout start optimization.
     * 
     * For each exercise ID in the request, returns:
     * - All completed, non-warmup sets from that exercise's latest completed session
     * - That exercise's stalledSessions count
     * 
     * Returns no rows for exercises with no completed working-set history.
     * Latest session is determined by: session date DESC, session id DESC (tie-breaker).
     * Sets within latest session ordered by id ASC (insertion order).
     *
     * This eliminates 2*N per-exercise queries during startSession.
     */
    @Query("""
        SELECT 
            es.id, es.sessionId, es.exerciseId, es.exerciseName,
            es.muscleGroup, es.weight, es.reps, es.rpe, es.isWarmup,
            es.restTaken, es.completed, es.repsInReserve,
            ts.date AS date,
            COALESCE(em.stalledSessions, 0) AS stalledSessions
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        LEFT JOIN exercise_metadata em ON es.exerciseId = em.exercise_id
        WHERE es.exerciseId IN (:exerciseIds)
          AND es.completed = 1
          AND es.isWarmup = 0
          AND ts.id = (
              SELECT ts2.id FROM workout_sessions ts2
              INNER JOIN exercise_sets es2 ON ts2.id = es2.sessionId
              WHERE es2.exerciseId = es.exerciseId
                AND es2.completed = 1
                AND es2.isWarmup = 0
              ORDER BY ts2.date DESC, ts2.id DESC
              LIMIT 1
          )
        ORDER BY es.exerciseId ASC, es.id ASC
    """)
    suspend fun getProgressionDataForExercises(
        exerciseIds: List<String>
    ): List<ProgressionSetWithStalled>

    @Transaction
    suspend fun insertSessionAtomic(session: TrainingSession, sets: List<ExerciseSet>) {
        insertTrainingSession(session)
        insertExerciseSets(sets)
    }

    @Transaction
    suspend fun insertSessionWithPRsAtomic(session: TrainingSession, sets: List<ExerciseSet>, prs: List<PersonalRecord>) {
        insertTrainingSession(session)
        insertExerciseSets(sets)
        for (pr in prs) {
            insertPersonalRecord(pr)
        }
    }

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE date = :date AND completed = 1")
    fun getCompletedSessionCountFlow(date: String): Flow<Int>
}

/**
 * Data class for batched progression query result.
 * Combines ExerciseSet fields with stalledSessions count for progression engine input.
 */
data class ProgressionSetWithStalled(
    val id: Long,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Int,
    val restTaken: Int,
    val completed: Int,
    val repsInReserve: Int,
    val date: String,
    val stalledSessions: Int
) {
    fun toExerciseSet(): ExerciseSet = ExerciseSet(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        muscleGroup = muscleGroup,
        weight = weight,
        reps = reps,
        rpe = rpe,
        isWarmup = isWarmup != 0,
        restTaken = restTaken,
        completed = completed != 0,
        repsInReserve = repsInReserve,
        effectiveSetValue = 0.0,
        weightUnit = "kg"
    )
}
