package com.apexfit.app.utils

import com.apexfit.app.UserDefaults
import com.apexfit.app.data.*
import kotlin.math.roundToInt

fun calcMacroTargets(
    calorieTarget: Int,
    bodyWeightKg: Double,
    goal: String,
    heightCm: Double = UserDefaults.HEIGHT_CM,
    sex: String = "male"
): com.apexfit.app.data.NutritionTargets = MacroEngine.calcMacroTargets(calorieTarget, bodyWeightKg, goal, heightCm, sex)

object MacroEngine {
    fun calcMacroTargets(
        calorieTarget: Int,
        bodyWeightKg: Double,
        goal: String,
        heightCm: Double = UserDefaults.HEIGHT_CM,
        sex: String = "male"
    ): com.apexfit.app.data.NutritionTargets {
        val lbm = GoalEngine.estimateLBM(bodyWeightKg, heightCm, sex)
        val basisKg = minOf(lbm, bodyWeightKg)  // never exceed total weight

        val proteinMultiplier = when (goal.lowercase()) {
            "lose fat"        -> 2.4
            "recomposition"   -> 2.8
            "gain muscle"     -> 2.0
            else              -> 1.6
        }
        var proteinG = (basisKg * proteinMultiplier)
            .roundToInt()
            .coerceIn(100, 350)  // raise cap from 250 to 350

        var fatG = (bodyWeightKg * when (goal.lowercase()) {
            "gain muscle", "recomposition" -> 1.0   // 1g/kg supports hormonal function
            else -> 0.8
        }).roundToInt().coerceAtLeast(40)

        var proteinCals = proteinG * 4
        var fatCals     = fatG * 9

        if (proteinCals + fatCals >= calorieTarget) {
            val totalCals = proteinCals + fatCals
            if (totalCals > 0) {
                val scale = (calorieTarget.toDouble() / totalCals.toDouble()).coerceAtLeast(0.0)
                proteinG = (proteinG * scale).roundToInt()
                fatG = (fatG * scale).roundToInt()
                proteinCals = proteinG * 4
                fatCals = fatG * 9
            }
        }
        // Re-apply protein floor — scaling can push protein below minimum
        proteinG = proteinG.toDouble().coerceIn(100.0, 350.0).roundToInt()
        proteinCals = proteinG * 4
        // Recompute carbs to absorb the protein adjustment
        val carbG = ((calorieTarget - proteinG * 4.0 - fatG * 9.0) / 4.0).coerceAtLeast(0.0)

        val carbsG      = carbG.roundToInt().coerceIn(0, 600)

        val weeklySessions = when (goal.lowercase()) {
            "gain muscle" -> 4
            "lose fat"    -> 5
            else          -> 4
        }
        return com.apexfit.app.data.NutritionTargets(
            calories = calorieTarget,
            protein = proteinG,
            carbs = carbsG,
            fat = fatG,
            weeklyTrainingSessions = weeklySessions
        )
    }

    fun calcCycledTargets(
        weeklyCalorieTarget: Int,   // total weekly calories / 7 = flat daily
        weeklyTrainingSessions: Int,
        goal: String,
        bodyWeightKg: Double,
        heightCm: Double,
        sex: String
    ): TrainingDayTargets {
        val safeWeeklySessions = weeklyTrainingSessions.coerceIn(1, 6)
        val restDays = 7 - safeWeeklySessions
        val totalWeekly = weeklyCalorieTarget * 7

        var trainingBonus = when (goal.lowercase()) {
            "gain muscle"   -> 200
            "lose fat"      -> 100
            "recomposition" -> 150
            else -> 0
        }
        var trainingDayCals = weeklyCalorieTarget + trainingBonus
        var restDayCals = if (restDays > 0) {
            ((totalWeekly - (trainingDayCals * safeWeeklySessions))
                .toDouble() / restDays).roundToInt()
        } else {
            weeklyCalorieTarget
        }

        if (restDays > 0 && restDayCals < 800) {
            val proportion = if (weeklyCalorieTarget > restDayCals) {
                (weeklyCalorieTarget - 800).toDouble() / (weeklyCalorieTarget - restDayCals).toDouble()
            } else {
                0.0
            }
            trainingBonus = (trainingBonus * proportion).toInt().coerceAtLeast(0)
            trainingDayCals = weeklyCalorieTarget + trainingBonus
            restDayCals = ((totalWeekly - (trainingDayCals * safeWeeklySessions))
                .toDouble() / restDays).roundToInt()
            while (restDayCals < 800 && trainingBonus > 0) {
                trainingBonus--
                trainingDayCals = weeklyCalorieTarget + trainingBonus
                restDayCals = ((totalWeekly - (trainingDayCals * safeWeeklySessions))
                    .toDouble() / restDays).roundToInt()
            }
        }

        val lbm = GoalEngine.estimateLBM(bodyWeightKg, heightCm, sex)
        val trainingProtein = (lbm * 2.2).roundToInt()  // higher on training days
        val restProtein = (lbm * 1.8).roundToInt()

        val fat = (bodyWeightKg * 0.9).roundToInt().coerceAtLeast(40)

        val trainingCarbs = (((trainingDayCals - (trainingProtein * 4) - (fat * 9)).toDouble()) / 4.0)
            .coerceAtLeast(50.0).roundToInt()
        val restCarbs = (((restDayCals - (restProtein * 4) - (fat * 9)).toDouble()) / 4.0)
            .coerceAtLeast(30.0).roundToInt()

        return TrainingDayTargets(
            trainingDayCals, restDayCals,
            trainingProtein, restProtein,
            trainingCarbs, restCarbs,
            fat
        )
    }
}
