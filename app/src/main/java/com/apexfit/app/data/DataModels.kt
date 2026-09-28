package com.apexfit.app.data

import androidx.room.*

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true), Index(value = ["target_muscle"])]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "target_muscle")
    val targetMuscle: String,

    @ColumnInfo(name = "secondary_muscles")
    val secondaryMuscles: List<String> = emptyList(),

    @ColumnInfo(name = "equipment_needed")
    val equipmentNeeded: String,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["start_time_epoch"]), Index(value = ["routine_id"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,

    @ColumnInfo(name = "routine_id")
    val routineId: Long?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "start_time_epoch")
    val startTimeEpoch: Long,

    @ColumnInfo(name = "end_time_epoch")
    val endTimeEpoch: Long?,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long = 0,

    @ColumnInfo(name = "total_volume_kg")
    val totalVolumeKg: Double = 0.0,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["parent_session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["parent_session_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["parent_session_id", "exercise_id"])
    ]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "set_id")
    val setId: Long = 0,

    @ColumnInfo(name = "parent_session_id")
    val parentSessionId: Long,

    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,

    @ColumnInfo(name = "set_order")
    val setOrder: Int,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,

    @ColumnInfo(name = "reps_completed")
    val repsCompleted: Int,

    @ColumnInfo(name = "rpe")
    val rpe: Double?,

    @ColumnInfo(name = "is_warmup")
    val isWarmup: Boolean = false,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = true
)