package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PersonalRecordWithName
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.data.MuscleRecoveryStatus
import com.apexfit.app.data.OnboardingProfile
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import java.time.LocalDate

private val onboardingPreviewCatalog = mapOf(
    "Flat Dumbbell Press" to "Chest",
    "Chest-Supported Dumbbell Row" to "Back",
    "Dumbbell Lateral Raise" to "Shoulders",
    "Hammer Bicep Curl" to "Biceps",
    "Leg Press (High & Wide)" to "Quads",
    "Romanian Deadlift" to "Hamstrings",
    "Seated Leg Curl" to "Hamstrings",
    "Standing Calf Raise" to "Calves",
    "Barbell Hip Thrust" to "Glutes",
    "Standing Overhead Press" to "Shoulders",
    "Dual Rope Tricep Pushdown" to "Triceps",
    "Lat Pulldown (Neutral Grip)" to "Back"
).map { (name, muscle) ->
    Exercise(
        id = name.lowercase().replace(' ', '-'),
        name = name,
        category = "Strength",
        primaryMuscle = muscle,
        equipmentRequired = "Dumbbell"
    )
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun WelcomeNightLedger() {
    ApexFitTheme { OnboardingScreen(onComplete = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun OnboardingGoal() = RenderOnboarding(1)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun OnboardingDetails() = RenderOnboarding(2)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun OnboardingExperience() = RenderOnboarding(3)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun OnboardingFrequency() = RenderOnboarding(4)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun StartingPlanReady() = RenderOnboarding(5)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun StartingPlanReadyExpanded() = RenderOnboarding(5, expandedSession = 0)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun StartingPlanReadyLoadingLibrary() = RenderOnboarding(5, catalogState = UiState.Loading)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun StartingPlanReadyUnavailableLibrary() = RenderOnboarding(5, catalogState = UiState.Error("Exercise library unavailable"))

@Composable
private fun RenderOnboarding(
    step: Int,
    expandedSession: Int = -1,
    catalogState: UiState<List<Exercise>> = UiState.Success(onboardingPreviewCatalog)
) {
    ApexFitTheme {
        OnboardingContent(
            "kg", false, null, {}, {},
            initialStep = step,
            initialProfile = OnboardingProfile(
                name = "Alex", goal = "Gain Muscle", experience = "Beginner", daysPerWeek = 4
            ),
            exerciseCatalog = onboardingPreviewCatalog,
            initialExpandedSession = expandedSession,
            exerciseCatalogState = catalogState
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun MuscleRecoveryWithTraining() {
    ApexFitTheme {
        MuscleRecoveryContent(UiState.Success(listOf(
            MuscleRecoveryStatus("Quads", 31, 0.35f, "2026-11-15"),
            MuscleRecoveryStatus("Back", 0, 1f, "2026-11-12")
        ))) {}
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1191, locale = "en-US")
@Composable
fun ProgressWithInsufficientWeighIns() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            ProgressHeader()
            ProgressOverviewContent(
                UiState.Success(listOf(
                    UiWeightEntry(weight = 78.7, date = "2026-11-08"),
                    UiWeightEntry(weight = 78.4, date = "2026-11-15")
                )),
                listOf(
                    RichTrainingSession(date = "2026-11-09", sessionType = "Upper Body A", completed = true),
                    RichTrainingSession(date = "2026-11-11", sessionType = "Lower Body A", completed = true),
                    RichTrainingSession(date = "2026-11-13", sessionType = "Upper Body B", completed = true),
                    RichTrainingSession(date = "2026-11-15", sessionType = "Lower Body B", completed = true)
                ), LocalDate.parse("2026-11-15"), "Gain Muscle", "kg",
                listOf(
                    PersonalRecordWithName(
                        PersonalRecord("bench-1rm", "barbell-bench-press", "estimated_1rm", 48.5, "2026-11-15"),
                        "Bench Press"
                    ),
                    PersonalRecordWithName(
                        PersonalRecord("lateral-raise-weight", "dumbbell-lateral-raise", "max_weight", 16.0, "2026-11-14"),
                        "Dumbbell Lateral Raise"
                    )
                ), 4,
                UiState.Success(listOf(
                    MuscleRecoveryStatus("Shoulders", 18, 0.55f, "2026-11-14"),
                    MuscleRecoveryStatus("Chest", 0, 1f, "2026-11-14"),
                    MuscleRecoveryStatus("Back", 0, 1f, "2026-11-12")
                )),
                onLogWeight = {}, onOpenRecords = {}, onOpenHistory = {}, onOpenMeasurements = {},
                onOpenVolume = {}, onOpenRecovery = {}, onOpenFatigue = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun NutritionWithLoggedMacros() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            NutritionDailySummary(
                calories = 2140,
                target = 2450,
                trainingDay = true,
                macros = listOf("Protein" to "142 / 180 g", "Carbs" to "195 / 250 g", "Fat" to "52 / 70 g")
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun NutritionWithNoTarget() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            NutritionDailySummary(0, null, false, listOf("Protein" to "0 g", "Carbs" to "0 g", "Fat" to "0 g"))
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1100, locale = "en-US")
@Composable
fun ProgressWithDatedWeightTrend() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            ProgressHeader()
            ProgressOverviewContent(
                UiState.Success(listOf(
                    UiWeightEntry(weight = 79.0, date = "2026-11-01"),
                    UiWeightEntry(weight = 78.6, date = "2026-11-08"),
                    UiWeightEntry(weight = 78.4, date = "2026-11-15")
                )),
                listOf(
                    RichTrainingSession(date = "2026-11-09", sessionType = "Upper Body A", completed = true),
                    RichTrainingSession(date = "2026-11-11", sessionType = "Lower Body A", completed = true),
                    RichTrainingSession(date = "2026-11-13", sessionType = "Upper Body B", completed = true),
                    RichTrainingSession(date = "2026-11-15", sessionType = "Lower Body B", completed = true)
                ), LocalDate.parse("2026-11-15"), "Gain Muscle", "kg",
                listOf(
                    PersonalRecordWithName(
                        PersonalRecord("bench-1rm", "barbell-bench-press", "estimated_1rm", 48.5, "2026-11-15"),
                        "Bench Press"
                    ),
                    PersonalRecordWithName(
                        PersonalRecord("lateral-raise-weight", "dumbbell-lateral-raise", "max_weight", 16.0, "2026-11-14"),
                        "Dumbbell Lateral Raise"
                    )
                ), 4,
                UiState.Success(listOf(
                    MuscleRecoveryStatus("Shoulders", 18, 0.55f, "2026-11-14"),
                    MuscleRecoveryStatus("Chest", 0, 1f, "2026-11-14"),
                    MuscleRecoveryStatus("Back", 0, 1f, "2026-11-12")
                )),
                onLogWeight = {}, onOpenRecords = {}, onOpenHistory = {}, onOpenMeasurements = {},
                onOpenVolume = {}, onOpenRecovery = {}, onOpenFatigue = {}
            )
        }
    }
}
