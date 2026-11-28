package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.StrengthExerciseOption
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.StrengthObservation
import com.apexfit.app.utils.StrengthSessionPoint
import com.apexfit.app.utils.DateTimeUtils
import com.apexfit.app.utils.toDisplayWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private sealed interface StrengthLoad<out T> {
    data object Loading : StrengthLoad<Nothing>
    data object Failed : StrengthLoad<Nothing>
    data class Ready<T>(val value: T) : StrengthLoad<T>
}

@Composable
internal fun ExerciseStrengthScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val date by remember { DateTimeUtils.todayDateFlow() }
        .collectAsStateWithLifecycle(initialValue = DateTimeUtils.todayDateString())
    val today = LocalDate.parse(date)
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    val exercises by key(today, retry) {
        produceState<StrengthLoad<List<StrengthExerciseOption>>>(StrengthLoad.Loading) {
            try {
                progressViewModel.observeStrengthExercises(today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val options = (exercises as? StrengthLoad.Ready)?.value
    val selected = options?.firstOrNull { it.exerciseId == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null }
    val start = when (rangeIndex) {
        0 -> today.minusDays(89)
        1 -> today.minusDays(364)
        else -> LocalDate.of(1, 1, 1)
    }
    val history by key(selectedId, start, today, retry) {
        produceState<StrengthLoad<List<StrengthSessionPoint>>>(StrengthLoad.Loading) {
            val id = selectedId ?: return@produceState
            try {
                progressViewModel.observeStrengthHistory(id, start, today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { if (selectedId == null) onBack() else selectedId = null }) {
                Text(if (selectedId == null) "Back to Progress" else "Back to exercises")
            }
            Text(selected?.name ?: "Exercise strength", style = MaterialTheme.typography.headlineMedium)
        }
        if (exercises is StrengthLoad.Failed) item {
            Text("Exercise history couldn't load.")
            TextButton(onClick = { retry++ }) { Text("Try again") }
        } else if (options == null) item {
            CircularProgressIndicator()
        } else if (selectedId == null) {
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Find a logged exercise") })
            }
            val filtered = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) item { Text("No matching logged exercises.", color = ApexColors.TextSecondary) }
            items(filtered, key = { it.exerciseId }) { exercise ->
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                    selectedId = exercise.exerciseId
                }.padding(vertical = 8.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(exercise.category, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    HorizontalDivider(color = ApexColors.BorderSubtle, modifier = Modifier.padding(top = 10.dp))
                }
            }
        } else if (selected == null) {
            item { Text("No saved qualifying sets remain for this exercise.") }
        } else {
            item {
                Text(selected.category + " · " + selected.equipment, color = ApexColors.TextSecondary)
                ApexSegmentedControl(labels = listOf("90 days", "1 year", "All"), selectedIndex = rangeIndex,
                    onSelect = { rangeIndex = it })
                Text("Recorded loads only. Historical load convention and resistance are not verified.",
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            when (val data = history) {
                StrengthLoad.Loading -> item { CircularProgressIndicator() }
                StrengthLoad.Failed -> item {
                    Text("Strength history couldn't load.")
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                is StrengthLoad.Ready -> {
                    val points = data.value
                    if (points.isEmpty()) item { Text("No qualifying sets in this period.") }
                    else {
                        item { StrengthHistorySummary(points, units) }
                        items(points.asReversed(), key = { it.sessionId }) { point ->
                            StrengthSessionRow(point, units)
                        }
                    }
                }
            }
        }
    }
}

private fun observedPair(observation: StrengthObservation?, units: String): String {
    if (observation == null) return "—"
    val load = observation.loadKg?.let {
        java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
        }.format(it.toDisplayWeight(units))
    }
    return if (load == null) "${observation.reps} reps · load unavailable"
        else "$load $units × ${observation.reps}"
}

@Composable
internal fun StrengthHistorySummary(points: List<StrengthSessionPoint>, units: String) {
    val heavy = remember(points) { points.mapNotNull { it.heaviestRecorded }
        .maxWithOrNull(compareBy<StrengthObservation> { it.loadKg }.thenBy { it.reps }) }
    val reps = remember(points) { points.mapNotNull { it.mostReps }
        .maxWithOrNull(compareBy<StrengthObservation> { it.reps }.thenBy { it.loadKg ?: -1.0 }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Highest recorded load", style = MaterialTheme.typography.titleMedium)
        Text(observedPair(heavy, units), style = MaterialTheme.typography.headlineSmall, color = ApexColors.Primary)
        Text("Most reps in one set: ${observedPair(reps, units)}", color = ApexColors.TextSecondary)
        Text("${points.size} saved sessions · ${points.sumOf { it.workingSets }} working sets",
            color = ApexColors.TextSecondary)
        var metric by rememberSaveable { mutableIntStateOf(0) }
        ApexSegmentedControl(labels = listOf("Load", "Reps"), selectedIndex = metric, onSelect = { metric = it })
        Text(if (metric == 0) "Highest recorded load by date" else "Most reps in one set by date",
            style = MaterialTheme.typography.titleMedium)
        val daily = remember(points, metric) {
            points.groupBy { it.date }.toSortedMap().mapNotNull { (date, sameDay) ->
                val value = if (metric == 0) sameDay.mapNotNull { it.heaviestRecorded?.loadKg }.maxOrNull()
                    else sameDay.mapNotNull { it.mostReps?.reps?.toDouble() }.maxOrNull()
                value?.let { date to it }
            }
        }
        if (daily.size < 2) Text("Log on two dates to see a trend.", color = ApexColors.TextSecondary)
        else {
            val min = daily.minOf { it.second }
            val max = daily.maxOf { it.second }
            val span = (max - min).coerceAtLeast(0.1)
            val days = ChronoUnit.DAYS.between(daily.first().first, daily.last().first).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(112.dp).semantics {
                contentDescription = if (metric == 0)
                    "Recorded load by date. First ${daily.first().second.toDisplayWeight(units)} $units, last ${daily.last().second.toDisplayWeight(units)} $units. Full session values below."
                else "Repetitions by date. First ${daily.first().second.toInt()}, last ${daily.last().second.toInt()}. Full session values below."
            }) {
                val plotted = daily.map { (date, load) ->
                    Offset(size.width * (0.03f + 0.94f * ChronoUnit.DAYS.between(daily.first().first, date).toFloat() / days.toFloat()),
                        size.height * (0.85f - 0.70f * ((load - min) / span).toFloat()))
                }
                plotted.zipWithNext().forEach { (a, b) -> drawLine(ApexColors.Primary, a, b, strokeWidth = 2.dp.toPx()) }
                plotted.forEach { drawCircle(ApexColors.Primary, radius = 3.dp.toPx(), center = it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateTimeUtils.formatDateForDisplay(daily.first().first.toString()), style = MaterialTheme.typography.labelSmall)
                Text(DateTimeUtils.formatDateForDisplay(daily.last().first.toString()), style = MaterialTheme.typography.labelSmall)
            }
            Text("Compare like-for-like setups only. Higher recorded load does not necessarily mean greater strength.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StrengthSessionRow(point: StrengthSessionPoint, units: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text(DateTimeUtils.formatDateForDisplay(point.date.toString()) + if (point.partial) " · Partial workout" else "",
            style = MaterialTheme.typography.titleMedium)
        Text("Highest load: ${observedPair(point.heaviestRecorded, units)}", color = ApexColors.TextSecondary)
        Text("Most reps: ${observedPair(point.mostReps, units)}", color = ApexColors.TextSecondary)
        Text("${point.workingSets} sets · ${point.totalReps} reps", style = MaterialTheme.typography.bodySmall)
        point.observations.forEach { Text(observedPair(it, units), style = MaterialTheme.typography.bodySmall) }
    }
}
