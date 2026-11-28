package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.StrengthExerciseOption
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.StrengthObservation
import com.apexfit.app.utils.RecordedLoadBasis
import com.apexfit.app.utils.LoadConfirmations
import com.apexfit.app.utils.SetLoadConfirmation
import com.apexfit.app.utils.RepRecordProjection
import com.apexfit.app.utils.RepRecordEvent
import com.apexfit.app.utils.EstimatedStrengthPoint
import com.apexfit.app.utils.EstimatedStrength
import kotlinx.coroutines.launch
import com.apexfit.app.utils.StrengthSessionPoint
import com.apexfit.app.utils.DateTimeUtils
import com.apexfit.app.utils.toDisplayWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private sealed interface StrengthLoad<out T> {
    data object Loading : StrengthLoad<Nothing>
    data object Failed : StrengthLoad<Nothing>
    data class Ready<T>(val value: T) : StrengthLoad<T>
}

@Composable
internal fun ExerciseStrengthScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val date by remember { DateTimeUtils.todayDateFlow() }
        .collectAsStateWithLifecycle(initialValue = DateTimeUtils.todayDateString())
    val today = LocalDate.parse(date)
    val units by progressViewModel.units.collectAsStateWithLifecycle()
    val exercises by key(today, retry) {
        produceState<StrengthLoad<List<StrengthExerciseOption>>>(StrengthLoad.Loading) {
            try {
                progressViewModel.observeStrengthExercises(today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val options = (exercises as? StrengthLoad.Ready)?.value
    val selected = options?.firstOrNull { it.exerciseId == selectedId }
    BackHandler(enabled = selectedId != null) { selectedId = null }
    val start = when (rangeIndex) {
        0 -> today.minusDays(89)
        1 -> today.minusDays(364)
        else -> LocalDate.of(1, 1, 1)
    }
    val history by key(selectedId, start, today, retry) {
        produceState<StrengthLoad<List<StrengthSessionPoint>>>(StrengthLoad.Loading) {
            val id = selectedId ?: return@produceState
            try {
                progressViewModel.observeStrengthHistory(id, start, today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val records by key(selectedId, today, retry) {
        produceState<StrengthLoad<RepRecordProjection>>(StrengthLoad.Loading) {
            val id = selectedId ?: return@produceState
            try {
                progressViewModel.observeRepRecords(id, today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val confirmations by key(retry) {
        produceState<StrengthLoad<Map<Long, SetLoadConfirmation>>>(StrengthLoad.Loading) {
            try {
                progressViewModel.setLoadConfirmations.collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val estimates by key(selectedId, start, today, retry) {
        produceState<StrengthLoad<List<EstimatedStrengthPoint>>>(StrengthLoad.Loading) {
            val id = selectedId ?: return@produceState
            try {
                progressViewModel.observeEstimatedStrength(id, start, today).collect { value = StrengthLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = StrengthLoad.Failed }
        }
    }
    val confirmed = (confirmations as? StrengthLoad.Ready)?.value
    var editing by remember { mutableStateOf<StrengthObservation?>(null) }
    var basis by remember { mutableStateOf(RecordedLoadBasis.TOTAL) }
    var setup by remember { mutableStateOf("") }
    var repLimit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val edit = editing
    if (edit != null) {
        fun save(choice: RecordedLoadBasis?) {
            if (saving) return
            saving = true
            saveError = false
            scope.launch {
                try {
                    progressViewModel.confirmSetLoad(edit, choice, setup, repLimit)
                    editing = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { saveError = true }
                finally { saving = false }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = { Text("Confirm recorded load") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(observedPair(edit, units))
                    Text("Confirm only if you remember the exact setup. Assisted or bodyweight-only sets must stay unconfirmed.")
                    RecordedLoadBasis.entries.forEach { choice ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !saving) { basis = choice },
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == basis, onClick = { basis = choice }, enabled = !saving)
                            Text(choice.label)
                        }
                    }
                    OutlinedTextField(setup, { if (it.length <= 48) setup = it },
                        label = { Text("Setup name (same for comparable sets)") },
                        placeholder = { Text("e.g. Gym A flat barbell") }, singleLine = true, enabled = !saving)
                    if (basis == RecordedLoadBasis.TOTAL && edit.reps in 2..5) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = repLimit, onCheckedChange = { repLimit = it }, enabled = !saving)
                            Text("Unassisted free-weight set, full uninterrupted reps, reached my rep limit (no reps left).")
                        }
                        Text("Optional: only confirm if you remember. Never train to failure just to unlock estimates.",
                            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    }
                    if (saveError) Text("Could not save. Try again.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { save(basis) },
                enabled = !saving && LoadConfirmations.validSetup(setup)) { Text("Confirm") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { save(null) }, enabled = !saving) { Text("Clear") }
                    TextButton(onClick = { editing = null }, enabled = !saving) { Text("Cancel") }
                }
            }
        )
    }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { if (selectedId == null) onBack() else selectedId = null }) {
                Text(if (selectedId == null) "Back to Progress" else "Back to exercises")
            }
            Text(selected?.name ?: "Exercise strength", style = MaterialTheme.typography.headlineMedium)
        }
        if (exercises is StrengthLoad.Failed) item {
            Text("Exercise history couldn't load.")
            TextButton(onClick = { retry++ }) { Text("Try again") }
        } else if (options == null) item {
            CircularProgressIndicator()
        } else if (selectedId == null) {
            item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Find a logged exercise") })
            }
            val filtered = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) item { Text("No matching logged exercises.", color = ApexColors.TextSecondary) }
            items(filtered, key = { it.exerciseId }) { exercise ->
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                    selectedId = exercise.exerciseId
                }.padding(vertical = 8.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(exercise.category, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    HorizontalDivider(color = ApexColors.BorderSubtle, modifier = Modifier.padding(top = 10.dp))
                }
            }
        } else if (selected == null) {
            item { Text("No saved qualifying sets remain for this exercise.") }
        } else {
            item {
                Text(selected.category + " · " + selected.equipment, color = ApexColors.TextSecondary)
                ApexSegmentedControl(labels = listOf("90 days", "1 year", "All"), selectedIndex = rangeIndex,
                    onSelect = { rangeIndex = it })
                Text("Recorded loads only. Historical load convention and resistance are not verified.",
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            when (val recordData = records) {
                StrengthLoad.Loading -> item { Text("Loading rep records…", color = ApexColors.TextSecondary) }
                StrengthLoad.Failed -> item {
                    Text("Rep records couldn't load.")
                    TextButton(onClick = { retry++ }) { Text("Retry") }
                }
                is StrengthLoad.Ready -> item { RepRecordsPanel(recordData.value, units) }
            }
            when (val estimateData = estimates) {
                StrengthLoad.Loading -> item { Text("Loading estimated strength…", color = ApexColors.TextSecondary) }
                StrengthLoad.Failed -> item {
                    Text("Estimated strength couldn't load.")
                    TextButton(onClick = { retry++ }) { Text("Retry") }
                }
                is StrengthLoad.Ready -> item { EstimatedStrengthPanel(estimateData.value, units) }
            }
            if (confirmations is StrengthLoad.Failed) item {
                Text("Load confirmations couldn't load.")
                TextButton(onClick = { retry++ }) { Text("Retry") }
            }
            when (val data = history) {
                StrengthLoad.Loading -> item { CircularProgressIndicator() }
                StrengthLoad.Failed -> item {
                    Text("Strength history couldn't load.")
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                is StrengthLoad.Ready -> {
                    val points = data.value
                    if (points.isEmpty()) item { Text("No qualifying sets in this period.") }
                    else {
                        item { StrengthHistorySummary(points, units) }
                        items(points.asReversed(), key = { it.sessionId }) { point ->
                            StrengthSessionRow(point, units, confirmed) { observation ->
                                val prior = confirmed?.let { LoadConfirmations.match(observation, it) }
                                basis = prior?.basis ?: RecordedLoadBasis.TOTAL
                                setup = prior?.setup.orEmpty()
                                repLimit = prior?.freeWeightRepLimit == true
                                saveError = false
                                editing = observation
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun observedPair(observation: StrengthObservation?, units: String): String {
    if (observation == null) return "—"
    val load = observation.loadKg?.let {
        java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
        }.format(it.toDisplayWeight(units))
    }
    return if (load == null) "${observation.reps} reps · load unavailable"
        else "$load $units × ${observation.reps}"
}

@Composable
internal fun StrengthHistorySummary(points: List<StrengthSessionPoint>, units: String) {
    val heavy = remember(points) { points.mapNotNull { it.heaviestRecorded }
        .maxWithOrNull(compareBy<StrengthObservation> { it.loadKg }.thenBy { it.reps }) }
    val reps = remember(points) { points.mapNotNull { it.mostReps }
        .maxWithOrNull(compareBy<StrengthObservation> { it.reps }.thenBy { it.loadKg ?: -1.0 }) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Highest recorded load", style = MaterialTheme.typography.titleMedium)
        Text(observedPair(heavy, units), style = MaterialTheme.typography.headlineSmall, color = ApexColors.Primary)
        Text("Most reps in one set: ${observedPair(reps, units)}", color = ApexColors.TextSecondary)
        Text("${points.size} saved sessions · ${points.sumOf { it.workingSets }} working sets",
            color = ApexColors.TextSecondary)
        var metric by rememberSaveable { mutableIntStateOf(0) }
        ApexSegmentedControl(labels = listOf("Load", "Reps"), selectedIndex = metric, onSelect = { metric = it })
        Text(if (metric == 0) "Highest recorded load by date" else "Most reps in one set by date",
            style = MaterialTheme.typography.titleMedium)
        val daily = remember(points, metric) {
            points.groupBy { it.date }.toSortedMap().mapNotNull { (date, sameDay) ->
                val value = if (metric == 0) sameDay.mapNotNull { it.heaviestRecorded?.loadKg }.maxOrNull()
                    else sameDay.mapNotNull { it.mostReps?.reps?.toDouble() }.maxOrNull()
                value?.let { date to it }
            }
        }
        if (daily.size < 2) Text("Log on two dates to see a trend.", color = ApexColors.TextSecondary)
        else {
            val min = daily.minOf { it.second }
            val max = daily.maxOf { it.second }
            val span = (max - min).coerceAtLeast(0.1)
            val days = ChronoUnit.DAYS.between(daily.first().first, daily.last().first).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(112.dp).semantics {
                contentDescription = if (metric == 0)
                    "Recorded load by date. First ${daily.first().second.toDisplayWeight(units)} $units, last ${daily.last().second.toDisplayWeight(units)} $units. Full session values below."
                else "Repetitions by date. First ${daily.first().second.toInt()}, last ${daily.last().second.toInt()}. Full session values below."
            }) {
                val plotted = daily.map { (date, load) ->
                    Offset(size.width * (0.03f + 0.94f * ChronoUnit.DAYS.between(daily.first().first, date).toFloat() / days.toFloat()),
                        size.height * (0.85f - 0.70f * ((load - min) / span).toFloat()))
                }
                plotted.zipWithNext().forEach { (a, b) -> drawLine(ApexColors.Primary, a, b, strokeWidth = 2.dp.toPx()) }
                plotted.forEach { drawCircle(ApexColors.Primary, radius = 3.dp.toPx(), center = it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(DateTimeUtils.formatDateForDisplay(daily.first().first.toString()), style = MaterialTheme.typography.labelSmall)
                Text(DateTimeUtils.formatDateForDisplay(daily.last().first.toString()), style = MaterialTheme.typography.labelSmall)
            }
            Text("Compare like-for-like setups only. Higher recorded load does not necessarily mean greater strength.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StrengthSessionRow(
    point: StrengthSessionPoint, units: String,
    confirmations: Map<Long, SetLoadConfirmation>?,
    onConfirm: (StrengthObservation) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text(DateTimeUtils.formatDateForDisplay(point.date.toString()) + if (point.partial) " · Partial workout" else "",
            style = MaterialTheme.typography.titleMedium)
        Text("Highest load: ${observedPair(point.heaviestRecorded, units)}", color = ApexColors.TextSecondary)
        Text("Most reps: ${observedPair(point.mostReps, units)}", color = ApexColors.TextSecondary)
        Text("${point.workingSets} sets · ${point.totalReps} reps", style = MaterialTheme.typography.bodySmall)
        point.observations.forEach { observation ->
            val confirmation = confirmations?.let { LoadConfirmations.match(observation, it) }
            val canConfirm = confirmations != null && observation.fingerprint != null &&
                observation.setId > 0 && (observation.loadKg ?: 0.0) > 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(observedPair(observation, units), style = MaterialTheme.typography.bodySmall)
                    if (confirmation != null) Text("${confirmation.basis.label} · ${confirmation.setup}",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (canConfirm) TextButton(onClick = { onConfirm(observation) }) {
                    Text(if (confirmation == null) "Confirm setup" else "Edit setup override")
                }
            }
        }
    }
}

@Composable
internal fun RepRecordsPanel(projection: RepRecordProjection, units: String) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var source by remember { mutableStateOf<RepRecordEvent?>(null) }
    source?.let { event ->
        AlertDialog(onDismissRequest = { source = null }, title = { Text("Source set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(observedPair(StrengthObservation(event.setId, event.loadKg, event.key.reps), units))
                    Text(DateTimeUtils.formatDateForDisplay(event.date.toString()))
                    Text("${event.key.basis.label} · ${event.key.setup}")
                    Text("Saved session: ${event.sessionId}")
                    Text("Set ID: ${event.setId}")
                    if (event.chronologyLimited) Text("Exact within-day order is unavailable.")
                }
            }, confirmButton = { TextButton(onClick = { source = null }) { Text("Close") } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Exact-rep records · all time", style = MaterialTheme.typography.titleMedium)
        Text("Only user-confirmed matching setups qualify. Stack settings are not measured external force; one rep is not necessarily a maximal test.",
            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (projection.best.isEmpty()) Text("Confirm a saved set's load setup below to start building comparable records.",
            color = ApexColors.TextSecondary)
        else {
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "Show current records" else "Show record history")
            }
            val visible = if (showHistory) projection.events.sortedByDescending { it.date } else projection.best
            visible.forEach { event ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { source = event },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(observedPair(StrengthObservation(event.setId, event.loadKg, event.key.reps), units))
                        Text("${event.key.basis.label} · ${event.key.setup}", color = ApexColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(DateTimeUtils.formatDateForDisplay(event.date.toString()), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


@Composable
internal fun EstimatedStrengthPanel(points: List<EstimatedStrengthPoint>, units: String) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var selectedSetup by rememberSaveable { mutableStateOf<String?>(null) }
    val setups = remember(points) { points.map { it.setup }.distinct().sorted() }
    val activeSetup = selectedSetup?.takeIf { it in setups } ?: setups.firstOrNull()
    val series = remember(points, activeSetup) { points.filter { it.setup == activeSetup } }
    val daily = remember(series) {
        series.groupBy { it.date }.toSortedMap().values.map { day ->
            day.maxWithOrNull(compareBy<EstimatedStrengthPoint> { it.estimated1RmKg }.thenByDescending { it.setId })!!
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Estimated strength", style = MaterialTheme.typography.titleMedium)
        Text("Epley estimate, not a measured 1RM. Only confirmed unassisted total free-weight sets of 2–5 reps at the rep limit qualify.",
            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (points.isEmpty()) {
            Text("No eligible confirmed sets in this period.", color = ApexColors.TextSecondary)
        } else {
            if (setups.size > 1) {
                Text("Compare only within the same setup.", style = MaterialTheme.typography.bodySmall)
                setups.forEach { setup ->
                    Row(Modifier.fillMaxWidth().clickable { selectedSetup = setup }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = setup == activeSetup, onClick = { selectedSetup = setup })
                        Text(setup)
                    }
                }
            } else Text(activeSetup.orEmpty(), color = ApexColors.TextSecondary)
            val latest = daily.lastOrNull()
            if (latest != null) {
                Text("Latest dated estimate", style = MaterialTheme.typography.bodySmall)
                Text(String.format(Locale.getDefault(), "%.1f %s", latest.estimated1RmKg.toDisplayWeight(units), units),
                    style = MaterialTheme.typography.headlineSmall, color = ApexColors.Primary)
                Text("Relative strength: " + (latest.relativeStrength?.let { String.format(Locale.getDefault(), "%.2f× bodyweight", it) }
                    ?: "Unavailable — no recent prior weigh-in"))
                latest.bodyWeight?.let { weight ->
                    Text("Bodyweight: " + String.format(Locale.getDefault(), "%.1f %s", weight.kg.toDisplayWeight(units), units) +
                        " · " + DateTimeUtils.formatDateForDisplay(weight.date.toString()) + " · ${weight.ageDays} day(s) earlier",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Historical daily bodyweight averages must precede the workout by 1–7 days. Same-day ordering is unknown. No current-weight substitution.",
                style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            if (daily.size >= 2) {
                val low = daily.minOf { it.estimated1RmKg }
                val span = (daily.maxOf { it.estimated1RmKg } - low).coerceAtLeast(0.1)
                val elapsed = ChronoUnit.DAYS.between(daily.first().date, daily.last().date).coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(90.dp).semantics {
                    contentDescription = "Highest daily estimated one rep max for the selected setup, ${daily.size} dates. Source sets below."
                }) {
                    val plotted = daily.map { p ->
                        Offset(size.width * (0.03f + 0.94f * ChronoUnit.DAYS.between(daily.first().date, p.date).toFloat() / elapsed.toFloat()),
                            size.height * (0.85f - 0.70f * ((p.estimated1RmKg - low) / span).toFloat()))
                    }
                    plotted.zipWithNext().forEach { (a, b) -> drawLine(ApexColors.Primary, a, b, strokeWidth = 2.dp.toPx()) }
                    plotted.forEach { drawCircle(ApexColors.Primary, radius = 3.dp.toPx(), center = it) }
                }
            }
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show recent estimates" else "Show all source sets") }
            val visible = series.asReversed().let { if (showAll) it else it.take(3) }
            visible.forEach { point ->
                HorizontalDivider(color = ApexColors.BorderSubtle)
                Text(DateTimeUtils.formatDateForDisplay(point.date.toString()) + " · " +
                    observedPair(StrengthObservation(point.setId, point.loadKg, point.reps), units))
                Text(String.format(Locale.getDefault(), "Estimate %.1f %s", point.estimated1RmKg.toDisplayWeight(units), units) +
                    (point.relativeStrength?.let { String.format(Locale.getDefault(), " · %.2f× bodyweight", it) } ?: " · relative unavailable"),
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                Text("Source set ${point.setId}", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextSecondary)
            }
        }
    }
}
