package com.apexfit.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.apexfit.app.data.Exercise
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexFitTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanBuilderExerciseEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun choosingLibraryExerciseReturnsToEditorWithoutReplacingPrescription() {
        val savedForm = AtomicReference<PlanBuilderExerciseEditorForm>()
        val initialForm = PlanBuilderExerciseEditorForm(
            name = "",
            muscleGroup = "Chest",
            sets = "4",
            minReps = "6",
            maxReps = "10",
            weight = "30",
            weightUnit = "kg",
            restSeconds = "120",
            notes = "Pause briefly at the bottom."
        )

        composeRule.setContent {
            ApexFitTheme {
                var form by remember { mutableStateOf(initialForm) }
                PlanBuilderExerciseEditorHost(
                    form = form,
                    isEditing = false,
                    libraryState = UiState.Success(
                        listOf(
                            Exercise(
                                id = "cable-chest-press",
                                name = "Cable Chest Press",
                                category = "Cable",
                                primaryMuscle = "Chest",
                                equipmentRequired = "Cable"
                            )
                        )
                    ),
                    onDismiss = {},
                    onFieldChange = { field, value ->
                        form = when (field) {
                            PlanBuilderExerciseEditorField.NAME -> form.copy(name = value)
                            PlanBuilderExerciseEditorField.SETS -> form.copy(sets = value)
                            PlanBuilderExerciseEditorField.MIN_REPS -> form.copy(minReps = value)
                            PlanBuilderExerciseEditorField.MAX_REPS -> form.copy(maxReps = value)
                            PlanBuilderExerciseEditorField.WEIGHT -> form.copy(weight = value)
                            PlanBuilderExerciseEditorField.REST -> form.copy(restSeconds = value)
                            PlanBuilderExerciseEditorField.NOTES -> form.copy(notes = value)
                        }
                    },
                    onMuscleChange = { form = form.copy(muscleGroup = it) },
                    onWeightUnitChange = { form = form.copy(weightUnit = it) },
                    onExerciseSelected = {
                        form = form.copy(name = it.name, muscleGroup = it.primaryMuscle)
                    },
                    onSave = { savedForm.set(form) }
                )
            }
        }

        composeRule.onNodeWithTag("browse_exercise_library_button").performClick()
        composeRule.onNodeWithText("Choose exercise").assertIsDisplayed()
        composeRule.onNodeWithText("Cable Chest Press").performClick()
        composeRule.onNodeWithTag("exercise_name_input").assertTextContains("Cable Chest Press")
        composeRule.onNodeWithTag("save_exercise_confirm_button").performClick()

        val saved = requireNotNull(savedForm.get())
        assertEquals("Cable Chest Press", saved.name)
        assertEquals("Chest", saved.muscleGroup)
        assertEquals("4", saved.sets)
        assertEquals("6", saved.minReps)
        assertEquals("10", saved.maxReps)
        assertEquals("30", saved.weight)
        assertEquals("kg", saved.weightUnit)
        assertEquals("120", saved.restSeconds)
        assertEquals("Pause briefly at the bottom.", saved.notes)
    }

    @Test
    fun editingAndSavingKeepsExistingExercisePrescription() {
        val savedForm = AtomicReference<PlanBuilderExerciseEditorForm>()
        val existingForm = PlanBuilderExerciseEditorForm(
            name = "Dumbbell Chest Press",
            muscleGroup = "Chest",
            sets = "3",
            minReps = "8",
            maxReps = "12",
            weight = "15",
            weightUnit = "kg",
            restSeconds = "90",
            notes = "Keep wrists neutral."
        )

        composeRule.setContent {
            ApexFitTheme {
                PlanBuilderExerciseEditorHost(
                    form = existingForm,
                    isEditing = true,
                    libraryState = UiState.Success(emptyList()),
                    onDismiss = {},
                    onFieldChange = { _, _ -> },
                    onMuscleChange = {},
                    onWeightUnitChange = {},
                    onExerciseSelected = {},
                    onSave = { savedForm.set(existingForm) }
                )
            }
        }

        composeRule.onNodeWithText("Edit exercise").assertIsDisplayed()
        composeRule.onNodeWithTag("save_exercise_confirm_button").performClick()

        assertEquals(existingForm, savedForm.get())
    }
}

