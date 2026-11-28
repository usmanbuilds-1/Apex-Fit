package com.apexfit.app.ui.screens
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.*
import com.apexfit.app.R
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TRAIN TAB
@Composable
fun TrainTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    planBuilderViewModel: PlanBuilderViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    restTimerViewModel: RestTimerViewModel,
    onNavigateToPlanBuilder: (Long) -> Unit,
    onNavigateToWorkoutImport: () -> Unit
) {
    val subTab by fitnessViewModel.trainSubTab.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val libraryUnits by fitnessViewModel.units.collectAsStateWithLifecycle()
    val libraryContext = LocalContext.current
    val libraryScope = rememberCoroutineScope()
    var libraryDetail by remember { mutableStateOf<Pair<com.apexfit.app.data.Exercise, FormGuideContent>?>(null) }
    var libraryError by remember { mutableStateOf(false) }
    val recoveryRequired by workoutExecutionViewModel.sessionManager.sessionRestoreFailed.collectAsStateWithLifecycle()
    var recoveryDismissed by remember(recoveryRequired, subTab) { mutableStateOf(false) }
    var discardingRecovery by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf(false) }
    // Functional recovery gate only: no redesign or navigation changes.
    if (recoveryRequired && !recoveryDismissed) {
        AlertDialog(
            onDismissRequest = { if (!discardingRecovery) recoveryDismissed = true },
            title = { Text("Saved workout needs attention") },
            text = {
                Text(
                    if (recoveryError) "Could not discard the saved draft. Try again."
                    else "The previous workout could not be safely restored. Its saved data has been kept. Keep it for recovery, or discard the saved draft to start a new workout. Workouts already in your history are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !discardingRecovery,
                    onClick = {
                        discardingRecovery = true
                        libraryScope.launch {
                            try {
                                recoveryError = !workoutExecutionViewModel.sessionManager.discardUnrestorableSession()
                            } finally {
                                discardingRecovery = false
                            }
                        }
                    }
                ) { Text("Discard draft") }
            },
            dismissButton = {
                TextButton(enabled = !discardingRecovery, onClick = { recoveryDismissed = true }) { Text("Keep for now") }
            }
        )
    }

    if (showLibrary) {
        val library by planBuilderViewModel.exerciseLibrary.collectAsStateWithLifecycle()
        BackHandler {
            if (libraryDetail != null) libraryDetail = null else showLibrary = false
        }
        val detail = libraryDetail
        if (detail != null) {
            FormGuideSheet(detail.first, detail.second, preferredUnits = libraryUnits,
                onDismiss = { libraryDetail = null })
        } else {
            ExercisePicker(library, "Exercise library", onBack = { showLibrary = false }, onSelect = { exercise ->
                libraryScope.launch {
                    libraryDetail = runCatching {
                        loadFormGuideData(libraryContext, exercise.name)
                    }.getOrNull()
                    libraryError = libraryDetail == null
                }
            })
        }
        if (libraryError) {
            AlertDialog(onDismissRequest = { libraryError = false },
                title = { Text("Exercise details unavailable") },
                confirmButton = { TextButton(onClick = { libraryError = false }) { Text("Close") } })
        }
        return
    }


    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (subTab == 2) "Plans" else "Train", color = PrimaryText,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (subTab != 2) {
                    Icon(painter = painterResource(R.drawable.ic_apex_logo), contentDescription = null,
                        tint = ApexColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("APEX FIT", color = PrimaryText, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            TrainSubTabControl(
                labels = listOf("Program", "Workout", "Plans"),
                selectedIndex = subTab,
                onSelect = fitnessViewModel::setTrainSubTab,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (subTab) {
                0 -> ProgramSubTab(
                    fitnessViewModel = fitnessViewModel,
                    planBuilderViewModel = planBuilderViewModel,
                    workoutExecutionViewModel = workoutExecutionViewModel,
                    onOpenLibrary = { showLibrary = true },
                    modifier = Modifier.weight(1f)
                )
                1 -> WorkoutExecutionSubTab(fitnessViewModel, workoutExecutionViewModel, restTimerViewModel, snackbarHostState)
                2 -> NewPlansSubTab(
                    planBuilderViewModel = planBuilderViewModel,
                    onNavigateToPlanBuilder = onNavigateToPlanBuilder,
                    onNavigateToWorkoutImport = onNavigateToWorkoutImport,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun TrainSubTabControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                var labelWidthPx by remember(label) { mutableIntStateOf(0) }
                Column(
                    modifier = Modifier
                        .semantics { selected = isSelected }
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .heightIn(min = 48.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        label,
                        color = if (isSelected) ApexColors.Primary else ApexColors.TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 10.dp),
                        onTextLayout = { labelWidthPx = it.size.width }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (isSelected) {
                        Box(
                            Modifier.width(with(density) { labelWidthPx.toDp() })
                                .height(2.dp).background(ApexColors.Primary)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}

@Composable
fun ProgramSubTab(
    fitnessViewModel: FitnessViewModel,
    planBuilderViewModel: PlanBuilderViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedDay by planBuilderViewModel.selectedDayOfWeek.collectAsStateWithLifecycle()
    val selectedDaySession by planBuilderViewModel.selectedDaySession.collectAsStateWithLifecycle()
    val exercises by planBuilderViewModel.selectedDayExercises.collectAsStateWithLifecycle()
    val sessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val cycleWeek by planBuilderViewModel.activeCycleWeek.collectAsStateWithLifecycle()
    val currentPlan by planBuilderViewModel.activePlan.collectAsStateWithLifecycle()
    val allExercises by planBuilderViewModel.allPlanExercises.collectAsStateWithLifecycle()
    val isStarting by workoutExecutionViewModel.sessionManager.isStartingSession.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var swapExercise by remember { mutableStateOf<com.apexfit.app.data.PlanExercise?>(null) }
    var pendingSwap by remember { mutableStateOf<com.apexfit.app.data.PlanExercise?>(null) }
    var replacement by remember { mutableStateOf<com.apexfit.app.data.Exercise?>(null) }
    var swapMessage by remember { mutableStateOf<String?>(null) }
    var formGuideExercise by remember { mutableStateOf<com.apexfit.app.data.Exercise?>(null) }
    var formGuideContent by remember { mutableStateOf<FormGuideContent?>(null) }
    var formGuidePlanExercise by remember { mutableStateOf<com.apexfit.app.data.PlanExercise?>(null) }

    val onOpenFormGuide: (com.apexfit.app.data.PlanExercise) -> Unit = remember(scope, context, units) {
        { planExercise ->
            scope.launch {
                loadFormGuideData(context, planExercise.name, planExercise)?.let { (exercise, content) ->
                    formGuideExercise = exercise
                    formGuideContent = content
                    formGuidePlanExercise = planExercise
                }
            }
        }
    }

    val swap = swapExercise
    if (swap != null) {
        val library by planBuilderViewModel.exerciseLibrary.collectAsStateWithLifecycle()
        ExercisePickerDialog(
            state = library,
            title = "Substitute exercise",
            initialMuscle = swap.muscleGroup,
            excludeId = swap.exerciseId,
            onDismiss = { swapExercise = null },
            onSelect = {
                replacement = it
                swapExercise = null
                pendingSwap = swap
            }
        )
    }

    val selectedReplacement = replacement
    val original = pendingSwap
    if (selectedReplacement != null && original != null) {
        AlertDialog(
            onDismissRequest = { replacement = null },
            title = { Text("Substitute exercise?") },
            text = {
                Text("Replace " + original.name + " with " + selectedReplacement.name +
                    ". The new exercise starts with load calibration.")
            },
            dismissButton = { TextButton(onClick = { replacement = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    replacement = null
                    scope.launch {
                        val updated = planBuilderViewModel.substituteScheduledExercise(original, selectedReplacement)
                        swapMessage = if (updated) "Exercise updated" else "The exercise could not be updated."
                    }
                }) { Text("Replace") }
            }
        )
    }

    swapMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { swapMessage = null },
            title = { Text(message) },
            confirmButton = { TextButton(onClick = { swapMessage = null }) { Text("Close") } }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        TrainProgramSchedule(
            sessionsState = sessionsState,
            cycleWeek = cycleWeek,
            cycleWeeks = (currentPlan as? UiState.Success)?.data?.cycleWeeks ?: 1,
            allExercises = allExercises,
            selectedDay = selectedDay,
            selectedSession = selectedDaySession,
            selectedExercises = exercises,
            preferredUnits = units,
            isStarting = isStarting,
            onSelectDay = planBuilderViewModel::selectDay,
            onStartSession = { session ->
                if (!isStarting) {
                    workoutExecutionViewModel.startWorkoutSession(session)
                    fitnessViewModel.setTrainSubTab(1)
                }
            },
            onOpenPlans = { fitnessViewModel.setTrainSubTab(2) },
            onOpenLibrary = onOpenLibrary,
            onOpenFormGuide = onOpenFormGuide,
            onSubstituteExercise = { swapExercise = it },
            modifier = Modifier.fillMaxSize()
        )

        val guideExercise = formGuideExercise
        val guideContent = formGuideContent
        if (guideExercise != null && guideContent != null) {
            FormGuideSheet(
                exercise = guideExercise,
                content = guideContent,
                preferredUnits = units,
                onDismiss = {
                    formGuideExercise = null
                    formGuideContent = null
                    formGuidePlanExercise = null
                },
                onSelectAlternative = { selected ->
                    val originalExercise = formGuidePlanExercise
                    if (originalExercise != null) {
                        formGuideExercise = null
                        formGuideContent = null
                        formGuidePlanExercise = null
                        replacement = selected
                        pendingSwap = originalExercise
                    }
                }
            )
        }
    }
}

@Composable
fun WorkoutExecutionSubTab(
    fitnessViewModel: FitnessViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel,
    restTimerViewModel: RestTimerViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val activeSession by workoutExecutionViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val exercises by workoutExecutionViewModel.activeExercises.collectAsStateWithLifecycle()
    val currentIdx by workoutExecutionViewModel.currentExerciseIdx.collectAsStateWithLifecycle()
    val loggedSets by workoutExecutionViewModel.loggedSets.collectAsStateWithLifecycle()
    val effectiveSetsMap by workoutExecutionViewModel.effectiveSetsStateFlow.collectAsStateWithLifecycle()
    val warmupComp by workoutExecutionViewModel.warmupCompleted.collectAsStateWithLifecycle()
    val lastWeights by workoutExecutionViewModel.lastWeights.collectAsStateWithLifecycle()
    val weightContextLines by workoutExecutionViewModel.weightContextLines.collectAsStateWithLifecycle()
    val liveSession by workoutExecutionViewModel.sessionManager.activeSession.collectAsStateWithLifecycle()
    var showCircuitEditor by rememberSaveable { mutableStateOf(false) }
    var circuitCount by rememberSaveable { mutableStateOf("2") }
    var transitionRest by rememberSaveable { mutableStateOf("15") }
    var roundRest by rememberSaveable { mutableStateOf("90") }
    val circuitSession = liveSession
    val circuit = circuitSession?.circuit
    if (showCircuitEditor && circuitSession != null) {
        val count = circuitCount.toIntOrNull()
        val between = transitionRest.toIntOrNull()
        val round = roundRest.toIntOrNull()
        val valid = count != null && count in 2..circuitSession.exercises.size &&
            between != null && between in 1..3600 && round != null && round in 1..3600 &&
            com.apexfit.app.utils.CircuitExecution.canConfigure(circuitSession)
        AlertDialog(
            onDismissRequest = { showCircuitEditor = false },
            title = { Text("Workout circuit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Group the first consecutive exercises for this workout only. Sets alternate in rounds; completed or skipped members are bypassed.")
                    OutlinedTextField(circuitCount, { if (it.length <= 3 && it.all(Char::isDigit)) circuitCount = it },
                        label = { Text("Number of initial exercises (2 or more)") }, singleLine = true)
                    if (count != null && count in 2..circuitSession.exercises.size) {
                        Text(circuitSession.exercises.take(count).joinToString(" → ") { it.exerciseName },
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(transitionRest, { if (it.length <= 4 && it.all(Char::isDigit)) transitionRest = it },
                        label = { Text("Between members (seconds)") }, singleLine = true)
                    OutlinedTextField(roundRest, { if (it.length <= 4 && it.all(Char::isDigit)) roundRest = it },
                        label = { Text("Between rounds / after group (seconds)") }, singleLine = true)
                    Text("Rest values must be 1–3600 seconds. Choose enough rest for your exercises; grouping does not guarantee better results.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                workoutExecutionViewModel.configureCircuit(com.apexfit.app.data.ActiveCircuit(count!!, between!!, round!!))
                showCircuitEditor = false
            }) { Text("Save group") } },
            dismissButton = {
                Row {
                    if (circuit != null) TextButton(onClick = {
                        workoutExecutionViewModel.configureCircuit(null); showCircuitEditor = false
                    }) { Text("Remove group") }
                    TextButton(onClick = { showCircuitEditor = false }) { Text("Cancel") }
                }
            }
        )
    }
    val volumeAdjustments = remember(liveSession) {
        liveSession?.exercises.orEmpty().mapNotNull { exercise ->
            exercise.volumeAdjustment?.let { exercise.executionKey to it }
        }.toMap()
    }
    val preferredUnits by workoutExecutionViewModel.units.collectAsStateWithLifecycle()
    val saveError by workoutExecutionViewModel.saveError.collectAsStateWithLifecycle()
    val setSaveError by workoutExecutionViewModel.setSaveError.collectAsStateWithLifecycle()
    val savingSet by workoutExecutionViewModel.isSavingSet.collectAsStateWithLifecycle()
    val showRirOverlay by workoutExecutionViewModel.showRirOverlay.collectAsStateWithLifecycle()
    var advancedEditor by remember { mutableStateOf(false) }
    val advancedExercise = liveSession?.exercises?.getOrNull(currentIdx)
    val advancedIndex = advancedExercise?.sets?.indexOfFirst { !it.completed && !it.isWarmup } ?: -1
    val advancedSet = advancedExercise?.sets?.getOrNull(advancedIndex)
    if (advancedEditor && advancedExercise != null && advancedSet != null && liveSession?.circuit == null) {
        AdvancedBoutEditor(advancedSet, onDismiss = { advancedEditor = false }, onSave = { weight, reps, details ->
            if (workoutExecutionViewModel.logAdvancedBout(advancedExercise.executionKey, advancedIndex, weight, reps, details) != null)
                advancedEditor = false
        })
    }
     val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentExercise = exercises.getOrNull(currentIdx)
    val currentExerciseEquipment by produceState<String?>(
        initialValue = null,
        currentExercise?.exerciseId,
        currentExercise?.name
    ) {
        value = currentExercise?.let { scheduled ->
            try {
                withContext(Dispatchers.IO) {
                    val dao = com.apexfit.app.data.AppDatabase.getDatabase(context).fitnessDao()
                    (dao.getExerciseById(scheduled.exerciseId)
                        ?: dao.getExerciseByName(scheduled.name))?.equipmentRequired
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    var workoutClockNow by remember(liveSession?.startTime) {
        mutableStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(liveSession?.startTime) {
        if (liveSession != null) {
            while (true) {
                workoutClockNow = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

     var showFinishEarlyDialog by rememberSaveable { mutableStateOf(false) }
    var showNormalFinishFeelDialog by rememberSaveable { mutableStateOf(false) }
    var selectedFeelRating by rememberSaveable { mutableStateOf(4) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    var formGuideExercise by remember { mutableStateOf<com.apexfit.app.data.Exercise?>(null) }
    var formGuideContent by remember { mutableStateOf<FormGuideContent?>(null) }
    val onOpenFormGuide: (com.apexfit.app.data.PlanExercise) -> Unit = remember(scope, context, preferredUnits) {
        { planExercise ->
            scope.launch {
                loadFormGuideData(context, planExercise.name, planExercise)?.let { (exercise, content) ->
                    formGuideExercise = exercise
                    formGuideContent = content
                }
            }
        }
    }

    LaunchedEffect(setSaveError, showRirOverlay) {
        if (setSaveError != null && !showRirOverlay) {
            snackbarHostState.showSnackbar(setSaveError.orEmpty())
            workoutExecutionViewModel.dismissSetSaveError()
        }
    }

    saveError?.let { err ->
        AlertDialog(
            onDismissRequest = { workoutExecutionViewModel.dismissSaveError() },
            containerColor = DarkCardSurface,
            title = { Text(stringResource(R.string.train_save_failed), color = ApexColors.Alert, fontFamily = SyneFamily, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = SecondaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { workoutExecutionViewModel.finishWorkoutSession(selectedFeelRating) }) {
                    Text(stringResource(R.string.train_retry_full), color = ApexColors.Primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { workoutExecutionViewModel.savePartialAndExit(selectedFeelRating) }) {
                        Text(stringResource(R.string.train_retry_partial), color = AccentSecondary)
                    }
                    TextButton(onClick = { workoutExecutionViewModel.dismissSaveError() }) {
                        Text(stringResource(R.string.train_cancel), color = MutedText)
                    }
                }
            }
        )
    }

    BackHandler(enabled = activeSession != null) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "EXIT WORKOUT?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                val compSetsCount = loggedSets.values.flatten().count { it.completed }
                Text(
                    text = "You have $compSetsCount completed sets. Do you want to save them now, discard everything, or keep working out?",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        showFinishEarlyDialog = true
                    }
                ) {
                    Text(stringResource(R.string.train_save_exit), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = ApexColors.Primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            workoutExecutionViewModel.cancelActiveWorkout()
                        }
                    ) {
                        Text(stringResource(R.string.train_discard), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = ApexColors.Alert)
                    }
                    TextButton(
                        onClick = {
                            showExitDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.train_keep_working_out), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = MutedText)
                    }
                }
            }
        )
    }

    if (showFinishEarlyDialog) {
        AlertDialog(
            onDismissRequest = { showFinishEarlyDialog = false },
            title = {
                Text(
                    text = "Finish workout early?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to finish your workout session now? Any completed sets logged so far will be saved and recorded to your athletic log.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "HOW DID IT FEEL? (1–5)",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AmberAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { rate ->
                            val isSelected = selectedFeelRating == rate
                            IconButton(
                                onClick = { selectedFeelRating = rate },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) AmberAccent else DarkRaised,
                                        CircleShape
                                    )
                            ) {
                                Text(
                                    text = "$rate",
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else PrimaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedFeelRating) {
                            1 -> "1 · Very fatigued"
                            2 -> "2 · Tired"
                            3 -> "3 · Typical"
                            4 -> "4 · Energetic"
                            else -> "5 · Very good"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishEarlyDialog = false
                        workoutExecutionViewModel.finishWorkoutEarly(selectedFeelRating)
                    }
                ) {
                    Text(
                        text = "SAVE AND FINISH",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishEarlyDialog = false }
                ) {
                    Text(
                        text = "BACK TO WORKOUT",
                        fontFamily = SyneFamily,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = DarkCardSurface,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showNormalFinishFeelDialog) {
        AlertDialog(
            onDismissRequest = { showNormalFinishFeelDialog = false },
            title = {
                Text(
                    text = "Finish workout?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Save the completed sets in this session and rate how the workout felt.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "HOW DID IT FEEL? (1–5)",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AmberAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { rate ->
                            val isSelected = selectedFeelRating == rate
                            IconButton(
                                onClick = { selectedFeelRating = rate },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) AmberAccent else DarkRaised,
                                        CircleShape
                                    )
                            ) {
                                Text(
                                    text = "$rate",
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else PrimaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedFeelRating) {
                            1 -> "1 · Very fatigued"
                            2 -> "2 · Tired"
                            3 -> "3 · Typical"
                            4 -> "4 · Energetic"
                            else -> "5 · Very good"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNormalFinishFeelDialog = false
                        workoutExecutionViewModel.finishWorkoutSession(selectedFeelRating)
                    }
                ) {
                    Text(
                        text = "SAVE AND FINISH",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNormalFinishFeelDialog = false }
                ) {
                    Text(
                        text = "BACK TO WORKOUT",
                        fontFamily = SyneFamily,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = DarkCardSurface,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            shape = RoundedCornerShape(14.dp)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (activeSession == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(ApexIcons.Play),
                            contentDescription = "Inactive",
                            tint = MutedText,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NO WORKOUT RUNNING",
                            fontFamily = SyneFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose a workout from your program to get started.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        TextButton(onClick = { fitnessViewModel.setTrainSubTab(0) }) {
                            Text("View program", color = ApexColors.Primary)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    val elapsedSeconds = ((workoutClockNow - (liveSession?.startTime ?: workoutClockNow))
                        .coerceAtLeast(0L) / 1_000L).toInt()
                    val elapsedTime = String.format(
                        java.util.Locale.US,
                        "%02d:%02d",
                        elapsedSeconds / 60,
                        elapsedSeconds % 60
                    )
                    WorkoutExecutionHeader(
                        sessionLabel = activeSession?.label ?: liveSession?.sessionType.orEmpty(),
                        elapsedTime = elapsedTime,
                        onBack = { showExitDialog = true },
                        onFinish = { showNormalFinishFeelDialog = true }
                    )
                    if (circuitSession != null && com.apexfit.app.utils.CircuitExecution.canConfigure(circuitSession)) {
                        TextButton(enabled = !savingSet && !showRirOverlay, onClick = {
                            circuitCount = (circuit?.memberCount ?: 2).toString()
                            transitionRest = (circuit?.transitionRestSeconds ?: 15).toString()
                            roundRest = (circuit?.roundRestSeconds ?: 90).toString()
                            showCircuitEditor = true
                        }) { Text(if (circuit == null) "Configure superset / circuit" else "Edit circuit") }
                    }
                    val proposalExercise = liveSession?.exercises?.getOrNull(currentIdx)
                    val proposal = proposalExercise?.progressionProposal
                    var showProgressionConfirm by remember { mutableStateOf(false) }
                    if (proposal != null && proposalExercise != null && liveSession?.circuit == null &&
                        proposalExercise.sets.none { it.completed }) {
                        TextButton(enabled = !savingSet && !showRirOverlay && liveSession?.restCheckpoint == null,
                            onClick = { showProgressionConfirm = true }) {
                            Text("Use suggested load · confirm setup")
                        }
                        if (showProgressionConfirm) AlertDialog(
                            onDismissRequest = { showProgressionConfirm = false },
                            title = { Text("Same equipment setup?") },
                            text = { Text("Previous setup: ${proposal.basis.label} · ${proposal.setup}. Source workout ${proposal.sourceDate}. Apply ${proposal.suggestedKg.toDisplayWeight(preferredUnits)} $preferredUnits in this same load convention, replacing the weights of all unlogged prescribed sets? Adjust manually if equipment or conditions differ.") },
                            confirmButton = { TextButton(onClick = {
                                showProgressionConfirm = false
                                workoutExecutionViewModel.applyProgression(proposalExercise.executionKey, proposal)
                            }) { Text("Confirm and apply") } },
                            dismissButton = { TextButton(onClick = { showProgressionConfirm = false }) { Text("Keep plan load") } })
                    }
                    val grouped = circuitSession != null && com.apexfit.app.utils.CircuitExecution.contains(circuitSession, currentIdx)
                    if (grouped && circuit != null) {
                        Text("Circuit · ${currentIdx + 1} of ${circuit.memberCount} members · ${circuit.transitionRestSeconds}s transition / ${circuit.roundRestSeconds}s round rest",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
                    }
                    if (advancedSet != null && circuitSession?.circuit == null && liveSession?.restCheckpoint == null) {
                        TextButton(enabled = !savingSet && !showRirOverlay, onClick = { advancedEditor = true }) {
                            Text("Log drop set / rest-pause / failure")
                        }
                    }
                    ExerciseListSection(
                        exercises = exercises,
                        currentIdx = currentIdx,
                        loggedSets = loggedSets,
                        effectiveSetsMap = effectiveSetsMap,
                        warmupComp = warmupComp,
                        lastWeights = lastWeights,
                        weightContextLines = weightContextLines,
                        volumeAdjustments = volumeAdjustments,
                        onUndoVolumeAdjustment = workoutExecutionViewModel.sessionManager::undoSetVolumeAdjustment,
                        preferredUnits = preferredUnits,
                        showRirOverlay = showRirOverlay || savingSet,
                        onOpenFormGuide = onOpenFormGuide,
                        onToggleWarmupItem = { workoutExecutionViewModel.toggleWarmupItem(it) },
                        onLogSetState = { exId, sIdx, w, r, rpe, comp, restTaken ->
                            workoutExecutionViewModel.logWorkoutSetState(
                                exerciseId = exId,
                                setIndex = sIdx,
                                weight = w,
                                reps = r,
                                rpe = rpe,
                                completed = comp,
                                restTakenSeconds = restTaken
                            )
                        },
                        onCompleteSet = { exId, exName, muscleGroup, sIdx, w, r, totalSets, _ ->
                            restTimerViewModel.clearClosedRestTimerContext()
                            workoutExecutionViewModel.openRirSelector(
                                exerciseId = exId,
                                exerciseName = exName,
                                muscleGroup = muscleGroup,
                                setIndex = sIdx,
                                weight = w,
                                reps = r,
                                totalSets = totalSets
                            )
                        },
                        onAddCustomLogSet = { workoutExecutionViewModel.addCustomLogSet(it) },
                        onNextExercise = { workoutExecutionViewModel.nextExercise() },
                        onSkipExercise = { ex ->
                            workoutExecutionViewModel.skipExercise {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.train_exercise_skipped, ex.name))
                                }
                            }
                        },
                        onShowNormalFinishFeelDialog = { showNormalFinishFeelDialog = true },
                        onShowFinishEarlyDialog = { showFinishEarlyDialog = true },
                        onShowExitDialog = { showExitDialog = true },
                        onCancelOrCompleteEmptySession = {
                            if (!workoutExecutionViewModel.hasCompletedSets) {
                                restTimerViewModel.cancelRestTimer()
                            }
                            workoutExecutionViewModel.cancelOrCompleteEmptySession()
                        },
                        modifier = Modifier.weight(1f),
                        currentExerciseEquipment = currentExerciseEquipment,
                        groupedExecution = grouped,
                        terminalExercise = if (grouped && circuitSession != null)
                            com.apexfit.app.utils.CircuitExecution.terminal(circuitSession, currentIdx) else null
                    )
                }
            }

            if (formGuideExercise != null && formGuideContent != null) {
                val exercise = formGuideExercise
                val content = formGuideContent
                if (exercise != null && content != null) {
                    FormGuideSheet(
                        exercise = exercise,
                        content = content,
                        preferredUnits = preferredUnits,
                        onDismiss = {
                            formGuideExercise = null
                            formGuideContent = null
                        }
                    )
                }
            }
        }
    }
}

private suspend fun loadFormGuideData(
    context: android.content.Context,
    exerciseName: String,
    prescription: com.apexfit.app.data.PlanExercise? = null
): Pair<com.apexfit.app.data.Exercise, FormGuideContent>? = withContext(Dispatchers.IO) {
    val dao = com.apexfit.app.data.AppDatabase.getDatabase(context).fitnessDao()
    val slug = exerciseNameToSlug(exerciseName)
    val exercise = dao.getExerciseById(slug) ?: dao.getExerciseByName(exerciseName)
        ?: return@withContext null
    val metadata = dao.getMetadataForExercise(exercise.id)
    val sourceInstructions = ExerciseMediaCatalog.get(context, exercise.id)?.instructions.orEmpty()
    val recentHistory = dao.getRecentExerciseHistoryForGuide(exercise.id)
    val personalRecords = dao.getPRsForExercise(exercise.id)
    val exerciseCatalog = dao.getAllExercises()
    exercise to FormGuideBuilder.buildFormGuide(
        exercise = exercise,
        metadata = metadata,
        sourceInstructions = sourceInstructions,
        prescription = prescription,
        recentHistory = recentHistory,
        personalRecords = personalRecords,
        exerciseCatalog = exerciseCatalog
    )
}

@Composable
fun NewPlansSubTab(
    planBuilderViewModel: PlanBuilderViewModel,
    onNavigateToPlanBuilder: (Long) -> Unit,
    onNavigateToWorkoutImport: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val plans by planBuilderViewModel.workoutPlans.collectAsStateWithLifecycle()
    val activePlanState by planBuilderViewModel.activePlan.collectAsStateWithLifecycle()
    val activeSessions by planBuilderViewModel.activeAllCycleSessions.collectAsStateWithLifecycle()
    val scheduleDate by planBuilderViewModel.activeScheduleDate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (activePlanState is UiState.Loading) {
        PlansLoadingState(modifier)
        return
    }

    val activePlan = (activePlanState as? UiState.Success)?.data
    val activePlanError = (activePlanState as? UiState.Error)?.message
    PlansSubTabContent(
        plans = plans,
        activePlan = activePlan,
        activePlanError = activePlanError,
        activeSessions = activeSessions,
        today = scheduleDate,
        onCreatePlan = { onNavigateToPlanBuilder(0L) },
        onImportPlan = onNavigateToWorkoutImport,
        onEditPlan = onNavigateToPlanBuilder,
        onActivatePlan = { plan ->
            planBuilderViewModel.activatePlan(plan.id)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.train_plan_activated))
            }
        },
        modifier = modifier
    )
}


@Composable
private fun AdvancedBoutEditor(
    set: com.apexfit.app.data.ActiveSet,
    onDismiss: () -> Unit,
    onSave: (Double, Int, AdvancedSetDetails) -> Unit
) {
    var kind by remember { mutableStateOf(AdvancedSetKind.DROP) }
    var weight by remember { mutableStateOf(set.weight.toString()) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var outcome by remember { mutableStateOf(SetOutcome.UNKNOWN) }
    data class SegmentInput(val weight: String, val reps: String, val rest: String, val outcome: SetOutcome)
    val rows = remember { mutableStateListOf(SegmentInput("", "", "0", SetOutcome.UNKNOWN)) }
    val parentKg = weight.toDoubleOrNull()?.fromDisplayWeightToKg(set.weightUnit)
    val segments = if (kind == AdvancedSetKind.WORKING) emptyList() else rows.mapNotNull {
        val kg = it.weight.toDoubleOrNull()?.fromDisplayWeightToKg(set.weightUnit) ?: return@mapNotNull null
        val count = it.reps.toIntOrNull() ?: return@mapNotNull null
        val rest = it.rest.toIntOrNull() ?: return@mapNotNull null
        SetContinuation(kg, count, rest, it.outcome)
    }
    val details = AdvancedSetDetails(kind, outcome, segments)
    val valid = parentKg != null && reps.toIntOrNull()?.let { it in 1..1000 } == true &&
        (kind == AdvancedSetKind.WORKING || segments.size == rows.size) && AdvancedSets.valid(parentKg, details)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Log completed working bout") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter actual completed repetitions. Log the initial set and all continuations together; do not enter planned work.")
                AdvancedSetKind.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == option, onClick = { kind = option })
                        Text(when (option) {
                            AdvancedSetKind.DROP -> "Drop set"
                            AdvancedSetKind.REST_PAUSE -> "Rest-pause"
                            AdvancedSetKind.WORKING -> "Straight set · failure status"
                        })
                    }
                }
                OutlinedTextField(weight, { weight = it.take(14) }, label = { Text("Initial load (${set.weightUnit})") }, singleLine = true)
                OutlinedTextField(reps, { reps = it.take(4) }, label = { Text("Initial reps") }, singleLine = true)
                BoutOutcomeSelector(outcome) { outcome = it }
                if (kind != AdvancedSetKind.WORKING) {
                    rows.forEachIndexed { i, row ->
                        HorizontalDivider()
                        Text("Continuation ${i + 1}")
                        OutlinedTextField(row.weight, { rows[i] = row.copy(weight = it.take(14)) },
                            label = { Text("Load (${set.weightUnit})") }, singleLine = true)
                        OutlinedTextField(row.reps, { rows[i] = row.copy(reps = it.take(4)) },
                            label = { Text("Completed reps") }, singleLine = true)
                        OutlinedTextField(row.rest, { rows[i] = row.copy(rest = it.take(4)) },
                            label = { Text("Actual preceding rest seconds") }, singleLine = true)
                        BoutOutcomeSelector(row.outcome) { rows[i] = row.copy(outcome = it) }
                        if (rows.size > 1) TextButton(onClick = { rows.removeAt(i) }) { Text("Remove continuation") }
                    }
                    TextButton(enabled = rows.size < 10, onClick = {
                        rows.add(SegmentInput("", "", if (kind == AdvancedSetKind.REST_PAUSE) "" else "0", SetOutcome.UNKNOWN))
                    }) { Text("Add continuation") }
                    Text(if (kind == AdvancedSetKind.DROP) "Each continuation must use a lower load."
                        else "Rest-pause continuations use the same load and positive rest.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("One working bout. No extra set credit or automatic progression. Failure is optional and is not recommended just to unlock a metric.",
                    style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            }
        }, confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(weight.toDouble(), reps.toInt(), details)
        }) { Text("Save completed bout") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun BoutOutcomeSelector(value: SetOutcome, onChange: (SetOutcome) -> Unit) {
    SetOutcome.entries.forEach { option ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == option, onClick = { onChange(option) })
            Text(when (option) {
                SetOutcome.FAILURE -> "Reached failure"
                SetOutcome.NOT_FAILURE -> "Stopped before failure"
                SetOutcome.UNKNOWN -> "Failure status unknown"
            })
        }
    }
}
