package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    onNavigateToPlanBuilder: () -> Unit
) {
    val subTab by fitnessViewModel.trainSubTab.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
            // Segmented Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardSurface)
                    .padding(4.dp)
            ) {
                listOf("PROGRAM", "WORKOUTS", "PLANS").forEachIndexed { index, title ->
                    val isSelected = subTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { fitnessViewModel.setTrainSubTab(index) }
                            .padding(vertical = 12.dp)
                            .heightIn(min = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0A0A0F) else SecondaryText
                        )
                    }
                }
            }

            when (subTab) {
                0 -> ProgramSubTab(fitnessViewModel, planBuilderViewModel, workoutExecutionViewModel)
                1 -> WorkoutExecutionSubTab(fitnessViewModel, workoutExecutionViewModel, restTimerViewModel, snackbarHostState)
                2 -> NewPlansSubTab(fitnessViewModel, planBuilderViewModel, onNavigateToPlanBuilder, snackbarHostState)
            }
        }
    }
}

@Composable
fun ProgramSubTab(
    fitnessViewModel: FitnessViewModel,
    planBuilderViewModel: PlanBuilderViewModel,
    workoutExecutionViewModel: WorkoutExecutionViewModel
) {
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedDay by planBuilderViewModel.selectedDayOfWeek.collectAsStateWithLifecycle()
    val selectedDaySessionRaw by planBuilderViewModel.selectedDaySession.collectAsStateWithLifecycle()
    val exercises by planBuilderViewModel.selectedDayExercises.collectAsStateWithLifecycle()

    val sessionManager = workoutExecutionViewModel.sessionManager
    val isStarting by sessionManager.isStartingSession.collectAsStateWithLifecycle()

    val selectedDaySession = selectedDaySessionRaw

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val scope = rememberCoroutineScope()
    var substJob: Job? by remember { mutableStateOf(null) }
    val substitutionList by planBuilderViewModel.substitutionList.collectAsStateWithLifecycle()
    val activeSubstIndex by planBuilderViewModel.activeSubstIndex.collectAsStateWithLifecycle()

    var formGuideExercise by remember { mutableStateOf<com.apexfit.app.data.Exercise?>(null) }
    var formGuideContent by remember { mutableStateOf<FormGuideContent?>(null) }

    val onOpenFormGuide: (String) -> Unit = remember(scope, context) {
        { exerciseName ->
            scope.launch {
                val db = com.apexfit.app.data.AppDatabase.getDatabase(context)
                val slug = exerciseNameToSlug(exerciseName)
                val (exercise, metadata) = withContext(Dispatchers.IO) {
                    val foundEx = db.fitnessDao().getExerciseById(slug)
                    val meta = if (foundEx != null) db.fitnessDao().getMetadataForExercise(slug) else null
                    Pair(foundEx, meta)
                }
                if (exercise != null) {
                    formGuideExercise = exercise
                    formGuideContent = FormGuideBuilder.buildFormGuide(exercise, metadata)
                }
            }
        }
    }

    val onSelectSubstitution: (Int, String, String) -> Unit = remember(scope, planBuilderViewModel) {
        { index, muscleGroup, exerciseName ->
            planBuilderViewModel.selectSubstIndex(index)
            substJob?.cancel()
            substJob = scope.launch {
                val suggestions = planBuilderViewModel.getSubstitutionSuggestions(muscleGroup, exerciseName)
                planBuilderViewModel.setSubstitutionList(suggestions)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Horizontal Day Picker Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daysOfWeek, key = { it }) { day ->
                    val isSelected = selectedDay.equals(day, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) AmberAccent else DarkCardSurface)
                            .border(
                                BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                                RoundedCornerShape(14.dp)
                            )
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { planBuilderViewModel.selectDay(day) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = day.substring(0, 3).uppercase(),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0A0A0F) else SecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedDaySession == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dynamic session scheduled for $selectedDay.",
                        color = SecondaryText,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp
                    )
                }
            } else {
                val isImperial = remember(units) { units.lowercase() in listOf("lb", "lbs") }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Focus Card first
                    item {
                        PremiumCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "CURRENT SESSION PARAMETERS",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedDaySession.label,
                                fontFamily = SyneFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedDaySession.focus,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = SecondaryText
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Fast Warmup Checklist preview
                            Text(
                                text = "Warmup",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✓ 5 Mins light cardio + Shoulder cuff activations + Barbell sets",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MutedText
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (!isStarting) {
                                        workoutExecutionViewModel.startWorkoutSession(selectedDaySession)
                                        fitnessViewModel.setTrainSubTab(1)
                                    }
                                },
                                enabled = !isStarting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.train_start_current_workout_run),
                                    fontFamily = SyneFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A0A0F)
                                )
                            }
                        }
                    }

                    // Exercises list
                    itemsIndexed(exercises, key = { _, ex -> ex.id }) { index, ex ->
                        var isExpanded by rememberSaveable { mutableStateOf(false) }
                        val displayWeight = if (isImperial) (ex.weight * AppConstants.KG_TO_LBS).let { Math.round(it * 10.0) / 10.0 } else ex.weight

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { isExpanded = !isExpanded }
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ex.name,
                                            fontFamily = SyneFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${ex.sets} Sets x ${ex.repsMin}-${ex.repsMax} Reps • $displayWeight $units",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = AmberAccent
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                        contentDescription = "Expand details",
                                        tint = SecondaryText
                                    )
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(BorderSubtle)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Rest Target Interval: ${ex.restSeconds} Seconds",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = SecondaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ex.notes,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MutedText,
                                        lineHeight = 16.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Form Guide button
                                        Button(
                                            onClick = { onOpenFormGuide(ex.name) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.train_form_guide), fontFamily = SyneFamily, fontSize = 11.sp, color = PrimaryText)
                                        }

                                        // Replace Exercise button suggestion
                                        Button(
                                            onClick = { onSelectSubstitution(index, ex.muscleGroup, ex.name) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.train_substitute), fontFamily = SyneFamily, fontSize = 11.sp, color = AmberAccent)
                                        }
                                    }

                                    // If substitution recommendations loaded for this particular index
                                    if (activeSubstIndex == index && substitutionList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkRaised)
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Suggested Substitutions",
                                                fontFamily = SyneFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberAccent
                                            )

                                            substitutionList.forEach { p ->
                                                Column {
                                                    Text(
                                                        text = "• ${p.first}",
                                                        fontFamily = SyneFamily,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryText
                                                    )
                                                    Text(
                                                        text = p.second,
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 11.sp,
                                                        color = SecondaryText,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } // end if-else
        } // end Column

        if (formGuideExercise != null && formGuideContent != null) {
            val exercise = formGuideExercise ?: return@Box
            val content = formGuideContent ?: return@Box
            FormGuideSheet(
                exercise = exercise,
                content = content,
                onDismiss = {
                    formGuideExercise = null
                    formGuideContent = null
                }
            )
        }
    } // end Box
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
    val preferredUnits by workoutExecutionViewModel.units.collectAsStateWithLifecycle()
    val saveError by workoutExecutionViewModel.saveError.collectAsStateWithLifecycle()
    val showRirOverlay by workoutExecutionViewModel.showRirOverlay.collectAsStateWithLifecycle()
    val sessionRestoreFailed by workoutExecutionViewModel.sessionManager.sessionRestoreFailed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(sessionRestoreFailed) {
        if (sessionRestoreFailed) {
            snackbarHostState.showSnackbar(
                message = "Your previous session could not be restored.",
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
        }
    }

    var showFinishEarlyDialog by rememberSaveable { mutableStateOf(false) }
    var showNormalFinishFeelDialog by rememberSaveable { mutableStateOf(false) }
    var selectedFeelRating by rememberSaveable { mutableStateOf(4) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    saveError?.let { err ->
        AlertDialog(
            onDismissRequest = { workoutExecutionViewModel.dismissSaveError() },
            containerColor = DarkCardSurface,
            title = { Text(stringResource(R.string.train_save_failed), color = Color(0xFFE84A4A), fontFamily = SyneFamily, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = SecondaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { workoutExecutionViewModel.finishWorkoutSession(selectedFeelRating) }) {
                    Text(stringResource(R.string.train_retry_full), color = AmberAccent)
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
                    Text(stringResource(R.string.train_save_exit), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = AmberAccent)
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
                        Text(stringResource(R.string.train_discard), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFFE84A4A))
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
                    text = "FINISH WORKOUT EARLY?",
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
                        text = "RATE YOUR WORKOUT FEEL (1-5):",
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
                            1 -> "1 - Extremely fatigued / bad"
                            2 -> "2 - Tired / poor feel"
                            3 -> "3 - Average / neutral"
                            4 -> "4 - Energetic / strong feel"
                            else -> "5 - Beast mode / excellent!"
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
                        text = "YES, FINISH & SAVE",
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
                        text = "CANCEL",
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
                    text = "CONGRATULATIONS!",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Incredible job completing your athletic workout session today! Let's rate how your body felt:",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "RATE YOUR WORKOUT FEEL (1-5):",
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
                            1 -> "1 - Extremely fatigued / bad"
                            2 -> "2 - Tired / poor feel"
                            3 -> "3 - Average / neutral"
                            4 -> "4 - Energetic / strong feel"
                            else -> "5 - Beast mode / excellent!"
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
                        text = "SAVE LOG & COMPLETE",
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
                        text = "BACK",
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
                            imageVector = Icons.Filled.PlayArrow,
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
                            text = "Select a day above to start your workout.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                ExerciseListSection(
                    exercises = exercises,
                    currentIdx = currentIdx,
                    loggedSets = loggedSets,
                    effectiveSetsMap = effectiveSetsMap,
                    warmupComp = warmupComp,
                    lastWeights = lastWeights,
                    weightContextLines = weightContextLines,
                    preferredUnits = preferredUnits,
                    showRirOverlay = showRirOverlay,
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
                    onOpenRirSelector = { exId, exName, muscleGroup, sIdx, w, r, totalSets ->
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
                    }
                )
            }
        }
    }
}

@Composable
fun NewPlansSubTab(
    fitnessViewModel: FitnessViewModel,
    planBuilderViewModel: PlanBuilderViewModel,
    onNavigateToPlanBuilder: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val plans by planBuilderViewModel.workoutPlans.collectAsStateWithLifecycle()
    val activePlanState by planBuilderViewModel.activePlan.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (activePlanState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OrangeAccent)
        }
        return
    }

    val activePlan: com.apexfit.app.data.WorkoutPlan? = (activePlanState as? UiState.Success)?.data
    val context = LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Button(
                onClick = onNavigateToPlanBuilder,
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_custom_plan_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.train_build_custom_workout_plan),
                        fontFamily = SyneFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // List of athletic directories
        item {
            Text(
                text = "REGISTERED TRAINING DIRECTORIES",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedText
            )
        }

        items(plans, key = { it.id }) { plan ->
            val isActive = activePlan?.id == plan.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardSurface)
                    .border(BorderStroke(1.dp, if (isActive) AmberAccent else BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plan.name,
                                fontFamily = SyneFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E6A41))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.train_active), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = PrimaryText)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Target Goal: ${plan.goal}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }

                    if (!isActive) {
                        Button(
                            onClick = {
                                planBuilderViewModel.activatePlan(plan.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.train_plan_activated))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.train_activate), fontFamily = SyneFamily, fontSize = 11.sp, color = PrimaryText)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
