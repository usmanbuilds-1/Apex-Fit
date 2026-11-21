package com.apexfit.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.NutritionTargets
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.UiReadinessFactor
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.SessionHeroCategory
import kotlinx.collections.immutable.persistentListOf

@PreviewTest
@Preview(widthDp = 390, heightDp = 1086, locale = "en-US")
@Composable
fun TodayScheduledPull() {
    RenderToday(SessionHeroCategory.UPPER_PULL, "Upper Body A")
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun TodayRecoveryWithNoHistory() {
    RenderToday(SessionHeroCategory.RECOVERY)
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US", fontScale = 1.3f)
@Composable
fun TodayResumeWithoutMatchingPhoto() {
    RenderToday(SessionHeroCategory.UPPER_PUSH, "Push", inProgress = true)
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun TodayScheduledLower() {
    RenderToday(SessionHeroCategory.LOWER_QUAD, "Lower Body A")
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US", fontScale = 1.3f)
@Composable
fun TodayReadinessExplanation() {
    ApexFitTheme {
        Box(Modifier.fillMaxSize()) {
            TodayReadinessExplanationDialog(
                UiSessionReadiness(
                    score = 82,
                    label = "Estimated readiness",
                    colorHex = "#7192D3",
                    prediction = "This estimate uses recent training and nutrition logs.",
                    recommendation = "Consider your energy, sleep, and soreness when choosing today’s effort.",
                    factors = persistentListOf(
                        UiReadinessFactor("Systemic training load", "neutral", "Higher estimate"),
                        UiReadinessFactor("Logged nutrition", "neutral", "Moderate estimate"),
                        UiReadinessFactor("Chest recovery", "positive", "4 logged exposures")
                    ),
                    confidence = "Recent muscle history is available"
                )
            ) {}
        }
    }
}

@Composable
private fun RenderToday(
    category: SessionHeroCategory,
    session: String? = null,
    inProgress: Boolean = false
) {
    val hasSchedule = session != null
    val focus = when (category) {
        SessionHeroCategory.UPPER_PULL -> "Chest · Back · Shoulders"
        SessionHeroCategory.UPPER_PUSH -> "Chest · Triceps"
        SessionHeroCategory.LOWER_QUAD -> "Quads · Glutes"
        else -> ""
    }
    ApexFitTheme {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                TodayContent(
                    heroCategory = category,
                    todayDate = "2025-11-14",
                    sessionLabel = session,
                    sessionFocus = focus,
                    scheduledExerciseCount = if (hasSchedule) 5 else 0,
                    weeklySessionTarget = if (hasSchedule) 4 else 0,
                    hasActiveSession = inProgress,
                    estimatedWorkoutDuration = if (hasSchedule) 54 else 0,
                    readiness = if (hasSchedule) UiSessionReadiness(
                    score = 82, label = "Good", colorHex = "#7192D3",
                        prediction = "The estimate uses logged training and nutrition, with recent history for your trained muscles.",
                        recommendation = "Consider your energy, sleep, and soreness when choosing today’s effort."
                    ) else null,
                    loggedCalories = if (hasSchedule) 2140 else 0,
                    loggedProtein = if (hasSchedule) 142 else 0,
                    loggedCarbs = if (hasSchedule) 195 else 0,
                    loggedFat = if (hasSchedule) 52 else 0,
                    macroTargets = if (hasSchedule) NutritionTargets(2450, 180, 250, 70) else null,
                    weightEntries = if (hasSchedule) listOf(
                        UiWeightEntry(weight = 78.7, date = "2025-11-10"),
                        UiWeightEntry(weight = 78.4, date = "2025-11-14")
                    ) else emptyList(),
                    completedSessions = if (hasSchedule) listOf(
                        RichTrainingSession("2025-11-13", "Lower Body B", true, durationMinutes = 48),
                        RichTrainingSession("2025-10-21", "Upper Body B", true, durationMinutes = 45)
                    ) else emptyList(),
                    completedSessionsThisWeek = if (hasSchedule) 1 else 0,
                    onOpenSettings = {},
                    onNutrition = {},
                    onProgress = {},
                    onStartWorkout = {}
                )
            }
            BottomNavBar(activeTab = 0, onTabSelected = {})
        }
    }
}

