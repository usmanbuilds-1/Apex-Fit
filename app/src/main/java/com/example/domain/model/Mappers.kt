package com.example.domain.model

import com.example.data.ExerciseSet
import com.example.data.TrainingSession
import com.example.data.PersonalRecord
import com.example.data.WeightEntry
import com.example.data.BodyMeasurement
import com.example.data.NutritionEntry
import com.example.data.DetectedPatternEntity

// ============================================================================
// MAPPERS: Entity → Domain (database → UI layer)
// ============================================================================

// ExerciseSet: Room Entity → Domain Model
fun ExerciseSet.toDomain(): DomainExerciseSet = DomainExerciseSet(
    id = this.id,
    sessionId = this.sessionId,
    exerciseId = this.exerciseId,
    exerciseName = this.exerciseName,
    muscleGroup = this.muscleGroup,
    weight = this.weight,
    reps = this.reps,
    rpe = this.rpe,
    repsInReserve = this.repsInReserve,
    isWarmup = this.isWarmup,
    restTaken = this.restTaken,
    completed = this.completed,
    effectiveSetValue = this.effectiveSetValue
)

// TrainingSession: Room Entity → Domain Model
fun TrainingSession.toDomain(): DomainTrainingSession = DomainTrainingSession(
    id = this.id,
    date = this.date,
    sessionType = this.sessionType,
    completed = this.completed,
    durationMinutes = this.durationMinutes,
    sessionFeel = this.sessionFeel
)

// PersonalRecord: Room Entity → Domain Model
fun PersonalRecord.toDomain(): DomainPersonalRecord = DomainPersonalRecord(
    id = this.id.hashCode().toLong(),
    exerciseName = this.exerciseId,
    weight = this.value,
    reps = 1, // Defaulting reps to 1 for generic PRs
    estimatedOneRepMax = this.value,
    prType = this.type,
    achievedAt = try { this.date.toLong() } catch (e: Exception) { System.currentTimeMillis() },
    workoutSessionId = null
)

// WeightEntry: Room Entity → Domain Model
fun WeightEntry.toDomain(): DomainWeightEntry = DomainWeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

// BodyMeasurement: Room Entity → Domain Model
fun BodyMeasurement.toDomain(): DomainBodyMeasurement = DomainBodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

// NutritionEntry: Room Entity → Domain Model
fun NutritionEntry.toDomain(): DomainNutritionEntry = DomainNutritionEntry(
    id = this.id,
    date = this.date,
    time = this.time,
    calories = this.calories,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat,
    mealName = this.name
)

// DetectedPatternEntity: Room Entity → Domain Model
fun DetectedPatternEntity.toDomain(): DomainDetectedPattern = DomainDetectedPattern(
    id = this.id.hashCode().toLong(),
    patternType = this.type,
    description = this.description,
    severity = if (this.confidence >= 0.8f) "critical" else "info",
    detectedAt = try { this.detectedAt.toLong() } catch (e: Exception) { System.currentTimeMillis() },
    resolved = false
)

// ============================================================================
// MAPPERS: Domain → Entity (UI layer → database)
// ============================================================================

// DomainExerciseSet → Room Entity
fun DomainExerciseSet.toEntity(): ExerciseSet = ExerciseSet(
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

// DomainTrainingSession → Room Entity
fun DomainTrainingSession.toEntity(): TrainingSession = TrainingSession(
    id = this.id,
    date = this.date,
    sessionType = this.sessionType,
    completed = this.completed,
    durationMinutes = this.durationMinutes,
    sessionFeel = this.sessionFeel
)

// DomainPersonalRecord → Room Entity
fun DomainPersonalRecord.toEntity(): PersonalRecord = PersonalRecord(
    id = "${this.exerciseName}_${this.prType}",
    exerciseId = this.exerciseName,
    type = this.prType,
    value = this.weight,
    date = this.achievedAt.toString()
)

// DomainWeightEntry → Room Entity
fun DomainWeightEntry.toEntity(): WeightEntry = WeightEntry(
    id = this.id,
    weight = this.weight,
    date = this.date,
    time = this.time
)

// DomainBodyMeasurement → Room Entity
fun DomainBodyMeasurement.toEntity(): BodyMeasurement = BodyMeasurement(
    id = this.id,
    bodyPart = this.bodyPart,
    value = this.value,
    unit = this.unit,
    date = this.date
)

// DomainNutritionEntry → Room Entity
fun DomainNutritionEntry.toEntity(): NutritionEntry = NutritionEntry(
    id = this.id,
    date = this.date,
    name = this.mealName,
    time = this.time,
    calories = this.calories,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat
)

// DomainDetectedPattern → Room Entity
fun DomainDetectedPattern.toEntity(): DetectedPatternEntity = DetectedPatternEntity(
    id = this.id.toString(),
    type = this.patternType,
    title = this.patternType,
    description = this.description,
    confidence = if (this.severity == "critical") 0.9f else 0.5f,
    actionable = "Resolve detected architectural pattern mismatch",
    detectedAt = this.detectedAt.toString()
)

// ============================================================================
// BATCH MAPPERS (Uniquely named to prevent erasure JVM signature clashes)
// ============================================================================

fun List<ExerciseSet>.toDomainSets(): List<DomainExerciseSet> = this.map { it.toDomain() }
fun List<DomainExerciseSet>.toEntitySets(): List<ExerciseSet> = this.map { it.toEntity() }

fun List<TrainingSession>.toDomainSessions(): List<DomainTrainingSession> = this.map { it.toDomain() }
fun List<DomainTrainingSession>.toEntitySessions(): List<TrainingSession> = this.map { it.toEntity() }

fun List<PersonalRecord>.toDomainRecords(): List<DomainPersonalRecord> = this.map { it.toDomain() }
fun List<DomainPersonalRecord>.toEntityRecords(): List<PersonalRecord> = this.map { it.toEntity() }

fun List<WeightEntry>.toDomainWeights(): List<DomainWeightEntry> = this.map { it.toDomain() }
fun List<DomainWeightEntry>.toEntityWeights(): List<WeightEntry> = this.map { it.toEntity() }

fun List<BodyMeasurement>.toDomainMeasurements(): List<DomainBodyMeasurement> = this.map { it.toDomain() }
fun List<DomainBodyMeasurement>.toEntityMeasurements(): List<BodyMeasurement> = this.map { it.toEntity() }

fun List<NutritionEntry>.toDomainNutrition(): List<DomainNutritionEntry> = this.map { it.toDomain() }
fun List<DomainNutritionEntry>.toEntityNutrition(): List<NutritionEntry> = this.map { it.toEntity() }

fun List<DetectedPatternEntity>.toDomainPatterns(): List<DomainDetectedPattern> = this.map { it.toDomain() }
fun List<DomainDetectedPattern>.toEntityPatterns(): List<DetectedPatternEntity> = this.map { it.toEntity() }
