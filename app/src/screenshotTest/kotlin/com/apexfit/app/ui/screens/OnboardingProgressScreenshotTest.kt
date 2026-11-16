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
fun StartingPlanReady() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            SetupReady(OnboardingProfile(name = "Alex", goal = "Gain Muscle", daysPerWeek = 4))
        }
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
