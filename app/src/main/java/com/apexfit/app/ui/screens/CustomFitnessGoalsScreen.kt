package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.data.StrengthExerciseOption
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.*
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

private sealed interface GoalLoad {
    data object Loading : GoalLoad
    data object Failed : GoalLoad
    data class Ready(val values: List<FitnessGoalProgress>) : GoalLoad
}

@Composable
internal fun CustomFitnessGoalsScreen(progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    val date by remember { DateTimeUtils.todayDateFlow() }
        .collectAsStateWithLifecycle(initialValue = DateTimeUtils.todayDateString())
    val today = LocalDate.parse(date)
    var retry by remember { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var action by remember { mutableStateOf<Pair<FitnessGoal, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state by key(today, retry) {
        produceState<GoalLoad>(GoalLoad.Loading) {
            try {
                progressViewModel.observeCustomGoals(today).collect { value = GoalLoad.Ready(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { value = GoalLoad.Failed }
        }
    }
    var exercises by remember(today, retry) { mutableStateOf<List<StrengthExerciseOption>?>(null) }
    var exerciseError by remember(today, retry) { mutableStateOf(false) }
    LaunchedEffect(today, retry) {
        try { progressViewModel.observeStrengthExercises(today).collect { exercises = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { exerciseError = true }
    }
    if (creating) GoalEditor(today, exercises, exerciseError, busy, error, onDismiss = {
        if (!busy) creating = false
    }, onSave = { spec ->
        if (!busy) {
            busy = true; error = false
            scope.launch {
                try { progressViewModel.createCustomGoal(spec); creating = false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    })
    action?.let { (goal, delete) ->
        AlertDialog(onDismissRequest = { if (!busy) action = null },
            title = { Text(if (delete) "Delete goal?" else "Archive goal?") },
            text = { Text(if (delete) "Only this goal definition will be removed. Your workout and weight logs stay intact."
                else "The goal's target remains in history. Future dates will not count.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; error = false
                scope.launch {
                    try {
                        if (delete) progressViewModel.deleteCustomGoal(goal.id) else progressViewModel.archiveCustomGoal(goal.id)
                        action = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }) { Text(if (delete) "Delete" else "Archive") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { action = null }) { Text("Cancel") } })
    }
    val names = exercises.orEmpty().associate { it.exerciseId to it.name }
    LazyColumn(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to Progress") }
            Text("Custom fitness goals", style = MaterialTheme.typography.headlineMedium)
            Text("Personal tracking milestones. No automatic calorie or workout changes.", color = ApexColors.TextSecondary)
            TextButton(enabled = !busy, onClick = { error = false; creating = true }) { Text("Create goal") }
            if (error && !creating) Text("Couldn't save the change. Try again.", color = MaterialTheme.colorScheme.error)
        }
        when (val data = state) {
            GoalLoad.Loading -> item { CircularProgressIndicator() }
            GoalLoad.Failed -> item { Text("Goals couldn't load."); TextButton(onClick = { retry++ }) { Text("Retry") } }
            is GoalLoad.Ready -> {
                if (data.values.isEmpty()) item { Text("No custom goals yet.") }
                items(data.values, key = { it.goal.id }) { progress ->
                    FitnessGoalCard(progress, names[progress.goal.spec.exerciseId], today)
                    Row {
                        if (!progress.ended) TextButton(enabled = !busy, onClick = { action = progress.goal to false }) { Text("Archive") }
                        TextButton(enabled = !busy, onClick = { action = progress.goal to true }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalEditor(today: LocalDate, exercises: List<StrengthExerciseOption>?, exerciseError: Boolean,
                       busy: Boolean, error: Boolean, onDismiss: () -> Unit, onSave: (FitnessGoalSpec) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(FitnessGoalKind.TRAINING_DAYS) }
    var target by rememberSaveable { mutableStateOf("") }
    var minimum by rememberSaveable { mutableStateOf("") }
    var exerciseId by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var basis by rememberSaveable { mutableStateOf(RecordedLoadBasis.TOTAL) }
    var setup by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val exercise = kind in setOf(FitnessGoalKind.LOAD, FitnessGoalKind.REPS, FitnessGoalKind.SESSION_VOLUME)
    val parsedDate = runCatching { LocalDate.parse(deadline.trim()) }.getOrNull()
    val spec = FitnessGoalSpec(kind, target.toDoubleOrNull() ?: Double.NaN,
        if (exercise) exerciseId else "", if (exercise) basis else null, if (exercise) setup.trim() else "",
        if (kind == FitnessGoalKind.LOAD || kind == FitnessGoalKind.REPS) minimum.toDoubleOrNull() ?: Double.NaN else 0.0,
        parsedDate)
    val valid = spec.valid() && (deadline.isBlank() || (parsedDate != null && parsedDate > today)) &&
        (!exercise || exercises.orEmpty().any { it.exerciseId == exerciseId })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create personal goal") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Starts ${today.plusDays(1)}. Today's logs will not be counted retrospectively.")
                Box {
                    TextButton(enabled = !busy, onClick = { expanded = true }) { Text(kind.label + " ▾") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        FitnessGoalKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                kind = option; target = ""; minimum = ""; expanded = false
                            })
                        }
                    }
                }
                if (exercise) {
                    if (exerciseError) Text("Exercise list unavailable. Reopen this screen to retry.")
                    else if (exercises == null) CircularProgressIndicator()
                    else {
                        OutlinedTextField(search, { search = it }, label = { Text("Find logged exercise") }, singleLine = true)
                        exercises.filter { it.name.contains(search, ignoreCase = true) || it.exerciseId == exerciseId }.take(12).forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = exerciseId == option.exerciseId, onClick = { exerciseId = option.exerciseId })
                                Text(option.name)
                            }
                        }
                        if (exerciseId.isNotBlank()) Text("Selected: " + (exercises.firstOrNull { it.exerciseId == exerciseId }?.name ?: "Unavailable"))
                    }
                    RecordedLoadBasis.entries.forEach { choice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = basis == choice, onClick = { basis = choice })
                            Text(choice.label)
                        }
                    }
                    OutlinedTextField(setup, { if (it.length <= 48) setup = it },
                        label = { Text("Exact confirmed setup name") }, singleLine = true)
                }
                OutlinedTextField(target, { if (it.length <= 16) target = it }, label = {
                    Text(when (kind) {
                        FitnessGoalKind.LOAD, FitnessGoalKind.WEIGHT_UP, FitnessGoalKind.WEIGHT_DOWN -> "Target kg"
                        FitnessGoalKind.REPS -> "Target reps in one set"
                        FitnessGoalKind.SESSION_VOLUME -> "Target recorded kg × reps"
                        FitnessGoalKind.TRAINING_DAYS -> "Target completed days"
                        FitnessGoalKind.MINUTES -> "Target recorded minutes"
                    })
                }, singleLine = true)
                if (kind == FitnessGoalKind.LOAD || kind == FitnessGoalKind.REPS)
                    OutlinedTextField(minimum, { if (it.length <= 16) minimum = it },
                        label = { Text(if (kind == FitnessGoalKind.LOAD) "Minimum reps in same set" else "Minimum load kg in same set") },
                        singleLine = true)
                OutlinedTextField(deadline, { if (it.length <= 10) deadline = it },
                    label = { Text("Optional deadline YYYY-MM-DD") }, singleLine = true)
                Text("Weight goals track recorded daily averages only; they are not weight-loss advice. Use a suitable target agreed with a qualified professional when needed.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                if (error) Text("Couldn't save. Check values and try again.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = valid && !busy, onClick = { onSave(spec) }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun FitnessGoalCard(progress: FitnessGoalProgress, name: String?, today: LocalDate) {
    val goal = progress.goal
    val spec = goal.spec
    fun number(value: Double) = java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }.format(value)
    val unit = when (spec.kind) {
        FitnessGoalKind.LOAD, FitnessGoalKind.WEIGHT_UP, FitnessGoalKind.WEIGHT_DOWN -> "kg"
        FitnessGoalKind.REPS -> "reps"
        FitnessGoalKind.SESSION_VOLUME -> "kg × reps"
        FitnessGoalKind.TRAINING_DAYS -> "days"
        FitnessGoalKind.MINUTES -> "min"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text(spec.kind.label, style = MaterialTheme.typography.titleMedium)
        if (spec.exerciseGoal) Text("${name ?: spec.exerciseId} · ${spec.basis?.label} · ${spec.setup}", color = ApexColors.TextSecondary)
        Text("Target: ${number(spec.target)} $unit" +
            when (spec.kind) {
                FitnessGoalKind.LOAD -> " × at least ${number(spec.minimum)} reps"
                FitnessGoalKind.REPS -> " at ≥ ${number(spec.minimum)} kg"
                else -> ""
            })
        Text("Baseline: " + (goal.baseline?.let { "${number(it)} $unit · ${goal.baselineDate}" } ?: "Unavailable"),
            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        Text(progress.current?.let { "${number(it.value)} $unit · ${it.date}" } ?: "No qualifying observations",
            style = MaterialTheme.typography.headlineSmall)
        Text(when {
            progress.achievement != null -> "Recorded target reached ${progress.achievement.date}"
            goal.archivedOn != null -> "Archived"
            progress.ended -> "Tracking period ended"
            today < goal.start -> "Upcoming"
            else -> "Tracking"
        }, color = ApexColors.Primary)
        Text("Start ${goal.start}" + (spec.deadline?.let { " · Deadline $it" } ?: " · No deadline"),
            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
        progress.achievement?.let { Text("Source: ${it.source}", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextSecondary) }
    }
}
