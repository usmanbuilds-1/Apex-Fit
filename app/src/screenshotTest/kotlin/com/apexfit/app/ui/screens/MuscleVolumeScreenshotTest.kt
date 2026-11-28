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


@PreviewTest
@Preview(widthDp = 320, heightDp = 700, locale = "en-US")
@Composable
fun MuscleVolumeHeatmapMixedBandsSmallScreen() {
    val session = RichTrainingSession("2026-11-16", "Mixed", true, exercises = listOf(
        ExerciseLog("press", "Press", "Chest", List(10) { ExerciseSet(weight = 20.0, reps = 8) },
            secondaryMuscles = listOf("Triceps")),
        ExerciseLog("row", "Row", "Back", List(5) { ExerciseSet(weight = 20.0, reps = 8) }),
        ExerciseLog("curl", "Curl", "Biceps", List(2) { ExerciseSet(weight = 5.0, reps = 10) })
    ))
    ApexFitTheme { MuscleVolumeContent(listOf(session), LocalDate.parse("2026-11-16"), onBack = {}) }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleFrequencyYearWithHistoricalWork() {
    val sessions = listOf(pullSession().copy(date = "2025-02-01"),
        pullSession().copy(date = "2025-02-05"), pullSession().copy(date = "2025-09-01"))
    ApexFitTheme {
        MuscleVolumeContent(sessions, LocalDate.parse("2026-11-16"), onBack = {},
            initialSelectedMuscle = "back", initialPeriod = MuscleVolumePeriod.YEAR,
            periodAnchor = LocalDate.parse("2025-01-01"), onNavigatePeriod = {})
    }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeMatchedMonthComparison() {
    val sessions = listOf(pullSession().copy(date = "2026-10-01"),
        pullSession().copy(date = "2026-11-01"), pullSession().copy(date = "2026-11-05"),
        pullSession().copy(date = "2026-11-16"))
    ApexFitTheme {
        MuscleVolumeContent(sessions, LocalDate.parse("2026-11-16"), onBack = {},
            initialSelectedMuscle = "back", initialPeriod = MuscleVolumePeriod.MONTH)
    }
}

@PreviewTest
@Preview(widthDp = 320, heightDp = 700, locale = "en-US")
@Composable
fun MuscleVolumeComparisonZeroBaseline() {
    val prior = RichTrainingSession("2026-10-01", "Push", true, exercises = listOf(
        ExerciseLog("press", "Press", "Chest", List(4) { ExerciseSet(weight = 20.0, reps = 8) })
    ))
    ApexFitTheme {
        MuscleVolumeContent(listOf(prior, pullSession().copy(date = "2026-11-01")),
            LocalDate.parse("2026-11-16"), onBack = {}, initialSelectedMuscle = "back",
            initialPeriod = MuscleVolumePeriod.MONTH)
    }
}


@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun MuscleVolumeWeeklyTargetHistory() {
    val today = LocalDate.parse("2026-11-16")
    val history = listOf(
        com.apexfit.app.utils.MuscleTargetRevision("back", today.minusWeeks(1), 8),
        com.apexfit.app.utils.MuscleTargetRevision("back", today.plusWeeks(1), 10)
    )
    ApexFitTheme {
        MuscleVolumeContent(listOf(pullSession()), today, onBack = {},
            initialSelectedMuscle = "back", targets = history, onScheduleTarget = { _, _, _ -> })
    }
}

@PreviewTest
@Preview(widthDp = 320, heightDp = 700, locale = "en-US")
@Composable
fun MuscleVolumeWeeklyZeroTarget() {
    val today = LocalDate.parse("2026-11-16")
    ApexFitTheme {
        MuscleVolumeContent(emptyList(), today, onBack = {}, initialSelectedMuscle = "chest",
            targets = listOf(com.apexfit.app.utils.MuscleTargetRevision("chest", today, 0)),
            onScheduleTarget = { _, _, _ -> })
    }
}
