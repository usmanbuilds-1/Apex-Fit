package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.TrainViewModel
import com.apexfit.app.ui.theme.*
import kotlin.math.roundToInt

private val RIR_OPTIONS = listOf(0, 1, 2, 3, 4, 5)

@Composable
fun RestTimerOverlay(fitnessViewModel: FitnessViewModel, trainViewModel: TrainViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REST TIMER",
                    style = MaterialTheme.typography.titleSmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                RestTimerDisplay(trainViewModel = trainViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                RestTimerButtons(
                    onSkip = { fitnessViewModel.closeRestTimer() },
                    onAddTime = { trainViewModel.addRestTimerSeconds(15) },
                    canSubtract = trainViewModel.restTimerSeconds.value > 0,
                    onSubtractTime = {
                        val current = trainViewModel.restTimerSeconds.value
                        if (current >= 15) {
                            trainViewModel.addRestTimerSeconds(-15)
                        } else if (current > 0) {
                            trainViewModel.addRestTimerSeconds(-current)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RestTimerDisplay(trainViewModel: TrainViewModel) {
    val seconds by trainViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val totalSeconds by trainViewModel.restTimerTotal.collectAsStateWithLifecycle()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatTimerTime(seconds),
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = OrangeAccent,
            fontFamily = SyneFamily
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (totalSeconds > 0) (totalSeconds - seconds).toFloat() / totalSeconds else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = OrangeAccent,
            trackColor = DarkRaised
        )
    }
}

@Composable
private fun RestTimerButtons(
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    canSubtract: Boolean,
    onSubtractTime: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(
            onClick = onSubtractTime,
            enabled = canSubtract
        ) {
            Text(stringResource(R.string.overlay_15s), color = SecondaryText, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onAddTime
        ) {
            Text(stringResource(R.string.overlay_15s_1), color = OrangeAccent, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSkip,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.overlay_skip), color = SecondaryText, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onSkip,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.overlay_ready), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTimerTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
}

@Composable
fun RirSelectorOverlay(fitnessViewModel: FitnessViewModel, trainViewModel: TrainViewModel) {
    val selectedRir by trainViewModel.selectedRir.collectAsStateWithLifecycle()
    val weight by trainViewModel.rirSelectorWeight.collectAsStateWithLifecycle()
    val reps by trainViewModel.rirSelectorReps.collectAsStateWithLifecycle()
    val exerciseHistory by trainViewModel.rirHistoricalSets.collectAsStateWithLifecycle()
    val isShowingHistory by trainViewModel.isShowingRirHistory.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val currentPage = if (isShowingHistory) 1 else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentPage == 0) {
                    // Page 1: RIR selection
                    Text(
                        text = "REPS IN RESERVE",
                        style = MaterialTheme.typography.titleSmall,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold
                    )
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
                                    .clickable { trainViewModel.selectRirOption(rir) },
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
                        onClick = { trainViewModel.confirmRirSelection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.overlay_confirm_next_set), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Page 2: History
                    Text(
                        text = "EXERCISE HISTORY",
                        style = MaterialTheme.typography.titleSmall,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold
                    )
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
                                        Math.round(session.weight * 2.20462 * 10.0) / 10.0
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
                        onClick = {
                            trainViewModel.confirmRirAndNextSet()
                            fitnessViewModel.closeRirSelector()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
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
fun SessionCompleteOverlay(fitnessViewModel: FitnessViewModel, trainViewModel: TrainViewModel) {
    val sessionSets by trainViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
    val brokenPRs by trainViewModel.completedPRsBroken.collectAsStateWithLifecycle()
    val hypertrophyScore by trainViewModel.completedHypertrophyScore.collectAsStateWithLifecycle()
    val sessionStats by trainViewModel.completedStats.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val sessionDuration = sessionStats.first

    val (totalVolume, totalSets, totalReps) = remember(sessionSets) {
        val workSets = sessionSets.filter { !it.isWarmup }
        Triple(workSets.sumOf { it.weight * it.reps }, workSets.size, workSets.sumOf { it.reps })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Trophy / celebration icon
                Icon(
                    painter = painterResource(R.drawable.ic_emoji_events),
                    contentDescription = "Workout Complete",
                    tint = OrangeAccent,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WORKOUT COMPLETE",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryText,
                    fontWeight = FontWeight.Black,
                    fontFamily = SyneFamily
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Stats grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "DURATION", value = "${sessionDuration}m")
                    StatItem(label = "SETS", value = totalSets.toString())
                    StatItem(label = "REPS", value = totalReps.toString())
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "VOLUME", value = "${totalVolume.roundToInt()} $units")
                    StatItem(label = "HYPERTROPHY", value = "${(hypertrophyScore).roundToInt()}")
                }

                // PRs broken
                if (brokenPRs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = OrangeAccent.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🏆 PERSONAL RECORDS CONQUERED",
                                style = MaterialTheme.typography.titleSmall,
                                color = OrangeAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            brokenPRs.forEach { pr ->
                                Text(
                                    text = pr,
                                    color = PrimaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { fitnessViewModel.dismissSessionComplete() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.overlay_done), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = PrimaryText,
            fontFamily = SyneFamily
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MutedText,
            fontWeight = FontWeight.Bold
        )
    }
}
