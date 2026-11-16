package com.apexfit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexTextStyles
import com.apexfit.app.utils.toDisplayWeight
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun ProgressOverviewContent(
    weights: UiState<List<UiWeightEntry>>,
    sessions: List<RichTrainingSession>,
    today: LocalDate,
    goal: String,
    units: String,
    prCount: Int,
    recoverySummary: String,
    onLogWeight: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenFatigue: () -> Unit
) {
    val history = remember(weights) {
        (weights as? UiState.Success)?.data.orEmpty()
            .mapNotNull { entry -> runCatching { LocalDate.parse(entry.date) to entry }.getOrNull() }
            .sortedWith(compareBy<Pair<LocalDate, UiWeightEntry>> { it.first }.thenBy { it.second.time })
            .associate { it.first to it.second.weight }.toList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Weight trend", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onLogWeight) { Text("Log weight") }
        }
        when {
            weights is UiState.Loading -> Text("Loading weigh-ins…", color = ApexColors.TextSecondary)
            weights is UiState.Error -> Text("Weigh-ins could not be loaded.", color = ApexColors.Alert)
            history.isEmpty() -> Text("Your first weigh-in will appear here.", color = ApexColors.TextSecondary)
            else -> {
                Text(String.format(Locale.getDefault(), "%.1f %s", history.last().second.toDisplayWeight(units), units),
                    style = ApexTextStyles.dataXL)
                if (history.size < 3) {
                    Text("Your chart appears after three dated weigh-ins.", color = ApexColors.TextSecondary)
                } else {
                    val delta = history.last().second - history.first().second
                    val displayDelta = delta.toDisplayWeight(units)
                    val direction = when {
                        kotlin.math.abs(delta) < 0.05 -> "Weight is steady"
                        delta > 0 -> "Weight is rising"
                        else -> "Weight is falling"
                    }
                    val goalText = goal.takeIf { it.isNotBlank() }?.lowercase(Locale.getDefault())
                    val trend = if (goalText == null) direction else "$direction · goal: $goalText"
                    Text(trend, color = ApexColors.TextSecondary)
                    val chartDescription = "$trend. " + String.format(Locale.getDefault(),
                        "%+.1f %s between %s and %s", displayDelta, units, history.first().first, history.last().first)
                    Canvas(Modifier.fillMaxWidth().height(98.dp).semantics { contentDescription = chartDescription }) {
                        val low = history.minOf { it.second }
                        val high = history.maxOf { it.second }
                        val range = (high - low).coerceAtLeast(0.2)
                        val days = ChronoUnit.DAYS.between(history.first().first, history.last().first).toFloat()
                        val points = history.map { (date, weight) ->
                            val x = ChronoUnit.DAYS.between(history.first().first, date).toFloat() / days * size.width
                            val y = size.height * (.85f - ((weight - low) / range).toFloat() * .70f)
                            Offset(x, y)
                        }
                        points.zipWithNext { first, second ->
                            drawLine(ApexColors.Primary, first, second, strokeWidth = 2.dp.toPx())
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Strength", style = MaterialTheme.typography.titleLarge)
        Text(if (prCount > 0) "$prCount personal records logged" else "No personal records logged yet.",
            color = ApexColors.TextSecondary)
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Consistency", style = MaterialTheme.typography.titleLarge)
        val week = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val count = sessions.count { session ->
            session.completed && runCatching { LocalDate.parse(session.date) in week..today }.getOrDefault(false)
        }
        Text(if (count == 1) "1 session completed this week" else "$count sessions completed this week",
            color = ApexColors.TextSecondary)
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Recovery", style = MaterialTheme.typography.titleLarge)
        Text(recoverySummary, color = ApexColors.TextSecondary)
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Explore", style = MaterialTheme.typography.titleLarge)
        listOf(
            "Personal records" to onOpenRecords, "Workout history" to onOpenHistory,
            "Measurements" to onOpenMeasurements, "Volume" to onOpenVolume,
            "Muscle recovery" to onOpenRecovery, "Fatigue & deload" to onOpenFatigue
        ).forEach { (label, action) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("›", color = ApexColors.TextSecondary)
            }
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}
