package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.utils.ProgressionEngine

private val RIR_OPTIONS = listOf(0, 1, 2, 3, 4, 5)

@Composable
fun RpeRirSelectorSheet(
    exerciseName: String,
    setNumber: Int,
    totalSets: Int,
    targetRepsMin: Int,
    targetRepsMax: Int,
    selectedRir: Int,
    weight: Double,
    reps: Int,
    exerciseHistory: List<LastSetWithDate>,
    isShowingHistory: Boolean,
    units: String,
    onSelectRirOption: (Int) -> Unit,
    onConfirmRirSelection: () -> Unit,
    onReturnFromHistory: () -> Unit,
    onConfirmRirAndNextSet: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexColors.Canvas)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isShowingHistory) "PREVIOUS SETS" else "LOG SET",
                style = MaterialTheme.typography.labelLarge,
                color = ApexColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = ApexColors.TextSecondary)
            }
        }

        if (isShowingHistory) {
            Text("Previous sets", style = MaterialTheme.typography.headlineSmall, color = ApexColors.TextPrimary,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = ApexColors.BorderSubtle)
            if (exerciseHistory.isEmpty()) {
                Text(
                    text = "No previous sets for this exercise yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ApexColors.TextSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
                )
            } else {
                exerciseHistory.take(5).forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.date, color = ApexColors.TextSecondary, fontSize = 13.sp)
                        val displayWeight = if (units.lowercase() in listOf("lb", "lbs")) {
                            Math.round(session.weight * AppConstants.KG_TO_LBS * 10.0) / 10.0
                        } else session.weight
                        Text(
                            text = "$displayWeight $units × ${session.reps}",
                            color = ApexColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onReturnFromHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Back to effort", color = ApexColors.Primary)
            }
        } else {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                color = ApexColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "SET $setNumber OF $totalSets  ·  TARGET $targetRepsMin–$targetRepsMax REPS",
                style = MaterialTheme.typography.labelMedium,
                color = ApexColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )

            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Spacer(Modifier.height(20.dp))
            Text("COMPLETED SET", style = MaterialTheme.typography.labelMedium,
                color = ApexColors.TextTertiary, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$weight $units  ×  $reps reps",
                style = MaterialTheme.typography.titleLarge,
                color = ApexColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(28.dp))
            Text("How many more reps could you have done?", style = MaterialTheme.typography.titleMedium,
                color = ApexColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                RIR_OPTIONS.forEach { rir ->
                    val isSelected = selectedRir == rir
                    val interactionSource = remember(rir) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ApexColors.PrimaryMist else Color.Transparent)
                            .semantics { selected = isSelected }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.RadioButton
                            ) { onSelectRirOption(rir) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rir == 5) "5+" else rir.toString(),
                            color = if (isSelected) ApexColors.Primary else ApexColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val rpe = ProgressionEngine.calculateRPEFromRIR(selectedRir)
            Text(
                text = "RIR ${if (selectedRir == 5) "5+" else selectedRir}  ·  RPE $rpe",
                style = MaterialTheme.typography.bodyMedium,
                color = ApexColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = ApexColors.BorderSubtle)
            TextButton(onClick = onConfirmRirSelection, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("View previous sets", color = ApexColors.TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onConfirmRirAndNextSet,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Log set $setNumber", color = ApexColors.OnGlow, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RirSelectorOverlay(
    fitnessViewModel: FitnessViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    onTriggerRestTimer: (rpe: Int, exerciseName: String, muscleGroup: String, weight: Double, units: String, reps: Int, setIndex: Int, totalSets: Int) -> Unit,
    onDismiss: () -> Unit = { workoutExecutionViewModel.closeRirSelector() }
) {
    val state by workoutExecutionViewModel.rirOverlayState.collectAsStateWithLifecycle()
    val activeExercises by workoutExecutionViewModel.activeExercises.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val exercise = activeExercises.firstOrNull { it.exerciseId == state.exerciseId }
    val repMin = exercise?.repsMin?.coerceAtLeast(1) ?: 1
    val repMax = exercise?.repsMax?.coerceAtLeast(repMin) ?: repMin

    RpeRirSelectorSheet(
        exerciseName = state.exerciseName,
        setNumber = state.setIndex + 1,
        totalSets = state.totalSets.coerceAtLeast(1),
        targetRepsMin = repMin,
        targetRepsMax = repMax,
        selectedRir = state.selectedRir,
        weight = state.weight,
        reps = state.reps,
        exerciseHistory = state.historicalSets,
        isShowingHistory = state.isShowingHistory,
        units = units,
        onSelectRirOption = workoutExecutionViewModel::selectRirOption,
        onConfirmRirSelection = workoutExecutionViewModel::confirmRirSelection,
        onReturnFromHistory = workoutExecutionViewModel::returnToRirSelection,
        onConfirmRirAndNextSet = {
            workoutExecutionViewModel.confirmRirAndNextSet { rpe, name, muscle, weight, reps, setIndex, totalSets ->
                onTriggerRestTimer(rpe, name, muscle, weight, units, reps, setIndex, totalSets)
            }
            workoutExecutionViewModel.closeRirSelector()
        },
        onDismiss = onDismiss
    )
}
