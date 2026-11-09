package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.toDisplayWeight

@Composable
fun WorkoutHistoryScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val sessions by progressViewModel.workoutHistory.collectAsStateWithLifecycle()
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp)) {
        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Text("Workout history", color = PrimaryText, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 10.dp))
        }
        if (sessions.isEmpty()) {
            Text("Complete a workout to see your history here.", color = SecondaryText, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(sessions, key = { it.id }) { session ->
                    WorkoutHistoryEntry(session, units, progressViewModel)
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryEntry(session: TrainingSession, units: String, viewModel: ProgressViewModel) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = DarkCardSurface)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
            Text(session.sessionType.ifBlank { "Workout" }, color = PrimaryText, fontWeight = FontWeight.Bold)
            Text("${session.date} · ${session.durationMinutes} min" + if (session.status == "partial") " · Partial" else "", color = SecondaryText)
            Text(if (expanded) "Hide sets" else "View sets", color = AmberAccent)
            if (expanded) {
                val flow = remember(session.id) { viewModel.setsForSession(session.id) }
                val sets by flow.collectAsStateWithLifecycle(initialValue = emptyList())
                if (sets.isEmpty()) Text("No sets recorded.", color = SecondaryText)
                sets.groupBy { it.exerciseName }.forEach { (name, exerciseSets) ->
                    Text(name.ifBlank { "Exercise" }, color = PrimaryText, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp))
                    exerciseSets.forEachIndexed { index, set ->
                        val weight = set.weight.toDisplayWeight(units)
                        Text("Set ${index + 1}: $weight $units × ${set.reps} · RPE ${set.rpe}" +
                            if (set.isWarmup) " · Warmup" else "", color = SecondaryText)
                    }
                }
            }
        }
    }
}
