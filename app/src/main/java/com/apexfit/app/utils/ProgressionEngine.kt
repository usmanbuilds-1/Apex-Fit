package com.apexfit.app.utils

import kotlin.math.roundToInt

@JvmInline value class WeightKg(val value: Double)
@JvmInline value class WeightLbs(val value: Double)

object ProgressionEngine {

    fun getExerciseType(name: String, muscleGroup: String): String {
        val n = name.lowercase()
        val m = muscleGroup.lowercase()
        
        return when {
            // Cardio
            n.contains("run") || n.contains("jog") || n.contains("treadmill") || n.contains("cardio") || n.contains("bike") || n.contains("cycle") || n.contains("elliptical") || n.contains("walk") || m.contains("cardio") -> "cardio"
            // Core / Isolation
            m.contains("core") || n.contains("plank") || n.contains("crunch") -> "isolation"

            // Compound Lower
            n.contains("goblet") || (n.contains("squat") && !n.contains("dumbbell") && !n.contains("db") && !n.contains("machine") && !n.contains("smith")) -> "compound_lower"
            n.contains("deadlift") && !n.contains("dumbbell") && !n.contains("db") -> "compound_lower"
            n.contains("lunge") && !n.contains("dumbbell") && !n.contains("db") -> "compound_lower"
            n.contains("leg press") -> "compound_lower"
            
            // Compound Upper
            (n.contains("bench press") || n.contains("chest press") || n.contains("overhead press") || n.contains("military press") || n.contains("shoulder press")) && !n.contains("dumbbell") && !n.contains("db") && !n.contains("machine") && !n.contains("smith") -> "compound_upper"
            (n.contains("row") || n.contains("pullup") || n.contains("chinup") || n.contains("lat pulldown")) && !n.contains("dumbbell") && !n.contains("db") && !n.contains("cable") && !n.contains("machine") -> "compound_upper"
            n.contains("dips") -> "compound_upper"
            
            // Dumbbell Upper
            (n.contains("dumbbell") || n.contains("db")) && (m.contains("chest") || m.contains("back") || m.contains("shoulder") || m.contains("delt") || n.contains("press") || n.contains("row")) -> "dumbbell_upper"
            
            // Dumbbell Lower
            (n.contains("dumbbell") || n.contains("db") || n.contains("goblet")) && (m.contains("quad") || m.contains("hamstring") || m.contains("glute") || m.contains("calf") || n.contains("squat") || n.contains("lunge") || n.contains("deadlift")) -> "dumbbell_lower"
            
            // Machine / Cable Exercises
            n.contains("machine") || n.contains("smith") || n.contains("lever") || n.contains("cable") || n.contains("pec deck") -> "machine"

            // Default to isolation for minor muscles/exercise names (curl, extension, raise, fly, etc.)
            n.contains("curl") || n.contains("extension") || n.contains("raise") || n.contains("fly") || n.contains("pushdown") || n.contains("kickback") || m.contains("bicep") || m.contains("tricep") || m.contains("calf") || m.contains("core") -> "isolation"
            
            else -> "isolation"
        }
    }

    // Round to nearest 2.5 for simplicity (2.5, 5, 7.5, 10, 12.5, etc.)
    fun Double.roundToNearest2_5(): Double {
        return (this / 2.5).roundToInt() * 2.5
    }

    fun calculateBeginnerStartingWeight(
        exerciseType: String,
        userBodyWeightKg: WeightKg,
        userHeightCm: Double,
        preferredUnits: String = "lbs"
    ): Double = calculateBeginnerStartingWeight(
        exerciseType = exerciseType,
        userBodyWeightKg = userBodyWeightKg.value,
        userHeightCm = userHeightCm,
        preferredUnits = preferredUnits
    )

    // ── STARTING WEIGHT SYSTEM ─────────────────────────────────────────────────
    //
    // Uses body-weight ratios calibrated from Strength Level population data
    // (48M+ lifts across all experience levels). Ratios scale with both body
    // weight and experience, so a 60kg untrained male and a 90kg intermediate
    // male receive appropriately different starting weights.
    //
    // Evidence basis:
    //   Tier 1 — 5 major barbell exercises: directly validated BW×ratio norms
    //     Male:   bench 0.50×, squat 0.75×, deadlift 0.90×, OHP 0.35×, row 0.50×
    //     Female: bench 0.30×, squat 0.60×, deadlift 0.70×, OHP 0.22×, row 0.35×
    //     Source: Strength Level (48M+ lifts); training weight = 1RM × 0.65
    //
    //   Tier 2 — all other categories: BW-ratio tables derived from Strength
    //     Level norms for each category at beginner level (novice baseline).
    //     Female ratios ≈ 55–65% of male per population standards.
    //     Source: Strength Level population percentiles across exercise categories.
    //
    //   Experience multipliers (relative to novice = 1.0):
    //     untrained 0.80 | novice 1.00 | intermediate 1.70 | advanced 2.40
    //     Derived from Strength Level intermediate/beginner standard ratios.
    //
    //   Limitations: individual variation is substantial (±30–40%). These are
    //   population-level starting estimates, not individual measurements.
    //   The user's RPE on the first working set will correct any mismatch within
    //   one or two sets. Conservative is intentional — starting too light is
    //   safer and recoverable; starting too heavy risks injury and bad form.
    //
    // NOTE: Pass experienceLevel and sex from the user's profile for accurate
    // estimates. Defaults (novice, male) are used when profile data is unavailable.
    //
    fun calculateBeginnerStartingWeight(
        exerciseType: String,
        userBodyWeightKg: Double,
        userHeightCm: Double,    // retained for API compatibility; not used in formula
        preferredUnits: String = "lbs",
        exerciseName: String = "",
        sex: String = "male",
        experienceLevel: String = "novice"  // untrained | novice | intermediate | advanced
    ): Double {
        val bw = userBodyWeightKg.coerceAtLeast(40.0)
        val isFemale = sex.lowercase().startsWith("f")

        // Experience multiplier relative to novice baseline
        val expMult = when (experienceLevel.lowercase()) {
            "untrained"    -> 0.80
            "intermediate" -> 1.70
            "advanced"     -> 2.40
            else           -> 1.00  // novice (safe default)
        }

        val nameL = exerciseName.lowercase()

        // ── Tier 1: Named major barbell exercises ────────────────────────────────
        // Exact BW norms at novice level; experience multiplier applied on top.
        val tier1Kg: Double? = when {
            nameL.contains("bench press") && !nameL.contains("dumbbell") && !nameL.contains(" db ") ->
                bw * (if (isFemale) 0.195 else 0.325)
            nameL.contains("squat") && !nameL.contains("goblet") && !nameL.contains("dumbbell") ->
                bw * (if (isFemale) 0.390 else 0.488)
            nameL.contains("deadlift") && !nameL.contains("dumbbell") && !nameL.contains(" db ") ->
                bw * (if (isFemale) 0.455 else 0.585)
            nameL.contains("overhead press") || nameL.contains(" ohp") ->
                bw * (if (isFemale) 0.143 else 0.228)
            nameL.contains("barbell row") || nameL.contains("bent-over row") ->
                bw * (if (isFemale) 0.228 else 0.325)
            else -> null
        }

        // ── Tier 2: Category-based BW ratios ────────────────────────────────────
        // Lateral raises and flyes are lighter isolation exercises — detected by
        // name to avoid over-prescribing relative to curls/pushdowns.
        val isLightIsolation = nameL.contains("raise") || nameL.contains(" fly") ||
                               nameL.contains("flye") || nameL.contains("face pull")

        val tier2Kg: Double = when (exerciseType.lowercase()) {
            "compound_upper" -> bw * (if (isFemale) 0.200 else 0.325)
            "compound_lower" -> bw * (if (isFemale) 0.390 else 0.488)
            "dumbbell_upper" -> bw * (if (isFemale) 0.070 else 0.120)
            "dumbbell_lower" -> bw * (if (isFemale) 0.090 else 0.130)
            "machine"        -> bw * (if (isFemale) 0.180 else 0.300)
            "isolation"      ->
                if (isLightIsolation) bw * (if (isFemale) 0.035 else 0.050)
                else                  bw * (if (isFemale) 0.060 else 0.080)
            else             -> bw * (if (isFemale) 0.055 else 0.085)
        }

        val baseKg = tier1Kg ?: tier2Kg
        val startKg = (baseKg * expMult).coerceAtLeast(if (isFemale) 2.0 else 2.5)

        val result = startKg.roundToNearest2_5()
        return if (preferredUnits.lowercase() in listOf("lb", "lbs")) result * AppConstants.KG_TO_LBS else result
    }


enum class OutcomeType { SUCCESS, PROGRESSING, STALLED, PLATEAU }

data class ProgressionResult(
    val newWeight: Double,
    val outcome: OutcomeType,
    val reason: String
)

    fun calculateProgressiveWeight(
        exerciseId: String,
        lastSessionSets: List<com.apexfit.app.ui.models.UiExerciseSet>,
        repsMin: Int,
        repsMax: Int,
        targetSets: Int,
        recoveryMultiplier: Double,
        currentWeight: WeightLbs,
        exerciseType: String,
        consecutiveStalledSessions: Int = 0,
        incrementOverrideKg: WeightKg? = null,
        weightUnit: String = "kg"
    ): ProgressionResult = calculateProgressiveWeight(
        exerciseId = exerciseId,
        lastSessionSets = lastSessionSets,
        repsMin = repsMin,
        repsMax = repsMax,
        targetSets = targetSets,
        recoveryMultiplier = recoveryMultiplier,
        currentWeight = currentWeight.value,
        exerciseType = exerciseType,
        consecutiveStalledSessions = consecutiveStalledSessions,
        incrementOverrideKg = incrementOverrideKg?.value,
        weightUnit = weightUnit
    )

    fun calculateProgressiveWeight(
        exerciseId: String,
        lastSessionSets: List<com.apexfit.app.ui.models.UiExerciseSet>,
        repsMin: Int,
        repsMax: Int,
        targetSets: Int,
        recoveryMultiplier: Double,
        currentWeight: Double,
        exerciseType: String,
        consecutiveStalledSessions: Int = 0,
        incrementOverrideKg: Double? = null,
        weightUnit: String = "kg",
        // ACSM (2009): novices should not train to or near failure initially.
        // Novice success threshold is RPE ≤ 7 (RIR ≥ 3); intermediate+ RPE ≤ 8 (RIR ≥ 2).
        isNovice: Boolean = false
    ): ProgressionResult {
        require(repsMin <= repsMax) {
            "repsMin ($repsMin) must be <= repsMax ($repsMax). Check plan configuration."
        }
        
        val workingSets = lastSessionSets.filter { !it.isWarmup && it.completed }
        
        if (workingSets.isEmpty()) {
            return ProgressionResult(currentWeight, OutcomeType.STALLED, "No completed sets recorded.")
        }

        // Unrated RPE handling: If any set is unrated (0), hold progression
        if (workingSets.any { it.rpe == 0 }) {
            return ProgressionResult(currentWeight, OutcomeType.STALLED, "Sets have unrated RPE. Please rate your effort to progress.")
        }

        // Outcome Determination
        // 1. SUCCESS: All sets >= repsMax at appropriate RPE for experience level
        // ACSM progression models: novice RPE ≤ 7 (RIR ≥ 3), intermediate+ RPE ≤ 8 (RIR ≥ 2)
        val successRpeThreshold = if (isNovice) 7 else 8
        val allHitMax = workingSets.size >= targetSets && workingSets.all { it.reps >= repsMax && it.rpe <= successRpeThreshold }
        
        // 2. PROGRESSING: All sets within range [repsMin, repsMax] but not all hit success criteria
        val allInRepsRange = workingSets.size >= targetSets && workingSets.all { it.reps >= repsMin }
        
        // 3. PLATEAU: 2+ consecutive failed sessions (failed to hit repsMin or sets target)
        val failedTarget = workingSets.size < targetSets || workingSets.any { it.reps < repsMin }
        
        val outcome = when {
            consecutiveStalledSessions >= 1 && failedTarget -> OutcomeType.PLATEAU // current failed + 1 previous = 2 consecutive
            allHitMax -> OutcomeType.SUCCESS
            allInRepsRange -> OutcomeType.PROGRESSING
            else -> OutcomeType.STALLED
        }

        // Weight Increment Logic
        // NOTE: All weight values in this function are in LBS regardless of user preference.  
        // WorkoutSessionManager converts to lbs before calling and converts back after.  
        // baseIncrement values are lbs-calibrated (e.g. 5.0 = 5 lbs ≈ 2.3 kg for lower body).  
        // Do NOT change these values without updating unit handling in WorkoutSessionManager.  
        val baseIncrement = if (incrementOverrideKg != null) {
            incrementOverrideKg * AppConstants.KG_TO_LBS
        } else {
            when(exerciseType) {
                "compound_lower" -> 5.0
                "compound_upper" -> 2.5
                "dumbbell_upper" -> 2.5
                "dumbbell_lower" -> 2.5
                "isolation" -> 1.25
                else -> 2.5
            }
        }

        // Plate size determines valid increment grid for this exercise type.
        val (isolationPlate, compoundPlate) = when (weightUnit) {
            "kg"  -> Pair(1.25, 2.5)
            else  -> Pair(1.25, 2.5)  // lbs — same increments
        }
        val plateSize = if (exerciseType == "isolation") isolationPlate else compoundPlate

        // §6.3 fix: round the INCREMENT to the plate grid, not the final weight.
        // This preserves recovery modulation — a 0.6× multiplier on a 2.5 lb
        // increment gives 1.5 lb → rounds to 1.25 lb (isolation) or 2.5 lb (compound).
        val rawIncrement = baseIncrement * recoveryMultiplier
        val roundedIncrement = if (rawIncrement >= plateSize / 2.0) {
            (Math.round(rawIncrement / plateSize) * plateSize).coerceAtLeast(plateSize)
        } else {
            0.0   // Too small to make a valid plate increment — hold
        }

        // §6.2 fix: for PLATEAU, use floor rounding (always round DOWN) to
        // guarantee at least 10% deload regardless of plate grid.
        val newWeight = when(outcome) {
            OutcomeType.SUCCESS  -> currentWeight + roundedIncrement
            OutcomeType.PLATEAU  -> (Math.floor(currentWeight * 0.9 / plateSize) * plateSize)
            else                 -> currentWeight
        }

        val reason = when(outcome) {
            OutcomeType.SUCCESS     -> "Hit all targets at max reps with reserve. Advancing weight."
            OutcomeType.PROGRESSING -> "All sets within rep range. Hold weight and aim for ${repsMax} reps."
            OutcomeType.STALLED     -> "Failed to hit minimum reps/sets target. Holding weight to retry."
            OutcomeType.PLATEAU     -> "Multiple failed sessions. Deloading 10% to recover and break plateau."
        }

        return ProgressionResult(newWeight, outcome, reason)
    }

    fun calculateRestTimeSeconds(
        exerciseType: String,  // "compound_upper", "compound_lower", "isolation", etc.
        rpe: Int,              // 5-10
        restSecondsOverride: Int? = null
    ): Int {
        // Step 1: Base rest time by exercise type
        val baseRestSeconds = if (restSecondsOverride != null) {
            restSecondsOverride
        } else {
            when(exerciseType) {
                "compound_upper" -> 180    // 3 minutes
                "compound_lower" -> 180    // 3 minutes
                "dumbbell_upper" -> 150    // 2.5 minutes
                "dumbbell_lower" -> 150    // 2.5 minutes
                "machine" -> 120           // 2 minutes
                "isolation" -> 90          // 1.5 minutes
                "cardio" -> 45             // 45 seconds
                else -> 120                // Default 2 minutes
            }
        }
        
        // Step 2: Adjust for RPE
        val rpeAdjustment = when(rpe) {
            in 5..6 -> -30   // Lower intensity: reduce rest
            7 -> 0           // Transition zone: base time
            8 -> 0           // Sweet spot: base time
            9 -> 30          // High effort: add 30s
            10 -> 60         // Maxed out: add 60s
            else -> 0
        }
        
        // Step 3: Calculate total rest time
        val totalRestSeconds = baseRestSeconds + rpeAdjustment
        
        // Step 4: Ensure minimum rest (at least 45 seconds)
        return maxOf(totalRestSeconds, 45)
    }

    fun calculateRPEFromRIR(repsInReserve: Int): Int {
        return when(repsInReserve) {
            in 4..Int.MAX_VALUE -> 6   // 4+ reps left -> RPE 6
            3 -> 7                     // 3 reps left -> RPE 7
            2 -> 8                     // 2 reps left -> RPE 8
            1 -> 9                     // 1 rep left -> RPE 9
            0 -> 10                    // 0 reps left -> RPE 10
            else -> 8                  // Default to sweet spot RPE 8
        }
    }

    fun calculateEffectiveSetValue(rpe: Int): Double {
        return when(rpe) {
            in 5..6 -> 0.25
            7 -> 0.5            // 50% effective
            8 -> 0.75           // 75% effective
            9 -> 0.9            // 90% effective
            10 -> 1.0           // 100% effective
            else -> 0.0
        }
    }

    fun getEffectivenessLabel(rpe: Int): String {
        return when(rpe) {
            in 0..4 -> "Too light (technique work only)"
            in 5..6 -> "Technique volume (25% effective)"
            7 -> "Building zone (50% effective)"
            8 -> "SWEET SPOT (75% effective)"
            9 -> "High effort (90% effective)"
            10 -> "Maximal (100% effective)"
            else -> "Unknown"
        }
    }
}