package com.apexfit.app.utils

import com.apexfit.app.UserDefaults
import com.apexfit.app.data.*
import kotlin.math.roundToInt

fun calcMacroTargets(
    calorieTarget: Int,
    bodyWeightKg: Double,
    goal: String,
    heightCm: Double = UserDefaults.HEIGHT_CM,
    sex: String = "male",
    weeklyTrainingSessions: Int = UserDefaults.WEEKLY_WORKOUTS
): com.apexfit.app.data.NutritionTargets =
    MacroEngine.calcMacroTargets(calorieTarget, bodyWeightKg, goal, heightCm, sex, weeklyTrainingSessions)

object MacroEngine {
    // General adult resistance-training defaults, not individualized prescriptions.
    // Morton et al. (2018), doi:10.1136/bjsports-2017-097608;
    // Iraki et al. (2019), doi:10.3390/sports7070154.
    private fun proteinGoal(weightKg: Double, goal: String): Int =
        (weightKg.coerceAtLeast(0.0) * when (goal.trim().lowercase()) {
            "lose fat" -> 2.2
            "recomposition" -> 2.0
            "gain muscle", "build muscle" -> 1.8
            else -> 1.6
        }).roundToInt().coerceIn(0, 350)

    private fun fatGoal(weightKg: Double, goal: String, budget: Int): Int {
        val gramsPerKg = when (goal.trim().lowercase()) {
            "gain muscle", "build muscle", "recomposition" -> 1.0
            else -> 0.8
        }
        return maxOf((weightKg.coerceAtLeast(0.0) * gramsPerKg).roundToInt(),
            kotlin.math.ceil(budget * 0.20 / 9.0).toInt())
    }

    // A product guardrail against additional cycling reductions; not a medically
    // established minimum energy requirement for a particular person.
    fun hasRestrictedBudget(calories: Int, bodyWeightKg: Double, goal: String): Boolean {
        val budget = calories.coerceAtLeast(0)
        return budget < 1200 ||
            proteinGoal(bodyWeightKg, goal).toLong() * 4 + fatGoal(bodyWeightKg, goal, budget).toLong() * 9 > budget
    }

    private fun allocate(budget: Int, desiredProtein: Int, desiredFat: Int): Triple<Int, Int, Int> {
        val required = desiredProtein.toLong() * 4 + desiredFat.toLong() * 9
        val scale = if (required > budget && required > 0) budget.toDouble() / required else 1.0
        val protein = (desiredProtein * scale).toInt()
        val fat = (desiredFat * scale).toInt()
        val carbs = ((budget - protein * 4 - fat * 9).coerceAtLeast(0) / 4.0).roundToInt()
        return Triple(protein, carbs, fat)
    }

    fun calcMacroTargets(
        calorieTarget: Int,
        bodyWeightKg: Double,
        goal: String,
        heightCm: Double = UserDefaults.HEIGHT_CM,
        sex: String = "male",
        weeklyTrainingSessions: Int = UserDefaults.WEEKLY_WORKOUTS
    ): com.apexfit.app.data.NutritionTargets {
        val budget = calorieTarget.coerceAtLeast(0)
        val (protein, carbs, fat) = allocate(budget, proteinGoal(bodyWeightKg, goal),
            fatGoal(bodyWeightKg, goal, budget))
        return com.apexfit.app.data.NutritionTargets(
            calories = budget, protein = protein, carbs = carbs, fat = fat,
            weeklyTrainingSessions = weeklyTrainingSessions.coerceIn(0, 7)
        )
    }

    /** Exact seven-day projected calorie allocation. Distribute rounding remainder over rest dates.
     * No carry-forward debt and no claim about previously displayed targets. */
    fun calcProjectedDayTargets(baseCalories: Int, projection: WeeklyNutritionProjection, date: java.time.LocalDate,
                                goal: String, bodyWeightKg: Double, heightCm: Double, sex: String): com.apexfit.app.data.NutritionTargets {
        require(date in projection.monday..projection.monday.plusDays(6))
        val base = baseCalories.coerceAtLeast(0)
        val count = projection.frequency
        val cycle = calcCycledTargets(base, count, goal, bodyWeightKg, heightCm, sex)
        val restDates = (0L..6L).map(projection.monday::plusDays).filterNot { it in projection.trainingDates }
        val remainderBudget = base.toLong() * 7 - cycle.trainingDayCalories.toLong() * count
        val calories = if (date in projection.trainingDates) cycle.trainingDayCalories else {
            val quotient = remainderBudget / restDates.size
            val remainder = remainderBudget % restDates.size
            (quotient + if (restDates.indexOf(date).toLong() < remainder) 1 else 0).toInt()
        }
        val protein = cycle.trainingDayProtein
        val fat = cycle.fat
        val carbs = ((calories - protein * 4 - fat * 9).coerceAtLeast(0) / 4.0).roundToInt()
        return com.apexfit.app.data.NutritionTargets(calories, protein, carbs, fat, count)
    }

    fun calcCycledTargets(
        weeklyCalorieTarget: Int,
        weeklyTrainingSessions: Int,
        goal: String,
        bodyWeightKg: Double,
        heightCm: Double,
        sex: String
    ): TrainingDayTargets {
        val base = weeklyCalorieTarget.coerceAtLeast(0)
        val trainingDays = weeklyTrainingSessions.coerceIn(0, 7)
        val restDays = 7 - trainingDays
        val desiredProtein = proteinGoal(bodyWeightKg, goal)
        val desiredFat = fatGoal(bodyWeightKg, goal, base)
        val desiredEnergy = desiredProtein.toLong() * 4 + desiredFat.toLong() * 9
        // Preserve the explicitly selected weekly budget. If that budget is
        // already restricted, flatten cycling rather than silently raising it.
        val restFloor = maxOf(1200L, kotlin.math.ceil(base * 0.90).toLong(), desiredEnergy)
            .coerceAtMost(base.toLong())
        val preferredBonus = when (goal.trim().lowercase()) {
            "gain muscle", "build muscle" -> 200
            "lose fat" -> 100
            "recomposition" -> 150
            else -> 0
        }
        val bonus = if (trainingDays == 0 || restDays == 0 || hasRestrictedBudget(base, bodyWeightKg, goal)) 0
            else minOf(preferredBonus.toLong(), (base - restFloor) * restDays / trainingDays).toInt()
        val trainCalories = base + bonus
        val restCalories = if (restDays == 0) base else
            ((base.toLong() * 7 - trainCalories.toLong() * trainingDays).toDouble() / restDays).roundToInt()
        // Keep protein/fat consistent across rest and training days. Allocate
        // against the lower budget first; carbohydrate carries the difference.
        val (protein, _, fat) = allocate(minOf(trainCalories, restCalories), desiredProtein, desiredFat)
        fun carbs(calories: Int) = ((calories - protein * 4 - fat * 9).coerceAtLeast(0) / 4.0).roundToInt()
        return TrainingDayTargets(trainCalories, restCalories, protein, protein,
            carbs(trainCalories), carbs(restCalories), fat)
    }
}
