package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.toDisplayWeight

@Composable
fun PersonalRecordsScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val records by progressViewModel.personalRecords.collectAsStateWithLifecycle()
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(DarkBackground).padding(horizontal = 16.dp)) {
        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
            }
            Text("Personal records", color = PrimaryText, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 10.dp))
        }
        if (records.isEmpty()) {
            Text("Complete a workout to start building records.", color = SecondaryText, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(records, key = { it.record.id }) { entry ->
                    val record = entry.record
                    val label = when (record.type) {
                        "max_weight" -> "Heaviest set"
                        "estimated_1rm" -> "Estimated one-rep max"
                        "volume" -> "Session volume"
                        else -> record.type.replace('_', ' ')
                    }
                    val suffix = if (record.type == "volume") "$units × reps" else units
                    Card(colors = CardDefaults.cardColors(containerColor = DarkCardSurface)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(entry.displayName ?: record.exerciseId, color = PrimaryText, fontWeight = FontWeight.Bold)
                            Text("$label · ${record.value.toDisplayWeight(units)} $suffix", color = SecondaryText)
                            Text(record.date, color = SecondaryText)
                        }
                    }
                }
            }
        }
    }
}
