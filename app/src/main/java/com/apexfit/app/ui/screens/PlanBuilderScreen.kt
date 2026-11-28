@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource
import com.apexfit.app.ui.models.UiState

import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.PlanBuilderViewModel
import com.apexfit.app.WorkoutImportViewModel
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.utils.MuscleGroups
import com.apexfit.app.utils.PlanExerciseDraftField
import com.apexfit.app.utils.PlanExerciseDraftIssue
import com.apexfit.app.utils.PlanExerciseDraftValidation
import com.apexfit.app.utils.validatePlanExerciseDraft

@Composable
internal fun PlanBuilderDayDialog(
    initialLabel: String,
    initialDayOfWeek: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (label: String, dayOfWeek: String) -> Unit,
    initialValidationError: String? = null,
    initialCycleWeek: Int = 1,
    cycleWeeks: Int = 1,
    onSaveWithCycle: ((String, String, Int) -> Unit)? = null
) {
    var dayLabel by rememberSaveable(isEditing, initialLabel) { mutableStateOf(initialLabel) }
    var dayLabelError by rememberSaveable(isEditing, initialLabel) { mutableStateOf(initialValidationError) }
    var selectedDayOfWeek by rememberSaveable(isEditing, initialDayOfWeek) {
        mutableStateOf(initialDayOfWeek)
    }
    var selectedCycleWeek by rememberSaveable(initialCycleWeek, cycleWeeks) { mutableStateOf(initialCycleWeek.coerceIn(1, cycleWeeks.coerceAtLeast(1))) }
    val requiredLabelError = stringResource(R.string.plan_builder_session_name_is_required)
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkRaised,
        title = {
            Text(
                text = if (isEditing) "EDIT TRAINING DAY" else "ADD TRAINING DAY",
                fontFamily = SyneFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = dayLabel,
                    onValueChange = {
                        dayLabel = it
                        dayLabelError = null
                    },
                    isError = dayLabelError != null,
                    supportingText = dayLabelError?.let { { Text(it) } },
                    label = {
                        Text(
                            stringResource(R.string.plan_builder_session_name_e_g_upper_a),
                            color = SecondaryText
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("day_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ApexColors.Primary,
                        unfocusedBorderColor = BorderSubtle,
                        cursorColor = ApexColors.Primary
                    ),
                    singleLine = true
                )

                if (cycleWeeks > 1) {
                    Text("Cycle week", color = SecondaryText)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        (1..cycleWeeks).forEach { week ->
                            TextButton(onClick = { selectedCycleWeek = week }) {
                                Text("Week $week", color = if (week == selectedCycleWeek) ApexColors.Primary else SecondaryText)
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.plan_builder_day_of_the_week),
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    daysOfWeek.forEach { day ->
                        val isSelected = selectedDayOfWeek == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ApexColors.Primary else DarkBackground)
                                .border(
                                    BorderStroke(1.dp, if (isSelected) ApexColors.Primary else BorderSubtle),
                                    RoundedCornerShape(8.dp)
                                )
                                .semantics { selected = isSelected }
                                .clickable(role = Role.Tab) { selectedDayOfWeek = day }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ApexColors.OnGlow else SecondaryText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (dayLabel.isBlank()) {
                        dayLabelError = requiredLabelError
                    } else {
                        dayLabelError = null
                        if (onSaveWithCycle != null) onSaveWithCycle(dayLabel.trim(), selectedDayOfWeek, selectedCycleWeek)
                        else onSave(dayLabel.trim(), selectedDayOfWeek)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                modifier = Modifier.testTag("save_day_confirm_button")
            ) {
                Text(
                    stringResource(R.string.plan_builder_save),
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plan_builder_cancel), fontFamily = SyneFamily, color = SecondaryText)
            }
        }
    )
}

@Composable
internal fun PlanBuilderMetadataFields(
    planName: String,
    planGoal: String,
    planNameError: String?,
    onPlanNameChange: (String) -> Unit,
    onPlanGoalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlanBuilderMetadataField(
            label = stringResource(R.string.plan_builder_plan_name),
            value = planName,
            onValueChange = onPlanNameChange,
            error = planNameError,
            testTag = "plan_name_input"
        )
        PlanBuilderGoalSelector(
            value = planGoal,
            onValueChange = onPlanGoalChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanBuilderGoalSelector(
    value: String,
    onValueChange: (String) -> Unit
) {
    val options = remember {
        listOf("Build muscle", "Lose fat", "Recomposition", "Maintain", "General Fitness")
    }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Goal", style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .testTag("plan_goal_input")
                    .border(1.dp, ApexColors.BorderSubtle, RoundedCornerShape(10.dp)),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ApexColors.SurfaceRaised,
                    unfocusedContainerColor = ApexColors.SurfaceRaised,
                    disabledContainerColor = ApexColors.SurfaceRaised,
                    errorContainerColor = ApexColors.SurfaceRaised,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = ApexColors.Primary
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanBuilderExerciseSummary(
    exercise: PlanExercise,
    preferredUnits: String,
    modifier: Modifier = Modifier
) {
    val isImperial = preferredUnits.lowercase() in listOf("lb", "lbs")
    val weightLabel = if (exercise.weight > 0.0) {
        val weight = if (isImperial) exercise.weight * AppConstants.KG_TO_LBS else exercise.weight
        val roundedWeight = kotlin.math.round(weight * 10.0) / 10.0
        val displayWeight = if (roundedWeight % 1.0 == 0.0) {
            roundedWeight.toInt().toString()
        } else {
            roundedWeight.toString()
        }
        "$displayWeight ${if (isImperial) "lb" else "kg"}"
    } else "Load not set"

    Column(modifier = modifier) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ApexColors.TextPrimary
        )
        val repRange = if (exercise.repsMin == exercise.repsMax) {
            exercise.repsMin.toString()
        } else {
            "${exercise.repsMin}–${exercise.repsMax}"
        }
        Text(
            text = "${exercise.sets} × $repRange · $weightLabel · ${exercise.restSeconds} sec",
            style = MaterialTheme.typography.bodySmall,
            color = ApexColors.TextSecondary
        )
        if (exercise.notes.isNotBlank()) {
            Text(
                text = exercise.notes,
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
internal fun PlanBuilderSessionRow(
    session: PlanSession,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    cycleWeeks: Int = 1
) {
    var menuExpanded by remember(session.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (cycleWeeks > 1) "W${session.cycleWeek} · ${session.day.take(3)}" else session.day.take(3),
            modifier = Modifier.width(if (cycleWeeks > 1) 76.dp else 40.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) ApexColors.Primary else ApexColors.TextSecondary
        )
        Text(
            text = session.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = ApexColors.TextPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Box {
            TextButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("session_actions_button_${session.id}")
                    .semantics { contentDescription = "Actions for ${session.label}" },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("⋯", style = MaterialTheme.typography.titleLarge, color = ApexColors.TextSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Move up") },
                    enabled = canMoveUp,
                    onClick = { menuExpanded = false; onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    enabled = canMoveDown,
                    onClick = { menuExpanded = false; onMoveDown() }
                )
                DropdownMenuItem(
                    text = { Text("Edit day") },
                    onClick = { menuExpanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Delete day", color = ApexColors.Alert) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
    HorizontalDivider(color = ApexColors.BorderSubtle)
}

@Composable
internal fun PlanBuilderExerciseRow(
    exercise: PlanExercise,
    preferredUnits: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember(exercise.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlanBuilderExerciseSummary(
            exercise = exercise,
            preferredUnits = preferredUnits,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("exercise_actions_button_${exercise.id}")
                    .semantics { contentDescription = "Actions for ${exercise.name}" },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("⋯", style = MaterialTheme.typography.titleLarge, color = ApexColors.TextSecondary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit exercise") },
                    onClick = { menuExpanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Remove exercise", color = ApexColors.Alert) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
    HorizontalDivider(color = ApexColors.BorderSubtle)
}

@Composable
private fun PlanBuilderMetadataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    error: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
        TextField(
            value = value,
            onValueChange = onValueChange,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ApexColors.TextPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .border(1.dp, ApexColors.BorderSubtle, RoundedCornerShape(10.dp)),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ApexColors.SurfaceRaised,
                unfocusedContainerColor = ApexColors.SurfaceRaised,
                disabledContainerColor = ApexColors.SurfaceRaised,
                errorContainerColor = ApexColors.SurfaceRaised,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = ApexColors.Primary
            )
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = ApexColors.Alert)
        }
    }
}

@Composable
internal fun PlanBuilderSaveBar(isSaving: Boolean, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ApexColors.Canvas).navigationBarsPadding()) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                .heightIn(min = 56.dp).testTag("save_plan_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (isSaving) "Saving…" else "Save plan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ApexColors.OnGlow
            )
        }
    }
}

@Composable
fun PlanBuilderScreen(
    planBuilderViewModel: PlanBuilderViewModel,
    planId: Long,
    onNavigateBack: () -> Unit,
    startInImportMode: Boolean = false,
    workoutImportViewModel: WorkoutImportViewModel = hiltViewModel()
) {
    DisposableEffect(Unit) {
        onDispose {
            planBuilderViewModel.resetPlanBuilder()
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionsList by planBuilderViewModel.planBuilderSessions.collectAsStateWithLifecycle()
    val exercisesList by planBuilderViewModel.planBuilderExercises.collectAsStateWithLifecycle()
    val preferredUnits by planBuilderViewModel.units.collectAsStateWithLifecycle()
    var activePlan by remember { mutableStateOf<WorkoutPlan?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var planName by rememberSaveable { mutableStateOf("") }
    var planGoal by rememberSaveable { mutableStateOf("") }
    var planNameError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDayDialog by rememberSaveable { mutableStateOf(false) }
    var showAddExerciseDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSessionIdForExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPlanSessionId by rememberSaveable(planId) { mutableStateOf<Long?>(null) }
    var editingSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var groupEditorSession by rememberSaveable { mutableStateOf<Long?>(null) }
    var groupEditorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionToDelete by rememberSaveable { mutableStateOf<PlanSession?>(null) }
    var exerciseToDelete by rememberSaveable { mutableStateOf<PlanExercise?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showWorkoutImporter by rememberSaveable(planId, startInImportMode) { mutableStateOf(startInImportMode) }

    LaunchedEffect(planId) {
        try {
            planBuilderViewModel.resetPlanBuilder()
            if (planId != 0L) {
                val (plan, sessions, exercises) = planBuilderViewModel.loadPlanForEditing(planId)
                activePlan = plan
                planName = plan.name
                planGoal = plan.goal
                planBuilderViewModel.initializePlanBuilder(plan, sessions, exercises)
            } else {
                planBuilderViewModel.initializePlanBuilder(null, emptyList(), emptyList())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "Couldn't load this plan. Go back and try again."
        } finally {
            loading = false
        }
    }
    if (loading || loadError != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = ApexColors.Primary)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError.orEmpty(), color = ApexColors.TextPrimary)
                TextButton(onClick = onNavigateBack) { Text("Go back") }
            }
        }
        return
    }

    if (showWorkoutImporter) {
        WorkoutImportFlow(
            viewModel = workoutImportViewModel,
            onClose = {
                workoutImportViewModel.clear()
                if (startInImportMode) {
                    onNavigateBack()
                } else {
                    showWorkoutImporter = false
                }
            },
            onSaved = {
                workoutImportViewModel.clear()
                showWorkoutImporter = false
                onNavigateBack()
            }
        )
        return
    }

    fun savePlan() {
        if (isSaving) return
        if (planName.isBlank()) {
            planNameError = context.getString(R.string.plan_builder_plan_name_cannot_be_empty)
            return
        }
        planNameError = null
        if (sessionsList.isEmpty() || sessionsList.any { day -> exercisesList.none { it.planSessionId == day.id } }) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Add at least one exercise to every training day.") }
            return
        }
        com.apexfit.app.utils.PermanentWorkoutGroups.validationError(exercisesList)?.let { error ->
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
            return
        }
        isSaving = true
        val savedPlanId = activePlan?.id ?: 0L
        val finalPlan = activePlan?.copy(
            name = planName.trim(),
            goal = planGoal.trim().ifEmpty { "General Fitness" }
        ) ?: WorkoutPlan(
            name = planName.trim(),
            goal = planGoal.trim().ifEmpty { "General Fitness" },
            isActive = true
        )
        val updatedSessions = sessionsList.map { session ->
            val muscles = exercisesList
                .filter { it.planSessionId == session.id }
                .map { it.muscleGroup }
                .distinct()
            val focus = if (muscles.isEmpty()) "General" else muscles.joinToString(", ")
            session.copy(planId = savedPlanId, focus = focus)
        }
        val updatedExercises = exercisesList.map { exercise ->
            exercise.copy(weight = exercise.weight.coerceAtLeast(0.0))
        }
        coroutineScope.launch {
            try {
                planBuilderViewModel.updateWorkoutPlan(finalPlan, updatedSessions, updatedExercises)
                onNavigateBack()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Couldn't save your plan. Please try again.")
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Plan Builder",
                        fontFamily = InterFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ApexColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            painter = painterResource(ApexIcons.ArrowLeft),
                            contentDescription = "Back",
                            tint = ApexColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ApexColors.Canvas
                )
            )
        },
        bottomBar = { PlanBuilderSaveBar(isSaving, onSave = ::savePlan) }
    ) { innerPadding ->
        val exercisesBySession = remember(exercisesList) {
            exercisesList.groupBy { it.planSessionId }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (planId == 0L && sessionsList.isEmpty() && planName.isBlank()) {
                item {
                    OutlinedButton(
                        onClick = { showWorkoutImporter = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(50.dp),
                        border = BorderStroke(1.dp, ApexColors.Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(painterResource(ApexIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Import workout from text",
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = ApexColors.Primary
                        )
                    }
                }
            }

            // Plan metadata stays on the canvas; the fields are the only surfaces here.
            item {
                PlanBuilderMetadataFields(
                    planName = planName,
                    planGoal = planGoal,
                    planNameError = planNameError,
                    onPlanNameChange = { planName = it; planNameError = null },
                    onPlanGoalChange = { planGoal = it }
                )
            }

            val selectedSession = sessionsList.firstOrNull { it.id == selectedPlanSessionId }
                ?: sessionsList.firstOrNull()
            val selectedSessionExercises = selectedSession?.let { exercisesBySession[it.id].orEmpty() }
                .orEmpty()

            // Keep the weekly schedule together, then show exercises for the selected day.
            item {
                Text(
                    if ((activePlan?.cycleWeeks ?: 1) > 1) "${activePlan?.cycleWeeks}-week rotation" else "Days",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ApexColors.TextPrimary
                )
            }

            if (sessionsList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No training days yet", style = MaterialTheme.typography.titleMedium,
                            color = ApexColors.TextPrimary)
                        Text("Add a day to start building your weekly plan.",
                            style = MaterialTheme.typography.bodyMedium, color = ApexColors.TextSecondary)
                    }
                }
            } else {
                itemsIndexed(sessionsList, key = { _, session -> session.id }) { index, session ->
                    PlanBuilderSessionRow(
                        session = session,
                        cycleWeeks = activePlan?.cycleWeeks ?: 1,
                        isSelected = session.id == selectedSession?.id,
                        canMoveUp = index > 0,
                        canMoveDown = index < sessionsList.lastIndex,
                        onSelect = { selectedPlanSessionId = session.id },
                        onMoveUp = { planBuilderViewModel.reorderSession(index, true) },
                        onMoveDown = { planBuilderViewModel.reorderSession(index, false) },
                        onEdit = {
                            editingSessionId = session.id
                            showAddDayDialog = true
                        },
                        onDelete = { sessionToDelete = session }
                    )
                }
            }

            item {
                TextButton(
                    onClick = {
                        editingSessionId = null
                        showAddDayDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_day_button"),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(painterResource(ApexIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add day", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (selectedSession != null) {
                item(key = "selected_session_${selectedSession.id}") {
                    Text(
                        selectedSession.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ApexColors.TextPrimary
                    )
                }

                if (selectedSessionExercises.isEmpty()) {
                    item(key = "empty_exercises_${selectedSession.id}") {
                        Text(
                            "No exercises yet. Add one to this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApexColors.TextSecondary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(selectedSessionExercises, key = { it.id }) { exercise ->
                        PlanBuilderExerciseRow(
                            exercise = exercise,
                            preferredUnits = preferredUnits,
                            onEdit = {
                                selectedSessionIdForExercise = selectedSession.id
                                editingExerciseId = exercise.id
                                showAddExerciseDialog = true
                            },
                            onDelete = { exerciseToDelete = exercise }
                        )
                    }
                }

                item(key = "groups_${selectedSession.id}") {
                    val groups = selectedSessionExercises.filter { it.groupKey != null }.groupBy { requireNotNull(it.groupKey) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (key, members) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (members.size == 2) "Superset" else "Circuit", style = MaterialTheme.typography.titleSmall)
                                    Text(members.joinToString(" → ") { it.name }, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                                    Text("${members.first().groupTransitionRest}s transition · ${members.first().groupRoundRest}s round rest",
                                        style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                                }
                                TextButton(enabled = !isSaving, onClick = {
                                    groupEditorKey = key; groupEditorSession = selectedSession.id
                                }) { Text("Edit") }
                            }
                        }
                        if (selectedSessionExercises.zipWithNext().any { (a, b) -> a.groupKey == null && b.groupKey == null }) {
                            TextButton(enabled = !isSaving, onClick = {
                                groupEditorKey = null; groupEditorSession = selectedSession.id
                            }) { Text("Create superset / circuit") }
                        }
                    }
                }
                item(key = "add_exercise_${selectedSession.id}") {
                    TextButton(
                        onClick = {
                            selectedSessionIdForExercise = selectedSession.id
                            editingExerciseId = null
                            showAddExerciseDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_exercise_button_${selectedSession.id}"),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(painterResource(ApexIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Add exercise", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // Add / Edit Day Dialog
    if (showAddDayDialog) {
        val session = editingSessionId?.let { planBuilderViewModel.getSessionById(it) }
        PlanBuilderDayDialog(
            initialLabel = session?.label.orEmpty(),
            initialDayOfWeek = session?.day.orEmpty().ifBlank { "Monday" },
            initialCycleWeek = session?.cycleWeek ?: 1,
            cycleWeeks = activePlan?.cycleWeeks ?: 1,
            isEditing = editingSessionId != null,
            onDismiss = { showAddDayDialog = false },
            onSave = { _, _ -> },
            onSaveWithCycle = { dayLabel, selectedDayOfWeek, selectedCycleWeek ->
                val duplicate = (activePlan?.cycleWeeks ?: 1) > 1 && sessionsList.any {
                    it.id != editingSessionId && it.cycleWeek == selectedCycleWeek && it.day.equals(selectedDayOfWeek, true)
                }
                if (duplicate) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("That weekday is already scheduled in this cycle week.") }
                } else {
                if (editingSessionId == null) {
                    val newSession = PlanSession(
                        id = planBuilderViewModel.generateNewSessionId(),
                        planId = activePlan?.id ?: 0L,
                        label = dayLabel,
                        day = selectedDayOfWeek,
                        focus = "General",
                        cycleWeek = selectedCycleWeek
                    )
                    planBuilderViewModel.addSession(newSession)
                    selectedPlanSessionId = newSession.id
                } else {
                    val currentSession = editingSessionId?.let { planBuilderViewModel.getSessionById(it) }
                    if (currentSession != null) {
                        planBuilderViewModel.updateSession(
                            currentSession.copy(label = dayLabel, day = selectedDayOfWeek, cycleWeek = selectedCycleWeek)
                        )
                    }
                }
                showAddDayDialog = false
                }
            }
        )
    }

    groupEditorSession?.let { sessionId ->
        val rows = exercisesList.filter { it.planSessionId == sessionId }
        key(sessionId, groupEditorKey) {
            PermanentGroupEditor(rows, groupEditorKey,
                onDismiss = { groupEditorSession = null; groupEditorKey = null },
                onRemove = { groupKey ->
                    planBuilderViewModel.removePermanentGroup(sessionId, groupKey)
                    groupEditorSession = null; groupEditorKey = null
                },
                onConfirm = { ids, members, transition, round ->
                    val groupKey = groupEditorKey ?: java.util.UUID.randomUUID().toString()
                    if (planBuilderViewModel.configurePermanentGroup(sessionId, ids, groupKey, members, transition, round)) {
                        groupEditorSession = null; groupEditorKey = null
                    } else coroutineScope.launch { snackbarHostState.showSnackbar("Group changed or overlaps another group. Review the exercises and try again.") }
                })
        }
    }

    // Add / Edit Exercise Dialog
    if (showAddExerciseDialog) {
        val exercise = editingExerciseId?.let { planBuilderViewModel.getExerciseById(it) }
        val libraryState by planBuilderViewModel.exerciseLibrary.collectAsStateWithLifecycle()
        val preferredUnits by planBuilderViewModel.units.collectAsStateWithLifecycle()
        val defaultUnit = if (!exercise?.weightUnit.isNullOrBlank()) exercise!!.weightUnit else (if (preferredUnits.lowercase() in listOf("lb", "lbs")) "lb" else "kg")
        var unitSuffix by rememberSaveable { mutableStateOf(defaultUnit) }
        var exName by rememberSaveable { mutableStateOf(exercise?.name ?: "") }
        var selectedMuscle by rememberSaveable { mutableStateOf(exercise?.muscleGroup ?: MuscleGroups.ALL.first()) }
        var setsText by rememberSaveable { mutableStateOf(exercise?.sets?.toString() ?: "3") }
        var repsMinText by rememberSaveable { mutableStateOf(exercise?.repsMin?.toString() ?: "8") }
        var repsMaxText by rememberSaveable { mutableStateOf(exercise?.repsMax?.toString() ?: "12") }
        var weightText by rememberSaveable {
            mutableStateOf(
                if (exercise != null && exercise.weight > 0.0) {
                    if (unitSuffix.lowercase() in listOf("lb", "lbs")) {
                        (Math.round(exercise.weight * AppConstants.KG_TO_LBS * 10.0) / 10.0).toString()
                    } else {
                        exercise.weight.toString()
                    }
                } else {
                    ""
                }
            )
        }
        var restText by rememberSaveable { mutableStateOf(exercise?.restSeconds?.toString() ?: "90") }
        var notesText by rememberSaveable { mutableStateOf(exercise?.notes ?: "") }

        var exNameError by remember { mutableStateOf<String?>(null) }
        var setsError by remember { mutableStateOf<String?>(null) }
        var repsMinError by remember { mutableStateOf<String?>(null) }
        var repsMaxError by remember { mutableStateOf<String?>(null) }
        var restError by remember { mutableStateOf<String?>(null) }
        var weightError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(editingExerciseId, showAddExerciseDialog) {
            if (showAddExerciseDialog) {
                val currentEx = editingExerciseId?.let { planBuilderViewModel.getExerciseById(it) }
                val initialUnit = if (!currentEx?.weightUnit.isNullOrBlank()) currentEx!!.weightUnit else (if (preferredUnits.lowercase() in listOf("lb", "lbs")) "lb" else "kg")
                unitSuffix = initialUnit
                exName = currentEx?.name ?: ""
                selectedMuscle = currentEx?.muscleGroup ?: MuscleGroups.ALL.first()
                setsText = currentEx?.sets?.toString() ?: "3"
                repsMinText = currentEx?.repsMin?.toString() ?: "8"
                repsMaxText = currentEx?.repsMax?.toString() ?: "12"
                weightText = if (currentEx != null && currentEx.weight > 0.0) {
                    if (initialUnit.lowercase() in listOf("lb", "lbs")) {
                        (Math.round(currentEx.weight * AppConstants.KG_TO_LBS * 10.0) / 10.0).toString()
                    } else {
                        currentEx.weight.toString()
                    }
                } else {
                    ""
                }
                restText = currentEx?.restSeconds?.toString() ?: "90"
                notesText = currentEx?.notes ?: ""
            }
        }

        PlanBuilderExerciseEditorHost(
            libraryState = libraryState,
                form = PlanBuilderExerciseEditorForm(
                    name = exName,
                    muscleGroup = selectedMuscle,
                    sets = setsText,
                    minReps = repsMinText,
                    maxReps = repsMaxText,
                    weight = weightText,
                    weightUnit = unitSuffix,
                    restSeconds = restText,
                    notes = notesText,
                    nameError = exNameError,
                    setsError = setsError,
                    minRepsError = repsMinError,
                    maxRepsError = repsMaxError,
                    weightError = weightError,
                    restError = restError
                ),
                isEditing = editingExerciseId != null,
                onDismiss = { showAddExerciseDialog = false },
                onFieldChange = { field, value ->
                    when (field) {
                        PlanBuilderExerciseEditorField.NAME -> {
                            exName = value
                            exNameError = null
                        }
                        PlanBuilderExerciseEditorField.SETS -> {
                            setsText = value
                            setsError = null
                        }
                        PlanBuilderExerciseEditorField.MIN_REPS -> {
                            repsMinText = value
                            repsMinError = null
                        }
                        PlanBuilderExerciseEditorField.MAX_REPS -> {
                            repsMaxText = value
                            repsMaxError = null
                            repsMinError = null
                        }
                        PlanBuilderExerciseEditorField.WEIGHT -> {
                            weightText = value
                            weightError = null
                        }
                        PlanBuilderExerciseEditorField.REST -> {
                            restText = value
                            restError = null
                        }
                        PlanBuilderExerciseEditorField.NOTES -> notesText = value
                    }
                },
                onMuscleChange = { selectedMuscle = it },
                onWeightUnitChange = { unit ->
                    if (!unitSuffix.equals(unit, ignoreCase = true)) {
                        val currentVal = weightText.replace(',', '.').toDoubleOrNull()
                        if (currentVal != null) {
                            val converted = if (unit == "lb") {
                                Math.round(currentVal * AppConstants.KG_TO_LBS * 10.0) / 10.0
                            } else {
                                Math.round((currentVal / AppConstants.KG_TO_LBS) * 10.0) / 10.0
                            }
                            weightText = converted.toString()
                        }
                        unitSuffix = unit
                    }
                },
                onExerciseSelected = { match ->
                    exName = match.name
                    selectedMuscle = match.primaryMuscle
                    exNameError = null
                    val previousRest = restText
                    coroutineScope.launch {
                        val suggested = planBuilderViewModel.exerciseDefaultRestSeconds(match.id)
                        if (exName == match.name && restText == previousRest && suggested != null) {
                            restText = suggested.toString()
                        }
                    }
                },
                onSave = saveAction@{
                    exNameError = null
                    setsError = null
                    repsMinError = null
                    repsMaxError = null
                    restError = null
                    weightError = null

                    val validation = validatePlanExerciseDraft(
                        name = exName,
                        setsText = setsText,
                        minRepsText = repsMinText,
                        maxRepsText = repsMaxText,
                        weightText = weightText,
                        weightUnit = unitSuffix,
                        restText = restText
                    )
                    if (validation is PlanExerciseDraftValidation.Invalid) {
                        val numericError = context.getString(R.string.plan_builder_numeric_values_must_be_valid)
                        when (validation.field) {
                            PlanExerciseDraftField.NAME -> exNameError = context.getString(
                                R.string.plan_builder_exercise_name_is_required
                            )
                            PlanExerciseDraftField.SETS -> setsError = if (
                                validation.issue == PlanExerciseDraftIssue.OUT_OF_RANGE
                            ) context.getString(R.string.plan_builder_sets_range) else numericError
                            PlanExerciseDraftField.MIN_REPS -> repsMinError = when (validation.issue) {
                                PlanExerciseDraftIssue.MIN_REPS_EXCEEDS_MAX -> context.getString(
                                    R.string.plan_builder_min_reps_greater_than_max
                                )
                                PlanExerciseDraftIssue.OUT_OF_RANGE -> context.getString(
                                    R.string.plan_builder_reps_range
                                )
                                else -> numericError
                            }
                            PlanExerciseDraftField.MAX_REPS -> repsMaxError = if (
                                validation.issue == PlanExerciseDraftIssue.OUT_OF_RANGE
                            ) context.getString(R.string.plan_builder_reps_range) else numericError
                            PlanExerciseDraftField.REST -> restError = if (
                                validation.issue == PlanExerciseDraftIssue.OUT_OF_RANGE
                            ) context.getString(R.string.plan_builder_rest_range) else numericError
                            PlanExerciseDraftField.WEIGHT -> {
                                val maxWeight = if (unitSuffix.lowercase() in listOf("lb", "lbs")) 660.0 else 300.0
                                weightError = context.getString(
                                    R.string.plan_builder_weight_range,
                                    maxWeight.toString(),
                                    unitSuffix
                                )
                            }
                        }
                        return@saveAction
                    }
                    val validDraft = validation as PlanExerciseDraftValidation.Valid

                    val targetSessionId = selectedSessionIdForExercise ?: return@saveAction

                    if (editingExerciseId == null) {
                        val newId = planBuilderViewModel.generateNewSessionId()
                        val newEx = PlanExercise(
                            id = newId,
                            planSessionId = targetSessionId,
                            name = validDraft.name,
                            muscleGroup = selectedMuscle,
                            sets = validDraft.sets,
                            repsMin = validDraft.minReps,
                            repsMax = validDraft.maxReps,
                            weight = validDraft.weightKg,
                            weightUnit = "kg",
                            restSeconds = validDraft.restSeconds,
                            notes = notesText.trim()
                        )
                        planBuilderViewModel.addExercise(newEx)
                    } else {
                        val exercise = editingExerciseId?.let { planBuilderViewModel.getExerciseById(it) }
                        if (exercise != null) {
                            val updated = exercise.copy(
                                name = validDraft.name,
                                muscleGroup = selectedMuscle,
                                sets = validDraft.sets,
                                repsMin = validDraft.minReps,
                                repsMax = validDraft.maxReps,
                                weight = validDraft.weightKg,
                                weightUnit = "kg",
                                restSeconds = validDraft.restSeconds,
                                notes = notesText.trim()
                            )
                            planBuilderViewModel.updateExercise(updated)
                        }
                    }

                    showAddExerciseDialog = false
                }
            )
    }

    // Session Deletion Dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = "Delete Session?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${sessionToDelete?.label}'? This cannot be undone.",
                    fontFamily = JetBrainsMonoFamily,
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { deleting ->
                            planBuilderViewModel.deleteSession(deleting.id)
                            if (selectedPlanSessionId == deleting.id) selectedPlanSessionId = null
                        }
                        sessionToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.plan_builder_delete), color = RedAccent, fontFamily = SyneFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.plan_builder_cancel_1), fontFamily = SyneFamily, color = SecondaryText)
                }
            }
        )
    }

    // Exercise Deletion Dialog
    if (exerciseToDelete != null) {
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = "Delete Exercise?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${exerciseToDelete?.name}'? This cannot be undone." +
                        if (exerciseToDelete?.groupKey != null) " If only one group member remains, the group will be removed." else "",
                    fontFamily = JetBrainsMonoFamily,
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exerciseToDelete?.let { planBuilderViewModel.deleteExercise(it.id) }
                        exerciseToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.plan_builder_delete), color = RedAccent, fontFamily = SyneFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text(stringResource(R.string.plan_builder_cancel_1), fontFamily = SyneFamily, color = SecondaryText)
                }
            }
        )
    }
}


@Composable
private fun PermanentGroupEditor(rows: List<PlanExercise>, existingKey: String?,
                                 onDismiss: () -> Unit, onRemove: (String) -> Unit,
                                 onConfirm: (List<Long>, List<Long>, Int, Int) -> Unit) {
    val existing = rows.filter { existingKey != null && it.groupKey == existingKey }
    val snapshotIds = remember { rows.map { it.id } }
    val eligible = rows.map { it.groupKey == null || it.groupKey == existingKey }
    val defaultStart = if (existing.isNotEmpty()) rows.indexOf(existing.first()) else
        eligible.zipWithNext().indexOfFirst { (a, b) -> a && b }.coerceAtLeast(0)
    var first by rememberSaveable { mutableIntStateOf(defaultStart) }
    var count by rememberSaveable { mutableIntStateOf(existing.size.coerceAtLeast(2)) }
    var transition by rememberSaveable { mutableStateOf(existing.firstOrNull()?.groupTransitionRest?.toString() ?: "15") }
    var round by rememberSaveable { mutableStateOf(existing.firstOrNull()?.groupRoundRest?.toString() ?: "120") }
    val maxCount = eligible.drop(first.coerceIn(0, eligible.size)).takeWhile { it }.size
    val selected = rows.drop(first.coerceAtLeast(0)).take(count.coerceAtLeast(0))
    val between = transition.toIntOrNull()
    val after = round.toIntOrNull()
    val valid = rows.map { it.id } == snapshotIds && count in 2..maxCount &&
        between != null && between in 1..3600 && after != null && after in 1..3600
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existingKey == null) "Create exercise group" else "Edit exercise group") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose consecutive exercises. Existing groups cannot overlap.")
                rows.forEachIndexed { index, exercise ->
                    val canStart = eligible.getOrNull(index) == true && eligible.getOrNull(index + 1) == true
                    Row(Modifier.fillMaxWidth().clickable(enabled = canStart, role = Role.RadioButton) {
                        first = index; count = 2
                    }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = index == first, onClick = null, enabled = canStart)
                        Text(exercise.name, color = if (canStart) ApexColors.TextPrimary else ApexColors.TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Members: $count", Modifier.weight(1f))
                    TextButton(enabled = count > 2, onClick = { count-- }) { Text("−") }
                    TextButton(enabled = count < maxCount, onClick = { count++ }) { Text("+") }
                }
                Text(selected.joinToString(" → ") { it.name }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(transition, { transition = it.filter(Char::isDigit).take(4) },
                    label = { Text("Transition rest (sec)") }, singleLine = true)
                OutlinedTextField(round, { round = it.filter(Char::isDigit).take(4) },
                    label = { Text("Round rest (sec)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onConfirm(snapshotIds, selected.map { it.id }, requireNotNull(between), requireNotNull(after))
        }) { Text("Save group") } },
        dismissButton = { Row {
            if (existingKey != null) TextButton(onClick = { onRemove(existingKey) }) { Text("Remove group") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } })
}
