package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.apexfit.app.ui.models.UiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.MuscleRecoveryStatus
import com.apexfit.app.ui.theme.*

@Composable
fun MuscleRecoveryScreen(
    progressViewModel: ProgressViewModel,
    onBack: () -> Unit
) {
    val statusesState by progressViewModel.muscleRecoveryStatuses.collectAsStateWithLifecycle()
    
    if (statusesState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BlueAccent)
        }
        return
    }

    if (statusesState is UiState.Error) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.progress_detail_error_loading_recovery_statuse), color = Color.White)
        }
        return
    }

    val statuses: List<com.apexfit.app.data.MuscleRecoveryStatus> = (statusesState as? UiState.Success)?.data ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Text(
                text = "MUSCLE RECOVERY",
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = SyneFamily,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statuses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No training data yet.\nLog a workout to see muscle recovery status.",
                    color = MutedText,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(statuses.size, key = { statuses[it].muscleGroup }) { idx ->
                    val status = statuses[idx]
                    RecoveryCard(status)
                }
            }
        }
    }
}

@Composable
private fun RecoveryCard(status: MuscleRecoveryStatus) {
    val recoveryColor = when {
        status.hoursRemaining <= 0 -> GreenAccent
        status.hoursRemaining < 24 -> AmberAccent
        status.hoursRemaining < 48 -> Color(0xFFFF6B35)
        else -> Color(0xFFE63946)
    }
    val recoveryLabel = when {
        status.hoursRemaining <= 0 -> "FULLY RECOVERED"
        status.hoursRemaining < 24 -> "RECOVERING"
        status.hoursRemaining < 48 -> "NEEDS REST"
        else -> "FATIGUED"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.muscleGroup,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (status.lastTrainedDate.isNotEmpty()) {
                    Text(
                        text = "Last trained: ${status.lastTrainedDate}",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = recoveryLabel,
                    color = recoveryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                if (status.hoursRemaining > 0) {
                    Text(
                        text = "${status.hoursRemaining}h left",
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { status.recoveryFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = recoveryColor,
            trackColor = DarkRaised
        )
    }
}

@Composable
fun BodyMeasurementDetailScreen(
    progressViewModel: ProgressViewModel,
    bodyPart: String,
    onBack: () -> Unit
) {
    val measurements by progressViewModel.measurementsForBodyPart(bodyPart).collectAsStateWithLifecycle(initialValue = emptyList())
    var newValue by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    val lengthUnit = if (units == "kg") "cm" else "in"
    // TODO v1.1: Add separate Length Units selector in Settings (M-022 full implementation)
    // For v1.0, this coupling is acceptable — most users use metric or imperial consistently
    fun convertForDisplay(valueInCm: Double): Double {
        return if (lengthUnit == "in") valueInCm / 2.54 else valueInCm
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Text(
                text = bodyPart.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                fontFamily = SyneFamily,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input new measurement
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LOG NEW MEASUREMENT",
                    color = SecondaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = {
                        newValue = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                        error = ""
                    },
                    label = { Text(stringResource(R.string.progress_detail_dimension_value, lengthUnit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedBorderColor = OrangeAccent,
                        unfocusedBorderColor = DarkRaised
                    )
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val v = newValue.toDoubleOrNull()
                        if (v == null || v <= 0) {
                            error = "Enter a valid positive number"
                        } else {
                            progressViewModel.logMeasurement(bodyPart, v)
                            newValue = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.progress_detail_save_measurement), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // History list
        Text(
            text = "HISTORY",
            color = SecondaryText,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (measurements.isEmpty()) {
            Text(
                text = "No measurements logged yet.",
                color = MutedText,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(measurements.size, key = { measurements[it].id }) { idx ->
                    val m = measurements[idx]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(m.date, color = SecondaryText, fontSize = 13.sp)
                            Text(
                                text = stringResource(
                                    R.string.progress_detail_fmt_str,
                                    String.format(java.util.Locale.US, "%.1f", convertForDisplay(m.value)),
                                    lengthUnit
                                ),
                                color = PrimaryText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
