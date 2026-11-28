package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.RestTimerViewModel
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.utils.ProgressionEngine
import com.apexfit.app.utils.EffortSelection

private val RIR_OPTIONS = listOf(0, 1, 2, 3, 4, 5)

@Composable
fun RpeRirSelectorSheet(
    exerciseName: String,
    exerciseNumber: Int,
    exerciseCount: Int,
    setNumber: Int,
    totalSets: Int,
    targetRepsMin: Int,
    targetRepsMax: Int,
    selectedRir: Int,
    weight: Double,
    reps: Int,
    exerciseHistory: List<LastSetWithDate>,
    isShowingHistory: Boolean,
    hasLoggedSet: Boolean,
    hasRestContext: Boolean,
    restSeconds: Int,
    totalRestSeconds: Int,
    lastSetContext: String,
    nextSetContext: String,
    primaryActionLabel: String,
    units: String,
    onSelectRirOption: (Int) -> Unit,
    onConfirmRirSelection: () -> Unit,
    onReturnFromHistory: () -> Unit,
    onAddRestTime: (Int) -> Unit,
    onSubtractRestTime: () -> Unit,
    onConfirmRirAndNextSet: () -> Unit,
    onDismiss: () -> Unit,
    isSavingSet: Boolean = false,
    setSaveError: String? = null,
    loadBasis: com.apexfit.app.utils.RecordedLoadBasis? = null,
    loadSetup: String = "",
    freeWeightRepLimit: Boolean = false,
    onLoadBasis: ((com.apexfit.app.utils.RecordedLoadBasis?) -> Unit)? = null,
    onLoadSetup: (String) -> Unit = {},
    onRepLimit: (Boolean) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().background(ApexColors.Canvas).systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, enabled = !isSavingSet, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(ApexIcons.ArrowLeft), contentDescription = "Back to workout",
                        tint = ApexColors.TextPrimary)
                }
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.titleLarge,
                        color = ApexColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isShowingHistory) "Previous sets" else "Exercise $exerciseNumber of $exerciseCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (isShowingHistory) {
                HistoryContent(exerciseHistory = exerciseHistory, exerciseName = exerciseName, units = units)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Set $setNumber · $weight $units × $reps",
                        style = MaterialTheme.typography.titleMedium,
                        color = ApexColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Target $targetRepsMin–$targetRepsMax",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ApexColors.TextSecondary,
                        textAlign = TextAlign.End
                    )
                }
                HorizontalDivider(color = ApexColors.BorderSubtle)
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "How many more reps could you have done?",
                    color = ApexColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(14.dp))
                RirOptions(selectedRir = selectedRir, onSelect = { if (!isSavingSet) onSelectRirOption(it) })
                Spacer(Modifier.height(9.dp))
                val rated = EffortSelection.isRated(selectedRir)
                val rpe = EffortSelection.rpe(selectedRir)
                val repStatus = when {
                    reps < targetRepsMin -> "Below target"
                    reps > targetRepsMax -> "Above target"
                    else -> "On target"
                }
                Text(if (rated) "RPE $rpe · $repStatus" else "Effort unrated · $repStatus", style = MaterialTheme.typography.bodyMedium,
                    color = ApexColors.TextSecondary, fontWeight = FontWeight.Medium)
                if (!hasLoggedSet && onLoadBasis != null) {
                    var showSetup by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    TextButton(onClick = { showSetup = !showSetup }, enabled = !isSavingSet) {
                        Text(if (loadBasis == null) "Load setup · optional" else loadBasis.label + " · " + loadSetup)
                    }
                    if (showSetup) {
                        Text("Confirm only the load you actually recorded.", style = MaterialTheme.typography.bodySmall)
                        (listOf<com.apexfit.app.utils.RecordedLoadBasis?>(null) +
                            com.apexfit.app.utils.RecordedLoadBasis.entries).forEach { basis ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(selected = basis == loadBasis,
                                    enabled = !isSavingSet, onClick = { onLoadBasis(basis) })
                                Text(basis?.label ?: "Unknown / assisted / bodyweight")
                            }
                        }
                        if (loadBasis != null) {
                            androidx.compose.material3.OutlinedTextField(loadSetup, onLoadSetup,
                                enabled = !isSavingSet, singleLine = true,
                                label = { Text("Setup name (same for comparable sets)") })
                        }
                        if (loadBasis == com.apexfit.app.utils.RecordedLoadBasis.TOTAL && selectedRir == 0 && reps in 2..5) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(checked = freeWeightRepLimit,
                                    enabled = !isSavingSet, onCheckedChange = onRepLimit)
                                Text("Unassisted free-weight set, full uninterrupted reps at my rep limit.")
                            }
                        }
                    }
                }
                if (!hasLoggedSet) {
                    if (!rated) Text("Choose an effort rating or log without one. Unrated sets do not establish effort-based progression.",
                        style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    TextButton(
                        enabled = rated && !isSavingSet,
                        onClick = onConfirmRirSelection,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("View previous sets", color = ApexColors.TextSecondary)
                    }
                }

                if (hasLoggedSet && hasRestContext) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    RestContext(
                        seconds = restSeconds,
                        totalSeconds = totalRestSeconds,
                        currentExerciseName = exerciseName,
                        lastSet = lastSetContext,
                        nextSet = nextSetContext,
                        onAddTime = onAddRestTime,
                        onSubtractTime = onSubtractRestTime
                    )
                } else if (!hasLoggedSet) {
                    Text(
                        text = "Rest starts after you log this set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexColors.TextTertiary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                }
            }
        }

        setSaveError?.let { Text(it, color = ApexColors.Alert, modifier = Modifier.padding(bottom = 8.dp)) }
        Button(
            enabled = !isSavingSet,
            onClick = if (isShowingHistory) onReturnFromHistory else onConfirmRirAndNextSet,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (isSavingSet) "Saving set…" else if (isShowingHistory) "Back to workout" else primaryActionLabel,
                color = ApexColors.OnGlow,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RirOptions(selectedRir: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(ApexColors.Surface)
            .border(1.dp, ApexColors.BorderSubtle, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        RIR_OPTIONS.forEach { rir ->
            val selected = selectedRir == rir
            val interaction = remember(rir) { MutableInteractionSource() }
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) ApexColors.Primary else ApexColors.SurfaceRaised)
                    .semantics {
                        this.selected = selected
                        contentDescription = if (rir == 5) "5 or more reps in reserve" else "$rir reps in reserve"
                    }
                    .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        onSelect(rir)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (rir == 5) "5+" else rir.toString(),
                    color = if (selected) ApexColors.OnGlow else ApexColors.TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 17.sp
                )
            }
        }
    }
}

@Composable
private fun RestContext(
    seconds: Int,
    totalSeconds: Int,
    currentExerciseName: String,
    lastSet: String,
    nextSet: String,
    onAddTime: (Int) -> Unit,
    onSubtractTime: () -> Unit
) {
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Rest", style = MaterialTheme.typography.titleMedium, color = ApexColors.TextPrimary,
            fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSubtractTime, modifier = Modifier.heightIn(min = 44.dp)
                    .background(ApexColors.SurfaceRaised, RoundedCornerShape(7.dp)),
                shape = RoundedCornerShape(7.dp)) {
                Text("−15", color = ApexColors.TextSecondary)
            }
            OutlinedButton(onClick = { onAddTime(15) }, modifier = Modifier.heightIn(min = 44.dp)
                    .background(ApexColors.SurfaceRaised, RoundedCornerShape(7.dp)),
                shape = RoundedCornerShape(7.dp)) {
                Text("+15", color = ApexColors.TextSecondary)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatTimerTime(seconds), fontSize = 40.sp, fontWeight = FontWeight.Bold,
            color = ApexColors.TextPrimary, fontFamily = FontFamily.Monospace)
        Text("of ${formatTotalTime(totalSeconds)}", fontSize = 16.sp, color = ApexColors.TextSecondary,
            modifier = Modifier.padding(bottom = 7.dp))
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { if (totalSeconds > 0) ((totalSeconds - seconds).toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = ApexColors.Primary,
        trackColor = ApexColors.SurfaceSunken
    )
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = ApexColors.BorderSubtle)
    if (lastSet.isNotBlank()) {
        val lastValue = lastSet.removePrefix("Last set: ")
            .substringBefore(" · RPE ")
            .substringBefore(" @ RPE ")
            .removeSuffix(" reps")
        ContextRow("Last", lastValue)
    }
    if (nextSet.isNotBlank()) {
        val nextValue = nextSet.removePrefix("Next: ")
            .removePrefix("$currentExerciseName · ")
        ContextRow("Next", nextValue)
    }
    HorizontalDivider(color = ApexColors.BorderSubtle)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ContextRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextPrimary,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

@Composable
private fun HistoryContent(exerciseHistory: List<LastSetWithDate>, exerciseName: String, units: String) {
    Text("Previous sets", style = MaterialTheme.typography.headlineSmall,
        color = ApexColors.TextPrimary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = ApexColors.BorderSubtle)
    if (exerciseHistory.isEmpty()) {
        Text("No previous sets for this exercise yet.", style = MaterialTheme.typography.bodyMedium,
            color = ApexColors.TextSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
    } else {
        exerciseHistory.take(5).forEach { session ->
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(session.date, color = ApexColors.TextSecondary, fontSize = 13.sp)
                val displayWeight = if (units.lowercase() in listOf("lb", "lbs")) {
                    Math.round(session.weight * AppConstants.KG_TO_LBS * 10.0) / 10.0
                } else session.weight
                Text("$displayWeight $units × ${session.reps}", color = ApexColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}

@Composable
fun RirSelectorOverlay(
    fitnessViewModel: FitnessViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    restTimerViewModel: RestTimerViewModel,
    onDismiss: () -> Unit
) {
    val state by workoutExecutionViewModel.rirOverlayState.collectAsStateWithLifecycle()
    val savingSet by workoutExecutionViewModel.isSavingSet.collectAsStateWithLifecycle()
    val saveError by workoutExecutionViewModel.setSaveError.collectAsStateWithLifecycle()
    val activeExercises by workoutExecutionViewModel.activeExercises.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val restSeconds by restTimerViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val restTotalSeconds by restTimerViewModel.restTimerTotal.collectAsStateWithLifecycle()
    val lastSetContext by restTimerViewModel.restTimerLastSetContext.collectAsStateWithLifecycle()
    val nextSetPreview by restTimerViewModel.restTimerNextSetPreview.collectAsStateWithLifecycle()
    val isRestVisible by restTimerViewModel.showRestOverlay.collectAsStateWithLifecycle()
    val occurrenceKeys = com.apexfit.app.utils.WorkoutOccurrenceKeys.allocate(activeExercises.map { it.exerciseId })
    val exerciseIndex = occurrenceKeys.indexOf(state.exerciseId)
    val exercise = activeExercises.getOrNull(exerciseIndex)
    val repMin = exercise?.repsMin?.coerceAtLeast(1) ?: 1
    val repMax = exercise?.repsMax?.coerceAtLeast(repMin) ?: repMin
    val isLastSet = state.setIndex + 1 >= state.totalSets
    val nextExercise = activeExercises.getOrNull(exerciseIndex + 1)
    val isWorkoutComplete = isLastSet && exerciseIndex >= 0 && exerciseIndex == activeExercises.lastIndex
    val nextAction = when {
        state.isShowingHistory -> "Back to workout"
        !state.hasLoggedSet -> if (EffortSelection.isRated(state.selectedRir)) "Log set ${state.setIndex + 1}" else "Log set ${state.setIndex + 1} unrated"
        !isLastSet -> "Start set ${state.setIndex + 2}"
        nextExercise != null -> "Start ${nextExercise.name}"
        else -> "Review workout"
    }

    RpeRirSelectorSheet(
        exerciseName = state.exerciseName,
        exerciseNumber = (exerciseIndex + 1).coerceAtLeast(1),
        exerciseCount = activeExercises.size.coerceAtLeast(1),
        setNumber = state.setIndex + 1,
        totalSets = state.totalSets.coerceAtLeast(1),
        targetRepsMin = repMin,
        targetRepsMax = repMax,
        selectedRir = state.selectedRir,
        weight = state.weight,
        reps = state.reps,
        exerciseHistory = state.historicalSets,
        isShowingHistory = state.isShowingHistory,
        hasLoggedSet = state.hasLoggedSet,
        hasRestContext = restTotalSeconds > 0 && lastSetContext.isNotBlank(),
        restSeconds = restSeconds,
        totalRestSeconds = restTotalSeconds,
        lastSetContext = lastSetContext,
        nextSetContext = nextSetPreview,
        primaryActionLabel = nextAction,
        units = units,
        onSelectRirOption = { rir ->
            workoutExecutionViewModel.selectRirOption(rir) {
            if (state.hasLoggedSet) {
                restTimerViewModel.updateRestTimerForRpe(
                    rpe = ProgressionEngine.calculateRPEFromRIR(rir),
                    exerciseName = state.exerciseName,
                    muscleGroup = state.muscleGroup,
                    exerciseId = state.exerciseId,
                    weight = state.weight,
                    units = units,
                    reps = state.reps
                )
            }
            }
        },
        onConfirmRirSelection = workoutExecutionViewModel::confirmRirSelection,
        onReturnFromHistory = workoutExecutionViewModel::returnToRirSelection,
        onAddRestTime = restTimerViewModel::addRestTimerSeconds,
        onSubtractRestTime = {
            if (restSeconds >= 15) restTimerViewModel.addRestTimerSeconds(-15)
            else if (restSeconds > 0) restTimerViewModel.addRestTimerSeconds(-restSeconds)
        },
        onConfirmRirAndNextSet = {
            if (state.isShowingHistory) {
                workoutExecutionViewModel.returnToRirSelection()
            } else if (!state.hasLoggedSet) {
                workoutExecutionViewModel.confirmRirAndNextSet { rpe, name, muscle, weight, reps, setIndex, totalSets ->
                    restTimerViewModel.triggerRestTimer(
                        rpe = rpe,
                        exerciseName = name,
                        muscleGroup = muscle,
                        reps = reps,
                        setIndex = setIndex,
                        totalSets = totalSets,
                        weight = weight,
                        units = units,
                        currentExerciseIdx = workoutExecutionViewModel.currentExerciseIdx.value,
                        completedExerciseId = state.exerciseId
                    )
                }
            } else {
                if (isRestVisible) restTimerViewModel.closeRestTimer()
                workoutExecutionViewModel.closeRirSelector()
                if (isWorkoutComplete) workoutExecutionViewModel.nextExercise()
            }
        },
        onDismiss = onDismiss,
        isSavingSet = savingSet,
        setSaveError = saveError,
        loadBasis = state.loadBasis, loadSetup = state.loadSetup, freeWeightRepLimit = state.freeWeightRepLimit,
        onLoadBasis = workoutExecutionViewModel::selectLoadBasis,
        onLoadSetup = workoutExecutionViewModel::setLoadSetup,
        onRepLimit = workoutExecutionViewModel::setFreeWeightRepLimit
    )
}

private fun formatTimerTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return String.format(java.util.Locale.US, "%02d:%02d", safeSeconds / 60, safeSeconds % 60)
}

private fun formatTotalTime(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return String.format(java.util.Locale.US, "%d:%02d", safeSeconds / 60, safeSeconds % 60)
}
