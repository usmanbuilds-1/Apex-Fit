package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.*
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.*
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun SavedPartialWorkoutReportPreview() {
    val today = LocalDate.parse("2026-11-16")
    val session = TrainingSession("s1", today.toString(), "Upper A", false, 40, 3, status = "partial")
    val sets = listOf(ExerciseSet(id = 1, sessionId = "s1", exerciseId = "press", exerciseName = "Bench press",
        muscleGroup = "Chest", weight = 40.0, reps = 8, rpe = 8, isWarmup = false, completed = true,
        occurrenceIndex = 1))
    val confirmations = mapOf(1L to SetLoadConfirmation(1, LoadConfirmations.fingerprint(sets.single()),
        RecordedLoadBasis.TOTAL, "gym a"))
    val report = WorkoutReports.calculate(SavedWorkoutFacts(session, sets), emptyList(), confirmations, today)
    ApexFitTheme { WorkoutHistoryDetail(session, sets, "kg", {}, report) }
}
