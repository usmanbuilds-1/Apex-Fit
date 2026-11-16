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
