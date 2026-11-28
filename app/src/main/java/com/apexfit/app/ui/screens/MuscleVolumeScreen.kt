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
import com.apexfit.app.utils.MuscleVolumeDay
import com.apexfit.app.utils.MuscleVolumeRow
import com.apexfit.app.utils.MuscleVolumePeriod
import com.apexfit.app.utils.MuscleVolumeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun MuscleVolumeScreen(homeViewModel: HomeViewModel, onBack: () -> Unit) {
    var retryAttempt by remember { mutableIntStateOf(0) }
    val date by homeViewModel.todayDate.collectAsStateWithLifecycle()
    val today = LocalDate.parse(date)
    var periodName by rememberSaveable { mutableStateOf(MuscleVolumePeriod.WEEK.name) }
    var historicalAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    val period = MuscleVolumePeriod.valueOf(periodName)
    val anchor = historicalAnchor?.let(LocalDate::parse)?.coerceAtMost(today) ?: today
    val window = period.window(anchor)
    val end = minOf(window.endInclusive, today)
    val loadState by key(window.start, end, retryAttempt) {
        produceState<MuscleVolumeLoadState>(initialValue = MuscleVolumeLoadState.Loading) {
            try {
                homeViewModel.observeMuscleHistory(window.start, end).collect { sessions ->
                    val rows = withContext(Dispatchers.Default) {
                        MuscleVolumeSummary.calculateWindow(sessions, window.start, end)
                    }
                    value = MuscleVolumeLoadState.Loaded(rows)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = MuscleVolumeLoadState.Failed
            }
        }
    }
    MuscleVolumeContent(
        sessions = null,
        today = today,
        onBack = onBack,
        loadError = loadState is MuscleVolumeLoadState.Failed,
        onRetry = { retryAttempt++ },
        selectedPeriod = period,
        periodAnchor = anchor,
        precomputedRows = (loadState as? MuscleVolumeLoadState.Loaded)?.rows,
        onPeriodSelected = { chosen ->
            periodName = chosen.name
            historicalAnchor = null
        },
        onNavigatePeriod = { amount ->
            val shifted = period.shift(window.start, amount.toLong())
            historicalAnchor = if (period.window(shifted).start >= period.window(today).start) null
                else shifted.toString()
        }
    )
}

private sealed interface MuscleVolumeLoadState {
    data object Loading : MuscleVolumeLoadState
    data class Loaded(val rows: List<MuscleVolumeRow>) : MuscleVolumeLoadState
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
    initialPeriod: MuscleVolumePeriod = MuscleVolumePeriod.WEEK,
    selectedPeriod: MuscleVolumePeriod? = null,
    periodAnchor: LocalDate = today,
    precomputedRows: List<MuscleVolumeRow>? = null,
    onPeriodSelected: ((MuscleVolumePeriod) -> Unit)? = null,
    onNavigatePeriod: ((Int) -> Unit)? = null
) {
    var periodIndex by rememberSaveable(initialPeriod) {
        mutableIntStateOf(MuscleVolumePeriod.entries.indexOf(initialPeriod).coerceAtLeast(0))
    }
    var preferredBack by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var selected by rememberSaveable(initialSelectedMuscle) { mutableStateOf(initialSelectedMuscle) }
    BackHandler(enabled = selected != null) { selected = null }
    val period = selectedPeriod ?: MuscleVolumePeriod.entries[periodIndex]
    val window = period.window(periodAnchor)
    val end = minOf(window.endInclusive, today)
    val rows = remember(sessions, precomputedRows, window.start, end) {
        precomputedRows ?: sessions?.let {
            MuscleVolumeSummary.calculateWindow(it, window.start, end)
        }.orEmpty()
    }
    val showBack = preferredBack ?: (rows.firstOrNull { it.directSets > 0 }?.muscle?.let(::showsOnBackOnly) == true)
    val chosen = rows.find { it.muscle == selected }
    val directCounts = remember(rows) { MuscleVolumeSummary.directCounts(rows) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
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
                labels = listOf("Week", "Month", "Year"), selectedIndex = period.ordinal,
                onSelect = {
                    periodIndex = it
                    onPeriodSelected?.invoke(MuscleVolumePeriod.entries[it])
                    preferredBack = null
                }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                if (onNavigatePeriod != null) TextButton(onClick = { onNavigatePeriod(-1) }) { Text("Previous") }
                Text("${window.start.format(dateFormat)} – ${end.format(dateFormat)}",
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    color = ApexColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall)
                if (onNavigatePeriod != null) TextButton(onClick = { onNavigatePeriod(1) },
                    enabled = window.endInclusive < today) { Text("Next") }
            }
        }
        when {
            loadError -> item {
                Text("Training volume couldn't load.", color = ApexColors.TextSecondary)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            sessions == null && precomputedRows == null -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Loading training volume…", color = ApexColors.TextSecondary)
                }
            }
            selected != null -> {
                item {
                    Text("${chosen?.directSets ?: 0} direct sets · ${chosen?.secondarySets ?: 0} indirect sets",
                        color = ApexColors.TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (chosen == null) item { Text("No logged sets for this muscle in this period.") }
                else {
                    item {
                        Text("Training frequency", style = MaterialTheme.typography.titleMedium)
                        Text("${chosen.directTrainingDays} direct days · ${chosen.exposureDays} exposure days",
                            style = MaterialTheme.typography.bodyLarge, color = ApexColors.Primary)
                        Text("Average gap: ${gapLabel(chosen.directMeanGapDays)} direct · ${gapLabel(chosen.exposureMeanGapDays)} all",
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("Exposure includes direct or indirect work on distinct dates. Gaps need at least two dates and do not measure recovery.",
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp))
                        Text("Fractional set estimate", style = MaterialTheme.typography.titleMedium)
                        Text(String.format(Locale.getDefault(), "%.1f", chosen.fractionalSets),
                            style = MaterialTheme.typography.headlineMedium, color = ApexColors.Primary)
                        Text("Direct + half of indirect sets. A research-based volume proxy, not measured muscle growth.",
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp))
                        Text(if (period == MuscleVolumePeriod.YEAR) "Sets by logged month" else "Sets by logged date",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Periods without logged sets are not shown. Indirect attribution uses current library metadata.",
                            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    val buckets = if (period == MuscleVolumePeriod.YEAR) {
                        chosen.daily.groupBy { it.date.withDayOfMonth(1) }.map { (month, days) ->
                            MuscleVolumeDay(month, days.sumOf { it.directSets }, days.sumOf { it.secondarySets })
                        }
                    } else chosen.daily
                    val maxSets = buckets.maxOfOrNull { it.directSets + it.secondarySets }?.coerceAtLeast(1) ?: 1
                    items(buckets, key = { "date:" + it.date }) { day ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (period == MuscleVolumePeriod.YEAR)
                                    day.date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                                    else day.date.format(dateFormat), style = MaterialTheme.typography.bodySmall)
                                Text("${day.directSets} direct · ${day.secondarySets} indirect",
                                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp)
                                .background(ApexColors.BorderSubtle)) {
                                if (day.directSets > 0) Box(Modifier.fillMaxHeight()
                                    .weight(day.directSets.toFloat()).background(ApexColors.Primary))
                                if (day.secondarySets > 0) Box(Modifier.fillMaxHeight()
                                    .weight(day.secondarySets.toFloat()).background(ApexColors.TextSecondary))
                                val unused = maxSets - day.directSets - day.secondarySets
                                if (unused > 0) Spacer(Modifier.weight(unused.toFloat()))
                            }
                        }
                    }
                    item {
                        Text("By exercise", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    }
                    items(chosen.exercises, key = { "exercise:" + it.key }) { exercise ->
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text("${exercise.directSets} direct · ${exercise.secondarySets} indirect",
                            color = ApexColors.TextSecondary)
                    }
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
                        ApexAnatomy(showBack, Modifier.widthIn(max = 320.dp).fillMaxWidth(), directSets = directCounts,
                            showVolume = true, onMuscleSelected = { selected = it })
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        com.apexfit.app.utils.DirectSetBand.entries.forEach { band ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(12.dp).background(com.apexfit.app.ui.components.directSetBandColor(band)))
                                Text(band.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("Colors show logged direct sets for the selected period, including saved work in partial workouts. Zero means no direct sets logged; not inactivity or recovery.",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Muscles by direct set count", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp))
                }
                val listedMuscles = (rows.map { it.muscle } + directCounts.keys).distinct()
                items(listedMuscles, key = { it }) { muscle ->
                    val row = rows.firstOrNull { it.muscle == muscle }
                    val direct = row?.directSets ?: 0
                    val indirect = row?.secondarySets ?: 0
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { selected = muscle }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(muscleName(muscle), modifier = Modifier.weight(1f))
                        Text("$direct direct",
                            color = if (direct > 0) ApexColors.Primary else ApexColors.TextSecondary)
                        if (indirect > 0) Text("$indirect indirect", color = ApexColors.TextSecondary)
                    }
                }
            }
        }
    }
}

internal fun muscleName(key: String): String = key.replace('_', ' ').replaceFirstChar {
    it.titlecase(Locale.getDefault())
}

private fun gapLabel(days: Double?): String = days?.let { String.format(Locale.getDefault(), "%.1f d", it) } ?: "—"
