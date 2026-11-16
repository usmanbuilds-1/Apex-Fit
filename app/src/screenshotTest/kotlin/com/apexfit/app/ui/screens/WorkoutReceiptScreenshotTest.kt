package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.SessionCommitResult
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun WorkoutReceiptWithAnEarnedRecord() {
    val receipt = SessionCommitResult("example", 52, 6.84, 18, listOf("press"), "Upper Body A",
        records = listOf(PersonalRecord("press_max", "press", "max_weight", 16.0, "2026-11-16")))
    val sets = listOf(UiExerciseSet(weight = 16.0, reps = 12, rpe = 8,
        exerciseId = "press", exerciseName = "Dumbbell Chest Press"))
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun WorkoutReceiptWithoutRecord() {
    val receipt = SessionCommitResult(
        "no-pr", 46, 4.2, 14, emptyList(), "Lower Body A"
    )
    val sets = listOf(
        UiExerciseSet(weight = 60.0, reps = 8, rpe = 8,
            exerciseId = "leg-press", exerciseName = "Leg Press")
    )
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun PartialWorkoutReceipt() {
    val receipt = SessionCommitResult(
        "partial", 24, 1.85, 7, emptyList(), "Upper Body A", isPartial = true
    )
    val sets = listOf(
        UiExerciseSet(weight = 14.0, reps = 10, rpe = 8,
            exerciseId = "press", exerciseName = "Dumbbell Chest Press")
    )
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}
