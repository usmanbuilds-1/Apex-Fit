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
    val sets = listOf(
        loggedExercise("press", "Dumbbell Chest Press",
            Triple(16.0, 12, 90), Triple(16.0, 13, 75), Triple(15.0, 12, 0)),
        loggedExercise("row", "Seated Cable Row",
            Triple(45.0, 10, 100), Triple(45.0, 11, 70), Triple(45.0, 10, 0)),
        loggedExercise("pulldown", "Lat Pulldown",
            Triple(50.0, 10, 90), Triple(50.0, 10, 65), Triple(50.0, 9, 0)),
        loggedExercise("shoulder-press", "Dumbbell Shoulder Press",
            Triple(14.0, 10, 90), Triple(14.0, 9, 70), Triple(14.0, 8, 0)),
        loggedExercise("fly", "Cable Fly",
            Triple(10.0, 12, 45), Triple(10.0, 12, 30), Triple(10.0, 10, 0)),
        loggedExercise("triceps", "Cable Triceps Extension",
            Triple(20.0, 12, 20), Triple(20.0, 12, 15), Triple(20.0, 10, 0))
    ).flatten()
    val record = PersonalRecord("press_max", "press", "max_weight", 16.0, "2026-11-16")
    val receipt = receipt("example", 52, "Upper Body A", sets, listOf(record))
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun WorkoutReceiptWithoutRecord() {
    val sets = listOf(
        loggedExercise("leg-press", "Leg Press",
            Triple(60.0, 8, 75), Triple(60.0, 8, 60), Triple(60.0, 7, 0), Triple(60.0, 8, 90)),
        loggedExercise("goblet-squat", "Goblet Squat",
            Triple(24.0, 10, 75), Triple(24.0, 10, 60), Triple(24.0, 9, 0)),
        loggedExercise("rdl", "Romanian Deadlift",
            Triple(40.0, 8, 90), Triple(40.0, 8, 75), Triple(40.0, 8, 60), Triple(40.0, 7, 0)),
        loggedExercise("calf-raise", "Standing Calf Raise",
            Triple(30.0, 12, 45), Triple(30.0, 12, 45), Triple(30.0, 12, 0))
    ).flatten()
    val receipt = receipt("no-pr", 46, "Lower Body A", sets)
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun PartialWorkoutReceipt() {
    val sets = listOf(
        loggedExercise("press", "Dumbbell Chest Press",
            Triple(14.0, 10, 75), Triple(14.0, 9, 50), Triple(12.0, 10, 0), Triple(12.0, 8, 45)),
        loggedExercise("row", "Seated Cable Row",
            Triple(35.0, 10, 60), Triple(35.0, 9, 40), Triple(32.5, 10, 0))
    ).flatten()
    val receipt = receipt("partial", 24, "Upper Body A", sets, isPartial = true)
    ApexFitTheme { SessionCompleteSheet(receipt, sets, "kg", onDismiss = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun WorkoutReceiptExerciseDetails() {
    val sets = listOf(
        loggedExercise("press", "Dumbbell Chest Press",
            Triple(16.0, 12, 90), Triple(16.0, 13, 75), Triple(14.0, 12, 0)),
        loggedExercise("row", "Seated Cable Row",
            Triple(45.0, 10, 80), Triple(47.5, 9, 60), Triple(47.5, 8, 0))
    ).flatten()
    val result = receipt("details", 38, "Upper Body A", sets)
    ApexFitTheme {
        SessionCompleteSheet(
            receipt = result,
            sessionSets = sets,
            units = "kg",
            onDismiss = {},
            initiallyShowDetails = true
        )
    }
}

private fun loggedExercise(
    exerciseId: String,
    exerciseName: String,
    vararg sets: Triple<Double, Int, Int>
): List<UiExerciseSet> = sets.mapIndexed { index, (weight, reps, restTaken) ->
    UiExerciseSet(
        id = index.toLong(),
        weight = weight,
        reps = reps,
        rpe = 8,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        restTaken = restTaken
    )
}

private fun receipt(
    sessionId: String,
    durationMinutes: Int,
    sessionType: String,
    sets: List<UiExerciseSet>,
    records: List<PersonalRecord> = emptyList(),
    isPartial: Boolean = false
) = SessionCommitResult(
    sessionId = sessionId,
    durationMinutes = durationMinutes,
    totalVolumeTonnes = sets.filter { it.completed && !it.isWarmup }.sumOf { it.weight * it.reps } / 1_000.0,
    setsCompleted = sets.count { it.completed && !it.isWarmup },
    confirmedPRs = records.map { it.exerciseId }.distinct(),
    sessionType = sessionType,
    records = records,
    isPartial = isPartial
)
