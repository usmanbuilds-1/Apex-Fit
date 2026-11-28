@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.ExerciseField
import com.apexfit.app.WorkoutImportStage
import com.apexfit.app.WorkoutImportUiState
import com.apexfit.app.WorkoutImportViewModel
import com.apexfit.app.domain.importer.EditableImportedDay
import com.apexfit.app.domain.importer.EditableImportedExercise
import com.apexfit.app.domain.importer.ExerciseLibraryEntry
import com.apexfit.app.domain.importer.ImportRepTarget
import com.apexfit.app.domain.importer.ImportWeightUnit
import com.apexfit.app.domain.importer.MatchConfidence
import com.apexfit.app.domain.importer.WorkoutTextNormalizer
import com.apexfit.app.ui.theme.*

@Composable
fun WorkoutImportFlow(
    viewModel: WorkoutImportViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val library by viewModel.exerciseLibrary.collectAsStateWithLifecycle()

    if (state.stage == WorkoutImportStage.PASTE) {
        WorkoutImportPasteScreen(
            input = state.inputText,
            isParsing = state.isParsing,
            error = state.errorMessage,
            onInput = viewModel::updateInput,
            onParse = viewModel::parse,
            onClose = onClose
        )
    } else {
        WorkoutImportReviewScreen(
            state = state,
            library = library,
            actions = WorkoutImportReviewActions(
                onBack = viewModel::backToPaste,
                onClose = onClose,
                onSave = { viewModel.save(onSaved) },
                onUpdatePlanName = viewModel::updatePlanName,
                onUpdatePlanGoal = viewModel::updatePlanGoal,
                onUpdateMakeActive = viewModel::updateMakeActive,
                onSelectFixedWeek = viewModel::selectFixedScheduleWeek,
                onAddDay = viewModel::addDay,
                onAddExercise = viewModel::addExercise,
                onSelectExercise = viewModel::selectExercise,
                onUpdateDayLabel = viewModel::updateDayLabel,
                onUpdateDayWeekday = viewModel::updateDayWeekday,
                onMoveDay = viewModel::moveDay,
                onDeleteDay = viewModel::deleteDay,
                onConfirmSuggestedMatch = viewModel::confirmSuggestedMatch,
                onUpdateExerciseText = viewModel::updateExerciseText,
                onUpdateWeightUnit = viewModel::updateWeightUnit,
                onMoveExercise = viewModel::moveExercise,
                onDeleteExercise = viewModel::deleteExercise,
                onSetGroup = viewModel::setImportedGroup
            )
        )
    }
}

internal data class WorkoutImportReviewActions(
    val onBack: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onUpdatePlanName: (String) -> Unit = {},
    val onUpdatePlanGoal: (String) -> Unit = {},
    val onUpdateMakeActive: (Boolean) -> Unit = {},
    val onSelectFixedWeek: (String) -> Unit = {},
    val onAddDay: () -> Unit = {},
    val onAddExercise: (dayKey: String, exerciseId: String) -> Unit = { _, _ -> },
    val onSelectExercise: (dayKey: String, exerciseKey: String, exerciseId: String) -> Unit = { _, _, _ -> },
    val onUpdateDayLabel: (dayKey: String, label: String) -> Unit = { _, _ -> },
    val onUpdateDayWeekday: (dayKey: String, weekday: String?) -> Unit = { _, _ -> },
    val onMoveDay: (dayKey: String, delta: Int) -> Unit = { _, _ -> },
    val onDeleteDay: (dayKey: String) -> Unit = {},
    val onConfirmSuggestedMatch: (dayKey: String, exerciseKey: String) -> Unit = { _, _ -> },
    val onUpdateExerciseText: (dayKey: String, exerciseKey: String, field: ExerciseField, value: String) -> Unit = { _, _, _, _ -> },
    val onUpdateWeightUnit: (dayKey: String, exerciseKey: String, unit: ImportWeightUnit) -> Unit = { _, _, _ -> },
    val onMoveExercise: (dayKey: String, exerciseKey: String, delta: Int) -> Unit = { _, _, _ -> },
    val onDeleteExercise: (dayKey: String, exerciseKey: String) -> Unit = { _, _ -> },
    val onSetGroup: (String, String, List<String>, Int?, Int?) -> Unit = { _, _, _, _, _ -> }
)

@Composable
private fun WorkoutImportPasteScreen(
    input: String,
    isParsing: Boolean,
    error: String?,
    onInput: (String) -> Unit,
    onParse: () -> Unit,
    onClose: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { ImportHeader("Import workout") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(ApexIcons.ArrowLeft), "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            Surface(color = DarkBackground) {
                Button(
                    onClick = onParse,
                    enabled = input.isNotBlank() && !isParsing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ApexColors.OnGlow
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isParsing) "Parsing" else "Parse workout",
                        color = ApexColors.OnGlow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Paste a routine from Reddit, Notes, a coach, or anywhere else. Apex Fit parses it on-device, then makes you review uncertain matches before saving.",
                    color = SecondaryText,
                    fontFamily = InterFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    placeholder = {
                        Text(
                            "Monday Push\nBench Press 3x8\nIncline DB Press 3x10-12\nLateral Raise 4x15",
                            color = MutedText,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = PrimaryText,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp
                    ),
                    colors = importFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let(onInput)
                    }) {
                        Icon(painterResource(ApexIcons.Add), null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                    Text(
                        input.length.toString() + " / " + WorkoutTextNormalizer.MAX_INPUT_CHARS,
                        color = MutedText,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp
                    )
                }
            }
            error?.let { item { ImportNotice(it, true) } }
            item {
                Text(
                    "Pasted text is treated as data. It is not executed and is not sent to an AI service.",
                    color = MutedText,
                    fontFamily = InterFamily,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun WorkoutImportReviewScreen(
    state: WorkoutImportUiState,
    library: List<ExerciseLibraryEntry>,
    actions: WorkoutImportReviewActions
) {
    val draft = state.draft ?: return
    var pickerTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    pickerTarget?.let { target ->
        ExercisePickerDialog(
            library = library,
            onDismiss = { pickerTarget = null },
            onSelect = { option ->
                if (target.second == null) {
                    actions.onAddExercise(target.first, option.id)
                } else {
                    actions.onSelectExercise(target.first, target.second!!, option.id)
                }
                pickerTarget = null
            }
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { ImportHeader("Review import") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(ApexIcons.ArrowLeft), "Back to text", tint = PrimaryText)
                    }
                },
                actions = {
                    TextButton(onClick = actions.onClose) { Text("Cancel", color = SecondaryText) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            Surface(color = DarkBackground) {
                Button(
                    onClick = actions.onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ApexColors.OnGlow
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (state.isSaving) "Saving" else "Save plan",
                        color = ApexColors.OnGlow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = actions.onUpdatePlanName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plan name") },
                    singleLine = true,
                    colors = importFieldColors()
                )
            }
            item {
                OutlinedTextField(
                    value = state.goal,
                    onValueChange = actions.onUpdatePlanGoal,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plan goal") },
                    supportingText = { Text("Defaults to your current APEX goal.") },
                    singleLine = true,
                    colors = importFieldColors()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Make this my active plan", color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Text("You can turn this off before saving.", color = MutedText, fontSize = 11.sp)
                    }
                    Switch(
                        checked = draft.makeActive,
                        onCheckedChange = actions.onUpdateMakeActive
                    )
                }
            }
            state.errorMessage?.let { item { ImportNotice(it, true) } }
            if (draft.schedule.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Recovered schedule", color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Text("This schedule repeats automatically. Week 1 starts on the Monday of the week you first activate this plan. Edit each template once; it applies to all its scheduled occurrences.", color = MutedText, fontSize = 12.sp)
                        draft.schedule.groupBy { it.week }.forEach { (week, slots) ->
                            Text(week, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                            slots.forEach { slot -> Text(slot.weekday + " · " + slot.workout, color = SecondaryText, fontSize = 12.sp) }

                        }
                    }
                }
            }
            state.parseWarnings.forEach { warning ->
                item { ImportNotice(warning, false) }
            }
            state.validationErrors.drop(1).forEach { warning ->
                item { ImportNotice(warning, true) }
            }

            items(draft.days, key = { it.key }) { day ->
                ImportedDayCard(
                    day = day,
                    isScheduledTemplate = draft.usesRotation,
                    onLabel = { actions.onUpdateDayLabel(day.key, it) },
                    onWeekday = { actions.onUpdateDayWeekday(day.key, it) },
                    onMoveUp = { actions.onMoveDay(day.key, -1) },
                    onMoveDown = { actions.onMoveDay(day.key, 1) },
                    onDeleteDay = { actions.onDeleteDay(day.key) },
                    onChooseExercise = { exerciseKey -> pickerTarget = day.key to exerciseKey },
                    onConfirmMatch = { exerciseKey -> actions.onConfirmSuggestedMatch(day.key, exerciseKey) },
                    onSelectCandidate = { exerciseKey, id -> actions.onSelectExercise(day.key, exerciseKey, id) },
                    onField = { exerciseKey, field, value ->
                        actions.onUpdateExerciseText(day.key, exerciseKey, field, value)
                    },
                    onUnit = { exerciseKey, unit ->
                        actions.onUpdateWeightUnit(day.key, exerciseKey, unit)
                    },
                    onMoveExercise = { exerciseKey, delta ->
                        actions.onMoveExercise(day.key, exerciseKey, delta)
                    },
                    onDeleteExercise = { exerciseKey ->
                        actions.onDeleteExercise(day.key, exerciseKey)
                    },
                    onAddExercise = { pickerTarget = day.key to null },
                    onSetGroup = { tag, members, transition, round -> actions.onSetGroup(day.key, tag, members, transition, round) }
                )
            }

            item {
                OutlinedButton(
                    onClick = actions.onAddDay,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painterResource(ApexIcons.Add), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add training day")
                }
            }
        }
    }
}

@Composable
private fun ImportedDayCard(
    day: EditableImportedDay,
    isScheduledTemplate: Boolean = false,
    onLabel: (String) -> Unit,
    onWeekday: (String?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteDay: () -> Unit,
    onChooseExercise: (String) -> Unit,
    onConfirmMatch: (String) -> Unit,
    onSelectCandidate: (String, String) -> Unit,
    onField: (String, ExerciseField, String) -> Unit,
    onUnit: (String, ImportWeightUnit) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onAddExercise: () -> Unit,
    onSetGroup: (String, List<String>, Int?, Int?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, if ((!isScheduledTemplate && day.dayOfWeek == null) || day.exercises.any { it.needsReview }) ApexColors.Amber else BorderSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isScheduledTemplate) "WORKOUT TEMPLATE" else "TRAINING DAY", color = AmberAccent, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp) {
                    Icon(painterResource(ApexIcons.ArrowUp), contentDescription = "Move training day up",
                        tint = SecondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown) {
                    Icon(painterResource(ApexIcons.ArrowDown), contentDescription = "Move training day down",
                        tint = SecondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDeleteDay) {
                    Icon(painterResource(ApexIcons.Delete), contentDescription = "Delete training day",
                        tint = ApexColors.Alert, modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = day.label,
                onValueChange = onLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Day label") },
                singleLine = true,
                colors = importFieldColors()
            )
            if (!isScheduledTemplate) WeekdayPicker(day.dayOfWeek, onWeekday)
            else Text("Weekdays assigned by recovered schedule", color = MutedText, fontSize = 11.sp)

            val candidates = day.groupCandidates()
            candidates.forEach { candidate ->
                key(candidate.tag) {
                    ImportedGroupReview(candidate, day.validGroupChoice(candidate.tag),
                        day.groupChoices.containsKey(candidate.tag), onSetGroup)
                }
            }
            (day.groupChoices.keys - candidates.map { it.tag }.toSet()).forEach { tag ->
                Text("Group $tag no longer has any members. Remove it before saving.", color = ApexColors.Alert)
                TextButton(onClick = { onSetGroup(tag, emptyList(), null, null) }) { Text("Remove group $tag") }
            }
            day.exercises.forEach { exercise ->
                ImportedExerciseCard(
                    exercise = exercise,
                    onChoose = { onChooseExercise(exercise.key) },
                    onConfirm = { onConfirmMatch(exercise.key) },
                    onSelectCandidate = { id -> onSelectCandidate(exercise.key, id) },
                    onField = { field, value -> onField(exercise.key, field, value) },
                    onUnit = { unit -> onUnit(exercise.key, unit) },
                    onMoveUp = { onMoveExercise(exercise.key, -1) },
                    onMoveDown = { onMoveExercise(exercise.key, 1) },
                    onDelete = { onDeleteExercise(exercise.key) }
                )
            }

            TextButton(onClick = onAddExercise) {
                Icon(painterResource(ApexIcons.Add), null)
                Spacer(Modifier.width(6.dp))
                Text("Add exercise")
            }
        }
    }
}

@Composable
private fun ImportedGroupReview(candidate: com.apexfit.app.domain.importer.ImportedGroupCandidate,
                                accepted: com.apexfit.app.domain.importer.ImportedGroupChoice?,
                                hasChoice: Boolean,
                                onSetGroup: (String, List<String>, Int?, Int?) -> Unit) {
    val members = candidate.members.map { it.key }
    var transition by remember(candidate.tag, members, accepted) { mutableStateOf(accepted?.transitionRest?.toString() ?: "15") }
    var round by remember(candidate.tag, members, accepted) { mutableStateOf(accepted?.roundRest?.toString() ?: "120") }
    val between = transition.toIntOrNull()
    val afterRound = round.toIntOrNull()
    val valid = candidate.consecutive && between != null && between in 1..3600 &&
        afterRound != null && afterRound in 1..3600
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Imported group ${candidate.tag}", style = MaterialTheme.typography.titleSmall)
        Text(candidate.members.joinToString(" → ") { it.selectedExerciseName ?: it.sourceName },
            color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text(when {
            accepted != null -> "Confirmed · ${accepted.transitionRest}s transition / ${accepted.roundRest}s round rest"
            hasChoice -> "Group changed. Confirm again or keep sequential."
            !candidate.consecutive -> "Group needs at least two consecutive exercises. Kept sequential."
            else -> "Sequential unless you explicitly confirm this group."
        }, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        if (candidate.consecutive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Transition sec", transition, Modifier.weight(1f)) { transition = it.take(4) }
                NumberField("Round rest sec", round, Modifier.weight(1f)) { round = it.take(4) }
            }
            TextButton(enabled = valid, onClick = {
                onSetGroup(candidate.tag, members, between, afterRound)
            }) { Text(if (accepted == null) "Confirm group" else "Update group") }
        }
        if (hasChoice) TextButton(onClick = { onSetGroup(candidate.tag, members, null, null) }) { Text("Keep sequential") }
    }
}

@Composable
private fun ImportedExerciseCard(
    exercise: EditableImportedExercise,
    onChoose: () -> Unit,
    onConfirm: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onField: (ExerciseField, String) -> Unit,
    onUnit: (ImportWeightUnit) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkRaised, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.selectedExerciseName ?: exercise.sourceName,
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (exercise.selectedExerciseName != null && exercise.selectedExerciseName != exercise.sourceName) {
                    Text("Source: " + exercise.sourceName, color = MutedText, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onMoveUp) {
                Icon(painterResource(ApexIcons.ArrowUp),
                    contentDescription = "Move ${exercise.selectedExerciseName ?: exercise.sourceName} up",
                    tint = SecondaryText, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown) {
                Icon(painterResource(ApexIcons.ArrowDown),
                    contentDescription = "Move ${exercise.selectedExerciseName ?: exercise.sourceName} down",
                    tint = SecondaryText, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(ApexIcons.Delete),
                    contentDescription = "Delete ${exercise.selectedExerciseName ?: exercise.sourceName}",
                    tint = ApexColors.Alert, modifier = Modifier.size(18.dp))
            }
        }

        MatchStatus(exercise, onConfirm, onChoose, onSelectCandidate)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Sets", exercise.setsText, Modifier.weight(1f)) { onField(ExerciseField.SETS, it) }
            NumberField("Min reps", exercise.repsMinText, Modifier.weight(1f)) { onField(ExerciseField.REPS_MIN, it) }
            NumberField("Max reps", exercise.repsMaxText, Modifier.weight(1f)) { onField(ExerciseField.REPS_MAX, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = exercise.weightText,
                onValueChange = { onField(ExerciseField.WEIGHT, it) },
                modifier = Modifier.weight(1f),
                label = { Text("Weight") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = importFieldColors()
            )
            UnitPicker(exercise.weightUnit, onUnit)
            NumberField("Rest sec", exercise.restSecondsText, Modifier.weight(1f)) { onField(ExerciseField.REST, it) }
        }

        if (exercise.repTarget == ImportRepTarget.AMRAP || exercise.repTarget == ImportRepTarget.FAILURE) {
            Text(
                if (exercise.repTarget == ImportRepTarget.AMRAP)
                    "Source says AMRAP. Enter a numeric planning target above; AMRAP stays in the notes."
                else
                    "Source says to failure. Enter a numeric planning target above; failure stays in the notes.",
                color = ApexColors.Amber,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        OutlinedTextField(
            value = exercise.notes,
            onValueChange = { onField(ExerciseField.NOTES, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 1,
            maxLines = 3,
            colors = importFieldColors()
        )
    }
}

@Composable
private fun MatchStatus(
    exercise: EditableImportedExercise,
    onConfirm: () -> Unit,
    onChoose: () -> Unit,
    onSelectCandidate: (String) -> Unit
) {
    val trusted = exercise.matchConfidence == MatchConfidence.EXACT ||
        exercise.matchConfidence == MatchConfidence.HIGH ||
        exercise.matchConfirmed
    if (trusted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(ApexIcons.Check), null, tint = ApexColors.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Matched to Apex Fit library", color = ApexColors.Primary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onChoose) { Text("Change") }
        }
    } else if (exercise.selectedExerciseId != null) {
        Column {
            Text(
                "Needs review: " + (exercise.selectedExerciseName ?: exercise.sourceName),
                color = ApexColors.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                TextButton(onClick = onConfirm) { Text("Confirm") }
                TextButton(onClick = onChoose) { Text("Choose another") }
            }
        }
    } else {
        Column {
            Text("Exercise not confidently matched", color = ApexColors.Amber, fontSize = 11.sp)
            exercise.candidates.take(3).forEach { candidate ->
                TextButton(onClick = { onSelectCandidate(candidate.entry.id) }) {
                    Text(candidate.entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = onChoose, shape = RoundedCornerShape(10.dp)) {
                Icon(painterResource(ApexIcons.Search), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search library")
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = importFieldColors()
    )
}

@Composable
private fun WeekdayPicker(value: String?, onValue: (String?) -> Unit) {
    val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (value == null) ApexColors.Amber else BorderSubtle)
        ) {
            Text(value ?: "Choose weekday", modifier = Modifier.weight(1f), color = PrimaryText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            weekdays.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day) },
                    onClick = {
                        onValue(day)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun UnitPicker(unit: ImportWeightUnit, onUnit: (ImportWeightUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(82.dp)) {
            Text(unit.code)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImportWeightUnit.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.code) },
                    onClick = {
                        onUnit(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    library: List<ExerciseLibraryEntry>,
    onDismiss: () -> Unit,
    onSelect: (ExerciseLibraryEntry) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(library, query) {
        val term = query.trim().lowercase()
        library.asSequence()
            .filter {
                term.isBlank() ||
                    it.name.lowercase().contains(term) ||
                    it.primaryMuscle.lowercase().contains(term) ||
                    it.aliases.any { alias -> alias.lowercase().contains(term) }
            }
            .take(60)
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        title = { ImportHeader("Choose exercise") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(ApexIcons.Search), null) },
                    placeholder = { Text("Search exercises") },
                    singleLine = true,
                    colors = importFieldColors()
                )
                LazyColumn {
                    items(filtered, key = { it.id }) { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(option.name, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                            Text(
                                option.primaryMuscle + " | " + option.equipment,
                                color = MutedText,
                                fontSize = 10.sp
                            )
                        }
                        HorizontalDivider(color = BorderSubtle)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ImportNotice(message: String, error: Boolean) {
    val accent = if (error) ApexColors.Alert else ApexColors.Amber
    val fill = if (error) ApexColors.AlertSoft else ApexColors.AmberSoft
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(10.dp))
            .padding(12.dp),
        color = accent,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

@Composable
private fun ImportHeader(text: String) {
    Text(
        text,
        fontFamily = SyneFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = PrimaryText
    )
}

@Composable
private fun importFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PrimaryText,
    unfocusedTextColor = PrimaryText,
    focusedBorderColor = ApexColors.Primary,
    unfocusedBorderColor = BorderSubtle,
    focusedLabelColor = ApexColors.Primary,
    unfocusedLabelColor = SecondaryText,
    cursorColor = ApexColors.Primary,
    focusedContainerColor = DarkRaised,
    unfocusedContainerColor = DarkRaised
)
