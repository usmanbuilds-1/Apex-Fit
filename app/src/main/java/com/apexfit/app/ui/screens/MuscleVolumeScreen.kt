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
import com.apexfit.app.ui.components.showsOnBackOnly
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.MuscleVolumePeriod
import com.apexfit.app.utils.MuscleVolumeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun MuscleVolumeScreen(homeViewModel: HomeViewModel, onBack: () -> Unit) {
    var retryAttempt by remember { mutableIntStateOf(0) }
    val loadState by produceState<MuscleVolumeLoadState>(
        initialValue = MuscleVolumeLoadState.Loading,
        key1 = homeViewModel,
        key2 = retryAttempt
    ) {
        try {
            homeViewModel.completedSessions.collect { sessions ->
                value = MuscleVolumeLoadState.Loaded(sessions)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = MuscleVolumeLoadState.Failed
        }
    }
    val date by homeViewModel.todayDate.collectAsStateWithLifecycle()
    MuscleVolumeContent(
        sessions = (loadState as? MuscleVolumeLoadState.Loaded)?.sessions,
        today = LocalDate.parse(date),
        onBack = onBack,
        loadError = loadState is MuscleVolumeLoadState.Failed,
        onRetry = { retryAttempt++ }
    )
}

private sealed interface MuscleVolumeLoadState {
    data object Loading : MuscleVolumeLoadState
    data class Loaded(val sessions: List<RichTrainingSession>) : MuscleVolumeLoadState
    data object Failed : MuscleVolumeLoadState
}

@Composable
internal fun MuscleVolumeContent(
    sessions: List<RichTrainingSession>?,
    today: LocalDate,
    onBack: () -> Unit,
    loadError: Boolean = false,
    onRetry: () -> Unit = {},
    initialSelectedMuscle: String? = null,
    initialPeriod: MuscleVolumePeriod = MuscleVolumePeriod.WEEK
) {
    var periodIndex by rememberSaveable(initialPeriod) {
        mutableIntStateOf(MuscleVolumePeriod.entries.indexOf(initialPeriod).coerceAtLeast(0))
    }
    var preferredBack by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var selected by rememberSaveable(initialSelectedMuscle) { mutableStateOf(initialSelectedMuscle) }
    BackHandler(enabled = selected != null) { selected = null }
    val period = MuscleVolumePeriod.entries[periodIndex]
    val rows = remember(sessions, today, period) {
        sessions?.let { MuscleVolumeSummary.calculate(it, today, period) }.orEmpty()
    }
    val showBack = preferredBack ?: (rows.firstOrNull { it.directSets > 0 }?.muscle?.let(::showsOnBackOnly) == true)
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
                onSelect = {
                    periodIndex = it
                    preferredBack = null
                }
            )
            Text("${window.start.format(dateFormat)} – ${window.endInclusive.format(dateFormat)}",
                color = ApexColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
        }
        when {
            loadError -> item {
                Text("Training volume couldn't load.", color = ApexColors.TextSecondary)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            sessions == null -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Loading training volume…", color = ApexColors.TextSecondary)
                }
            }
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
                        TextButton(onClick = { preferredBack = !showBack }) {
                            Text(if (showBack) "Show front" else "Show back")
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ApexAnatomy(showBack, Modifier.width(240.dp), directSets = directCounts,
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
