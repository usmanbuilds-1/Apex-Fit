package com.apexfit.app.utils

/**
 * Coarse training-focus categories used only to select contextual editorial imagery.
 *
 * This resolver intentionally ignores workout/session names. It derives the category
 * from the exercises that are actually scheduled so renamed, imported, PPL, bro-split,
 * upper/lower, and custom plans behave consistently.
 */
enum class SessionHeroCategory {
    UPPER_PUSH,
    UPPER_PULL,
    UPPER_ACCESSORY,
    LOWER_QUAD,
    LOWER_POSTERIOR,
    FULL_BODY,
    GENERIC_STRENGTH,
    RECOVERY
}

data class SessionHeroExercise(
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val workingSets: Int
)

object SessionHeroResolver {
    private const val SECONDARY_TOTAL_SHARE = 0.25

    fun resolve(exercises: List<SessionHeroExercise>): SessionHeroCategory {
        val active = exercises.filter { it.workingSets > 0 }
        if (active.isEmpty()) return SessionHeroCategory.RECOVERY

        val scores = linkedMapOf(
            SessionHeroCategory.UPPER_PUSH to 0.0,
            SessionHeroCategory.UPPER_PULL to 0.0,
            SessionHeroCategory.UPPER_ACCESSORY to 0.0,
            SessionHeroCategory.LOWER_QUAD to 0.0,
            SessionHeroCategory.LOWER_POSTERIOR to 0.0
        )

        var upperLoad = 0.0
        var lowerLoad = 0.0
        var torsoPrimaryLoad = 0.0
        var lowerPrimaryLoad = 0.0
        var accessoryPrimaryLoad = 0.0
        var classifiedLoad = 0.0

        fun addMuscle(rawMuscle: String, load: Double, isPrimary: Boolean) {
            if (load <= 0.0) return
            when (MuscleAliases.getCanonical(rawMuscle)) {
                "chest" -> {
                    scores[SessionHeroCategory.UPPER_PUSH] = scores.getValue(SessionHeroCategory.UPPER_PUSH) + load
                    upperLoad += load
                    classifiedLoad += load
                    if (isPrimary) torsoPrimaryLoad += load
                }
                "triceps" -> {
                    scores[SessionHeroCategory.UPPER_PUSH] = scores.getValue(SessionHeroCategory.UPPER_PUSH) + load
                    upperLoad += load
                    classifiedLoad += load
                    if (isPrimary) accessoryPrimaryLoad += load
                }
                "back", "trapezius" -> {
                    scores[SessionHeroCategory.UPPER_PULL] = scores.getValue(SessionHeroCategory.UPPER_PULL) + load
                    upperLoad += load
                    classifiedLoad += load
                    if (isPrimary) torsoPrimaryLoad += load
                }
                "biceps" -> {
                    scores[SessionHeroCategory.UPPER_PULL] = scores.getValue(SessionHeroCategory.UPPER_PULL) + load
                    upperLoad += load
                    classifiedLoad += load
                    if (isPrimary) accessoryPrimaryLoad += load
                }
                "shoulders", "forearms", "neck", "rotator_cuff", "serratus_anterior" -> {
                    scores[SessionHeroCategory.UPPER_ACCESSORY] = scores.getValue(SessionHeroCategory.UPPER_ACCESSORY) + load
                    upperLoad += load
                    classifiedLoad += load
                    if (isPrimary) accessoryPrimaryLoad += load
                }
                "quads", "hip_adductors", "hip_abductors" -> {
                    scores[SessionHeroCategory.LOWER_QUAD] = scores.getValue(SessionHeroCategory.LOWER_QUAD) + load
                    lowerLoad += load
                    classifiedLoad += load
                    if (isPrimary) lowerPrimaryLoad += load
                }
                "hamstrings", "glutes", "lower_back", "calves", "tibialis_anterior" -> {
                    scores[SessionHeroCategory.LOWER_POSTERIOR] = scores.getValue(SessionHeroCategory.LOWER_POSTERIOR) + load
                    lowerLoad += load
                    classifiedLoad += load
                    if (isPrimary) lowerPrimaryLoad += load
                }
                // Core-only or unknown work is deliberately not forced into an unrelated photo category.
                "core" -> Unit
                else -> Unit
            }
        }

        active.forEach { exercise ->
            val primaryLoad = exercise.workingSets.toDouble()
            addMuscle(exercise.primaryMuscle, primaryLoad, isPrimary = true)

            val secondaries = exercise.secondaryMuscles
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { MuscleAliases.getCanonical(it) }

            if (secondaries.isNotEmpty()) {
                val perSecondary = primaryLoad * SECONDARY_TOTAL_SHARE / secondaries.size
                secondaries.forEach { addMuscle(it, perSecondary, isPrimary = false) }
            }
        }

        // A true mixed upper/lower session gets a full-body image only when neither half is token volume.
        if (upperLoad > 0.0 && lowerLoad > 0.0) {
            val balance = minOf(upperLoad, lowerLoad) / maxOf(upperLoad, lowerLoad)
            if (balance >= 0.50) return SessionHeroCategory.FULL_BODY
        }

        // An arms/shoulders-focused day should not look like a chest or back session just because
        // triceps/biceps contribute to push/pull scores.
        if (torsoPrimaryLoad == 0.0 && lowerPrimaryLoad == 0.0 && accessoryPrimaryLoad > 0.0) {
            return SessionHeroCategory.UPPER_ACCESSORY
        }

        if (classifiedLoad == 0.0) return SessionHeroCategory.GENERIC_STRENGTH

        return scores.maxByOrNull { it.value }
            ?.takeIf { it.value > 0.0 }
            ?.key
            ?: SessionHeroCategory.GENERIC_STRENGTH
    }
}
