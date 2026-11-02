package com.apexfit.app.data

import com.apexfit.app.utils.AppConstants

data class NutritionPreferences(
    val protein: String = "No preference",
    val restrictions: String = "None",
    val budget: String = "Budget friendly",
    val supplements: String = "None"
)

/** All body measurements are canonical metric values, regardless of display units. */
data class OnboardingProfile(
    val name: String = "Athlete",
    val goal: String = "Gain Muscle",
    val age: Int = 26,
    val heightCm: Double = 175.0,
    val weightKg: Double = 70.0,
    val targetWeightKg: Double = 70.0,
    val sex: String = "male",
    val units: String = "kg",
    val experience: String = "Beginner",
    val daysPerWeek: Int = 3,
    val proteinPreference: String = "No preference",
    val dietaryRestrictions: String = "None",
    val foodBudget: String = "Budget friendly",
    val supplements: String = "None"
) {
    fun validationError(): String? = when {
        name.isBlank() || name.length > AppConstants.MAX_NAME_LENGTH -> "Please enter a name of 1–50 characters."
        goal !in GOALS -> "Choose a training goal."
        age !in AppConstants.MIN_AGE..AppConstants.MAX_AGE -> "Enter an age from 13 to 100."
        !heightCm.isFinite() || heightCm !in AppConstants.MIN_HEIGHT_CM..AppConstants.MAX_HEIGHT_CM -> "Enter a height from 100 to 250 cm."
        !weightKg.isFinite() || weightKg !in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG -> "Enter a weight from 20 to 300 kg."
        !targetWeightKg.isFinite() || targetWeightKg !in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG -> "Enter a target weight from 20 to 300 kg."
        sex !in listOf("male", "female") -> "Choose the sex used for calorie estimates."
        units !in listOf("kg", "lb") -> "Choose metric or imperial units."
        experience !in EXPERIENCE_LEVELS -> "Choose your training experience."
        daysPerWeek !in 3..6 -> "Choose 3 to 6 training days."
        else -> null
    }

    companion object {
        val GOALS = listOf("Gain Muscle", "Lose Fat", "Maintain", "Recomposition")
        val EXPERIENCE_LEVELS = listOf("Beginner", "Intermediate", "Advanced")
    }
}
