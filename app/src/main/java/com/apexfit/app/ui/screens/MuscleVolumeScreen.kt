package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.HomeViewModel
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.components.ApexAnatomy
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.MuscleVolumePeriod
import com.apexfit.app.utils.MuscleVolumeSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun MuscleVolumeScreen(homeViewModel: HomeViewModel, onBack: () -> Unit) {
    val sessions by homeViewModel.completedSessions.collectAsStateWithLifecycle(initialValue = null)
    val date by homeViewModel.todayDate.collectAsStateWithLifecycle()
    MuscleVolumeContent(sessions, LocalDate.parse(date), onBack)
}

@Composable
internal fun MuscleVolumeContent(
    sessions: List<RichTrainingSession>?,
    today: LocalDate,
    onBack: () -> Unit
) {
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var showBack by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    val period = MuscleVolumePeriod.entries[periodIndex]
    val rows = remember(sessions, today, period) {
        sessions?.let { MuscleVolumeSummary.calculate(it, today, period) }.orEmpty()
    }
    val chosen = rows.find { it.muscle == selected }
    val directCounts = remember(rows) { rows.associate { it.muscle to it.directSets } }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val window = period.window(today)

    LazyColumn(
        Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            TextButton(onClick = { if (selected == null) onBack() else selected = null }) {
                Text(if (selected == null) "Back to Progress" else "Back to Volume")
            }
            Text(selected?.let(::muscleName) ?: "Muscle volume", style = MaterialTheme.typography.headlineMedium)
            Text("Completed direct working sets", color = ApexColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp))
            ApexSegmentedControl(
                labels = listOf("Week", "Month"), selectedIndex = periodIndex,
                onSelect = { periodIndex = it }
            )
            Text("${window.start.format(dateFormat)} – ${window.endInclusive.format(dateFormat)}",
                color = ApexColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
        }
        when {
            sessions == null -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
            selected != null -> {
                item {
                    Text("${chosen?.directSets ?: 0} direct sets · ${chosen?.secondarySets ?: 0} secondary exposure",
                        color = ApexColors.TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (chosen == null) item { Text("No logged sets for this muscle in this period.") }
                else items(chosen.exercises, key = null) { exercise ->
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text("${exercise.directSets} direct · ${exercise.secondarySets} secondary",
                            color = ApexColors.TextSecondary)
                    }
                }
            }
            rows.isEmpty() -> item {
                Text("No completed working sets logged in this period.", color = ApexColors.TextSecondary)
            }
            else -> {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (showBack) "Back" else "Front", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showBack = !showBack }) {
                            Text(if (showBack) "Show front" else "Show back")
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ApexAnatomy(showBack, Modifier.width(138.dp), directSets = directCounts,
                            onMuscleSelected = { selected = it })
                    }
                    Text("Muscles by direct set count", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                items(rows, key = { it.muscle }) { row ->
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { selected = row.muscle }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(muscleName(row.muscle), modifier = Modifier.weight(1f))
                        Text("${row.directSets} direct",
                            color = if (row.directSets > 0) ApexColors.Primary else ApexColors.TextSecondary)
                        if (row.secondarySets > 0) Text("${row.secondarySets} secondary", color = ApexColors.TextSecondary)
                    }
                }
            }
        }
    }
}

internal fun muscleName(key: String): String = key.replace('_', ' ').replaceFirstChar {
    it.titlecase(Locale.getDefault())
}
