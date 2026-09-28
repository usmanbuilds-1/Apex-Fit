package com.apexfit.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {

    // --- EXERCISES ---
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE target_muscle = :muscle ORDER BY name ASC")
    fun getExercisesByMuscle(muscle: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    // --- WORKOUT SESSIONS ---
    @Query("SELECT * FROM workout_sessions ORDER BY start_time_epoch DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE session_id = :id LIMIT 1")
    fun getSessionById(id: Long): Flow<WorkoutSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    // --- WORKOUT SETS ---
    @Query("SELECT * FROM workout_sets WHERE parent_session_id = :sessionId ORDER BY set_order ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE exercise_id = :exerciseId ORDER BY set_id DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    // --- BACKUP & EXPORT/IMPORT HELPERS ---
    @Query("SELECT * FROM workout_sessions ORDER BY start_time_epoch ASC")
    fun getAllSessionsDirect(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sets ORDER BY parent_session_id ASC, set_order ASC")
    fun getAllSetsDirect(): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionsSync(sessions: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSetsSync(sets: List<WorkoutSetEntity>)

    @Transaction
    fun restoreWorkoutData(sessions: List<WorkoutSessionEntity>, sets: List<WorkoutSetEntity>) {
        insertSessionsSync(sessions)
        insertSetsSync(sets)
    }
}