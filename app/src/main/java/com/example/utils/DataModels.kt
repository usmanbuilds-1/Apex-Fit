package com.example.utils

data class WeightEntry(val date: String, val weight: Double)
data class TrendPoint(val date: String, val raw: Double, val trend: Double)
data class NutritionEntry(val date: String, val calories: Int, val protein: Int, val carbs: Int, val fat: Int)
data class ExerciseSet(val weight: Double, val reps: Int, val rpe: Int = 7, val isWarmup: Boolean = false, val completed: Boolean = true)
data class ExerciseLog(val id: String, val name: String, val muscleGroup: String, val sets: List<ExerciseSet>)
data class TrainingSession(val date: String, val sessionType: String, val completed: Boolean, val sessionFeel: Int = 3, val durationMinutes: Int = 0, val exercises: List<ExerciseLog> = emptyList())
data class NutritionTargets(val calories: Int, val protein: Int, val carbs: Int, val fat: Int, val weeklyTrainingSessions: Int = 4)
data class TDEEResult(val tdee: Int?, val confidence: String, val avgCalories: Int, val weightChangeKg: Double)
data class ComplianceResult(val calories: Int, val protein: Int, val training: Int, val overall: Int, val weakestDay: String?)
data class PlateauResult(val plateau: Boolean, val severity: String = "", val interventions: List<String> = emptyList())
data class FatigueResult(val ratio: Double?, val status: String, val statusLabel: String, val recommendation: String, val acuteLoad: Double, val chronicLoad: Double)
data class PRResult(val hasPR: Boolean, val newPRs: List<PREntry> = emptyList())
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

