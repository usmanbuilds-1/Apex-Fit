package com.apexfit.app.ui.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Immutable


@Immutable
data class UiWeightEntry(
    val id: Long = 0,
    val weight: Double,
    val date: String,
    val time: String = ""
)

@Immutable
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
        com.apexfit.app.utils.DateTimeUtils.formatDate(java.util.Date(timestamp))
    } catch(e: Exception) {
        ""
    }
    val time: String get() = try {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(timestamp))
    } catch(e: Exception) {
        "12:00"
    }
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

@Immutable
data class UiExerciseSet(
    val id: Long = 0,
    val weight: Double,
    val reps: Int,
    val rpe: Int,
    val isWarmup: Boolean = false,
    val completed: Boolean = true,
    val exerciseName: String = "",
    val sessionId: String = "",
    val muscleGroup: String = "",
    val exerciseId: String = "",
    val repsInReserve: Int = 2,
    val effectiveSetValue: Double = try {
        com.apexfit.app.utils.ProgressionEngine.calculateEffectiveSetValue(rpe)
    } catch(e: Exception) {
        0.0
    },
    val restTaken: Int = 0
)

@Immutable
data class UiTrainingSession(
    val id: String = "",
    val name: String,
    val date: String,
    val duration: Int = 0,
    val feelRating: Int = 0,
    val isCompleted: Boolean = false
)

@Immutable
data class UiBodyMeasurement(
    val id: Long = 0,
    val bodyPart: String,
    val value: Double,
    val unit: String = "cm",
    val date: String
)

@Immutable
data class UiPersonalRecord(
    val id: Long = 0,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val estimatedOneRepMax: Double,
    val prType: String,
    val achievedAt: Long
)

@Immutable
data class UiPlateauResult(
    val isPlateau: Boolean,
    val daysStalled: Int,
    val recommendation: String,
    val severity: String = "",
    val interventions: List<String> = emptyList()
)

// Data -> Ui conversions
fun com.apexfit.app.data.WeightEntry.toUi(): UiWeightEntry = UiWeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

fun com.apexfit.app.data.NutritionEntry.toUi(): UiNutritionEntry {
    val dateStr = this.date
    val timeStr = this.time
    val ts = try {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
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

fun com.apexfit.app.data.ExerciseSet.toUi(): UiExerciseSet = UiExerciseSet(
    id = this.id,
    weight = this.weight,
    reps = this.reps,
    rpe = this.rpe,
    isWarmup = this.isWarmup,
    completed = this.completed,
    exerciseName = this.exerciseName,
    sessionId = this.sessionId,
    muscleGroup = this.muscleGroup,
    exerciseId = this.exerciseId,
    repsInReserve = this.repsInReserve,
    effectiveSetValue = this.effectiveSetValue,
    restTaken = this.restTaken
)

fun com.apexfit.app.data.TrainingSession.toUi(): UiTrainingSession = UiTrainingSession(
    id = this.id,
    name = this.sessionType,
    date = this.date,
    duration = this.durationMinutes,
    feelRating = this.sessionFeel,
    isCompleted = this.completed
)

fun com.apexfit.app.data.BodyMeasurement.toUi(): UiBodyMeasurement = UiBodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

fun slugToDisplayName(slug: String): String =
    slug.split("-").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

fun com.apexfit.app.data.PersonalRecord.toUi(): UiPersonalRecord {
    val dateLong = try {
        com.apexfit.app.utils.DateTimeUtils.parseDate(this.date)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
    return UiPersonalRecord(
        id = 0L,
        exerciseName = slugToDisplayName(this.exerciseId),
        weight = this.value,
        reps = 1,
        estimatedOneRepMax = this.value,
        prType = this.type,
        achievedAt = dateLong
    )
}

fun com.apexfit.app.data.PersonalRecordWithName.toUi(): UiPersonalRecord {
    val dateLong = try {
        com.apexfit.app.utils.DateTimeUtils.parseDate(this.record.date)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
    return UiPersonalRecord(
        id = 0L,
        exerciseName = this.displayName ?: slugToDisplayName(this.record.exerciseId),
        weight = this.record.value,
        reps = 1,
        estimatedOneRepMax = this.record.value,
        prType = this.record.type,
        achievedAt = dateLong
    )
}

fun com.apexfit.app.data.PlateauResult.toUi(): UiPlateauResult = UiPlateauResult(
    isPlateau = this.isPlateaued,
    daysStalled = this.daysStalled,
    recommendation = this.interventionRecommendation.ifBlank { this.interventions.joinToString(". ") },
    severity = this.severity,
    interventions = this.interventions
)

// Ui -> Data conversions (for saving back)
fun UiWeightEntry.toData(): com.apexfit.app.data.WeightEntry = com.apexfit.app.data.WeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

fun UiNutritionEntry.toData(): com.apexfit.app.data.NutritionEntry {
    val (dateStr, timeStr) = try {
        val sdfTime = SimpleDateFormat("HH:mm", Locale.US)
        val d = Date(this.timestamp)
        Pair(com.apexfit.app.utils.DateTimeUtils.formatDate(d), sdfTime.format(d))
    } catch(e: Exception) {
        Pair("", "12:00")
    }
    return com.apexfit.app.data.NutritionEntry(
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

fun UiExerciseSet.toData(): com.apexfit.app.data.ExerciseSet = com.apexfit.app.data.ExerciseSet(
    id = this.id,
    sessionId = this.sessionId,
    exerciseId = this.exerciseId,
    exerciseName = this.exerciseName,
    muscleGroup = this.muscleGroup,
    weight = this.weight,
    reps = this.reps,
    rpe = this.rpe,
    isWarmup = this.isWarmup,
    restTaken = this.restTaken,
    completed = this.completed,
    repsInReserve = this.repsInReserve,
    effectiveSetValue = this.effectiveSetValue
)

fun UiTrainingSession.toData(): com.apexfit.app.data.TrainingSession = com.apexfit.app.data.TrainingSession(
    id = this.id,
    date = this.date,
    sessionType = this.name,
    completed = this.isCompleted,
    durationMinutes = this.duration,
    sessionFeel = this.feelRating
)

fun UiBodyMeasurement.toData(): com.apexfit.app.data.BodyMeasurement = com.apexfit.app.data.BodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

fun UiPersonalRecord.toData(): com.apexfit.app.data.PersonalRecord = com.apexfit.app.data.PersonalRecord(
    id = "${this.exerciseName}_${this.prType}",
    exerciseId = this.exerciseName,
    type = this.prType,
    value = this.weight,
    date = try {
        com.apexfit.app.utils.DateTimeUtils.formatDate(Date(this.achievedAt))
    } catch (e: Exception) {
        ""
    }
)

fun UiPlateauResult.toData(): com.apexfit.app.data.PlateauResult = com.apexfit.app.data.PlateauResult(
    isPlateaued = this.isPlateau,
    interventionRecommendation = this.recommendation,
    severity = this.severity,
    interventions = this.interventions,
    daysStalled = this.daysStalled
)

@Immutable
data class UiComplianceResult(
    val calories: Int,
    val protein: Int,
    val training: Int,
    val overall: Int,
    val weakestDay: String?
)

@Immutable
data class UiFatigueResult(
    val ratio: Double?,
    val status: String,
    val statusLabel: String,
    val recommendation: String
)

@Immutable
data class UiDeloadResult(
    val recommendation: String,
    val urgency: String,
    val signals: Int,
    val protocol: List<String>
)

@Immutable
data class UiReadinessFactor(
    val name: String,
    val impact: String,
    val value: String
)

@Immutable
data class UiSessionReadiness(
    val score: Int,
    val label: String,
    val colorHex: String,
    val prediction: String,
    val recommendation: String,
    val factors: List<UiReadinessFactor>
)

fun com.apexfit.app.utils.ComplianceResult.toUi(): UiComplianceResult = UiComplianceResult(
    calories = this.calories,
    protein = this.protein,
    training = this.training,
    overall = this.overall,
    weakestDay = this.weakestDay
)

fun UiComplianceResult.toData(): com.apexfit.app.utils.ComplianceResult = com.apexfit.app.utils.ComplianceResult(
    calories = this.calories,
    protein = this.protein,
    training = this.training,
    overall = this.overall,
    weakestDay = this.weakestDay
)

fun com.apexfit.app.utils.FatigueResult.toUi(): UiFatigueResult = UiFatigueResult(
    ratio = this.ratio,
    status = this.status,
    statusLabel = this.statusLabel,
    recommendation = this.recommendation
)

fun UiFatigueResult.toData(): com.apexfit.app.utils.FatigueResult = com.apexfit.app.utils.FatigueResult(
    ratio = this.ratio,
    status = this.status,
    statusLabel = this.statusLabel,
    recommendation = this.recommendation,
    acuteLoad = 0.0,
    chronicLoad = 0.0
)

fun com.apexfit.app.utils.DeloadResult.toUi(): UiDeloadResult = UiDeloadResult(
    recommendation = this.recommendation,
    urgency = this.urgency,
    signals = this.signals,
    protocol = this.protocol
)

fun UiDeloadResult.toData(): com.apexfit.app.utils.DeloadResult = com.apexfit.app.utils.DeloadResult(
    recommendation = this.recommendation,
    urgency = this.urgency,
    signals = this.signals,
    protocol = this.protocol
)

fun com.apexfit.app.utils.ReadinessFactor.toUi(): UiReadinessFactor = UiReadinessFactor(
    name = this.name,
    impact = this.impact,
    value = this.value
)

fun UiReadinessFactor.toData(): com.apexfit.app.utils.ReadinessFactor = com.apexfit.app.utils.ReadinessFactor(
    name = this.name,
    impact = this.impact,
    value = this.value
)

fun com.apexfit.app.utils.SessionReadiness.toUi(): UiSessionReadiness = UiSessionReadiness(
    score = this.score,
    label = this.label,
    colorHex = this.colorHex,
    prediction = this.prediction,
    recommendation = this.recommendation,
    factors = this.factors.map { it.toUi() }
)

fun UiSessionReadiness.toData(): com.apexfit.app.utils.SessionReadiness = com.apexfit.app.utils.SessionReadiness(
    score = this.score,
    label = this.label,
    colorHex = this.colorHex,
    prediction = this.prediction,
    recommendation = this.recommendation,
    factors = this.factors.map { it.toData() }
)

@Immutable
data class ChatMessage(
    val isUser: Boolean,
    val message: String
)
