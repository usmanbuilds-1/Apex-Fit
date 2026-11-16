package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.theme.ApexFitTheme
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeWithTraining() {
    val pullups = ExerciseLog("pull-up", "Pull-up", "Back", List(4) {
        ExerciseSet(weight = 0.0, reps = 8)
    }, secondaryMuscles = listOf("Biceps"))
    val sessions = listOf(RichTrainingSession("2026-11-16", "Pull", true, exercises = listOf(pullups)))
    ApexFitTheme { MuscleVolumeContent(sessions, LocalDate.parse("2026-11-16"), onBack = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeWithNoTraining() {
    ApexFitTheme { MuscleVolumeContent(emptyList(), LocalDate.parse("2026-11-16"), onBack = {}) }
}
