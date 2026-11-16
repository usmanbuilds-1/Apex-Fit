package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
    MuscleRecoveryContent(statusesState, onBack)
}

@Composable
internal fun MuscleRecoveryContent(
    state: UiState<List<MuscleRecoveryStatus>>,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Muscle Recovery", style = MaterialTheme.typography.titleLarge)
        }
        when (state) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent)
            }
            is UiState.Error -> Text(
                stringResource(R.string.progress_detail_error_loading_recovery_statuse),
                color = ApexColors.Alert,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            is UiState.Success -> {
                val statuses = state.data.filter { it.lastTrainedDate.isNotBlank() }
                if (statuses.isEmpty()) {
                    Text(
                        "Log a workout to see recovery estimates for the muscles you train.",
                        color = MutedText,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Text(
                        "Estimates reflect recent training. Use how you feel to guide your next session.",
                        color = MutedText,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    val sorted = statuses.sortedByDescending { it.hoursRemaining }
                    LazyColumn {
                        items(sorted.size, key = { sorted[it].muscleGroup }) { index ->
                            val status = sorted[index]
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(status.muscleGroup, style = MaterialTheme.typography.titleMedium)
                                    Text("Last trained ${com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(status.lastTrainedDate)}", color = MutedText)
                                }
                                if (status.hoursRemaining > 0) {
                                    Text("~${status.hoursRemaining} h remaining", color = AmberAccent)
                                } else {
                                    Text("Likely recovered", color = MutedText)
                                }
                            }
                        }
                    }
                }
            }
        }
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
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text("Delete measurement?") },
            text = { Text("This measurement will be removed from your history.") },
            confirmButton = { TextButton(onClick = { progressViewModel.deleteBodyMeasurement(id); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
    }
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
                    Text(error, color = ApexColors.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.progress_detail_save_measurement), color = ApexColors.OnGlow, fontWeight = FontWeight.Bold)
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
                            IconButton(onClick = { deleteId = m.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete measurement", tint = SecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}
