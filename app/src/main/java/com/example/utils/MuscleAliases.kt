package com.example.utils

object MuscleAliases {
    val map = mapOf(
        "Chest" to "chest",
        "Back" to "back", "Lats" to "back",
        "Shoulders" to "shoulders", "Delts" to "shoulders", "Front Delts" to "shoulders", "Side Delts" to "shoulders", "Rear Delts" to "shoulders",
        "Quads" to "quads", "Quadriceps" to "quads",
        "Hamstrings" to "hamstrings",
        "Glutes" to "glutes",
        "Calves" to "calves",
        "Biceps" to "biceps",
        "Triceps" to "triceps",
        "Core" to "core", "Abs" to "core",
        "Forearms" to "forearms",
        "Lower Back" to "lower_back"
    )

    fun getCanonical(muscle: String): String {
        return map[muscle] ?: muscle.lowercase().replace(" ", "_")
    }
}
