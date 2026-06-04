package com.example.ui.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiWeightEntry(
    val id: Long = 0,
    val weight: Double,
    val date: String,
    val time: String = ""
)

data class UiNutritionEntry(
    val id: Long = 0,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val timestamp: Long
) {
    val date: String get() = try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(timestamp))
    } catch(e: Exception) {
        ""
    }
    val time: String get() = try {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date(timestamp))
    } catch(e: Exception) {
        "12:00 PM"
    }
}

data class UiExerciseSet(
    val id: Long = 0,
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Boolean = false,
    val completed: Boolean = true,
    val exerciseName: String = "",
    val sessionId: Long = 0,
    val muscleGroup: String = ""
) {
    val effectiveSetValue: Double get() = try {
        com.example.utils.ProgressionEngine.calculateEffectiveSetValue(rpe)
    } catch(e: Exception) {
        0.0
    }
}

data class UiTrainingSession(
    val id: Long = 0,
    val name: String,
    val date: String,
    val duration: Int = 0,
    val feelRating: Int = 0,
    val isCompleted: Boolean = false
)

data class UiBodyMeasurement(
    val id: Long = 0,
    val bodyPart: String,
    val value: Double,
    val unit: String = "cm",
    val date: String
)

data class UiPersonalRecord(
    val id: Long = 0,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val estimatedOneRepMax: Double,
    val prType: String,
    val achievedAt: Long
)

data class UiPlateauResult(
    val isPlateau: Boolean,
    val daysStalled: Int,
    val recommendation: String
)

// Data -> Ui conversions
fun com.example.data.WeightEntry.toUi(): UiWeightEntry = UiWeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

fun com.example.data.NutritionEntry.toUi(): UiNutritionEntry {
    val dateStr = this.date
    val timeStr = this.time
    val ts = try {
        val format = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
        format.parse("$dateStr $timeStr")?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
    return UiNutritionEntry(
        id = this.id,
        name = this.name,
        calories = this.calories,
        protein = this.protein,
        carbs = this.carbs,
        fat = this.fat,
        timestamp = ts
    )
}

fun com.example.data.ExerciseSet.toUi(): UiExerciseSet = UiExerciseSet(
    id = this.id,
    weight = this.weight,
    reps = this.reps,
    rpe = this.rpe,
    isWarmup = this.isWarmup,
    completed = this.completed,
    exerciseName = this.exerciseName,
    sessionId = this.sessionId.toLongOrNull() ?: 0L,
    muscleGroup = this.muscleGroup
)

fun com.example.data.TrainingSession.toUi(): UiTrainingSession = UiTrainingSession(
    id = this.id.toLongOrNull() ?: 0L,
    name = this.sessionType,
    date = this.date,
    duration = this.durationMinutes,
    feelRating = this.sessionFeel,
    isCompleted = this.completed
)

fun com.example.data.BodyMeasurement.toUi(): UiBodyMeasurement = UiBodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

fun com.example.data.PersonalRecord.toUi(): UiPersonalRecord {
    val dateLong = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(this.date)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
    return UiPersonalRecord(
        id = 0L,
        exerciseName = this.exerciseId,
        weight = this.value,
        reps = 1,
        estimatedOneRepMax = this.value,
        prType = this.type,
        achievedAt = dateLong
    )
}

fun com.example.data.PlateauResult.toUi(): UiPlateauResult = UiPlateauResult(
    isPlateau = this.plateau,
    daysStalled = 0,
    recommendation = this.interventionRecommendation.ifBlank { this.interventions.joinToString(". ") }
)

// Ui -> Data conversions (for saving back)
fun UiWeightEntry.toData(): com.example.data.WeightEntry = com.example.data.WeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

fun UiNutritionEntry.toData(): com.example.data.NutritionEntry {
    val (dateStr, timeStr) = try {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)
        val d = Date(this.timestamp)
        Pair(sdfDate.format(d), sdfTime.format(d))
    } catch(e: Exception) {
        Pair("", "12:00 PM")
    }
    return com.example.data.NutritionEntry(
        id = this.id,
        date = dateStr,
        name = this.name,
        time = timeStr,
        calories = this.calories,
        protein = this.protein,
        carbs = this.carbs,
        fat = this.fat
    )
}

fun UiExerciseSet.toData(): com.example.data.ExerciseSet = com.example.data.ExerciseSet(
    id = this.id,
    sessionId = this.sessionId.toString(),
    exerciseId = "",
    exerciseName = this.exerciseName,
    muscleGroup = this.muscleGroup,
    weight = this.weight,
    reps = this.reps,
    rpe = this.rpe,
    isWarmup = this.isWarmup,
    completed = this.completed,
    repsInReserve = 2,
    effectiveSetValue = 0.0
)

fun UiTrainingSession.toData(): com.example.data.TrainingSession = com.example.data.TrainingSession(
    id = this.id.toString(),
    date = this.date,
    sessionType = this.name,
    completed = this.isCompleted,
    durationMinutes = this.duration,
    sessionFeel = this.feelRating
)

fun UiBodyMeasurement.toData(): com.example.data.BodyMeasurement = com.example.data.BodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

fun UiPersonalRecord.toData(): com.example.data.PersonalRecord = com.example.data.PersonalRecord(
    id = "${this.exerciseName}_${this.prType}",
    exerciseId = this.exerciseName,
    type = this.prType,
    value = this.weight,
    date = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this.achievedAt))
    } catch (e: Exception) {
        ""
    }
)

fun UiPlateauResult.toData(): com.example.data.PlateauResult = com.example.data.PlateauResult(
    isPlateaued = this.isPlateau,
    interventionRecommendation = this.recommendation,
    plateau = this.isPlateau,
    severity = "",
    interventions = emptyList()
)

data class UiComplianceResult(
    val calories: Int,
    val protein: Int,
    val training: Int,
    val overall: Int,
    val weakestDay: String?
)

data class UiFatigueResult(
    val ratio: Double?,
    val status: String,
    val statusLabel: String,
    val recommendation: String
)

data class UiDeloadResult(
    val recommendation: String,
    val urgency: String,
    val signals: Int,
    val protocol: List<String>
)

data class UiReadinessFactor(
    val name: String,
    val impact: String,
    val value: String
)

data class UiSessionReadiness(
    val score: Int,
    val label: String,
    val colorHex: String,
    val prediction: String,
    val recommendation: String,
    val factors: List<UiReadinessFactor>
)

fun com.example.utils.ComplianceResult.toUi(): UiComplianceResult = UiComplianceResult(
    calories = this.calories,
    protein = this.protein,
    training = this.training,
    overall = this.overall,
    weakestDay = this.weakestDay
)

fun UiComplianceResult.toData(): com.example.utils.ComplianceResult = com.example.utils.ComplianceResult(
    calories = this.calories,
    protein = this.protein,
    training = this.training,
    overall = this.overall,
    weakestDay = this.weakestDay
)

fun com.example.utils.FatigueResult.toUi(): UiFatigueResult = UiFatigueResult(
    ratio = this.ratio,
    status = this.status,
    statusLabel = this.statusLabel,
    recommendation = this.recommendation
)

fun UiFatigueResult.toData(): com.example.utils.FatigueResult = com.example.utils.FatigueResult(
    ratio = this.ratio,
    status = this.status,
    statusLabel = this.statusLabel,
    recommendation = this.recommendation,
    acuteLoad = 0.0,
    chronicLoad = 0.0
)

fun com.example.utils.DeloadResult.toUi(): UiDeloadResult = UiDeloadResult(
    recommendation = this.recommendation,
    urgency = this.urgency,
    signals = this.signals,
    protocol = this.protocol
)

fun UiDeloadResult.toData(): com.example.utils.DeloadResult = com.example.utils.DeloadResult(
    recommendation = this.recommendation,
    urgency = this.urgency,
    signals = this.signals,
    protocol = this.protocol
)

fun com.example.utils.ReadinessFactor.toUi(): UiReadinessFactor = UiReadinessFactor(
    name = this.name,
    impact = this.impact,
    value = this.value
)

fun UiReadinessFactor.toData(): com.example.utils.ReadinessFactor = com.example.utils.ReadinessFactor(
    name = this.name,
    impact = this.impact,
    value = this.value
)

fun com.example.utils.SessionReadiness.toUi(): UiSessionReadiness = UiSessionReadiness(
    score = this.score,
    label = this.label,
    colorHex = this.colorHex,
    prediction = this.prediction,
    recommendation = this.recommendation,
    factors = this.factors.map { it.toUi() }
)

fun UiSessionReadiness.toData(): com.example.utils.SessionReadiness = com.example.utils.SessionReadiness(
    score = this.score,
    label = this.label,
    colorHex = this.colorHex,
    prediction = this.prediction,
    recommendation = this.recommendation,
    factors = this.factors.map { it.toData() }
)

data class ChatMessage(
    val isUser: Boolean,
    val message: String
)

