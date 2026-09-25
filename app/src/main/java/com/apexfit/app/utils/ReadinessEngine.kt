package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseMetadata
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.exerciseId
import kotlin.math.exp
import kotlin.math.roundToInt

enum class MuscleSize { SMALL, MEDIUM, LARGE }

object MuscleRecoveryData {
    private val sizeMap = MuscleGroups.ALL.associateWith { muscle ->
        when (muscle.lowercase()) {
            "quads", "hamstrings", "glutes", "back" -> 2.0
            "chest", "shoulders", "triceps", "biceps", "calves" -> 1.2
            "forearms", "core", "neck" -> 0.8
            else -> 1.2
        }
    }

    private val secondaryMap = mapOf(
        "bench" to listOf("Shoulders" to 0.40, "Triceps" to 0.30),
        "incline_bench" to listOf("Shoulders" to 0.40, "Chest" to 0.30),
        "barbell_row" to listOf("Shoulders" to 0.35, "Biceps" to 0.25),
        "deadlift" to listOf("Quads" to 0.40, "Back" to 0.25, "Hamstrings" to 0.25),
        "squat" to listOf("Hamstrings" to 0.35, "Glutes" to 0.35),
        "ohp" to listOf("Shoulders" to 0.40, "Triceps" to 0.40),
        "pullup" to listOf("Shoulders" to 0.35, "Biceps" to 0.50),
        "dumbbell_row" to listOf("Shoulders" to 0.30, "Biceps" to 0.20),
        "cable_lateral_raise" to listOf("Shoulders" to 0.40),
        "face_pull" to listOf("Shoulders" to 0.50, "Biceps" to 0.20),
        "curl" to listOf("Biceps" to 0.10),
        "leg_press" to listOf("Hamstrings" to 0.35, "Glutes" to 0.30),
        "leg_curl" to listOf("Glutes" to 0.25)
    )

    fun tauFor(muscleGroup: String, tauOverrideDays: Double? = null): Double {
        if (tauOverrideDays != null) return tauOverrideDays
        val norm = muscleGroup.lowercase().trim()
        val canonical = com.apexfit.app.utils.MuscleAliases.getCanonical(norm)
        return sizeMap.entries.firstOrNull { 
            it.key.lowercase() == norm || it.key.lowercase() == canonical 
        }?.value ?: 1.2
    }

    fun getSecondaryMuscles(
        exerciseName: String,
        secondaryMuscles: List<String> = emptyList()
    ): List<Pair<String, Double>> {
        // If structured data available, use it with a flat 25% dose share
        if (secondaryMuscles.isNotEmpty()) {
            val share = 0.25 / secondaryMuscles.size
            return secondaryMuscles.map { muscle ->
                MuscleAliases.getCanonical(muscle.lowercase()) to share
            }
        }
        // Fallback to the substring map for exercises without structured data
        val normalized = exerciseName.lowercase().replace(" ", "_")
        return secondaryMap.entries
            .filter { normalized == it.key }
            .flatMap { it.value }
            .groupBy { it.first }
            .map { it.key to it.value.maxOf { p -> p.second } }
    }

    // Bi-exponential: fast component = inflammation/glycogen, slow component = structural repair.
    // This shape is real - DOMS has a fast and slow recovery phase. The exact split (65/35)
    // and the 3x multiplier on tau are engineering choices, not measured from your users.
    fun recoveredFraction(daysAgo: Double, tau: Double): Double {
        val fast = 0.65 * exp(-daysAgo / tau)
        val slow = 0.35 * exp(-daysAgo / (tau * 3))
        return 1.0 - (fast + slow)
    }
}

object FatigueDoseCalculator {
    // Fatigue COST curve. Has a floor - even light sets cost some recovery.
    // This is deliberately different from your hypertrophy "effective sets" curve,
    // which should hit 0.0 at low RPE because low-RPE sets aren't EFFECTIVE for growth,
    // even though they still cost some fatigue. Two different questions, two different curves.
    fun fatigueMultiplier(rpe: Int): Double = when {
        rpe >= 10 -> 1.00
        rpe == 9  -> 0.85
        rpe == 8  -> 0.70
        rpe == 7  -> 0.55
        rpe == 6  -> 0.45
        else      -> 0.30
    }

    fun isCompound(exerciseName: String): Boolean {
        val compounds = listOf("squat", "deadlift", "bench", "press", "row", "pullup", "dip", "lunge", "leg_press")
        return compounds.any { exerciseName.lowercase().contains(it) }
    }

    fun doseForSet(set: ExerciseSet): Double {
        if (set.isWarmup || !set.completed) return 0.0
        val rpe = set.rpe.coerceIn(1, 10)
        val reps = set.reps.coerceAtLeast(0)
        val weight = set.weight.coerceAtLeast(0.0)
        if (weight == 0.0 || reps == 0) return 0.0
        return weight * reps * fatigueMultiplier(rpe)
    }
}

data class MuscleFatigueSnapshot(val muscleGroup: String, val daysAgo: Double, val doseAmount: Double)

object MuscleReadinessCalculator {
    fun remainingFatigue(
        snapshots: List<MuscleFatigueSnapshot>,
        muscleGroup: String,
        tauOverrideDays: Double? = null
    ): Double {
        val tau = MuscleRecoveryData.tauFor(muscleGroup, tauOverrideDays = tauOverrideDays)
        return snapshots.filter { it.muscleGroup == muscleGroup }
            .sumOf { it.doseAmount * (1.0 - MuscleRecoveryData.recoveredFraction(it.daysAgo, tau)) }
    }

    fun userCapacity(pastSessionDoses: List<Double>, fallbackDefault: Double = 400.0): Double {
        if (pastSessionDoses.size < 3) return fallbackDefault
        val sorted = pastSessionDoses.sorted()
        val p90 = if (sorted.size < 3) {
            sorted.average()
        } else {
            sorted[((sorted.size - 1) * 0.9).toInt().coerceIn(0, sorted.size - 1)]
        }
        return p90.coerceAtLeast(1.0)
    }

    fun readinessPercent(
        snapshots: List<MuscleFatigueSnapshot>,
        muscleGroup: String,
        capacity: Double,
        tauOverrideDays: Double? = null
    ): Int {
        val remaining = remainingFatigue(snapshots, muscleGroup, tauOverrideDays = tauOverrideDays)
        val ratio = (remaining / capacity.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ((1.0 - ratio) * 100).roundToInt()
    }
}

object SystemicFatigueCalculator {
    // Single decay - CNS fatigue is one process (motor unit recruitment), not two phases.
    // Recovers faster than muscle tissue but responds harder to compound lifts.
    private const val TAU_DAYS = 1.3

    data class SystemicSnapshot(val daysAgo: Double, val doseAmount: Double)

    fun doseForSet(
        set: ExerciseSet,
        exerciseName: String,
        systemicMultiplierOverride: Double? = null
    ): Double {
        if (set.isWarmup || !set.completed) return 0.0
        val volumeLoad = set.weight.coerceAtLeast(0.0) * set.reps.coerceAtLeast(0)
        val mult = FatigueDoseCalculator.fatigueMultiplier(set.rpe.coerceIn(1, 10))
        val systemicMult = systemicMultiplierOverride ?: if (FatigueDoseCalculator.isCompound(exerciseName)) 1.4 else 0.7
        return volumeLoad * mult * systemicMult
    }

    fun readinessPercent(snapshots: List<SystemicSnapshot>, capacity: Double): Int {
        val remaining = snapshots.sumOf { it.doseAmount * exp(-it.daysAgo / TAU_DAYS) }
        val ratio = (remaining / capacity.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ((1.0 - ratio) * 100).roundToInt()
    }
}

object AcuteChronicRatioModifier {
    // Takes acuteLoad/chronicLoad from your EXISTING calcFatigueToFitness() output.
    // Do not recompute these separately - that's how two subsystems end up disagreeing.
    fun modifier(acuteLoad: Double, chronicLoad: Double): Double {
        if (chronicLoad < 1.0) return 1.0
        val ratio = acuteLoad / chronicLoad
        return when {
            ratio in 0.8..1.3 -> 1.0
            ratio in 1.3..1.5 -> 0.92
            ratio > 1.5       -> 0.80
            ratio in 0.5..0.8 -> 0.95
            else              -> 0.90
        }
    }
}

data class MuscleReadinessDetail(
    val muscleGroup: String,
    val readinessPercent: Int,
    val dataPoints: Int,
    val confidence: String
)

data class ReadinessScore(
    val overallPercent: Int,
    val label: String,
    val colorHex: String,
    val recommendation: String,
    val muscleDetails: List<MuscleReadinessDetail>,
    val systemicReadiness: Int,
    val nutritionScore: Int,
    val sleepScore: Int?,
    val acrModifier: Double,
    val dataConfidence: String
)

object ReadinessFinal {
    fun buildReadinessInputs(
        sessions: List<com.apexfit.app.utils.TrainingSession>,
        nutritionLog: List<com.apexfit.app.utils.NutritionEntry>,
        bodyWeightKg: Double,
        calorieTarget: Int,
        metadata: Map<String, ExerciseMetadata> = emptyMap()
    ): ReadinessScore {
        val targets = com.apexfit.app.utils.AlgorithmEngine.calcMacroTargets(calorieTarget, bodyWeightKg, "Maintain Weight")
        return buildReadinessInputs(
            completedSessions = sessions,
            todayExercises = emptyList(),
            nutritionLog = nutritionLog,
            targets = targets,
            metadata = metadata
        )
    }

    fun buildReadinessInputs(
        completedSessions: List<com.apexfit.app.utils.TrainingSession>,
        todayExercises: List<com.apexfit.app.data.PlanExercise>,
        nutritionLog: List<com.apexfit.app.utils.NutritionEntry>,
        targets: com.apexfit.app.utils.NutritionTargets,
        metadata: Map<String, ExerciseMetadata> = emptyMap()
    ): ReadinessScore {
        val currentDate = getCurrentDate()
        val completedLast30 = completedSessions.filter { session ->
            val days = getDaysBetween(session.date, currentDate)
            session.completed && days in 0..30
        }

        // Consolidated single pass over completedLast30 (Fix V-M8)
        // Simultaneously accumulates: muscleFatigueHistory, muscleSessionHistory, systemicHistory, systemicPastSessionDoses
        val muscleFatigueHistory = mutableMapOf<String, MutableList<MuscleFatigueSnapshot>>()
        val muscleSessionHistory = mutableMapOf<String, MutableList<Double>>()
        val systemicHistory = mutableListOf<SystemicFatigueCalculator.SystemicSnapshot>()
        val systemicPastSessionDoses = ArrayList<Double>(completedLast30.size)

        for (session in completedLast30) {
            val daysAgo = getDaysBetween(session.date, currentDate).toDouble()
            val sessionDosesByMuscle = mutableMapOf<String, Double>()
            var sessionSystemicDose = 0.0

            for (exercise in session.exercises) {
                val exerciseId = exercise.id.ifEmpty { exerciseNameToSlug(exercise.name) }
                val systemicMultiplier = metadata[exerciseId]?.systemicMultiplier
                val secondaries = MuscleRecoveryData.getSecondaryMuscles(exercise.name, exercise.secondaryMuscles)
                val secondaryTotalPct = secondaries.sumOf { it.second }
                val canonicalExerciseMuscle = com.apexfit.app.utils.MuscleAliases.getCanonical(exercise.muscleGroup)

                for (set in exercise.sets) {
                    if (set.isWarmup || !set.completed) continue

                    // 1) Muscle fatigue dose & secondary muscle distributions
                    val dose = FatigueDoseCalculator.doseForSet(set)
                    val primaryDose = dose * (1.0 - secondaryTotalPct.coerceAtMost(1.0))
                    val primaryList = muscleFatigueHistory.getOrPut(exercise.muscleGroup) { mutableListOf() }
                    primaryList.add(MuscleFatigueSnapshot(exercise.muscleGroup, daysAgo, primaryDose))

                    for ((secMuscle, pct) in secondaries) {
                        if (com.apexfit.app.utils.MuscleAliases.getCanonical(secMuscle) != canonicalExerciseMuscle) {
                            val secList = muscleFatigueHistory.getOrPut(secMuscle) { mutableListOf() }
                            secList.add(MuscleFatigueSnapshot(secMuscle, daysAgo, dose * pct))
                        }
                    }

                    // 2) Muscle session total dose accumulation
                    sessionDosesByMuscle[exercise.muscleGroup] = (sessionDosesByMuscle[exercise.muscleGroup] ?: 0.0) + dose

                    // 3) Systemic dose accumulation
                    val systemicDose = SystemicFatigueCalculator.doseForSet(
                        set = set,
                        exerciseName = exercise.name,
                        systemicMultiplierOverride = systemicMultiplier
                    )
                    systemicHistory.add(SystemicFatigueCalculator.SystemicSnapshot(daysAgo, systemicDose))
                    sessionSystemicDose += systemicDose
                }
            }

            for ((muscle, totalDose) in sessionDosesByMuscle) {
                val list = muscleSessionHistory.getOrPut(muscle) { mutableListOf() }
                list.add(totalDose)
            }
            systemicPastSessionDoses.add(sessionSystemicDose)
        }
        val systemicCapacity = if (systemicPastSessionDoses.size < 3) 1500.0 else {
            val sorted = systemicPastSessionDoses.sorted()
            val p90 = if (sorted.size < 3) {
                sorted.average()
            } else {
                sorted[((sorted.size - 1) * 0.9).toInt().coerceIn(0, sorted.size - 1)]
            }
            p90.coerceAtLeast(1.0)
        }

        // e) todaysMuscleGroups
        val todaysMuscleGroups = todayExercises.map { it.muscleGroup }.distinct()

        // f) nutritionScore
        val compliance = AlgorithmEngine.calcComplianceScores(nutritionLog, completedLast30, targets)
        val nutritionScore = (compliance.calories + compliance.protein) / 2

        // h) acuteLoad and chronicLoad
        val fatigueResult = AlgorithmEngine.calcFatigueToFitness(completedLast30)
        val acuteLoad = fatigueResult.acuteLoad
        val chronicLoad = fatigueResult.chronicLoad

        // i) totalCompletedSessions
        val totalCompletedSessions = completedSessions.count { it.completed }

        val tauOverrides = mutableMapOf<String, Double>()
        for (exercise in todayExercises) {
            val exerciseId = exercise.exerciseId
            val tau = metadata[exerciseId]?.recoveryTauDays
            if (tau != null) {
                tauOverrides[exercise.muscleGroup] = tau
                tauOverrides[exercise.muscleGroup.lowercase()] = tau
                tauOverrides[MuscleAliases.getCanonical(exercise.muscleGroup)] = tau
            }
        }

        return calculate(
            totalCompletedSessions = totalCompletedSessions,
            todaysMuscleGroups = todaysMuscleGroups,
            muscleFatigueHistory = muscleFatigueHistory,
            muscleSessionHistory = muscleSessionHistory,
            systemicHistory = systemicHistory,
            systemicCapacity = systemicCapacity,
            nutritionScore = nutritionScore,
            acuteLoad = acuteLoad,
            chronicLoad = chronicLoad,
            metadata = metadata,
            tauOverrides = tauOverrides
        )
    }

    fun calculate(
        totalCompletedSessions: Int, // used ONLY for the cold-start label override below
        todaysMuscleGroups: List<String>,
        muscleFatigueHistory: Map<String, List<MuscleFatigueSnapshot>>,
        muscleSessionHistory: Map<String, List<Double>>,
        systemicHistory: List<SystemicFatigueCalculator.SystemicSnapshot>,
        systemicCapacity: Double,
        nutritionScore: Int,
        acuteLoad: Double, // from calcFatigueToFitness(trainingLog).acuteLoad
        chronicLoad: Double, // from calcFatigueToFitness(trainingLog).chronicLoad
        metadata: Map<String, ExerciseMetadata> = emptyMap(),
        tauOverrides: Map<String, Double> = emptyMap()
    ): ReadinessScore {

        val muscleDetails = todaysMuscleGroups.map { muscle ->
            val history = muscleFatigueHistory[muscle] ?: emptyList()
            val sessionHist = muscleSessionHistory[muscle] ?: emptyList()
            val capacity = MuscleReadinessCalculator.userCapacity(sessionHist)
            val tauOverride = tauOverrides[muscle]
                ?: tauOverrides[muscle.lowercase()]
                ?: tauOverrides[MuscleAliases.getCanonical(muscle)]
                ?: metadata[muscle]?.recoveryTauDays
                ?: metadata[muscle.lowercase()]?.recoveryTauDays
                ?: metadata[MuscleAliases.getCanonical(muscle)]?.recoveryTauDays
            val readiness = MuscleReadinessCalculator.readinessPercent(
                snapshots = history,
                muscleGroup = muscle,
                capacity = capacity,
                tauOverrideDays = tauOverride
            )
            val confidence = when {
                sessionHist.size >= 5 -> "full"
                sessionHist.size >= 3 -> "limited"
                else -> "none"
            }
            MuscleReadinessDetail(muscle, readiness, sessionHist.size, confidence)
        }
        val avgMuscle = when {
            muscleDetails.isNotEmpty() -> muscleDetails.map { it.readinessPercent }.average()
            todaysMuscleGroups.isEmpty() -> 75.0  // non-training day — neutral
            else -> 100.0
        }

        val systemicScore = SystemicFatigueCalculator.readinessPercent(systemicHistory, systemicCapacity)
        val acrModifier = AcuteChronicRatioModifier.modifier(acuteLoad, chronicLoad)

        val wMuscle = 0.45
        val wSystemic = 0.25
        val wNutrition = 0.30

        val musclePart = (avgMuscle * wMuscle + systemicScore * wSystemic) * acrModifier
        val otherPart = nutritionScore * wNutrition
        val combined = musclePart + otherPart

        val finalScore = combined.roundToInt().coerceIn(0, 100)

        // Cold start: fewer than 3 sessions ever -> override label/copy only, not the raw score.
        // The score is mathematically "100%" because there's no fatigue data, not because
        // we've verified they're actually recovered. Be honest about that in the UI.
        if (totalCompletedSessions < 3) {
            return ReadinessScore(
                overallPercent = finalScore,
                label = "Building Baseline",
                colorHex = "#6366F1",
                recommendation = "Not enough history yet to score recovery accurately. Train your plan as scheduled - readiness gets reliable after a few sessions.",
                muscleDetails = muscleDetails,
                systemicReadiness = systemicScore,
                nutritionScore = nutritionScore,
                sleepScore = null,
                acrModifier = acrModifier,
                dataConfidence = "limited - building baseline"
            )
        }

        val (label, color, rec) = when {
            finalScore >= 85 -> Triple("Excellent", "#34D399", "Recovered and ready. Good day to push hard or chase a PR.")
            finalScore >= 70 -> Triple("Good", "#6366F1", "Train as programmed.")
            finalScore >= 55 -> Triple("Moderate", "#A78BFA", "Reduce load ~5%, prioritise technique.")
            finalScore >= 35 -> Triple("Low", "#F59E0B", "Drop volume ~40% or pick a lighter session.")
            else             -> Triple("Poor", "#E84040", "Rest or active recovery only today.")
        }

        val confidenceFlag = when {
            muscleDetails.any { it.confidence == "none" } -> "limited - some muscles lack history"
            else -> "full"
        }

        return ReadinessScore(finalScore, label, color, rec, muscleDetails, systemicScore,
            nutritionScore, null, acrModifier, confidenceFlag)
    }
}
