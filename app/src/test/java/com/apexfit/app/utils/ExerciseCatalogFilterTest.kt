package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogFilterTest {
    private val library = listOf(
        exercise("bench", "Dumbbell Bench Press", "Chest", listOf("Triceps", "Shoulders"), "Dumbbell"),
        exercise("fly", "Cable Fly", "Chest", emptyList(), "Cable"),
        exercise("dip", "Bench Dip", "Triceps", listOf("Chest"), "Bodyweight"),
        exercise("row", "Seated Cable Row", "Back", listOf("Biceps"), "Cable"),
        exercise("curl", "Dumbbell Curl", "Biceps", emptyList(), "Dumbbell"),
        exercise("deleted", "Old Chest Press", "Chest", emptyList(), "Machine", isDeleted = 1)
    )

    @Test
    fun primaryMuscleResultsComeFirstAndSecondaryIsOptIn() {
        val primaryOnly = ExerciseCatalogFilter.filter(
            library,
            muscle = "Chest",
            includeSecondaryMuscleMatches = false
        )
        assertEquals(listOf("Cable Fly", "Dumbbell Bench Press"), primaryOnly.map { it.exercise.name })
        assertTrue(primaryOnly.all { it.muscleMatch == MuscleMatchType.PRIMARY })

        val withSecondary = ExerciseCatalogFilter.filter(
            library,
            muscle = "Chest",
            includeSecondaryMuscleMatches = true
        )
        assertEquals(
            listOf("Cable Fly", "Dumbbell Bench Press", "Bench Dip"),
            withSecondary.map { it.exercise.name }
        )
        assertEquals(MuscleMatchType.SECONDARY, withSecondary.last().muscleMatch)
    }

    @Test
    fun equipmentAndSearchFiltersCompose() {
        val results = ExerciseCatalogFilter.filter(
            library,
            query = "cable",
            equipment = setOf("Cable")
        )
        assertEquals(listOf("Cable Fly", "Seated Cable Row"), results.map { it.exercise.name })
    }

    @Test
    fun deletedExercisesNeverAppear() {
        val results = ExerciseCatalogFilter.filter(library, muscle = "Chest")
        assertTrue(results.none { it.exercise.id == "deleted" })
    }

    private fun exercise(
        id: String,
        name: String,
        primary: String,
        secondary: List<String>,
        equipment: String,
        isDeleted: Int = 0
    ) = Exercise(
        id = id,
        name = name,
        category = equipment,
        primaryMuscle = primary,
        secondaryMuscles = secondary,
        equipmentRequired = equipment,
        isDeleted = isDeleted
    )
}
