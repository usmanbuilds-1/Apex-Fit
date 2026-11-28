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

@Composable
fun WorkoutHistoryScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val sessions by progressViewModel.workoutHistory.collectAsStateWithLifecycle()
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    if (selected != null) {
        val setFlow = remember(selected.id) { progressViewModel.setsForSession(selected.id) }
        val sets by setFlow.collectAsStateWithLifecycle(initialValue = null)
        WorkoutHistoryDetail(selected, sets, units, onBack = { selectedId = null })
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
    onBack: () -> Unit
) {
    val exercises = remember(sets) { sets.orEmpty().groupBy { set -> set.occurrenceIndex?.let { "occurrence:$it" } ?: "catalog:${set.exerciseId}" }.values.toList() }
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
        if (sets == null) item { Text("Loading logged sets…", color = ApexColors.TextSecondary) }
        else if (sets.isEmpty()) item { Text("No sets recorded.", color = ApexColors.TextSecondary) }
        items(exercises, key = { group -> group.first().occurrenceIndex?.let { "occurrence:$it" } ?: "catalog:${group.first().exerciseId}" }) { group ->
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.first().exerciseName.ifBlank { "Exercise" }, style = MaterialTheme.typography.titleMedium)
                group.forEachIndexed { index, set ->
                    val weight = set.weight.toDisplayWeight(units)
                    val effort = if (set.rpe > 0) " · RPE ${set.rpe}" else ""
                    val state = when {
                        set.isWarmup -> " · Warm-up"
                        !set.completed -> " · Incomplete"
                        else -> ""
                    }
                    Text("Set ${index + 1} · $weight $units × ${set.reps}$effort$state",
                        color = ApexColors.TextSecondary)
                }
            }
        }
    }
}

private fun sessionSummary(session: TrainingSession): String = listOfNotNull(
    com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(session.date), "${session.durationMinutes} min".takeIf { session.durationMinutes > 0 },
    "Partial".takeIf { session.status == "partial" }
).joinToString(" · ")

