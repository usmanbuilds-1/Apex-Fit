@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource
import com.apexfit.app.ui.models.UiState

import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    initialValidationError: String? = null
) {
    var dayLabel by rememberSaveable(isEditing, initialLabel) { mutableStateOf(initialLabel) }
    var dayLabelError by rememberSaveable(isEditing, initialLabel) { mutableStateOf(initialValidationError) }
    var selectedDayOfWeek by rememberSaveable(isEditing, initialDayOfWeek) {
        mutableStateOf(initialDayOfWeek)
    }
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
                        onSave(dayLabel.trim(), selectedDayOfWeek)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = planName,
            onValueChange = onPlanNameChange,
            isError = planNameError != null,
            supportingText = planNameError?.let { { Text(it) } },
            label = { Text(stringResource(R.string.plan_builder_plan_name)) },
            textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary),
            modifier = Modifier.fillMaxWidth().testTag("plan_name_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ApexColors.Primary,
                unfocusedBorderColor = ApexColors.BorderSubtle,
                focusedLabelColor = ApexColors.Primary,
                unfocusedLabelColor = ApexColors.TextSecondary,
                cursorColor = ApexColors.Primary
            ),
            singleLine = true
        )
        OutlinedTextField(
            value = planGoal,
            onValueChange = onPlanGoalChange,
            label = { Text("Goal") },
            textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary),
            modifier = Modifier.fillMaxWidth().testTag("plan_goal_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ApexColors.Primary,
                unfocusedBorderColor = ApexColors.BorderSubtle,
                focusedLabelColor = ApexColors.Primary,
                unfocusedLabelColor = ApexColors.TextSecondary,
                cursorColor = ApexColors.Primary
            ),
            singleLine = true
        )
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
    var editingSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Plan Builder",
                        style = MaterialTheme.typography.titleLarge,
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            // Training Sessions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Days",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ApexColors.TextPrimary
                    )
                    TextButton(
                        onClick = {
                            editingSessionId = null
                            showAddDayDialog = true
                        },
                        modifier = Modifier.testTag("add_day_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(painterResource(ApexIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Add day", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
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
                    val exercisesForSession = remember(exercisesList, session.id) { exercisesList.filter { it.planSessionId == session.id } }
                    val sessionExercises = exercisesForSession
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_card_${session.id}"),
                        colors = CardDefaults.cardColors(containerColor = ApexColors.Canvas),
                        shape = RoundedCornerShape(0.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Session Title Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ApexColors.TextPrimary
                                    )
                                    Text(
                                        text = "${session.day} · ${sessionExercises.size} exercises",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ApexColors.TextSecondary
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            planBuilderViewModel.reorderSession(index, true)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(ApexIcons.ArrowUp),
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) ApexColors.TextPrimary else ApexColors.TextTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            planBuilderViewModel.reorderSession(index, false)
                                        },
                                        enabled = index < sessionsList.size - 1,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(ApexIcons.ArrowDown),
                                            contentDescription = "Move Down",
                                            tint = if (index < sessionsList.size - 1) ApexColors.TextPrimary else ApexColors.TextTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Edit Day
                                    IconButton(
                                        onClick = {
                                            editingSessionId = session.id
                                            showAddDayDialog = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(ApexIcons.Edit),
                                            contentDescription = "Edit Day",
                                            tint = ApexColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Delete Day
                                    IconButton(
                                        onClick = {
                                            sessionToDelete = session
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(ApexIcons.Delete),
                                            contentDescription = "Delete Day",
                                            tint = ApexColors.Alert,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Exercises Sub-List
                            if (sessionExercises.isEmpty()) {
                                Text(
                                    "No exercises yet. Add one to this day.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexColors.TextSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column {
                                    sessionExercises.forEachIndexed { exIndex, exercise ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = exercise.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ApexColors.TextPrimary
                                                )
                                                Text(
                                                    text = "${exercise.muscleGroup} · ${exercise.sets} × ${exercise.repsMin}–${exercise.repsMax} reps · ${exercise.restSeconds}s rest",
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

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        selectedSessionIdForExercise = session.id
                                                        editingExerciseId = exercise.id
                                                        showAddExerciseDialog = true
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(ApexIcons.Edit),
                                                        contentDescription = "Edit Exercise",
                                                        tint = ApexColors.TextSecondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        exerciseToDelete = exercise
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(ApexIcons.Close),
                                                        contentDescription = "Delete Exercise",
                                                        tint = ApexColors.Alert,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (exIndex < sessionExercises.lastIndex) {
                                            HorizontalDivider(color = ApexColors.BorderSubtle)
                                        }
                                    }
                                }
                            }

                            // Add Exercise Button
                            TextButton(
                                onClick = {
                                    selectedSessionIdForExercise = session.id
                                    editingExerciseId = null
                                    showAddExerciseDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_exercise_button_${session.id}"),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(painterResource(ApexIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Add exercise", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
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
            isEditing = editingSessionId != null,
            onDismiss = { showAddDayDialog = false },
            onSave = { dayLabel, selectedDayOfWeek ->
                if (editingSessionId == null) {
                    planBuilderViewModel.addSession(
                        PlanSession(
                            id = planBuilderViewModel.generateNewSessionId(),
                            planId = activePlan?.id ?: 0L,
                            label = dayLabel,
                            day = selectedDayOfWeek,
                            focus = "General"
                        )
                    )
                } else {
                    val currentSession = editingSessionId?.let { planBuilderViewModel.getSessionById(it) }
                    if (currentSession != null) {
                        planBuilderViewModel.updateSession(
                            currentSession.copy(label = dayLabel, day = selectedDayOfWeek)
                        )
                    }
                }
                showAddDayDialog = false
            }
        )
    }

    // Add / Edit Exercise Dialog
    if (showAddExerciseDialog) {
        val exercise = editingExerciseId?.let { planBuilderViewModel.getExerciseById(it) }
        val libraryState by planBuilderViewModel.exerciseLibrary.collectAsStateWithLifecycle()
        var showExercisePicker by rememberSaveable { mutableStateOf(false) }
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

        if (showExercisePicker) {
            ExercisePickerDialog(
                state = libraryState, title = "Choose exercise",
                onDismiss = { showExercisePicker = false },
                onSelect = { match ->
                    exName = match.name
                    selectedMuscle = match.primaryMuscle
                    exNameError = null
                    showExercisePicker = false
                    val previousRest = restText
                    coroutineScope.launch {
                        val suggested = planBuilderViewModel.exerciseDefaultRestSeconds(match.id)
                        if (exName == match.name && restText == previousRest && suggested != null) {
                            restText = suggested.toString()
                        }
                    }
                }
            )
        }

        if (!showExercisePicker) {
            PlanBuilderExerciseEditorDialog(
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
                onBrowseLibrary = { showExercisePicker = true },
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
                        sessionToDelete?.let { planBuilderViewModel.deleteSession(it.id) }
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
                    text = "Are you sure you want to delete '${exerciseToDelete?.name}'? This cannot be undone.",
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

