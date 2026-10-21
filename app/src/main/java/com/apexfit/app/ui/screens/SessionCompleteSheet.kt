package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.R
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SessionCompleteSheet(
    sessionSets: List<UiExerciseSet>,
    brokenPRs: List<String>,
    hypertrophyScore: Double,
    sessionDuration: Int,
    units: String,
    onDismiss: () -> Unit
) {
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
                        colors = CardDefaults.cardColors(containerColor = ApexColors.Primary.copy(alpha = 0.15f)),
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
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.overlay_done), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SessionCompleteOverlay(
    fitnessViewModel: FitnessViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel
) {
    val sessionSets by workoutExecutionViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
    val brokenPRs by workoutExecutionViewModel.completedPRsBroken.collectAsStateWithLifecycle()
    val hypertrophyScore by workoutExecutionViewModel.completedHypertrophyScore.collectAsStateWithLifecycle()
    val sessionStats by workoutExecutionViewModel.completedStats.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val sessionDuration = sessionStats.first

    SessionCompleteSheet(
        sessionSets = sessionSets,
        brokenPRs = brokenPRs,
        hypertrophyScore = hypertrophyScore,
        sessionDuration = sessionDuration,
        units = units,
        onDismiss = { workoutExecutionViewModel.dismissSessionComplete() }
    )
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
