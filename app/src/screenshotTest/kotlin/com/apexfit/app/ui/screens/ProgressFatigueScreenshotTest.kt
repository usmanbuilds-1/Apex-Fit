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
import com.apexfit.app.ui.models.UiDeloadResult
import com.apexfit.app.ui.models.UiFatigueResult
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import kotlinx.collections.immutable.persistentListOf

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun FatigueWithInsufficientTrainingHistory() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            FatigueDeloadContent(
                fatigue = UiFatigueResult(
                    ratio = null,
                    status = "insufficient",
                    statusLabel = "Building baseline",
                    recommendation = "Log a few sessions to see load guidance."
                ),
                deload = UiDeloadResult(recommendation = "", urgency = "none", signals = 0),
                onOpenRecovery = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun FatigueWithinRecentTrainingRange() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            FatigueDeloadContent(
                fatigue = UiFatigueResult(
                    ratio = 0.92,
                    status = "balanced",
                    statusLabel = "Within your recent range",
                    recommendation = "Continue your current plan and adjust based on how you feel."
                ),
                deload = UiDeloadResult(recommendation = "", urgency = "none", signals = 0),
                onOpenRecovery = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun FatigueWithDeloadRecommendation() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            FatigueDeloadContent(
                fatigue = UiFatigueResult(
                    ratio = 1.28,
                    status = "elevated",
                    statusLabel = "Load is elevated",
                    recommendation = "Keep the next session conservative and monitor recovery."
                ),
                deload = UiDeloadResult(
                    recommendation = "Multiple fatigue signals support a short deload.",
                    urgency = "moderate",
                    signals = 3,
                    protocol = persistentListOf(
                        "Reduce working sets for the next training week.",
                        "Keep loads comfortable and avoid failure."
                    )
                ),
                onOpenRecovery = {}
            )
        }
    }
}
