package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.RestTimerViewModel
import com.apexfit.app.ui.theme.ApexColors

@Composable
fun RestTimerOverlay(
    seconds: Int,
    totalSeconds: Int,
    onDismiss: () -> Unit,
    onAddTime: (Int) -> Unit,
    onSubtractTime: () -> Unit,
    lastSet: String = "",
    nextSet: String = "",
    startActionLabel: String = "Start next set"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ApexColors.Canvas)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("REST", style = MaterialTheme.typography.labelLarge, color = ApexColors.TextTertiary,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
            IconButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Skip rest", tint = ApexColors.TextSecondary)
            }
        }

        Spacer(Modifier.weight(0.6f))
        Text(
            text = "${formatTimerTime(seconds)}  /  ${formatTimerTime(totalSeconds)}",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = ApexColors.TextPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { if (totalSeconds > 0) ((totalSeconds - seconds).toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = ApexColors.Primary,
            trackColor = ApexColors.SurfaceSunken
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSubtractTime, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("−15 sec", color = ApexColors.TextSecondary)
            }
            OutlinedButton(onClick = { onAddTime(15) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("+15 sec", color = ApexColors.Primary)
            }
        }
        Spacer(Modifier.weight(0.7f))

        if (lastSet.isNotBlank() || nextSet.isNotBlank()) {
            HorizontalDivider(color = ApexColors.BorderSubtle)
            if (lastSet.isNotBlank()) {
                ContextRow(label = "LAST SET", value = lastSet.removePrefix("Last set: "))
            }
            if (nextSet.isNotBlank()) {
                ContextRow(label = "UP NEXT", value = nextSet.removePrefix("Next: "))
            }
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(startActionLabel, color = ApexColors.OnGlow, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContextRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextPrimary,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RestTimerOverlay(
    restTimerViewModel: RestTimerViewModel,
    onDismiss: () -> Unit = { restTimerViewModel.closeRestTimer() }
) {
    val seconds by restTimerViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val totalSeconds by restTimerViewModel.restTimerTotal.collectAsStateWithLifecycle()
    val lastSet by restTimerViewModel.restTimerLastSetContext.collectAsStateWithLifecycle()
    val nextSet by restTimerViewModel.restTimerNextSetPreview.collectAsStateWithLifecycle()

    RestTimerOverlay(
        seconds = seconds,
        totalSeconds = totalSeconds,
        lastSet = lastSet,
        nextSet = nextSet,
        startActionLabel = if (nextSet.contains("Review workout", ignoreCase = true)) "Review workout" else "Start next set",
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

private fun formatTimerTime(seconds: Int): String {
    val mins = seconds.coerceAtLeast(0) / 60
    val secs = seconds.coerceAtLeast(0) % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}
