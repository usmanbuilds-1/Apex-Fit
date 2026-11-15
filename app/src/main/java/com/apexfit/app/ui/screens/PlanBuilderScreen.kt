@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens
import com.apexfit.app.ui.models.UiState

import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
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
            if (loading) CircularProgressIndicator(color = OrangeAccent)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError.orEmpty(), color = Color.White)
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

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.plan_builder_custom_plan_builder),
                        fontFamily = SyneFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            if (planName.isBlank()) {
                                planNameError = context.getString(R.string.plan_builder_plan_name_cannot_be_empty)
                                return@TextButton
                            } else {
                                planNameError = null
                            }
                            if (sessionsList.isEmpty() || sessionsList.any { day -> exercisesList.none { it.planSessionId == day.id } }) {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Add at least one exercise to every training day.") }
                                return@TextButton
                            }
                            isSaving = true
                            val planId = activePlan?.id ?: 0L
                            val finalPlan = activePlan?.copy(
                                name = planName.trim(),
                                goal = planGoal.trim().ifEmpty { "General Fitness" }
                            ) ?: WorkoutPlan(
                                name = planName.trim(),
                                goal = planGoal.trim().ifEmpty { "General Fitness" },
                                isActive = true
                            )

                            // Re-calculate focus for sessions
                            val updatedSessions = sessionsList.map { session ->
                                val muscles = exercisesList
                                    .filter { it.planSessionId == session.id }
                                    .map { it.muscleGroup }
                                    .distinct()
                                val focusStr = if (muscles.isEmpty()) "General" else muscles.joinToString(", ")
                                session.copy(planId = planId, focus = focusStr)
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
                        },
                        enabled = !isSaving,
                        modifier = Modifier.testTag("save_plan_button")
                    ) {
                        Text(stringResource(R.string.plan_builder_save),
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
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
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
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

            // Plan Meta Details Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()