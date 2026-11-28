package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.*
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.TrainingCalendar
import java.time.LocalDate
import java.time.YearMonth

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun TrainingCalendarMixedStatesPreview() {
    val today = LocalDate.parse("2026-11-16")
    val month = YearMonth.from(today)
    val plan = WorkoutPlan(id = 1, name = "Upper Lower", goal = "Build muscle", isActive = true)
    val slots = listOf(PlanSession(1, 1, "Upper A", "Monday", "Chest"),
        PlanSession(2, 1, "Lower A", "Tuesday", "Quads"))
    val logs = listOf(TrainingSession("a", "2026-11-09", "Upper A", true, 45, 3),
        TrainingSession("b", "2026-11-09", "Extra work", false, 20, 3, status = "partial"),
        TrainingSession("c", "2026-11-12", "Upper B", false, 30, 3, status = "partial"))
    ApexFitTheme {
        TrainingCalendarContent(month, today, TrainingCalendar.build(month, today, logs,
            listOf(CalendarPlanSnapshot(plan, slots))), LocalDate.parse("2026-11-09"), onBack = {})
    }
}
