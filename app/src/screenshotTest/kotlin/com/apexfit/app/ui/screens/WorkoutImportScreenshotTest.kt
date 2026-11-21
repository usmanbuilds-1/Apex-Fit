package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.WorkoutImportStage
import com.apexfit.app.WorkoutImportUiState
import com.apexfit.app.domain.importer.EditableImportedDay
import com.apexfit.app.domain.importer.EditableImportedExercise
import com.apexfit.app.domain.importer.EditableImportedPlan
import com.apexfit.app.domain.importer.ExerciseCandidate
import com.apexfit.app.domain.importer.ExerciseLibraryEntry
import com.apexfit.app.domain.importer.ImportRepTarget
import com.apexfit.app.domain.importer.ImportWeightUnit
import com.apexfit.app.domain.importer.MatchConfidence
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutImportReviewWithUnresolvedExercise() {
    RenderWorkoutImportReview(errorMessage = null)
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutImportReviewSaveError() {
    RenderWorkoutImportReview(errorMessage = "Couldn't save this plan. Try again.")
}

@Composable
private fun RenderWorkoutImportReview(errorMessage: String?) {
    val library = listOf(
        ExerciseLibraryEntry(
            id = "barbell-bench-press",
            name = "Barbell Bench Press",
            primaryMuscle = "Chest",
            equipment = "Barbell"
        ),
        ExerciseLibraryEntry(
            id = "cable-chest-fly",
            name = "Cable Chest Fly",
            primaryMuscle = "Chest",
            equipment = "Cable"
        )
    )
    val exactCandidate = ExerciseCandidate(
        entry = library[0], confidence = MatchConfidence.HIGH, score = 93, flagged = false
    )
    val unresolvedCandidate = ExerciseCandidate(
        entry = library[1], confidence = MatchConfidence.MEDIUM, score = 71, flagged = true
    )
    val draft = EditableImportedPlan(
        name = "Upper Body A",
        originalText = "Monday Push\nBench Press 3x8-10\nCable fly 3x12-15",
        days = listOf(
            EditableImportedDay(
                key = "monday-push",
                label = "Push",
                dayOfWeek = "Monday",
                focus = "Chest",
                exercises = listOf(
                    EditableImportedExercise(
                        key = "bench-press",
                        sourceName = "Bench Press",
                        selectedExerciseId = library[0].id,
                        selectedExerciseName = library[0].name,
                        muscleGroup = "Chest",
                        matchConfidence = MatchConfidence.HIGH,
                        matchConfirmed = true,
                        candidates = listOf(exactCandidate),
                        setsText = "3",
                        repsMinText = "8",
                        repsMaxText = "10",
                        weightText = "60",
                        weightUnit = ImportWeightUnit.KG,
                        restSecondsText = "120",
                        repTarget = ImportRepTarget.RANGE,
                        notes = "",
                        sourcePrescription = "3x8-10"
                    ),
                    EditableImportedExercise(
                        key = "cable-fly",
                        sourceName = "Cable fly",
                        selectedExerciseId = null,
                        selectedExerciseName = null,
                        muscleGroup = null,
                        matchConfidence = MatchConfidence.UNRESOLVED,
                        matchConfirmed = false,
                        candidates = listOf(unresolvedCandidate),
                        setsText = "3",
                        repsMinText = "12",
                        repsMaxText = "15",
                        weightText = "",
                        weightUnit = ImportWeightUnit.KG,
                        restSecondsText = "90",
                        repTarget = ImportRepTarget.RANGE,
                        notes = "",
                        sourcePrescription = "3x12-15"
                    )
                )
            )
        )
    )
    val state = WorkoutImportUiState(
        stage = WorkoutImportStage.REVIEW,
        draft = draft,
        goal = "Build muscle",
        parseWarnings = listOf("Some pasted lines need a quick review."),
        errorMessage = errorMessage,
        validationErrors = listOf(
            "Resolve uncertain matches before saving.",
            "Confirm the Cable Chest Fly match before saving."
        )
    )

    ApexFitTheme {
        WorkoutImportReviewScreen(
            state = state,
            library = library,
            actions = WorkoutImportReviewActions()
        )
    }
}
