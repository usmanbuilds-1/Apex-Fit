package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val time: String = "12:00 PM", // hh:mm a
    val weight: Double
) {
    constructor(date: String, weight: Double) : this(id = 0, date = date, time = "12:00 PM", weight = weight)
}

data class TrendPoint(
    val date: String,
    val weight: Double,
    val trendWeight: Double
) {
    val raw: Double get() = weight
    val trend: Double get() = trendWeight
}

@Entity(tableName = "nutrition_entries")
data class NutritionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val name: String = "Logged Meal", // Food/meal name description
    val time: String = "12:00 PM", // hh:mm a
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    constructor(date: String, calories: Int, protein: Int, carbs: Int, fat: Int) : this(
        id = 0,
        date = date,
        name = "Logged Meal",
        time = "12:00 PM",
        calories = calories,
        protein = protein.toDouble(),
        carbs = carbs.toDouble(),
        fat = fat.toDouble()
    )
}

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
    val exerciseId: String = "",
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Boolean,
    val restTaken: Int = 0,
    val completed: Boolean,
    val repsInReserve: Int = 2,
    val effectiveSetValue: Double = 0.0
) {
    constructor(weight: Double, reps: Int, rpe: Int = 7, isWarmup: Boolean = false, completed: Boolean = true) : this(
        id = 0,
        sessionId = "",
        exerciseId = "",
        exerciseName = "",
        muscleGroup = "",
        weight = weight,
        reps = reps,
        rpe = rpe,
        isWarmup = isWarmup,
        restTaken = 0,
        completed = completed,
        repsInReserve = 2,
        effectiveSetValue = 0.0
    )
}

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
    val isPlateaued: Boolean = false,
    val interventionRecommendation: String = "", // Deload Week, Rep Range Shift, Exercise Swap, Volume Increase
    val plateau: Boolean = isPlateaued,
    val severity: String = "",
    val interventions: List<String> = emptyList()
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
    val hasPR: Boolean = false,
    val newPRs: List<PREntry> = emptyList(),
    val exerciseId: String = "",
    val type: String = "", // max_weight, volume, estimated_1rm
    val previousValue: Double = 0.0,
    val newValue: Double = 0.0,
    val isNewRecord: Boolean = hasPR
) {
    constructor(exerciseId: String, type: String, previousValue: Double, newValue: Double, isNewRecord: Boolean) : this(
        hasPR = isNewRecord,
        newPRs = emptyList(),
        exerciseId = exerciseId,
        type = type,
        previousValue = previousValue,
        newValue = newValue,
        isNewRecord = isNewRecord
    )
}

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

// Consolidated Utility and Algorithm Models
data class NutritionTargets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val weeklyTrainingSessions: Int = 4)
data class TDEEResult(val tdee: Int?, val confidence: String, val avgCalories: Int, val weightChangeKg: Double)
data class ComplianceResult(val calories: Int, val protein: Int, val training: Int, val overall: Int, val weakestDay: String?)
data class FatigueResult(val ratio: Double?, val status: String, val statusLabel: String, val recommendation: String, val acuteLoad: Double, val chronicLoad: Double)
data class PREntry(val type: String, val label: String, val value: String, val previous: String)
data class DiminishingResult(val status: String, val message: String, val suggestions: List<String> = emptyList(), val slope: Double = 0.0)
data class WeakPoint(val muscle: String, val displayName: String, val overallScore: Int, val trend: String)
data class DeloadResult(val recommendation: String, val urgency: String, val signals: Int, val protocol: List<String> = emptyList())
data class HeatmapEntry(val volume: Int, val intensity: Int, val level: String, val colorHex: String)

data class DetectedPattern(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val confidence: Float,
    val actionable: String,
    val detectedAt: String
)

data class SessionReadiness(
    val score: Int,
    val label: String,
    val colorHex: String,
    val prediction: String,
    val recommendation: String,
    val factors: List<ReadinessFactor>
)

data class ReadinessFactor(
    val name: String,
    val impact: String,
    val value: String
)

data class PersonalPattern(
    val dayOfWeek: String,
    val avgCalories: Double,
    val avgProtein: Double,
    val avgCompliance: Double
)

data class SleepEntry(
    val date: String,
    val hours: Double,
    val quality: Int
)

data class UserIntelligenceProfile(
    val totalSessionsLogged: Int,
    val avgWeeklyCompliance: Int,
    val strongestDay: String,
    val weakestDay: String,
    val bestPerformingSessionType: String,
    val personalMEV: Map<String, Int>,
    val personalMAV: Map<String, Int>,
    val detectedPatterns: List<DetectedPattern>,
    val dataRichness: String,
    val lastUpdated: String
)

data class StreakInfo(val current: Int, val max: Int = 0)
data class StreakResult(val nutrition: StreakInfo, val training: StreakInfo = StreakInfo(0))

data class ExerciseLog(val id: String, val name: String, val muscleGroup: String, val sets: List<com.example.data.ExerciseSet>)

data class RichTrainingSession(
    val date: String,
    val sessionType: String,
    val completed: Boolean,
    val sessionFeel: Int = 3,
    val durationMinutes: Int = 0,
    val exercises: List<ExerciseLog> = emptyList()
)


