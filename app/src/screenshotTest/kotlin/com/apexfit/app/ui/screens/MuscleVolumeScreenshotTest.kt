package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.MuscleVolumePeriod
import java.time.LocalDate

private fun pullSession() = RichTrainingSession(
    "2026-11-16", "Pull", true,
    exercises = listOf(
        ExerciseLog("pull-up", "Pull-up", "Back", List(4) {
            ExerciseSet(weight = 0.0, reps = 8)
        }, secondaryMuscles = listOf("Biceps"))
    )
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeWithTraining() {
    val sessions = listOf(pullSession())
    ApexFitTheme { MuscleVolumeContent(sessions, LocalDate.parse("2026-11-16"), onBack = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeWithNoTraining() {
    ApexFitTheme { MuscleVolumeContent(emptyList(), LocalDate.parse("2026-11-16"), onBack = {}) }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeMonthWithTraining() {
    ApexFitTheme {
        MuscleVolumeContent(
            listOf(pullSession()), LocalDate.parse("2026-11-16"), onBack = {},
            initialPeriod = MuscleVolumePeriod.MONTH
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeSelectedMuscle() {
    ApexFitTheme {
        MuscleVolumeContent(
            listOf(pullSession()), LocalDate.parse("2026-11-16"), onBack = {},
            initialSelectedMuscle = "back"
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeLoading() {
    ApexFitTheme { MuscleVolumeContent(null, LocalDate.parse("2026-11-16"), onBack = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeLoadError() {
    ApexFitTheme {
        MuscleVolumeContent(
            null, LocalDate.parse("2026-11-16"), onBack = {}, loadError = true, onRetry = {}
        )
    }
}
