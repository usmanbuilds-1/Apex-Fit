package com.apexfit.app.utils

import com.apexfit.app.data.Exercise

enum class MuscleMatchType { PRIMARY, SECONDARY, NONE }

data class ExerciseCatalogResult(
    val exercise: Exercise,
    val muscleMatch: MuscleMatchType
)

/**
 * Shared filtering/ranking logic for Exercise Library, Plan Builder, and Swap flows.
 * UI layers should reuse this instead of implementing three subtly different filters.
 */
object ExerciseCatalogFilter {
    fun filter(
        exercises: List<Exercise>,
        query: String = "",
        equipment: Set<String> = emptySet(),
        muscle: String? = null,
        includeSecondaryMuscleMatches: Boolean = false
    ): List<ExerciseCatalogResult> {
        val normalizedQuery = query.trim().lowercase()
        val normalizedEquipment = equipment
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val canonicalMuscle = muscle
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(MuscleAliases::getCanonical)

        return exercises.asSequence()
            .filter { !it.isDeleted() }
            .filter { exercise ->
                normalizedQuery.isEmpty() ||
                    exercise.name.lowercase().contains(normalizedQuery) ||
                    exercise.primaryMuscle.lowercase().contains(normalizedQuery) ||
                    exercise.equipmentRequired.lowercase().contains(normalizedQuery)
            }
            .filter { exercise ->
                normalizedEquipment.isEmpty() ||
                    normalizedEquipment.any { requested ->
                        exercise.equipmentRequired
                            .lowercase()
                            .split(',', '/', '+')
                            .map { it.trim() }
                            .any { actual ->
                                actual == requested ||
                                    actual.contains(requested) ||
                                    requested.contains(actual)
                            }
                    }
            }
            .mapNotNull { exercise ->
                val match = when {
                    canonicalMuscle == null -> MuscleMatchType.NONE
                    MuscleAliases.getCanonical(exercise.primaryMuscle) == canonicalMuscle ->
                        MuscleMatchType.PRIMARY
                    includeSecondaryMuscleMatches && exercise.secondaryMuscles.any {
                        MuscleAliases.getCanonical(it) == canonicalMuscle
                    } -> MuscleMatchType.SECONDARY
                    else -> MuscleMatchType.NONE
                }

                if (canonicalMuscle != null && match == MuscleMatchType.NONE) null
                else ExerciseCatalogResult(exercise, match)
            }
            .sortedWith(
                compareBy<ExerciseCatalogResult> {
                    when (it.muscleMatch) {
                        MuscleMatchType.PRIMARY -> 0
                        MuscleMatchType.SECONDARY -> 1
                        MuscleMatchType.NONE -> 2
                    }
                }.thenBy { it.exercise.name.lowercase() }
            )
            .toList()
    }

    private fun Exercise.isDeleted(): Boolean = isDeleted != 0
}
