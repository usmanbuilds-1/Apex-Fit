@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
            viewModel = viewModel,
            library = library,
            onBack = viewModel::backToPaste,
            onClose = onClose,
            onSaved = onSaved
        )
    }
}

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
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
                        Icon(Icons.Filled.Add, null)
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
private fun WorkoutImportReviewScreen(
    viewModel: WorkoutImportViewModel,
    library: List<ExerciseLibraryEntry>,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft ?: return
    var pickerTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    pickerTarget?.let { target ->
        ExercisePickerDialog(
            library = library,
            onDismiss = { pickerTarget = null },
            onSelect = { option ->
                if (target.second == null) {
                    viewModel.addExercise(target.first, option.id)
                } else {
                    viewModel.selectExercise(target.first, target.second!!, option.id)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to text", tint = PrimaryText)
                    }
                },
                actions = {
                    TextButton(onClick = onClose) { Text("Cancel", color = SecondaryText) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            Surface(color = DarkBackground) {
                Button(
                    onClick = { viewModel.save(onSaved) },
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
                    onValueChange = viewModel::updatePlanName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plan name") },
                    singleLine = true,
                    colors = importFieldColors()
                )
            }
            item {
                OutlinedTextField(
                    value = state.goal,
                    onValueChange = viewModel::updatePlanGoal,
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
                        onCheckedChange = viewModel::updateMakeActive
                    )
                }
            }
            state.errorMessage?.let { item { ImportNotice(it, true) } }
            state.parseWarnings.take(4).forEach { warning ->
                item { ImportNotice(warning, false) }
            }
            state.validationErrors.drop(1).take(3).forEach { warning ->
                item { ImportNotice(warning, true) }
            }

            items(draft.days, key = { it.key }) { day ->
                ImportedDayCard(
                    day = day,
                    onLabel = { viewModel.updateDayLabel(day.key, it) },
                    onWeekday = { viewModel.updateDayWeekday(day.key, it) },
                    onMoveUp = { viewModel.moveDay(day.key, -1) },
                    onMoveDown = { viewModel.moveDay(day.key, 1) },
                    onDeleteDay = { viewModel.deleteDay(day.key) },
                    onChooseExercise = { exerciseKey -> pickerTarget = day.key to exerciseKey },
                    onConfirmMatch = { exerciseKey -> viewModel.confirmSuggestedMatch(day.key, exerciseKey) },
                    onSelectCandidate = { exerciseKey, id -> viewModel.selectExercise(day.key, exerciseKey, id) },
                    onField = { exerciseKey, field, value ->
                        viewModel.updateExerciseText(day.key, exerciseKey, field, value)
                    },
                    onUnit = { exerciseKey, unit ->
                        viewModel.updateWeightUnit(day.key, exerciseKey, unit)
                    },
                    onMoveExercise = { exerciseKey, delta ->
                        viewModel.moveExercise(day.key, exerciseKey, delta)
                    },
                    onDeleteExercise = { exerciseKey ->
                        viewModel.deleteExercise(day.key, exerciseKey)
                    },
                    onAddExercise = { pickerTarget = day.key to null }
                )
            }

            item {
                OutlinedButton(
                    onClick = viewModel::addDay,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, null)
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
    onAddExercise: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, if (day.needsReview) ApexColors.Amber else BorderSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRAINING DAY", color = AmberAccent, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp) { Text("↑", color = SecondaryText, fontSize = 18.sp) }
                IconButton(onClick = onMoveDown) { Text("↓", color = SecondaryText, fontSize = 18.sp) }
                IconButton(onClick = onDeleteDay) { Text("×", color = ApexColors.Alert, fontSize = 22.sp) }
            }
            OutlinedTextField(
                value = day.label,
                onValueChange = onLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Day label") },
                singleLine = true,
                colors = importFieldColors()
            )
            WeekdayPicker(day.dayOfWeek, onWeekday)

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
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add exercise")
            }
        }
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
            IconButton(onClick = onMoveUp) { Text("↑", color = SecondaryText, fontSize = 18.sp) }
            IconButton(onClick = onMoveDown) { Text("↓", color = SecondaryText, fontSize = 18.sp) }
            IconButton(onClick = onDelete) { Text("×", color = ApexColors.Alert, fontSize = 22.sp) }
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
            Icon(Icons.Filled.Check, null, tint = ApexColors.Primary, modifier = Modifier.size(16.dp))
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
                Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
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
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
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