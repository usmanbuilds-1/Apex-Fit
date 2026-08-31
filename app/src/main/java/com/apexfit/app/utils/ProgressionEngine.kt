package com.apexfit.app.utils

import kotlin.math.roundToInt

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
        exerciseType: String,  // "compound_upper", "compound_lower", "isolation"
        userBodyWeightKg: Double,
        userHeightCm: Double,  // for lever arm adjustment
        preferredUnits: String = "lbs"
    ): Double {
        
        val baseMultiplier = when(exerciseType) {
            "compound_upper" -> 0.35    // Barbell bench: 128 × 0.35 = 44.8 lbs
            "compound_lower" -> 0.40    // Barbell squat: 128 × 0.40 = 51.2 lbs
            "dumbbell_upper" -> 0.12    // DB chest press: 128 × 0.12 = 15.4 lbs per DB
            "dumbbell_lower" -> 0.15    // DB goblet squat: 128 × 0.15 = 19.2 lbs
            "isolation" -> 0.08         // Bicep curl: 128 × 0.08 = 10.2 lbs
            else -> 0.10
        }
        
        var startWeight = userBodyWeightKg * baseMultiplier
        
        // Adjust for height (tall = longer ROM = harder)
        if (userHeightCm > 185) {  // Taller than 6'1"
            startWeight *= 0.90  // Reduce by 10%
        } else if (userHeightCm < 170) {  // Shorter than 5'7"
            startWeight *= 1.10  // Increase by 10%
        }
        
        // Round to nearest 2.5 of the preferred unit to maintain accuracy
        val result = if (preferredUnits.lowercase() in listOf("lb", "lbs")) {
            (startWeight * AppConstants.KG_TO_LBS).roundToNearest2_5() / AppConstants.KG_TO_LBS
        } else {
            startWeight.roundToNearest2_5()
        }
        
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
        currentWeight: Double,
        exerciseType: String,
        consecutiveStalledSessions: Int = 0
    ): ProgressionResult {
        
        val workingSets = lastSessionSets.filter { !it.isWarmup && it.completed }
        
        if (workingSets.isEmpty()) {
            return ProgressionResult(currentWeight, OutcomeType.STALLED, "No completed sets recorded.")
        }

        // Unrated RPE handling: If any set is unrated (0), hold progression
        if (workingSets.any { it.rpe == 0 }) {
            return ProgressionResult(currentWeight, OutcomeType.STALLED, "Sets have unrated RPE. Please rate your effort to progress.")
        }

        // Outcome Determination
        // 1. SUCCESS: All sets >= repsMax AND RPE <= 8
        val allHitMax = workingSets.size >= targetSets && workingSets.all { it.reps >= repsMax && it.rpe <= 8 }
        
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
        val baseIncrement = when(exerciseType) {
            "compound_lower" -> 5.0
            "compound_upper" -> 2.5
            "dumbbell_upper" -> 2.5
            "dumbbell_lower" -> 2.5
            "isolation" -> 1.25
            else -> 2.5
        }

        val maxRpe = workingSets.map { it.rpe }.maxOrNull() ?: 0
        val rpeMultiplier = when {
            maxRpe <= 8.0 -> 1.0
            maxRpe <= 9.0 -> 0.5
            else -> 0.0
        }

        val newWeight = when(outcome) {
            OutcomeType.SUCCESS -> currentWeight + (baseIncrement * rpeMultiplier * recoveryMultiplier)
            OutcomeType.PLATEAU -> currentWeight * 0.9  // 10% deload in lbs
            else -> currentWeight
        }
        
        val reason = when(outcome) {
            OutcomeType.SUCCESS -> "Hit all targets at max reps with reserve. Advancing weight."
            OutcomeType.PROGRESSING -> "All sets within rep range. Hold weight and aim for ${repsMax} reps."
            OutcomeType.STALLED -> "Failed to hit minimum reps/sets target. Holding weight to retry."
            OutcomeType.PLATEAU -> "Multiple failed sessions. Deloading 10% to recover and break plateau."
        }
        
        return ProgressionResult(newWeight.roundToNearest2_5(), outcome, reason)
    }

    fun calculateRestTimeSeconds(
        exerciseType: String,  // "compound_upper", "compound_lower", "isolation", etc.
        rpe: Int               // 5-10
    ): Int {
        // Step 1: Base rest time by exercise type
        val baseRestSeconds = when(exerciseType) {
            "compound_upper" -> 180    // 3 minutes
            "compound_lower" -> 180    // 3 minutes
            "dumbbell_upper" -> 150    // 2.5 minutes
            "dumbbell_lower" -> 150    // 2.5 minutes
            "machine" -> 120           // 2 minutes
            "isolation" -> 90          // 1.5 minutes
            "cardio" -> 45             // 45 seconds
            else -> 120                // Default 2 minutes
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
            in 5..6 -> 0.0      // Not tracked
            7 -> 0.5            // 50% effective
            8 -> 0.75           // 75% effective
            9 -> 0.9            // 90% effective
            10 -> 1.0           // 100% effective
            else -> 0.0
        }
    }

    fun getEffectivenessLabel(rpe: Int): String {
        return when(rpe) {
            in 0..6 -> "Below threshold (not effective)"
            7 -> "Building zone (50% effective)"
            8 -> "SWEET SPOT (75% effective)"
            9 -> "High effort (90% effective)"
            10 -> "Maximal (100% effective)"
            else -> "Unknown"
        }
    }
}
