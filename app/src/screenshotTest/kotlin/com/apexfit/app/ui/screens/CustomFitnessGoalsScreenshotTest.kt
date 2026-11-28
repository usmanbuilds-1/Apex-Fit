package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.*
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun CustomGoalProgressPreview() {
    val today = LocalDate.parse("2026-11-28")
    val spec = FitnessGoalSpec(FitnessGoalKind.LOAD, 60.0, "bench", RecordedLoadBasis.TOTAL, "gym a", 8.0, today.plusWeeks(4))
    val goal = FitnessGoal("preview", spec, today.minusWeeks(2), 40.0, today.minusWeeks(3))
    val progress = FitnessGoalProgress(goal, GoalObservation(50.0, today.minusDays(1), "set:7"), null, false)
    ApexFitTheme { Column { FitnessGoalCard(progress, "Bench press", today) } }
}
