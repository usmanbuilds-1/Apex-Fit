package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.utils.WorkoutReport
import com.apexfit.app.utils.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.util.Locale

@Composable
fun WorkoutHistoryScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val sessions by progressViewModel.workoutHistory.collectAsStateWithLifecycle()
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    if (selected != null) {
        SavedWorkoutReportScreen(selected.id, progressViewModel, onBack = { selectedId = null })
        return
    }
    WorkoutHistoryContent(
        sessions = sessions,
        onBack = onBack,
        onOpenSession = { selectedId = it.id }
    )
}

@Composable
internal fun WorkoutHistoryContent(
    sessions: List<TrainingSession>,
    onBack: () -> Unit,
    onOpenSession: (TrainingSession) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to Progress") }
            Text("Workout history", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp))
        }
        if (sessions.isEmpty()) item {
            Text("Complete a workout to see your history here.", color = ApexColors.TextSecondary)
        }
        items(sessions, key = { it.id }) { session ->
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                onOpenSession(session)
            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.sessionType.ifBlank { "Workout" }, style = MaterialTheme.typography.titleMedium)
                    Text(sessionSummary(session), color = ApexColors.TextSecondary)
                }
                Text("›", color = ApexColors.TextSecondary)
            }
        }
    }
}

@Composable
internal fun WorkoutHistoryDetail(
    session: TrainingSession,
    sets: List<ExerciseSet>?,
    units: String,
    onBack: () -> Unit,
    report: WorkoutReport? = null
) {
    val exercises = remember(sets, report) { report?.exercises?.map { it.sets } ?: sets.orEmpty().groupBy { set -> set.occurrenceIndex?.let { "occurrence:$it:${set.exerciseId}" } ?: "catalog:${set.exerciseId}" }.values.toList() }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to history") }
            Text(session.sessionType.ifBlank { "Workout" }, style = MaterialTheme.typography.headlineMedium)
            Text(sessionSummary(session), color = ApexColors.TextSecondary, modifier = Modifier.padding(vertical = 10.dp))
            session.notes?.takeIf(String::isNotBlank)?.let {
                Text(it, color = ApexColors.TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        if (report != null) item { WorkoutReportSummary(report, units) }
        if (sets == null) item { Text("Loading logged sets…", color = ApexColors.TextSecondary) }
        else if (sets.isEmpty()) item { Text("No sets recorded.", color = ApexColors.TextSecondary) }
        items(exercises, key = { group -> group.first().occurrenceIndex?.let { "occurrence:$it:${group.first().exerciseId}" } ?: "catalog:${group.first().exerciseId}" }) { group ->
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.first().exerciseName.ifBlank { "Exercise" }, style = MaterialTheme.typography.titleMedium)
                report?.exercises?.firstOrNull { it.sets == group }?.let {
                    Text("${it.workingSets} working sets · ${it.reps} working reps", color = ApexColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                group.forEachIndexed { index, set ->
                    val weight = when (set.weightUnit.trim().lowercase(Locale.ROOT)) {
                        "kg" -> "${set.weight.toDisplayWeight(units)} $units"
                        "lb", "lbs" -> "${(set.weight / com.apexfit.app.utils.AppConstants.KG_TO_LBS).toDisplayWeight(units)} $units"
                        else -> "Load unavailable"
                    }
                    val effort = if (set.rpe > 0) " · RPE ${set.rpe}" else ""
                    val state = when {
                        set.isWarmup -> " · Warm-up"
                        !set.completed -> " · Incomplete"
                        else -> ""
                    }
                    Text("Set ${set.setNumber ?: index + 1} · $weight × ${set.reps}$effort$state",
                        color = ApexColors.TextSecondary)
                    val details = com.apexfit.app.utils.AdvancedSets.details(set)
                    if (details != null) {
                        Text("${details.kind.name.replace('_', ' ')} · initial outcome: ${details.outcome.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall)
                        details.continuations.forEachIndexed { i, segment ->
                            Text("Continuation ${i + 1}: ${segment.kg.toDisplayWeight(units)} $units × ${segment.reps} · ${segment.restSeconds}s preceding rest · ${segment.outcome.name.lowercase()}",
                                style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                        }
                    } else if (set.advancedDetails != null) Text("Advanced segment details unavailable.", color = ApexColors.TextSecondary)
                }
            }
        }
    }
}

private fun sessionSummary(session: TrainingSession): String = listOfNotNull(
    com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(session.date), "${session.durationMinutes} min".takeIf { session.durationMinutes > 0 },
    "Partial".takeIf { session.status == "partial" }
).joinToString(" · ")



private sealed interface ReportState {
    data object Loading : ReportState
    data object Failed : ReportState
    data class Ready(val report: WorkoutReport?) : ReportState
}

@Composable
internal fun SavedWorkoutReportScreen(sessionId: String, progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val date by remember { DateTimeUtils.todayDateFlow() }
        .collectAsStateWithLifecycle(initialValue = DateTimeUtils.todayDateString())
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    var retry by remember { mutableIntStateOf(0) }
    val state by key(sessionId, date, retry) {
        produceState<ReportState>(ReportState.Loading) {
            try {
                progressViewModel.observeWorkoutReport(sessionId, LocalDate.parse(date)).collect { value = ReportState.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = ReportState.Failed }
        }
    }
    val report = (state as? ReportState.Ready)?.report
    if (report != null) {
        WorkoutHistoryDetail(report.session, report.exercises.flatMap { it.sets }, units, onBack, report)
    } else Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        when (state) {
            ReportState.Loading -> CircularProgressIndicator()
            ReportState.Failed -> {
                Text("Workout report couldn't load.")
                TextButton(onClick = { retry++ }) { Text("Retry") }
            }
            is ReportState.Ready -> Text("This saved workout is no longer available.")
        }
    }
}

@Composable
internal fun WorkoutReportSummary(report: WorkoutReport, units: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Workout report", style = MaterialTheme.typography.titleLarge)
        Text(if (report.session.completed) "Completed workout" else "Saved partial workout", color = ApexColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${report.workingSets}", style = MaterialTheme.typography.headlineMedium)
                Text("Working sets", color = ApexColors.TextSecondary)
            }
            Column {
                Text("${report.workingReps}", style = MaterialTheme.typography.headlineMedium)
                Text("Working reps", color = ApexColors.TextSecondary)
            }
            Column {
                Text(report.durationMinutes?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium)
                Text("Logged min", color = ApexColors.TextSecondary)
            }
        }
        Text("${report.warmups} completed warm-ups · ${report.incomplete} incomplete · ${report.invalid} invalid working rows",
            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        if (report.legacyGrouping) Text("Some historical exercise occurrences are grouped by catalog ID; exact grouping is unknown.",
            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Comparable recorded volume", style = MaterialTheme.typography.titleMedium)
        if (report.loadGroups.isEmpty()) Text("No confirmed comparable load sets.", color = ApexColors.TextSecondary)
        report.loadGroups.forEach { group ->
            val name = report.exercises.flatMap { it.sets }.firstOrNull { it.exerciseId == group.exerciseId }?.exerciseName ?: group.exerciseId
            Text(name + " · " + group.setup)
            Text(String.format(Locale.getDefault(), "%.1f %s × reps", group.kgReps.toDisplayWeight(units), units) +
                " · ${group.sets} sets · ${group.basis.label}", style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        }
        Text("${report.unconfirmedWorkingSets} working sets excluded from comparable load totals. Separate setup and basis totals; not energy expenditure.",
            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Confirmed exact-rep record events", style = MaterialTheme.typography.titleMedium)
        if (report.records.isEmpty()) Text("No confirmed record events attributable to this workout.", color = ApexColors.TextSecondary)
        report.records.forEach { event ->
            val name = report.exercises.flatMap { it.sets }.firstOrNull { it.id == event.setId }?.exerciseName ?: "Exercise"
            Text("$name · ${event.loadKg.toDisplayWeight(units)} $units × ${event.key.reps}")
            Text("Source set ${event.setId} · ${event.key.setup} · ${event.key.trainingFormat.label}" + if (event.chronologyLimited) " · within-day order unknown" else "",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("Records reflect current saved history and setup confirmations; not an immutable original receipt.",
            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
