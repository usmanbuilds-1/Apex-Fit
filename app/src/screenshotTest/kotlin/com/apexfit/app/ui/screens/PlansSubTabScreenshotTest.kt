package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.ui.theme.SyneFamily
import java.time.LocalDate

private val plansPreview = listOf(
    WorkoutPlan(id = 1, name = "Upper / Lower 4 Day", goal = "Build muscle", isActive = true, daysPerWeek = 4),
    WorkoutPlan(id = 2, name = "Basic Push / Pull", goal = "Build muscle", isActive = false, daysPerWeek = 4),
    WorkoutPlan(id = 3, name = "Full Body Frequency", goal = "General fitness", isActive = false, daysPerWeek = 3),
    WorkoutPlan(id = 4, name = "APEX Hybrid Block", goal = "Build strength", isActive = false, daysPerWeek = 4)
)

private val activePlanPreviewSessions = listOf(
    PlanSession(id = 1, planId = 1, label = "Upper A", day = "Monday", focus = "Chest, Back, Shoulders"),
    PlanSession(id = 2, planId = 1, label = "Lower A", day = "Tuesday", focus = "Quads, Hamstrings, Calves"),
    PlanSession(id = 3, planId = 1, label = "Upper B", day = "Thursday", focus = "Back, Chest, Arms"),
    PlanSession(id = 4, planId = 1, label = "Lower B", day = "Friday", focus = "Glutes, Quads, Calves")
)

@Composable
private fun PlansPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    ApexFitTheme {
        Column(modifier = Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                androidx.compose.material3.Text(
                    "Plans", color = ApexColors.TextPrimary, fontFamily = SyneFamily,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
                TrainSubTabControl(
                    labels = listOf("Program", "Workout", "Plans"),
                    selectedIndex = 2,
                    onSelect = {},
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                content()
            }
            BottomNavBar(activeTab = 1, onTabSelected = {})
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun PlansNightLedger() {
    PlansPreviewFrame {
        PlansSubTabContent(
            plans = plansPreview,
            activePlan = plansPreview.first(),
            activePlanError = null,
            activeSessions = UiState.Success(activePlanPreviewSessions),
            onCreatePlan = {},
            onImportPlan = {},
            onEditPlan = {},
            onActivatePlan = {},
            today = LocalDate.of(2026, 11, 15)
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun PlansEmptyState() {
    PlansPreviewFrame {
        PlansSubTabContent(
            plans = emptyList(),
            activePlan = null,
            activePlanError = null,
            activeSessions = UiState.Success(emptyList()),
            onCreatePlan = {},
            onImportPlan = {},
            onEditPlan = {},
            onActivatePlan = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun PlansLoading() {
    PlansPreviewFrame { PlansLoadingState(Modifier.weight(1f)) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun PlansActivePlanUnavailable() {
    PlansPreviewFrame {
        PlansSubTabContent(
            plans = plansPreview,
            activePlan = null,
            activePlanError = "Could not load the active plan.",
            activeSessions = UiState.Error("Schedule unavailable"),
            onCreatePlan = {},
            onImportPlan = {},
            onEditPlan = {},
            onActivatePlan = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun PlansScheduleUnavailable() {
    PlansPreviewFrame {
        PlansSubTabContent(
            plans = plansPreview,
            activePlan = plansPreview.first(),
            activePlanError = null,
            activeSessions = UiState.Error("Schedule unavailable"),
            onCreatePlan = {},
            onImportPlan = {},
            onEditPlan = {},
            onActivatePlan = {}
        )
    }
}
