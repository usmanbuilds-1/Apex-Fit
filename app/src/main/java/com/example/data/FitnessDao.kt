package com.example.data

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
        WHERE (LOWER(es.exerciseName) = LOWER(:exerciseName) OR LOWER(es.exerciseId) = LOWER(:exerciseName)) AND es.completed = 1
        ORDER BY ts.date DESC, es.id ASC
    """)
    suspend fun getLastSetsForExercise(exerciseName: String): List<ExerciseSet>

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
    suspend fun insertWorkoutPlan(plan: WorkoutPlan)

    @Query("UPDATE workout_programs SET isActive = 0")
    suspend fun deactivateAllPlans()



    @Query("DELETE FROM workout_programs WHERE id = :planId")
    suspend fun deleteWorkoutPlan(planId: Long)

    // Plan Sessions
    @Query("SELECT * FROM plan_sessions WHERE planId = :planId ORDER BY id ASC")
    fun getSessionsForPlanFlow(planId: Long): Flow<List<PlanSession>>

    @Query("SELECT * FROM plan_sessions WHERE planId = :planId ORDER BY id ASC")
    suspend fun getSessionsForPlan(planId: Long): List<PlanSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanSessions(sessions: List<PlanSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanSession(session: PlanSession)

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



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<Exercise>)

    @Query("SELECT * FROM exercise_metadata WHERE exercise_id = :exerciseId LIMIT 1")
    suspend fun getMetadataForExercise(exerciseId: String): ExerciseMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseMetadata(metadata: ExerciseMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseMetadataList(metadataList: List<ExerciseMetadata>)



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



    // Personal Records
    @Query("SELECT * FROM personal_records ORDER BY date DESC")
    fun getAllPRsFlow(): Flow<List<PersonalRecord>>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalRecord(record: PersonalRecord)

    // Weekly Reports
    @Query("SELECT * FROM weekly_reports ORDER BY weekStart DESC")
    fun getAllWeeklyReportsFlow(): Flow<List<WeeklyReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyReport(report: WeeklyReport)

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
        SELECT es.*, ts.date as date 
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
        SELECT * FROM body_measurements 
        WHERE bodyPart = :bodyPart 
        ORDER BY date ASC
    """)
    suspend fun getBodyMeasurementsByBodyPart(bodyPart: String): List<BodyMeasurement>

    // Flow versions for reactive UI
    @Query("SELECT * FROM workout_sessions WHERE completed = 1 ORDER BY date DESC")
    fun getAllCompletedSessionsFlow(): Flow<List<TrainingSession>>

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId AND isWarmup = 0 AND completed = 1 ORDER BY id DESC")
    fun getExerciseSetsByExerciseIdFlow(exerciseId: String): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM exercise_sets WHERE completed = 1 ORDER BY id DESC")
    fun getAllExerciseSetsFlow(): Flow<List<ExerciseSet>>

    @Query("""
        SELECT es.*, ts.date as date 
        FROM exercise_sets es
        INNER JOIN workout_sessions ts ON es.sessionId = ts.id
        WHERE es.exerciseId = :exerciseId AND es.rpe = :rpe AND es.completed = 1
        ORDER BY es.id DESC LIMIT :limit
    """)
    suspend fun getExerciseSetsByExerciseIdAndRPE(exerciseId: String, rpe: Int, limit: Int): List<LastSetWithDate>

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
}

