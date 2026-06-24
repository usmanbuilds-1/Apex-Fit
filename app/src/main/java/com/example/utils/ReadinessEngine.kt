package com.example.utils

import com.example.data.ExerciseSet
import com.example.data.TrainingSession
import kotlin.math.exp

enum class MuscleSize { SMALL, MEDIUM, LARGE }

object MuscleRecoveryData {
    private val sizeMap = mapOf(
        "biceps" to MuscleSize.SMALL,
        "triceps" to MuscleSize.SMALL,
        "calves" to MuscleSize.SMALL,
        "core" to MuscleSize.SMALL,
        "cardio" to MuscleSize.SMALL,
        "shoulders" to MuscleSize.MEDIUM,
        "hamstrings" to MuscleSize.MEDIUM,
        "glutes" to MuscleSize.MEDIUM,
        "chest" to MuscleSize.LARGE,
        "back" to MuscleSize.LARGE,
        "quads" to MuscleSize.LARGE,
        "full body" to MuscleSize.LARGE
    )

    private val tauDays = mapOf(
        MuscleSize.SMALL to 0.8,
        MuscleSize.MEDIUM to 1.2,
        MuscleSize.LARGE to 2.0
    )

    private val secondaryMap = mapOf(
        "bench" to listOf("Shoulders" to 0.40, "Triceps" to 0.30),
        "incline_bench" to listOf("Shoulders" to 0.50, "Chest" to 0.30),
        "barbell_row" to listOf("Shoulders" to 0.35, "Biceps" to 0.25),
        "deadlift" to listOf("Hamstrings" to 0.50, "Back" to 0.30, "Glutes" to 0.35),
        "squat" to listOf("Hamstrings" to 0.40, "Glutes" to 0.40),
        "ohp" to listOf("Shoulders" to 0.60, "Triceps" to 0.50),
        "pullup" to listOf("Shoulders" to 0.35, "Biceps" to 0.60),
        "dumbbell_row" to listOf("Shoulders" to 0.30, "Biceps" to 0.20),
        "cable_lateral_raise" to listOf("Shoulders" to 0.40),
        "face_pull" to listOf("Shoulders" to 0.70, "Biceps" to 0.20),
        "curl" to listOf("Core" to 0.30),
        "leg_press" to listOf("Hamstrings" to 0.35, "Glutes" to 0.30),
        "leg_curl" to listOf("Glutes" to 0.25)
    )

    fun tauFor(muscleGroup: String): Double = tauDays[sizeMap[muscleGroup.lowercase().trim()] ?: MuscleSize.MEDIUM] ?: 1.2

    fun getSecondaryMuscles(exerciseName: String): List<Pair<String, Double>> {
        val normalized = exerciseName.lowercase().replace(" ", "_")
        return secondaryMap.entries
            .filter { normalized.contains(it.key) }
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
    fun remainingFatigue(snapshots: List<MuscleFatigueSnapshot>, muscleGroup: String): Double {
        val tau = MuscleRecoveryData.tauFor(muscleGroup)
        return snapshots.filter { it.muscleGroup == muscleGroup }
            .sumOf { it.doseAmount * (1.0 - MuscleRecoveryData.recoveredFraction(it.daysAgo, tau)) }
    }

    fun userCapacity(pastSessionDoses: List<Double>, fallbackDefault: Double = 400.0): Double {
        if (pastSessionDoses.size < 3) return fallbackDefault
        val sorted = pastSessionDoses.sorted()
        val p90 = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)]
        return p90.coerceAtLeast(1.0)
    }

    fun readinessPercent(snapshots: List<MuscleFatigueSnapshot>, muscleGroup: String, capacity: Double): Int {
        val remaining = remainingFatigue(snapshots, muscleGroup)
        val ratio = (remaining / capacity.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ((1.0 - ratio) * 100).toInt()
    }
}

object SystemicCNSCalculator {
    // Single decay - CNS fatigue is one process (motor unit recruitment), not two phases.
    // Recovers faster than muscle tissue but responds harder to compound lifts.
    private const val TAU_DAYS = 1.3

    data class SystemicSnapshot(val daysAgo: Double, val doseAmount: Double)

    fun doseForSet(set: ExerciseSet, exerciseName: String): Double {
        if (set.isWarmup || !set.completed) return 0.0
        val volumeLoad = set.weight.coerceAtLeast(0.0) * set.reps.coerceAtLeast(0)
        val mult = FatigueDoseCalculator.fatigueMultiplier(set.rpe.coerceIn(1, 10))
        val systemicMult = if (FatigueDoseCalculator.isCompound(exerciseName)) 1.4 else 0.7
        return volumeLoad * mult * systemicMult
    }

    fun readinessPercent(snapshots: List<SystemicSnapshot>, capacity: Double): Int {
        val remaining = snapshots.sumOf { it.doseAmount * exp(-it.daysAgo / TAU_DAYS) }
        val ratio = (remaining / capacity.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return ((1.0 - ratio) * 100).toInt()
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
        completedSessions: List<com.example.utils.TrainingSession>,
        todayExercises: List<com.example.data.PlanExercise>,
        nutritionLog: List<com.example.utils.NutritionEntry>,
        targets: com.example.utils.NutritionTargets
    ): ReadinessScore {
        val currentDate = getCurrentDate()
        val completedLast30 = completedSessions.filter { session ->
            session.completed && getDaysBetween(session.date, currentDate) <= 30
        }

        // a) muscleFatigueHistory
        val muscleFatigueHistory = mutableMapOf<String, MutableList<MuscleFatigueSnapshot>>()
        for (session in completedLast30) {
            val daysAgo = getDaysBetween(session.date, currentDate).toDouble()
            for (exercise in session.exercises) {
                for (set in exercise.sets) {
                    if (set.isWarmup || !set.completed) continue
                    val dose = FatigueDoseCalculator.doseForSet(set)
                    val primaryList = muscleFatigueHistory.getOrPut(exercise.muscleGroup) { mutableListOf() }
                    primaryList.add(MuscleFatigueSnapshot(exercise.muscleGroup, daysAgo, dose))
                    
                    val secondaries = MuscleRecoveryData.getSecondaryMuscles(exercise.name)
                    for ((secMuscle, pct) in secondaries) {
                        val secList = muscleFatigueHistory.getOrPut(secMuscle) { mutableListOf() }
                        secList.add(MuscleFatigueSnapshot(secMuscle, daysAgo, dose * pct))
                    }
                }
            }
        }

        // b) muscleSessionHistory
        val muscleSessionHistory = mutableMapOf<String, MutableList<Double>>()
        for (session in completedLast30) {
            val sessionDosesByMuscle = mutableMapOf<String, Double>()
            for (exercise in session.exercises) {
                for (set in exercise.sets) {
                    if (set.isWarmup || !set.completed) continue
                    val dose = FatigueDoseCalculator.doseForSet(set)
                    sessionDosesByMuscle[exercise.muscleGroup] = sessionDosesByMuscle.getOrDefault(exercise.muscleGroup, 0.0) + dose
                }
            }
            for ((muscle, totalDose) in sessionDosesByMuscle) {
                val list = muscleSessionHistory.getOrPut(muscle) { mutableListOf() }
                list.add(totalDose)
            }
        }

        // c) systemicHistory
        val systemicHistory = mutableListOf<SystemicCNSCalculator.SystemicSnapshot>()
        for (session in completedLast30) {
            val daysAgo = getDaysBetween(session.date, currentDate).toDouble()
            for (exercise in session.exercises) {
                for (set in exercise.sets) {
                    if (set.isWarmup || !set.completed) continue
                    val dose = SystemicCNSCalculator.doseForSet(set, exercise.name)
                    systemicHistory.add(SystemicCNSCalculator.SystemicSnapshot(daysAgo, dose))
                }
            }
        }

        // d) systemicCapacity
        val systemicPastSessionDoses = completedLast30.map { session ->
            session.exercises.sumOf { exercise ->
                exercise.sets.filter { !it.isWarmup && it.completed }.sumOf { set ->
                    SystemicCNSCalculator.doseForSet(set, exercise.name)
                }
            }
        }
        val systemicCapacity = if (systemicPastSessionDoses.size < 3) 1500.0 else {
            val sorted = systemicPastSessionDoses.sorted()
            val p90 = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)]
            p90.coerceAtLeast(1.0)
        }

        // e) todaysMuscleGroups
        val todaysMuscleGroups = todayExercises.map { it.muscleGroup }.distinct()

        // f) nutritionScore
        val compliance = AlgorithmEngine.calcComplianceScores(nutritionLog, completedSessions, targets)
        val nutritionScore = (compliance.calories + compliance.protein) / 2

        // g) sleepScore
        val sleepScore: Int? = null

        // h) acuteLoad and chronicLoad
        val fatigueResult = AlgorithmEngine.calcFatigueToFitness(completedSessions)
        val acuteLoad = fatigueResult.acuteLoad
        val chronicLoad = fatigueResult.chronicLoad

        // i) totalCompletedSessions
        val totalCompletedSessions = completedSessions.count { it.completed }

        return calculate(
            totalCompletedSessions = totalCompletedSessions,
            todaysMuscleGroups = todaysMuscleGroups,
            muscleFatigueHistory = muscleFatigueHistory,
            muscleSessionHistory = muscleSessionHistory,
            systemicHistory = systemicHistory,
            systemicCapacity = systemicCapacity,
            nutritionScore = nutritionScore,
            sleepScore = sleepScore,
            acuteLoad = acuteLoad,
            chronicLoad = chronicLoad
        )
    }

    fun calculate(
        totalCompletedSessions: Int, // used ONLY for the cold-start label override below
        todaysMuscleGroups: List<String>,
        muscleFatigueHistory: Map<String, List<MuscleFatigueSnapshot>>,
        muscleSessionHistory: Map<String, List<Double>>,
        systemicHistory: List<SystemicCNSCalculator.SystemicSnapshot>,
        systemicCapacity: Double,
        nutritionScore: Int,
        sleepScore: Int?, // always pass null - not collected yet
        acuteLoad: Double, // from calcFatigueToFitness(trainingLog).acuteLoad
        chronicLoad: Double // from calcFatigueToFitness(trainingLog).chronicLoad
    ): ReadinessScore {

        val muscleDetails = todaysMuscleGroups.map { muscle ->
            val history = muscleFatigueHistory[muscle] ?: emptyList()
            val sessionHist = muscleSessionHistory[muscle] ?: emptyList()
            val capacity = MuscleReadinessCalculator.userCapacity(sessionHist)
            val readiness = MuscleReadinessCalculator.readinessPercent(history, muscle, capacity)
            val confidence = when {
                sessionHist.size >= 5 -> "full"
                sessionHist.size >= 3 -> "limited"
                else -> "none"
            }
            MuscleReadinessDetail(muscle, readiness, sessionHist.size, confidence)
        }
        val avgMuscle = if (muscleDetails.isNotEmpty()) muscleDetails.map { it.readinessPercent }.average() else 100.0

        val systemicScore = SystemicCNSCalculator.readinessPercent(systemicHistory, systemicCapacity)
        val acrModifier = AcuteChronicRatioModifier.modifier(acuteLoad, chronicLoad)

        val (wMuscle, wSystemic, wNutrition, wSleep) =
            if (sleepScore != null) listOf(0.40, 0.20, 0.20, 0.20)
            else listOf(0.45, 0.25, 0.30, 0.0)

        var combined = avgMuscle * wMuscle + systemicScore * wSystemic +
                nutritionScore * wNutrition + (sleepScore?.toDouble() ?: 0.0) * wSleep
        combined *= acrModifier

        val finalScore = combined.toInt().coerceIn(0, 100)

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
                sleepScore = sleepScore,
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
            sleepScore == null -> "partial - sleep not logged"
            else -> "full"
        }

        return ReadinessScore(finalScore, label, color, rec, muscleDetails, systemicScore,
            nutritionScore, sleepScore, acrModifier, confidenceFlag)
    }
}
