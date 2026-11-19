package com.apexfit.app.ui.screens

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apexfit.app.data.Exercise
import com.apexfit.app.ui.components.ApexAnatomy
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.components.showsOnBackOnly
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.ExerciseCatalogFilter
import com.apexfit.app.utils.MuscleAliases
import com.apexfit.app.utils.MuscleMatchType

@Composable
internal fun ExercisePickerDialog(
    state: UiState<List<Exercise>>,
    title: String,
    initialMuscle: String? = null,
    excludeId: String? = null,
    onDismiss: () -> Unit,
    onSelect: (Exercise) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ExercisePicker(state, title, initialMuscle, excludeId, onDismiss, onSelect)
    }
}

@Composable
internal fun ExercisePicker(
    state: UiState<List<Exercise>>,
    title: String,
    initialMuscle: String? = null,
    excludeId: String? = null,
    onBack: () -> Unit,
    onSelect: (Exercise) -> Unit,
    initialQuery: String = "",
    initialShowBack: Boolean = false
) {
    val exercises = remember(state, excludeId) {
        (state as? UiState.Success)?.data.orEmpty().filter { it.id != excludeId && it.isDeleted == 0 }
    }
    var mode by rememberSaveable { mutableIntStateOf(if (initialMuscle == null) 0 else 1) }
    var showBack by rememberSaveable {
        mutableStateOf(initialShowBack || initialMuscle?.let(::showsOnBackOnly) == true)
    }
    var selectedMuscle by rememberSaveable { mutableStateOf(initialMuscle?.let(MuscleAliases::getCanonical)) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var includeSecondary by rememberSaveable { mutableStateOf(false) }
    var equipmentExpanded by remember { mutableStateOf(false) }
    var musclesExpanded by remember { mutableStateOf(false) }
    val equipmentOptions = remember(exercises) {
        exercises.flatMap { it.equipmentRequired.split(',', '/', '+') }.map(String::trim)
            .filter(String::isNotBlank).distinct().sorted()
    }
    val muscleOptions = remember(exercises) {
        exercises.flatMap { it.secondaryMuscles + it.primaryMuscle }.map(MuscleAliases::getCanonical)
            .filter(String::isNotBlank).distinct().sorted()
    }
    val results = remember(exercises, query, equipment, mode, selectedMuscle, includeSecondary) {
        ExerciseCatalogFilter.filter(exercises, query, setOfNotNull(equipment),
            if (mode == 1) selectedMuscle else null, includeSecondary)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ApexColors.Canvas).safeDrawingPadding().imePadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            ApexSegmentedControl(listOf("Search", "Muscles"), mode, onSelect = { mode = it },
                modifier = Modifier.padding(vertical = 16.dp))
            OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search exercises") }, singleLine = true)
            Box {
                TextButton(onClick = { equipmentExpanded = true }) {
                    Text(equipment ?: "All equipment")
                }
                DropdownMenu(equipmentExpanded, { equipmentExpanded = false }) {
                    DropdownMenuItem(text = { Text("All equipment") }, onClick = {
                        equipment = null
                        equipmentExpanded = false
                    })
                    equipmentOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            equipment = option
                            equipmentExpanded = false
                        })
                    }
                }
            }
        }
        if (mode == 1 && exercises.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { musclesExpanded = true }) {
                            Text(selectedMuscle?.let(::muscleName) ?: "All muscles")
                        }
                        DropdownMenu(musclesExpanded, { musclesExpanded = false }) {
                            DropdownMenuItem(text = { Text("All muscles") }, onClick = {
                                selectedMuscle = null
                                musclesExpanded = false
                            })
                            muscleOptions.forEach { muscle ->
                                DropdownMenuItem(text = { Text(muscleName(muscle)) }, onClick = {
                                    selectedMuscle = muscle
                                    musclesExpanded = false
                                })
                            }
                        }
                    }
                    TextButton(onClick = { showBack = !showBack }) {
                        Text(if (showBack) "Show front" else "Show back")
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ApexAnatomy(showBack, Modifier.width(240.dp), selectedMuscle = selectedMuscle,
                        onMuscleSelected = { selectedMuscle = it })
                }
                if (selectedMuscle != null) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) { includeSecondary = !includeSecondary },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeSecondary, onCheckedChange = null,
                            modifier = Modifier.padding(horizontal = 10.dp))
                        Text("Include secondary muscle involvement")
                    }
                }
            }
        }
        when {
            state is UiState.Loading -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
            state is UiState.Error -> item { Text("Exercise library could not be loaded.", color = ApexColors.Alert) }
            results.isEmpty() -> item {
                Text(if (exercises.isEmpty()) "No exercises available." else "No exercises match these filters.",
                    color = ApexColors.TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
            }
            else -> items(results, key = { it.exercise.id }) { result ->
                val exercise = result.exercise
                HorizontalDivider(color = ApexColors.BorderSubtle)
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onSelect(exercise) }.padding(vertical = 12.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    val match = if (result.muscleMatch == MuscleMatchType.SECONDARY) " · Secondary muscle" else ""
                    Text("${exercise.primaryMuscle} · ${exercise.equipmentRequired}$match",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

