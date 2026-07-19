package com.example.utils

object AppConstants {
    // Nutrition
    const val CALORIES_PER_GRAM_FAT = 9.0
    const val CALORIES_PER_GRAM_PROTEIN = 4.0
    const val CALORIES_PER_GRAM_CARB = 4.0
    const val CALORIES_PER_KG_BODYFAT = 7700.0

    // Unit conversion
    const val KG_TO_LBS = 2.20462
    const val LBS_TO_KG = 0.453592
    const val CM_TO_INCHES = 0.393701
    const val INCHES_TO_CM = 2.54

    // Progression
    const val DELOAD_PERCENTAGE = 0.90
    const val BEGINNER_WEEKS_THRESHOLD = 12
    const val RPE_TARGET_DEFAULT = 7

    // Algorithm thresholds
    const val PLATEAU_THRESHOLD_WEEKS = 3
    const val COMPLIANCE_GOOD_THRESHOLD = 80.0
    const val COMPLIANCE_POOR_THRESHOLD = 60.0
    const val READINESS_HIGH_THRESHOLD = 75
    const val READINESS_LOW_THRESHOLD = 40

    // UI / validation
    const val REST_TIMER_DEFAULT_SECONDS = 90
    const val MAX_NAME_LENGTH = 50
    const val MIN_AGE = 10
    const val MAX_AGE = 100
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 500.0
}
