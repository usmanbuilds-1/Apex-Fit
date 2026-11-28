package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.*
import java.time.LocalDate
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private sealed interface PeriodLoad {
    data object Loading : PeriodLoad
    data object Failed : PeriodLoad
    data class Ready(val report: TrainingPeriodReport) : PeriodLoad
}

@Composable
internal fun TrainingPeriodReportScreen(vm: ProgressViewModel, today: LocalDate, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TrainingReportPeriod.WEEK.name) }
    val period = TrainingReportPeriod.valueOf(mode)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = selected?.let(LocalDate::parse) ?: today
    val ranges = TrainingPeriodReports.ranges(period, anchor, today)
    var retry by remember { mutableIntStateOf(0) }
    val state by key(period, anchor, today, retry) {
        produceState<PeriodLoad>(PeriodLoad.Loading) {
            try { vm.observeTrainingPeriodReport(period, anchor, today).collect { value = PeriodLoad.Ready(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = PeriodLoad.Failed }
        }
    }
    val report = (state as? PeriodLoad.Ready)?.report
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to calendar") }
            Text("Training reports", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = period == TrainingReportPeriod.WEEK, onClick = { mode = "WEEK"; selected = null },
                    label = { Text("Weekly") })
                FilterChip(selected = period == TrainingReportPeriod.MONTH, onClick = { mode = "MONTH"; selected = null },
                    label = { Text("Monthly") })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = ranges.selected.start.minusDays(1).toString() }) { Text("Previous") }
                TextButton(onClick = { selected = null }) { Text("Current") }
                TextButton(enabled = ranges.selected.end < today,
                    onClick = { selected = ranges.selected.end.plusDays(1).toString() }) { Text("Next") }
            }
            Text(windowLabel(ranges.selected), style = MaterialTheme.typography.titleMedium)
            Text("Finished calendar days only; today's training is excluded.", color = ApexColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
        when (state) {
            PeriodLoad.Loading -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
            PeriodLoad.Failed -> item {
                Text("Report couldn't load.")
                TextButton(onClick = { retry++ }) { Text("Retry") }
            }
            is PeriodLoad.Ready -> {
                val current = report?.current
                if (current == null) item { Text("No elapsed days in this period.") }
                else {
                    item {
                        Text("Recorded training", style = MaterialTheme.typography.titleLarge)
                        Text(windowLabel(current.window), color = ApexColors.TextSecondary)
                        if (current.loggedWorkouts == 0) Text("No saved workouts; activity outside the app is unknown.")
                        else {
                            MetricLine("Training days", current.trainingDays.toString())
                            MetricLine("Completed / partial workouts", "${current.completedWorkouts} / ${current.partialWorkouts}")
                            MetricLine("Working bouts", current.workingBouts.toString())
                            MetricLine("Recorded working reps", current.workingReps.toString())
                            MetricLine("Recorded duration", current.durationMinutes?.let { "$it min" } ?: "Unavailable")
                            Text("Working bouts include completed non-warmup sets; extended bouts count once. " +
                                "Set records available for ${current.workoutsWithWorkingBouts}/${current.loggedWorkouts} workouts; " +
                                "duration recorded for ${current.durationRecordedWorkouts}/${current.loggedWorkouts}.",
                                style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text("Scheduled completion", style = MaterialTheme.typography.titleLarge)
                        val a = current.adherence
                        val format = NumberFormat.getPercentInstance()
                        val rate = a.completionRate
                        val low = a.completionLowerBound
                        val high = a.completionUpperBound
                        Text(when {
                            rate != null -> format.format(rate)
                            low != null && high != null -> "${format.format(low)}–${format.format(high)} possible"
                            else -> "Unavailable"
                        }, style = MaterialTheme.typography.headlineSmall)
                        Text("${a.completed} completed · ${a.partial} partial · ${a.missed} missed · ${a.excused} excused · ${a.unresolved} unconfirmed")
                        Text("Schedule known for ${a.knownScheduleDays}/${a.elapsedDays} days. " +
                            "${a.resolvedEligible}/${a.eligible} eligible outcomes resolved. Excused excluded.",
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    val a = report?.matchedCurrent
                    val b = report?.matchedPrior
                    if (a != null && b != null) item {
                        HorizontalDivider()
                        Text("Matched-day comparison", style = MaterialTheme.typography.titleLarge)
                        Text("${windowLabel(a.window)} vs ${windowLabel(b.window)} · ${a.window.days} days each",
                            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                        if (a.loggedWorkouts == 0 || b.loggedWorkouts == 0) {
                            Text("Not enough saved workout evidence for comparison.")
                        } else {
                            MetricLine("Training days", comparison(a.trainingDays.toLong(), b.trainingDays.toLong()))
                            MetricLine("Completed workouts", comparison(a.completedWorkouts.toLong(), b.completedWorkouts.toLong()))
                            if (a.workoutsWithWorkingBouts > 0 && b.workoutsWithWorkingBouts > 0)
                                MetricLine("Recorded working bouts", comparison(a.workingBouts.toLong(), b.workingBouts.toLong()))
                            if (a.durationMinutes != null && b.durationMinutes != null &&
                                a.durationRecordedWorkouts == a.loggedWorkouts && b.durationRecordedWorkouts == b.loggedWorkouts)
                                MetricLine("Recorded duration (min)", comparison(a.durationMinutes, b.durationMinutes))
                            Text("Differences in logged activity, not measured strength or muscle growth.",
                                style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun windowLabel(w: TrainingReportWindow): String =
    "${DateTimeUtils.formatDateForDisplay(w.start)} – ${DateTimeUtils.formatDateForDisplay(w.end)}"

private fun comparison(current: Long, previous: Long): String {
    val delta = current - previous
    return "$current vs $previous (${if (delta > 0) "+" else ""}$delta)"
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = ApexColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
