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
import com.apexfit.app.utils.MuscleVolumeComparisons
import com.apexfit.app.utils.MuscleVolumeComparison
import com.apexfit.app.utils.MuscleVolumeReport
import com.apexfit.app.utils.LoggedSetChange
import com.apexfit.app.utils.MuscleTargetHistory
import com.apexfit.app.utils.MuscleTargetRevision
import kotlinx.coroutines.launch
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
    val windows = MuscleVolumeComparisons.windows(period, anchor, today)
    val end = windows.selected.end
    val loadState by key(windows, retryAttempt) {
        produceState<MuscleVolumeLoadState>(initialValue = MuscleVolumeLoadState.Loading) {
            try {
                homeViewModel.observeMuscleHistory(windows.queryStart, end).collect { sessions ->
                    val report = withContext(Dispatchers.Default) {
                        MuscleVolumeComparisons.calculate(sessions, windows)
                    }
                    value = MuscleVolumeLoadState.Loaded(report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = MuscleVolumeLoadState.Failed
            }
        }
    }
    val targetState by key(retryAttempt) {
        produceState<TargetLoadState>(TargetLoadState.Loading) {
            try {
                homeViewModel.muscleTargetHistory.collect { value = TargetLoadState.Ready(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = TargetLoadState.Failed
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
        precomputedRows = (loadState as? MuscleVolumeLoadState.Loaded)?.report?.rows,
        precomputedComparison = (loadState as? MuscleVolumeLoadState.Loaded)?.report?.comparison,
        targets = (targetState as? TargetLoadState.Ready)?.history,
        targetLoadError = targetState is TargetLoadState.Failed,
        onScheduleTarget = homeViewModel::scheduleMuscleTarget,
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

private sealed interface TargetLoadState {
    data object Loading : TargetLoadState
    data class Ready(val history: List<MuscleTargetRevision>) : TargetLoadState
    data object Failed : TargetLoadState
}

private sealed interface MuscleVolumeLoadState {
    data object Loading : MuscleVolumeLoadState
    data class Loaded(val report: MuscleVolumeReport) : MuscleVolumeLoadState
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
    precomputedComparison: MuscleVolumeComparison? = null,
    onPeriodSelected: ((MuscleVolumePeriod) -> Unit)? = null,
    onNavigatePeriod: ((Int) -> Unit)? = null,
    targets: List<MuscleTargetRevision>? = emptyList(),
    targetLoadError: Boolean = false,
    onScheduleTarget: (suspend (String, Int?, LocalDate) -> Unit)? = null
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
    val previewReport = remember(sessions, period, periodAnchor, today) {
        sessions?.let { MuscleVolumeComparisons.calculate(it,
            MuscleVolumeComparisons.windows(period, periodAnchor, today)) }
    }
    val rows = precomputedRows ?: previewReport?.rows.orEmpty()
    val comparison = precomputedComparison ?: previewReport?.comparison
    val showBack = preferredBack ?: (rows.firstOrNull { it.directSets > 0 }?.muscle?.let(::showsOnBackOnly) == true)
    val chosen = rows.find { it.muscle == selected }
    val directCounts = remember(rows) { MuscleVolumeSummary.directCounts(rows) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val currentWeek = MuscleTargetHistory.week(today)
    val isCurrentWeek = period == MuscleVolumePeriod.WEEK && window.start == currentWeek
    val scope = rememberCoroutineScope()
    var editMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var targetInput by rememberSaveable { mutableStateOf("") }
    var editWeek by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTarget by remember { mutableStateOf(false) }
    var targetError by remember { mutableStateOf<String?>(null) }
    val editing = editMuscle
    val effective = editWeek?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (editing != null && effective != null && onScheduleTarget != null) {
        val valid = targetInput.toIntOrNull()?.let { it in 0..MuscleTargetHistory.MAX_TARGET } == true
        fun save(value: Int?) {
            if (savingTarget) return
            savingTarget = true
            targetError = null
            scope.launch {
                try {
                    onScheduleTarget(editing, value, effective)
                    editMuscle = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    targetError = "Could not save. Reopen the editor if the week has changed."
                } finally {
                    savingTarget = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!savingTarget) editMuscle = null },
            title = { Text("Weekly ${muscleName(editing).lowercase()} target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Effective ${effective.format(dateFormat)}. Previous and current weeks stay unchanged.")
                    OutlinedTextField(value = targetInput,
                        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) targetInput = it },
                        label = { Text("Direct working sets per week") },
                        singleLine = true, enabled = !savingTarget,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        isError = targetInput.isNotEmpty() && !valid)
                    Text("Choose your own target. This does not modify your workout plan.",
                        style = MaterialTheme.typography.bodySmall)
                    targetError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { save(targetInput.toIntOrNull()) },
                enabled = valid && !savingTarget) { Text(if (savingTarget) "Saving…" else "Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { save(null) }, enabled = !savingTarget) { Text("Clear target") }
                    TextButton(onClick = { editMuscle = null }, enabled = !savingTarget) { Text("Cancel") }
                }
            }
        )
    }
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
        if (!loadError && comparison != null) {
            item {
                VolumeComparisonHeader(comparison, selected, dateFormat)
            }
        }
        if (!loadError && (sessions != null || precomputedRows != null) && period == MuscleVolumePeriod.WEEK) {
            val muscle = selected
            if (targetLoadError) item {
                Text("Muscle targets couldn't load.", color = ApexColors.TextSecondary)
                TextButton(onClick = onRetry) { Text("Retry") }
            } else if (targets == null) item {
                Text("Loading weekly targets…", color = ApexColors.TextSecondary)
            } else if (muscle != null && muscle in MuscleTargetHistory.muscles) item {
                val revision = MuscleTargetHistory.resolve(targets, muscle, window.start)
                val nextWeek = MuscleTargetHistory.nextWeek(today)
                val pending = targets.firstOrNull { it.muscle == muscle && it.effectiveWeek == nextWeek }
                WeeklyTargetCard(chosen?.directSets ?: 0, revision, isCurrentWeek, pending, dateFormat)
                if (isCurrentWeek && onScheduleTarget != null) {
                    TextButton(onClick = {
                        editMuscle = muscle
                        editWeek = nextWeek.toString()
                        targetInput = (if (pending != null) pending.target else revision?.target)?.toString().orEmpty()
                        targetError = null
                    }) { Text("Edit next week's target") }
                }
            } else if (muscle == null) item {
                Text("Select a muscle to view or edit its weekly direct-set target.",
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                        Text("Periods without logged sets are not shown. Indirect counts use today's library metadata, not verified historical attribution. Zero indirect sets may mean missing attribution.",
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
            else -> {
                item {
                    if (rows.isEmpty()) Text("No completed working sets logged in this period.",
                        color = ApexColors.TextSecondary)
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
                val listedMuscles = (rows.map { it.muscle } +
                    comparison?.previousRows.orEmpty().map { it.muscle } + directCounts.keys).distinct()
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
                        Column(Modifier.weight(1f)) {
                            Text(muscleName(muscle))
                            if (period == MuscleVolumePeriod.WEEK && targets != null) {
                                MuscleTargetHistory.resolve(targets, muscle, window.start)?.target?.let { goal ->
                                    Text("$direct / $goal direct sets", style = MaterialTheme.typography.labelSmall,
                                        color = ApexColors.TextSecondary)
                                }
                            }
                            comparison?.forMuscle(muscle)?.direct?.takeIf { it.absolute != null }?.let {
                                Text("${signedChange(it.absolute!!)} vs prior",
                                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
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

@Composable
private fun VolumeComparisonHeader(
    comparison: MuscleVolumeComparison,
    selected: String?,
    format: DateTimeFormatter
) {
    val current = comparison.windows.current
    val prior = comparison.windows.previous
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Period comparison", style = MaterialTheme.typography.titleMedium)
        if (current == null || prior == null) {
            Text("Comparison begins after the first calendar day ends.", color = ApexColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("${current.start.format(format)} – ${current.end.format(format)} (${current.days}d)",
                style = MaterialTheme.typography.bodySmall)
            Text("vs ${prior.start.format(format)} – ${prior.end.format(format)} (${prior.days}d)",
                style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            Text(if (comparison.windows.matchedElapsed)
                "Same number of finished days. Today's sets remain in the main totals, not the comparison."
                else if (current.days != prior.days)
                    "Full calendar periods of different lengths; changes compare total logged sets."
                else "Full calendar periods.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (!comparison.currentHasLogs || !comparison.previousHasLogs) {
                Text(
                    when {
                        !comparison.currentHasLogs && !comparison.previousHasLogs -> "No qualifying work logged in either comparison period."
                        !comparison.previousHasLogs -> "No qualifying work logged in the prior period. Change unavailable."
                        else -> "No qualifying work logged in the selected comparison period. Change unavailable."
                    },
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
            if (selected != null) {
                val change = comparison.forMuscle(selected)
                VolumeComparisonLine("Direct sets", change.direct)
                VolumeComparisonLine("Indirect sets", change.indirect)
            } else if (comparison.currentHasLogs && comparison.previousHasLogs) {
                Text("Muscle list shows direct-set change for these dates. Select a muscle for direct and indirect details.",
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("Logged volume only; unlogged training is unknown.", color = ApexColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VolumeComparisonLine(label: String, change: LoggedSetChange) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text("${change.current ?: "—"} vs ${change.previous ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            val delta = change.absolute
            Text(if (delta == null) "Change unavailable" else {
                val pct = change.percent?.let { String.format(Locale.getDefault(), "%+.1f%%", it) }
                    ?: "percentage unavailable"
                "${signedChange(delta)} sets · $pct"
            }, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        }
    }
}

private fun signedChange(delta: Long): String = if (delta > 0) "+$delta" else delta.toString()

@Composable
private fun WeeklyTargetCard(
    actual: Int,
    revision: MuscleTargetRevision?,
    currentWeek: Boolean,
    pending: MuscleTargetRevision?,
    format: DateTimeFormatter
) {
    val target = revision?.target
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Weekly direct-set target", style = MaterialTheme.typography.titleMedium)
        Text(if (target == null) "No target set" else "$actual / $target logged sets",
            style = MaterialTheme.typography.titleLarge)
        if (target != null && target > 0) {
            LinearProgressIndicator(progress = { (actual.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = ApexColors.Primary,
                trackColor = ApexColors.BorderSubtle)
        }
        if (target != null) {
            Text("Effective ${revision?.effectiveWeek?.format(format) ?: "—"}. " +
                if (actual >= target) "Logged count meets target." else "${target - actual} more logged sets to target.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (currentWeek && pending != null) {
            Text(if (pending.target == null) "Target clears on ${pending.effectiveWeek.format(format)}"
                else "Next target: ${pending.target} from ${pending.effectiveWeek.format(format)}",
                color = ApexColors.Primary, style = MaterialTheme.typography.bodyMedium)
        }
        Text("User-defined tracking goal, not a recovery or muscle-growth prescription.",
            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
