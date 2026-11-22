package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.R
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderMetadataWithValues() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            PlanBuilderMetadataFields(
                planName = "APEX Upper / Lower",
                planGoal = "Build muscle",
                planNameError = null,
                onPlanNameChange = {},
                onPlanGoalChange = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderNameValidationError() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            PlanBuilderMetadataFields(
                planName = "",
                planGoal = "Build muscle",
                planNameError = "Enter a plan name",
                onPlanNameChange = {},
                onPlanGoalChange = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderSaveAction() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            PlanBuilderSaveBar(isSaving = false, onSave = {})
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderAddDayDialogRender() {
    ApexFitTheme {
        Box(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            PlanBuilderDayDialog(
                initialLabel = "",
                initialDayOfWeek = "Monday",
                isEditing = false,
                onDismiss = {},
                onSave = { _, _ -> }
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderEditDayDialogRender() {
    ApexFitTheme {
        Box(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            PlanBuilderDayDialog(
                initialLabel = "Upper Body A",
                initialDayOfWeek = "Monday",
                isEditing = true,
                onDismiss = {},
                onSave = { _, _ -> }
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderDayDialogValidationErrorRender() {
    ApexFitTheme {
        Box(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            PlanBuilderDayDialog(
                initialLabel = "",
                initialDayOfWeek = "Monday",
                isEditing = false,
                onDismiss = {},
                onSave = { _, _ -> },
                initialValidationError = "A session name is required"
            )
        }
    }
}

private fun exerciseEditorForm(
    name: String = "",
    sets: String = "3",
    minReps: String = "8",
    maxReps: String = "12",
    weight: String = "",
    notes: String = ""
) = PlanBuilderExerciseEditorForm(
    name = name,
    muscleGroup = "Chest",
    sets = sets,
    minReps = minReps,
    maxReps = maxReps,
    weight = weight,
    weightUnit = "kg",
    restSeconds = "90",
    notes = notes
)

@Composable
private fun PlanBuilderExerciseEditorPreview(form: PlanBuilderExerciseEditorForm, isEditing: Boolean) {
    Box(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
        PlanBuilderExerciseEditorDialog(
            form = form,
            isEditing = isEditing,
            onDismiss = {},
            onBrowseLibrary = {},
            onFieldChange = { _, _ -> },
            onMuscleChange = {},
            onWeightUnitChange = {},
            onSave = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderAddExerciseDialogRender() {
    ApexFitTheme {
        PlanBuilderExerciseEditorPreview(
            form = exerciseEditorForm(),
            isEditing = false
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderEditExerciseDialogRender() {
    ApexFitTheme {
        PlanBuilderExerciseEditorPreview(
            form = exerciseEditorForm(
                name = "Dumbbell Chest Press",
                sets = "3",
                minReps = "8",
                maxReps = "12",
                weight = "15",
                notes = "Keep wrists neutral."
            ),
            isEditing = true
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderExerciseEditorInvalidSetsRender() {
    ApexFitTheme {
        PlanBuilderExerciseEditorPreview(
            form = exerciseEditorForm(sets = "0").copy(
                setsError = stringResource(R.string.plan_builder_sets_range)
            ),
            isEditing = false
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderExerciseEditorInvalidRepRangeRender() {
    ApexFitTheme {
        PlanBuilderExerciseEditorPreview(
            form = exerciseEditorForm(minReps = "14", maxReps = "8").copy(
                minRepsError = stringResource(R.string.plan_builder_min_reps_greater_than_max)
            ),
            isEditing = true
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US", fontScale = 1.3f)
@Composable
fun PlanBuilderEditExerciseDialogLargeFontRender() {
    ApexFitTheme {
        PlanBuilderExerciseEditorPreview(
            form = exerciseEditorForm(
                name = "Dumbbell Chest Press",
                sets = "3",
                minReps = "8",
                maxReps = "12",
                weight = "15",
                notes = "Keep wrists neutral."
            ),
            isEditing = true
        )
    }
}

