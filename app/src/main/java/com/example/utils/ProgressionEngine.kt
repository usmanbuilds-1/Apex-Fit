package com.example.utils

import kotlin.math.roundToInt

object ProgressionEngine {

    fun getExerciseType(name: String, muscleGroup: String): String {
        val n = name.lowercase()
        val m = muscleGroup.lowercase()
        
        return when {
            // Cardio & Core
            n.contains("run") || n.contains("jog") || n.contains("treadmill") || n.contains("cardio") || n.contains("bike") || n.contains("cycle") || n.contains("elliptical") || n.contains("walk") || m.contains("cardio") || m.contains("core") || n.contains("plank") || n.contains("crunch") -> "cardio"

            // Compound Lower
            n.contains("squat") && !n.contains("dumbbell") && !n.contains("db") && !n.contains("machine") && !n.contains("smith") -> "compound_lower"
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
        userBodyWeightLbs: Double,
        userHeightCm: Double  // for lever arm adjustment
    ): Double {
        
        val baseMultiplier = when(exerciseType) {
            "compound_upper" -> 0.35    // Barbell bench: 128 × 0.35 = 44.8 lbs
            "compound_lower" -> 0.40    // Barbell squat: 128 × 0.40 = 51.2 lbs
            "dumbbell_upper" -> 0.12    // DB chest press: 128 × 0.12 = 15.4 lbs per DB
            "dumbbell_lower" -> 0.15    // DB goblet squat: 128 × 0.15 = 19.2 lbs
            "isolation" -> 0.08         // Bicep curl: 128 × 0.08 = 10.2 lbs
            else -> 0.10
        }
        
        var startWeight = userBodyWeightLbs * baseMultiplier
        
        // Adjust for height (tall = longer ROM = harder)
        if (userHeightCm > 185) {  // Taller than 6'1"
            startWeight *= 0.90  // Reduce by 10%
        } else if (userHeightCm < 170) {  // Shorter than 5'7"
            startWeight *= 1.10  // Increase by 10%
        }
        
        return startWeight.roundToNearest2_5()
    }

    fun calculateProgressiveWeight(
        lastWeight: Double,
        lastRPE: Int,
        daysSinceLastSession: Int,
        userBodyWeightLbs: Double,
        exerciseType: String
    ): Double {
        
        // Step 1: Base increment by exercise type
        val baseIncrement = when(exerciseType) {
            "compound_lower" -> 10.0      // 10 lbs
            "compound_upper" -> 5.0       // 5 lbs
            "dumbbell_upper" -> 5.0       // 5 lbs per dumbbell
            "dumbbell_lower" -> 5.0       // 5 lbs per dumbbell
            "isolation" -> 2.5            // 2.5 lbs
            else -> 5.0
        }
        
        // Step 2: Adjust for RPE (progression zone)
        val rpeMultiplier = when(lastRPE) {
            in 5..6 -> 0.0        // RPE <7: don't progress, maintain weight
            7 -> 0.5              // RPE 7: light progression (+50% of base)
            8 -> 1.0              // RPE 8: SWEET SPOT — full progression
            9 -> 0.5              // RPE 9: conservative (+50% of base)
            10 -> 0.0             // RPE 10: maxed out, maintain
            else -> 1.0
        }
        
        // Step 3: Adjust for recovery (days since last session)
        val recoveryMultiplier = when {
            daysSinceLastSession < 2 -> 0.5        // <48 hours: very conservative
            daysSinceLastSession in 2..3 -> 1.0    // 48-72 hours: normal
            daysSinceLastSession > 3 -> 1.25       // >72 hours: aggressive
            else -> 1.0
        }
        
        // Step 4: Adjust for body weight (relative strength potential)
        val bodyWeightAdjustment = when {
            userBodyWeightLbs < 140.0 -> 0.75    // Light: scale down
            userBodyWeightLbs >= 140.0 && userBodyWeightLbs <= 200.0 -> 1.0   // Normal: standard
            userBodyWeightLbs > 200.0 -> 1.25    // Heavy: scale up
            else -> 1.0
        }
        
        // Step 5: Calculate total increment
        val totalIncrement = baseIncrement * rpeMultiplier * recoveryMultiplier * bodyWeightAdjustment
        
        val suggestedWeight = lastWeight + totalIncrement
        
        return suggestedWeight.roundToNearest2_5()
    }

    fun getDaysBetween(lastDate: String, today: String): Int {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val last = format.parse(lastDate)
            val current = format.parse(today)
            if (last != null && current != null) {
                val diffInMillis = current.time - last.time
                val days = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
                if (days < 0) 2 else days
            } else {
                2
            }
        } catch (e: Exception) {
            2 // default fallback
        }
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

    fun getRIRDescription(repsInReserve: Int): String {
        return when(repsInReserve) {
            in 4..Int.MAX_VALUE -> "4 or more (very easy)"
            3 -> "3 more reps"
            2 -> "2 more reps (felt good) — Suggested"
            1 -> "1 more rep"
            0 -> "0 more reps (maxed out)"
            else -> "Unknown"
        }
    }

    fun suggestRIRFromPreviousSession(
        exerciseId: String,
        lastSetRIR: Int?
    ): Int {
        return lastSetRIR ?: 2  // Default to sweet spot (2 reps left = RPE 8)
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
