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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.SessionCommitResult
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexIcons
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
    val exerciseSummaries = remember(sessionSets) { summarizeCompletedExercises(sessionSets) }
    val totalRestSeconds = remember(sessionSets) { totalActualRestSeconds(sessionSets) }
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
    Column(Modifier.fillMaxSize().background(ApexColors.Canvas).safeDrawingPadding().padding(20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = if (showDetails) "Workout details" else if (receipt.isPartial) "Workout saved" else receipt.sessionType.takeIf { it.isNotBlank() }
                ?.let { "$it complete" } ?: "Workout complete"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { if (showDetails) showDetails = false else onDismiss() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(painterResource(ApexIcons.ArrowLeft), contentDescription = "Back",
                        tint = ApexColors.TextPrimary)
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = ApexColors.TextPrimary
                )
            }
            val duration = if (receipt.durationMinutes > 0) "${receipt.durationMinutes} min · " else ""
            Text("$duration${receipt.setsCompleted} working sets", color = ApexColors.TextSecondary)
            Text("Total rest ${formatReceiptDuration(totalRestSeconds)}", color = ApexColors.TextSecondary)
            HorizontalDivider(color = ApexColors.BorderSubtle)
            if (showDetails) {
                sessionSets.filter { it.completed }.groupBy { it.exerciseId }.values.forEach { sets ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sets.first().exerciseName, style = MaterialTheme.typography.titleMedium)
                        sets.forEach { set ->
                            val load = numberFormat.format(set.weight.toDisplayWeight(units))
                            val effort = if (set.rpe > 0) " · RPE ${set.rpe}" else ""
                            val warmup = if (set.isWarmup) " · Warm-up" else ""
                            val rest = " · Rest ${formatReceiptDuration(set.restTaken.toLong())}"
                            Text("$load $units × ${set.reps}$effort$warmup$rest", color = ApexColors.TextSecondary)
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
                                    Text("New best", color = ApexColors.TextSecondary)
                                }
                                Text(recordPerformanceValue(record, sessionSets, units, numberFormat),
                                    style = ApexTextStyles.dataMedium)
                            }
                            HorizontalDivider(color = ApexColors.BorderSubtle)
                        }
                    }
                }
                ExerciseReceiptSection(
                    summaries = exerciseSummaries,
                    units = units,
                    numberFormat = numberFormat
                )
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

@Composable
private fun ExerciseReceiptSection(
    summaries: List<ExerciseReceiptSummary>,
    units: String,
    numberFormat: NumberFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Exercise summary", style = MaterialTheme.typography.titleLarge, color = ApexColors.TextPrimary)
        if (summaries.isEmpty()) {
            Text("No completed exercises to summarize.", color = ApexColors.TextSecondary)
            return@Column
        }

        summaries.forEachIndexed { exerciseIndex, summary ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        summary.exerciseName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = ApexColors.TextPrimary
                    )
                    val setCount = summary.workingSets.size
                    val setLabel = when {
                        setCount == 1 -> "1 working set"
                        setCount > 1 -> "$setCount working sets"
                        else -> "Warm-up only"
                    }
                    Text(setLabel, color = ApexColors.TextSecondary)
                }

                if (summary.workingSets.isNotEmpty()) {
                    ReceiptSetLines(summary.workingSets, units, numberFormat, prefix = "Set")
                }

                if (summary.warmupSets.isNotEmpty()) {
                    Text(
                        "${summary.warmupSets.size} warm-up ${if (summary.warmupSets.size == 1) "set" else "sets"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ApexColors.TextTertiary
                    )
                    ReceiptSetLines(summary.warmupSets, units, numberFormat, prefix = "Warm-up")
                }

                Text(
                    "Rest ${formatReceiptDuration(summary.actualRestSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexColors.TextSecondary
                )
            }
            if (exerciseIndex < summaries.lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}

@Composable
private fun ReceiptSetLines(
    sets: List<UiExerciseSet>,
    units: String,
    numberFormat: NumberFormat,
    prefix: String
) {
    val firstWeight = sets.firstOrNull()?.weight ?: 0.0
    val sameLoggedLoad = firstWeight > 0.0 && sets.all { kotlin.math.abs(it.weight - firstWeight) < 0.001 }
    if (sameLoggedLoad) {
        val load = numberFormat.format(firstWeight.toDisplayWeight(units))
        Text(
            "$load $units × ${sets.joinToString(", ") { it.reps.toString() }} reps",
            color = ApexColors.TextSecondary
        )
    } else {
        sets.forEachIndexed { index, set ->
            val loadAndReps = if (set.weight > 0.0) {
                "${numberFormat.format(set.weight.toDisplayWeight(units))} $units × ${set.reps} reps"
            } else {
                "${set.reps} reps · load not logged"
            }
            Text("$prefix ${index + 1} · $loadAndReps", color = ApexColors.TextSecondary)
        }
    }
}

private fun recordPerformanceValue(
    record: PersonalRecord,
    sessionSets: List<UiExerciseSet>,
    units: String,
    numberFormat: NumberFormat
): String {
    val displayWeight = numberFormat.format(record.value.toDisplayWeight(units))
    if (record.type != "max_weight") return "$displayWeight $units"

    val matchingReps = sessionSets.asSequence()
        .filter { it.completed && !it.isWarmup && it.exerciseId == record.exerciseId }
        .filter { kotlin.math.abs(it.weight - record.value) < 0.001 }
        .firstOrNull()?.reps
        ?: return "$displayWeight $units"

    return "$displayWeight $units × $matchingReps"
}

@Composable
fun SessionCompleteOverlay(fitnessViewModel: FitnessViewModel, workoutExecutionViewModel: WorkoutExecutionViewModel) {
    val receipt by workoutExecutionViewModel.completedReceipt.collectAsStateWithLifecycle()
    val sessionSets by workoutExecutionViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val result = receipt ?: return
    SessionCompleteSheet(result, sessionSets, units, onDismiss = workoutExecutionViewModel::dismissSessionComplete)
}
