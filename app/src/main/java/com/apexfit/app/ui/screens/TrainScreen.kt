package com.apexfit.app.ui.screens
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
                            restTimerViewModel.cancelRestTimer()
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
                            workoutExecutionViewModel.skipExercise()
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.train_exercise_skipped, ex.name))
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
                        currentExerciseEquipment = currentExerciseEquipment
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
