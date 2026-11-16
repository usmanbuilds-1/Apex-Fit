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
import com.apexfit.app.data.MuscleRecoveryStatus
import com.apexfit.app.data.OnboardingProfile
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import java.time.LocalDate

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

@Composable
private fun RenderOnboarding(step: Int) {
    ApexFitTheme {
        OnboardingContent("kg", false, null, {}, {}, initialStep = step, initialProfile = OnboardingProfile(
            name = "Alex", goal = "Gain Muscle", experience = "Beginner", daysPerWeek = 4
        ))
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
@Preview(widthDp = 390, heightDp = 1100, locale = "en-US")
@Composable
fun ProgressWithInsufficientWeighIns() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            ProgressOverviewContent(
                UiState.Success(listOf(UiWeightEntry(weight = 78.4, date = "2026-11-16"))),
                emptyList(), LocalDate.parse("2026-11-16"), "Gain Muscle", "kg", 0,
                "Recovery estimates need more training history.",
                onLogWeight = {}, onOpenRecords = {}, onOpenHistory = {}, onOpenMeasurements = {},
                onOpenVolume = {}, onOpenRecovery = {}, onOpenFatigue = {}
            )
        }
    }
}
