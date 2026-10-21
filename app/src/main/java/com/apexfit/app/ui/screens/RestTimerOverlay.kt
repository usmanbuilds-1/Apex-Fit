package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.R
import com.apexfit.app.RestTimerViewModel
import com.apexfit.app.ui.theme.*

@Composable
fun RestTimerOverlay(
    seconds: Int,
    totalSeconds: Int,
    onDismiss: () -> Unit,
    onAddTime: (Int) -> Unit,
    onSubtractTime: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REST TIMER",
                        style = MaterialTheme.typography.titleSmall,
                        color = SecondaryText,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                RestTimerDisplay(seconds = seconds, totalSeconds = totalSeconds)
                Spacer(modifier = Modifier.height(24.dp))
                RestTimerButtons(
                    onSkip = onDismiss,
                    onAddTime = { onAddTime(15) },
                    canSubtract = seconds > 0,
                    onSubtractTime = onSubtractTime
                )
            }
        }
    }
}

@Composable
fun RestTimerOverlay(
    fitnessViewModel: FitnessViewModel,
    restTimerViewModel: RestTimerViewModel,
    onDismiss: () -> Unit = { restTimerViewModel.closeRestTimer() }
) {
    val seconds by restTimerViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val totalSeconds by restTimerViewModel.restTimerTotal.collectAsStateWithLifecycle()

    RestTimerOverlay(
        seconds = seconds,
        totalSeconds = totalSeconds,
        onDismiss = onDismiss,
        onAddTime = { restTimerViewModel.addRestTimerSeconds(it) },
        onSubtractTime = {
            if (seconds >= 15) {
                restTimerViewModel.addRestTimerSeconds(-15)
            } else if (seconds > 0) {
                restTimerViewModel.addRestTimerSeconds(-seconds)
            }
        }
    )
}

@Composable
private fun RestTimerDisplay(seconds: Int, totalSeconds: Int) {
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
            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
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
