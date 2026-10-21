package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.R
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AppConstants

private val RIR_OPTIONS = listOf(0, 1, 2, 3, 4, 5)

@Composable
fun RpeRirSelectorSheet(
    selectedRir: Int,
    weight: Double,
    reps: Int,
    exerciseHistory: List<LastSetWithDate>,
    isShowingHistory: Boolean,
    units: String,
    onSelectRirOption: (Int) -> Unit,
    onConfirmRirSelection: () -> Unit,
    onConfirmRirAndNextSet: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentPage = if (isShowingHistory) 1 else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentPage == 0) {
                    // Page 1: RIR selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REPS IN RESERVE",
                            style = MaterialTheme.typography.titleSmall,
                            color = SecondaryText,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "How many more reps could you have done?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "LOGGED: $weight $units × $reps reps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RIR_OPTIONS.forEach { rir ->
                            val isSelected = selectedRir == rir
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) OrangeAccent else DarkRaised)
                                    .clickable { onSelectRirOption(rir) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rir.toString(),
                                    color = if (isSelected) Color.Black else PrimaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onConfirmRirSelection,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.overlay_confirm_next_set), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Page 2: History
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXERCISE HISTORY",
                            style = MaterialTheme.typography.titleSmall,
                            color = SecondaryText,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (exerciseHistory.isEmpty()) {
                        Text(
                            text = "No previous sessions for this exercise.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(exerciseHistory.size, key = { exerciseHistory[it].id }) { idx ->
                                val session = exerciseHistory[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkRaised, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = session.date,
                                        color = SecondaryText,
                                        fontSize = 12.sp
                                    )
                                    val displayWeight = if (units.lowercase() in listOf("lb", "lbs")) {
                                        Math.round(session.weight * AppConstants.KG_TO_LBS * 10.0) / 10.0
                                    } else {
                                        session.weight
                                    }
                                    Text(
                                        text = "$displayWeight $units × ${session.reps}",
                                        color = PrimaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onConfirmRirAndNextSet,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.overlay_confirm_next_set), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RirSelectorOverlay(
    fitnessViewModel: FitnessViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    onTriggerRestTimer: (rpe: Int, exerciseName: String, muscleGroup: String, reps: Int, setIndex: Int, totalSets: Int) -> Unit,
    onDismiss: () -> Unit = { workoutExecutionViewModel.closeRirSelector() }
) {
    val selectedRir by workoutExecutionViewModel.selectedRir.collectAsStateWithLifecycle()
    val weight by workoutExecutionViewModel.rirSelectorWeight.collectAsStateWithLifecycle()
    val reps by workoutExecutionViewModel.rirSelectorReps.collectAsStateWithLifecycle()
    val exerciseHistory by workoutExecutionViewModel.rirHistoricalSets.collectAsStateWithLifecycle()
    val isShowingHistory by workoutExecutionViewModel.isShowingRirHistory.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()

    RpeRirSelectorSheet(
        selectedRir = selectedRir,
        weight = weight,
        reps = reps,
        exerciseHistory = exerciseHistory,
        isShowingHistory = isShowingHistory,
        units = units,
        onSelectRirOption = { workoutExecutionViewModel.selectRirOption(it) },
        onConfirmRirSelection = { workoutExecutionViewModel.confirmRirSelection() },
        onConfirmRirAndNextSet = {
            workoutExecutionViewModel.confirmRirAndNextSet(onTriggerRestTimer = onTriggerRestTimer)
            workoutExecutionViewModel.closeRirSelector()
        },
        onDismiss = onDismiss
    )
}
