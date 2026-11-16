package com.apexfit.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.SessionCommitResult
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexTextStyles
import com.apexfit.app.utils.toDisplayWeight
import java.text.NumberFormat

@Composable
internal fun SessionCompleteSheet(
    receipt: SessionCommitResult,
    sessionSets: List<UiExerciseSet>,
    units: String,
    onDismiss: () -> Unit
) {
    val workingSets = remember(sessionSets) { sessionSets.filter { it.completed && !it.isWarmup } }
    val names = remember(workingSets) { workingSets.associate { it.exerciseId to it.exerciseName } }
    val preview = LocalInspectionMode.current
    var reveal by rememberSaveable(receipt.sessionId) { mutableStateOf(preview && receipt.records.isNotEmpty()) }
    var showDetails by rememberSaveable(receipt.sessionId) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(receipt.sessionId) {
        if (!preview && !reveal && receipt.records.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            reveal = true
        }
    }
    val numberFormat = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    val volume = numberFormat.format((receipt.totalVolumeTonnes * 1_000).toDisplayWeight(units))
    Column(Modifier.fillMaxSize().background(ApexColors.Canvas).safeDrawingPadding().padding(20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { if (showDetails) showDetails = false else onDismiss() }) { Text("Back") }
            val title = if (showDetails) "Workout details" else if (receipt.isPartial) "Workout saved" else receipt.sessionType.takeIf { it.isNotBlank() }
                ?.let { "$it complete" } ?: "Workout complete"
            Text(title, style = MaterialTheme.typography.headlineLarge, color = ApexColors.TextPrimary)
            val duration = if (receipt.durationMinutes > 0) "${receipt.durationMinutes} min · " else ""
            Text("$duration${receipt.setsCompleted} working sets · $volume $units", color = ApexColors.TextSecondary)
            HorizontalDivider(color = ApexColors.BorderSubtle)
            if (showDetails) {
                sessionSets.filter { it.completed }.groupBy { it.exerciseId }.values.forEach { sets ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sets.first().exerciseName, style = MaterialTheme.typography.titleMedium)
                        sets.forEach { set ->
                            val load = numberFormat.format(set.weight.toDisplayWeight(units))
                            val effort = if (set.rpe > 0) " · RPE ${set.rpe}" else ""
                            val warmup = if (set.isWarmup) " · Warm-up" else ""
                            Text("$load $units × ${set.reps}$effort$warmup", color = ApexColors.TextSecondary)
                        }
                    }
                }
                if (sessionSets.none { it.completed }) Text("No completed sets to show.", color = ApexColors.TextSecondary)
            } else {
                AnimatedVisibility(visible = reveal, enter = fadeIn(tween(180))) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val recordHeading = if (receipt.records.size == 1) "1 PR today" else "${receipt.records.size} PRs today"
                        Text(recordHeading, style = MaterialTheme.typography.headlineMedium)
                        receipt.records.forEach { record ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(names[record.exerciseId]?.takeIf(String::isNotBlank) ?: "Exercise",
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(recordLabel(record), color = ApexColors.TextSecondary)
                                }
                                Text("${numberFormat.format(record.value.toDisplayWeight(units))} $units",
                                    style = ApexTextStyles.dataMedium)
                            }
                            HorizontalDivider(color = ApexColors.BorderSubtle)
                        }
                    }
                }
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp)) { Text("Done") }
        if (!showDetails) {
            OutlinedButton(onClick = { showDetails = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp)) { Text("View workout") }
        }
    }
}

private fun recordLabel(record: PersonalRecord): String = when (record.type) {
    "max_weight" -> "Heaviest load"
    "volume" -> "Highest set volume"
    "estimated_1rm" -> "Estimated 1-rep max"
    else -> "New best"
}

@Composable
fun SessionCompleteOverlay(fitnessViewModel: FitnessViewModel, workoutExecutionViewModel: WorkoutExecutionViewModel) {
    val receipt by workoutExecutionViewModel.completedReceipt.collectAsStateWithLifecycle()
    val sessionSets by workoutExecutionViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val result = receipt ?: return
    SessionCompleteSheet(result, sessionSets, units, onDismiss = workoutExecutionViewModel::dismissSessionComplete)
}
