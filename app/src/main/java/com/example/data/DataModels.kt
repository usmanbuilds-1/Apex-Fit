package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val time: String, // hh:mm a
    val weight: Double
)

data class TrendPoint(
    val date: String,
    val weight: Double,
    val trendWeight: Double
)

@Entity(tableName = "nutrition_entries")
data class NutritionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val name: String, // Food/meal name description
    val time: String, // hh:mm a
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey val id: String, // unique session UUID or string
    val date: String, // YYYY-MM-DD
    val sessionType: String, // e.g. "Upper A"
    val completed: Boolean,
    val durationMinutes: Int,
    val sessionFeel: Int // 1 to 5
)

@Entity(tableName = "exercise_sets")
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String = "",
    val exerciseId: String,
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Boolean,
    val restTaken: Int,
    val completed: Boolean,
    val repsInReserve: Int = 2,
    val effectiveSetValue: Double = 0.0
)

data class SessionResult(
    val sessionFeel: Int,
    val durationMinutes: Int,
    val exercises: List<ExerciseSet>
)

sealed class AlgorithmOutput {
    object Progressing : AlgorithmOutput()
    object Maintaining : AlgorithmOutput()
    object Fatigued : AlgorithmOutput()
    object Stalled : AlgorithmOutput()
}

data class PlateauResult(
    val isPlateaued: Boolean,
    val interventionRecommendation: String // Deload Week, Rep Range Shift, Exercise Swap, Volume Increase
)

data class FatigueRatio(
    val ratio: Double, // acute to chronic volume ratio
    val riskStatus: String // Low, Medium, High
)

@Entity(tableName = "weekly_reports")
data class WeeklyReport(
    @PrimaryKey val weekStart: String, // YYYY-MM-DD
    val score: Int,
    val geminiResponse: String
)

data class PRResult(
    val exerciseId: String,
    val type: String, // max_weight, volume, estimated_1rm
    val previousValue: Double,
    val newValue: Double,
    val isNewRecord: Boolean
)

data class VolumeData(
    val muscleGroup: String,
    val setVolume: Int,
    val effectiveSets: Int,
    val hypertrophicScore: Double
)

@Entity(tableName = "workout_plans")
data class WorkoutPlan(
    @PrimaryKey val id: Long,
    val name: String,
    val goal: String,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "plan_sessions")
data class PlanSession(
    @PrimaryKey val id: Long,
    val planId: Long,
    val label: String, // e.g., "Upper A"
    val day: String, // e.g., "Monday"
    val focus: String // Muscle group description
)

@Entity(tableName = "plan_exercises")
data class PlanExercise(
    @PrimaryKey val id: Long,
    val planSessionId: Long,
    val name: String,
    val muscleGroup: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weight: Double,
    val restSeconds: Int,
    val notes: String
)

@Entity(tableName = "personal_records")
data class PersonalRecord(
    @PrimaryKey val id: String, // exerciseId_type
    val exerciseId: String,
    val type: String, // max_weight, volume, estimated_1rm
    val value: Double,
    val date: String
)

@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyPart: String, // Neck, Shoulders, Chest, Left Bicep, etc.
    val value: Double,
    val unit: String, // "cm" or "inches"
    val date: String // YYYY-MM-DD
)

data class MuscleRecoveryStatus(
    val muscleGroup: String,
    val recoveryPercentage: Int, // 0 to 100
    val lastExercise: String,
    val lastTrainingDate: String?,
    val requiredHours: Int
)

@Entity(tableName = "detected_patterns")
data class DetectedPatternEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val description: String,
    val confidence: Float,
    val actionable: String,
    val detectedAt: String
)

// ── Active session state — lives in memory during a workout ──────

data class ActiveSet(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Int = 7,
    val isWarmup: Boolean = false,
    val restTakenSeconds: Int = 0,
    val completedAt: Long = 0L,
    val completed: Boolean = false,
    val repsInReserve: Int = 2
)

data class ActiveExercise(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: MutableList<ActiveSet>
)

data class ActiveSession(
    val planSessionId: String,
    val sessionType: String,
    val startTime: Long = System.currentTimeMillis(),
    val exercises: MutableList<ActiveExercise>
)

data class LastSetWithDate(
    val id: Long,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Boolean,
    val restTaken: Int,
    val completed: Boolean,
    val date: String,
    val repsInReserve: Int = 2
)

data class EffectiveSetsData(
    val exerciseId: String,
    val currentEffectiveSets: Double,  // e.g., 2.4
    val targetEffectiveSets: Double,   // e.g., 12.0
    val lastSetEffectiveness: Double,  // e.g., 0.75
    val lastSetRPE: Int,
    val progress: Double               // e.g., 20.0 (percent)
)

