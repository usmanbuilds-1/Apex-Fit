package com.example.domain.model

// ============================================================================
// DOMAIN MODELS — Pure Kotlin, framework-agnostic
// No Room annotations, no Android imports, no database coupling
// These are what the UI layer sees and works with
// ============================================================================

// ============================================================================
// CORE TRAINING MODELS
// ============================================================================

data class DomainExerciseSet(
    val id: Long = 0,
    val sessionId: String = "",
    val exerciseId: String,
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val repsInReserve: Int,
    val isWarmup: Boolean,
    val restTaken: Int,
    val completed: Boolean,
    val effectiveSetValue: Double = 0.0
)

data class DomainTrainingSession(
    val id: String,
    val date: String,              // YYYY-MM-DD
    val sessionType: String,       // "Upper A", "Lower B"
    val completed: Boolean,
    val durationMinutes: Int,
    val sessionFeel: Int           // 1-5
)

data class DomainWorkoutPlan(
    val id: Long = 0,
    val name: String,
    val goal: String,              // "muscle", "strength", "endurance"
    val isActive: Boolean,
    val createdAt: Long
)

data class DomainPlanSession(
    val id: Long = 0,
    val planId: Long,
    val label: String,             // "Upper A", "Lower B"
    val day: String,               // "Monday"
    val focus: String              // Muscle group description
)

data class DomainPlanExercise(
    val id: Long = 0,
    val planSessionId: Long,
    val name: String,
    val muscleGroup: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weight: Double,
    val restSeconds: Int,
    val notes: String = ""
)

// ============================================================================
// PERSONAL RECORDS
// ============================================================================

data class DomainPersonalRecord(
    val id: Long = 0,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val estimatedOneRepMax: Double,
    val prType: String,            // "max_weight", "max_1rm"
    val achievedAt: Long,
    val workoutSessionId: String? = null
)

// ============================================================================
// BODY TRACKING
// ============================================================================

data class DomainWeightEntry(
    val id: Long = 0,
    val weight: Double,
    val date: String,              // YYYY-MM-DD
    val time: String = ""
)

data class DomainWeightTrend(
    val date: String,
    val weight: Double,
    val trendValue: Double,        // 14-day EMA
    val confidence: String         // "High", "Medium", "Low"
)

data class DomainBodyMeasurement(
    val id: Long = 0,
    val bodyPart: String,          // "chest", "waist", "biceps"
    val value: Double,
    val unit: String = "cm",
    val date: String
)

// ============================================================================
// NUTRITION
// ============================================================================

data class DomainNutritionEntry(
    val id: Long = 0,
    val date: String,
    val time: String = "",
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val mealName: String = ""
)

data class DomainMacroTargets(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

// ============================================================================
// ALGORITHM RESULTS (What UI receives from use cases)
// ============================================================================

data class DomainTDEEResult(
    val bmr: Double,               // Basal metabolic rate
    val tdee: Double,              // Total daily energy expenditure
    val activityMultiplier: Double,
    val confidence: String         // "High", "Medium", "Low"
)

data class DomainAdaptiveTDEE(
    val baselineTDEE: Double,
    val adjustedTDEE: Double,      // Based on weight trend
    val weeklyWeightChange: Double,
    val direction: String          // "up", "down", "stable"
)

data class DomainPlateauResult(
    val plateau: Boolean,
    val daysSinceLastProgress: Int,
    val compliancePercentage: Int,
    val recommendation: String
)

data class DomainFatigueRatio(
    val ratio: Double,             // ACR (Acute:Chronic)
    val severity: String,          // "green", "yellow", "red"
    val recommendation: String
)

data class DomainSessionReadiness(
    val readinessScore: Int,       // 0-100
    val acr: Double,
    val fatigue: DomainFatigueRatio,
    val canTrain: Boolean,
    val recommendation: String
)

data class DomainEffectiveSetsData(
    val exerciseId: String,
    val currentEffectiveSets: Double,
    val targetEffectiveSets: Double,
    val lastSetEffectiveness: Double,
    val lastSetRPE: Int,
    val progress: Double           // percent
)

data class DomainPRCheckResult(
    val isNewMaxWeight: Boolean,
    val isNewMax1RM: Boolean,
    val lastMaxWeight: Double?,
    val lastMax1RM: Double?,
    val currentVolume: Double,
    val currentEstimated1RM: Double
)

// ============================================================================
// PATTERNS (Athletic System Monitor)
// ============================================================================

data class DomainDetectedPattern(
    val id: Long = 0,
    val patternType: String,       // "overtraining", "plateau", etc.
    val description: String,
    val severity: String,          // "critical", "warning", "info"
    val detectedAt: Long,
    val resolved: Boolean = false
)

data class DomainAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,          // "critical", "warning", "info"
    val actionable: Boolean,
    val suggestedAction: String = "",
    val createdAt: Long
)

// ============================================================================
// IN-MEMORY SESSION STATE (During active workout)
// ============================================================================

data class DomainActiveSet(
    val exerciseId: String,
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val repsInReserve: Int,
    val restTaken: Int,
    val completed: Boolean
)

data class DomainActiveExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val sets: List<DomainActiveSet>
)

data class DomainActiveSession(
    val sessionId: String,
    val sessionType: String,
    val exercises: List<DomainActiveExercise>,
    val startTime: Long,
    val sessionPRs: List<DomainPersonalRecord> = emptyList()
)

// ============================================================================
// UI STATE (What screens receive)
// ============================================================================

data class DomainHomeScreenState(
    val tdee: DomainTDEEResult?,
    val macroTargets: DomainMacroTargets?,
    val weightTrend: DomainWeightTrend?,
    val sessionReadiness: DomainSessionReadiness?,
    val recentPRs: List<DomainPersonalRecord>,
    val alerts: List<DomainAlert>,
    val isLoading: Boolean,
    val errorMessage: String? = null
)

data class DomainTrainScreenState(
    val activePlan: DomainWorkoutPlan?,
    val upcomingSessions: List<DomainPlanSession>,
    val activeSession: DomainActiveSession?,
    val sessionPRs: List<DomainPersonalRecord>,
    val isLoading: Boolean,
    val errorMessage: String? = null
)

data class DomainProgressScreenState(
    val weightHistory: List<DomainWeightTrend>,
    val effectiveSets: List<DomainEffectiveSetsData>,
    val allTimePRs: List<DomainPersonalRecord>,
    val muscleImbalance: Map<String, Double>,
    val isLoading: Boolean
)

// ============================================================================
// PROGRESSION ENGINE OUTPUTS
// ============================================================================

data class DomainProgressiveWeight(
    val suggestedWeight: Double,
    val lastWeight: Double?,
    val lastRPE: Int?,
    val daysSinceLastSession: Int?,
    val reasoning: String
)

data class DomainRestRecommendation(
    val baseRestSeconds: Int,
    val rpeAdjustmentSeconds: Int,
    val recoveryAdjustmentSeconds: Int,
    val totalRestSeconds: Int,
    val exerciseType: String
)
