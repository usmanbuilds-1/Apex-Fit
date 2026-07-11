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
        "Lower Back" to "lower_back",
        "Neck" to "neck",
        "Trapezius" to "trapezius",
        "Hip Abductors" to "hip_abductors",
        "Hip Adductors" to "hip_adductors",
        "Rotator Cuff" to "rotator_cuff",
        "Serratus Anterior" to "serratus_anterior",
        "Tibialis Anterior" to "tibialis_anterior"
    )

    fun getCanonical(muscle: String): String {
        val norm = muscle.trim()
        return map[norm]
            ?: map.entries.firstOrNull { it.key.equals(norm, ignoreCase = true) }?.value
            ?: norm.lowercase().replace(" ", "_")
    }
}
