package com.apexfit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.apexfit.app.data.MuscleRecoveryStatus
import com.apexfit.app.data.PersonalRecordWithName
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
    strengthRecords: List<PersonalRecordWithName>,
    weeklyTarget: Int,
    recovery: UiState<List<MuscleRecoveryStatus>>,
    onLogWeight: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenFatigue: () -> Unit,
    onOpenStrength: (() -> Unit)? = null,
    onOpenCalendar: (() -> Unit)? = null,
    onOpenGoals: (() -> Unit)? = null
) {
    val rawHistory = (weights as? UiState.Success)?.data.orEmpty()
    val latest = remember(rawHistory) { rawHistory.maxWithOrNull(com.apexfit.app.utils.BodyWeightHistory.uiChronological) }
    val history = remember(rawHistory) {
        com.apexfit.app.utils.BodyWeightHistory.dailyUi(rawHistory)
            .map { LocalDate.parse(it.date) to it.weight }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text(String.format(Locale.getDefault(), "%.1f %s", (latest?.weight ?: history.last().second).toDisplayWeight(units), units),
                    style = ApexTextStyles.dataXL)
                Text("Latest measurement · chart uses daily averages", color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (history.size >= 2) {
                    val delta = history.last().second - history.first().second
                    val displayDelta = delta.toDisplayWeight(units)
                    val elapsedDays = ChronoUnit.DAYS.between(history.first().first, history.last().first).coerceAtLeast(1)
                    val weeklyRate = (delta * 7.0 / elapsedDays).toDisplayWeight(units)
                    val direction = when {
                        kotlin.math.abs(delta) < 0.05 -> "Weight is steady"
                        delta > 0 -> "Weight is rising"
                        else -> "Weight is falling"
                    }
                    val goalText = goal.takeIf { it.isNotBlank() }?.lowercase(Locale.getDefault())
                    val trend = if (goalText == null) direction else "$direction · goal: $goalText"
                    Text(String.format(Locale.getDefault(), "%+.1f %s / week", weeklyRate, units),
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(trend, color = ApexColors.TextSecondary)
                    if (history.size < 3) {
                        Text("Your chart appears after three dated weigh-ins.", color = ApexColors.TextSecondary)
                    } else {
                        val chartDescription = "$trend. " + String.format(Locale.getDefault(),
                            "%+.1f %s across %d days", displayDelta, units, elapsedDays)
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
                } else {
                    Text("Your chart appears after three dated weigh-ins.", color = ApexColors.TextSecondary)
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Strength", style = MaterialTheme.typography.titleLarge)
            if (onOpenStrength != null) TextButton(onClick = onOpenStrength) { Text("Exercise trends") }
        }
        val recentRecords = remember(strengthRecords) {
            strengthRecords.groupBy { it.record.exerciseId }
                .mapNotNull { (_, entries) ->
                    entries.maxWithOrNull(compareBy<PersonalRecordWithName> { it.record.date }
                        .thenBy {
                            when (it.record.type) {
                                "estimated_1rm" -> 3
                                "max_weight" -> 2
                                else -> 1
                            }
                        })
                }
                .sortedByDescending { it.record.date }
                .take(2)
        }
        if (recentRecords.isEmpty()) {
            Text("No personal records logged yet.", color = ApexColors.TextSecondary)
        } else {
            recentRecords.forEachIndexed { index, entry ->
                val record = entry.record
                val label = when (record.type) {
                    "max_weight" -> "Heaviest set"
                    "estimated_1rm" -> "Estimated 1RM"
                    "volume" -> "Session volume"
                    else -> record.type.replace('_', ' ')
                }
                val suffix = if (record.type == "volume") "$units × reps" else units
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.displayName ?: record.exerciseId, style = MaterialTheme.typography.titleMedium)
                        Text(label, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${record.value.toDisplayWeight(units)} $suffix", style = ApexTextStyles.dataMedium)
                        Text(com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(record.date),
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (index < recentRecords.lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Consistency", style = MaterialTheme.typography.titleLarge)
        val week = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val count = sessions.count { session ->
            session.completed && runCatching { LocalDate.parse(session.date) in week..today }.getOrDefault(false)
        }
        val target = weeklyTarget.coerceIn(1, 6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("$count / $target sessions this week", color = ApexColors.TextSecondary)
            Row(Modifier.semantics {
                contentDescription = "$count of $target scheduled training sessions completed this week"
            }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(target) { index ->
                    Box(Modifier.size(10.dp).background(
                        if (index < count) ApexColors.Primary else ApexColors.BorderSubtle, CircleShape
                    ))
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Recovery", style = MaterialTheme.typography.titleLarge)
        when (recovery) {
            UiState.Loading -> Text("Loading training history…", color = ApexColors.TextSecondary)
            is UiState.Error -> Text("Training history is unavailable.", color = ApexColors.Alert)
            is UiState.Success -> {
                val recentMuscles = remember(recovery.data) {
                    recovery.data.mapNotNull { status ->
                        runCatching { LocalDate.parse(status.lastTrainedDate) to status }.getOrNull()
                    }.sortedByDescending { it.first }.take(3).map { it.second }
                }
                if (recentMuscles.isEmpty()) {
                    Text("Log a completed workout to see muscle training history.", color = ApexColors.TextSecondary)
                } else {
                    recentMuscles.forEachIndexed { index, status ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(status.muscleGroup, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Last trained ${com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(status.lastTrainedDate)}",
                                color = ApexColors.TextSecondary
                            )
                        }
                        if (index < recentMuscles.lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
                    }
                    Text("Training dates do not establish recovery.",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Explore", style = MaterialTheme.typography.titleLarge)
        listOf(
            "Personal records" to onOpenRecords, "Measurements" to onOpenMeasurements,
            "Workout history" to onOpenHistory, "Training volume" to onOpenVolume,
            "Muscle recovery" to onOpenRecovery, "Fatigue & deload" to onOpenFatigue
        ).let { actions ->
            if (onOpenCalendar != null) actions + ("Training calendar" to onOpenCalendar) else actions
        }.let { actions ->
            if (onOpenGoals != null) actions + ("Custom goals" to onOpenGoals) else actions
        }.forEach { (label, action) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("›", color = ApexColors.TextSecondary)
            }
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}
