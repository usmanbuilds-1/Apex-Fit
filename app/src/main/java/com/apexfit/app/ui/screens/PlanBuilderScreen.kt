@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.apexfit.app.ui.screens
import com.apexfit.app.ui.models.UiState

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.TrainViewModel
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.MuscleGroups

@Composable
fun PlanBuilderScreen(
    trainViewModel: TrainViewModel,
    onNavigateBack: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            trainViewModel.resetPlanBuilder()
        }
    }

    val context = LocalContext.current
    val dbSessionsState by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val dbExercises by trainViewModel.allPlanExercises.collectAsStateWithLifecycle()
    val activePlanState by trainViewModel.activePlan.collectAsStateWithLifecycle()
    
    if (dbSessionsState is UiState.Loading || activePlanState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = OrangeAccent)
        }
        return
    }

    if (dbSessionsState is UiState.Error || activePlanState is UiState.Error) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.plan_builder_error_loading_plan_data), color = Color.White)
        }
        return
    }

    val dbSessions: List<com.apexfit.app.data.PlanSession> = (dbSessionsState as? UiState.Success)?.data ?: emptyList()
    val activePlan: com.apexfit.app.data.WorkoutPlan? = (activePlanState as? UiState.Success)?.data
    
    val sessionsList by trainViewModel.planBuilderSessions.collectAsStateWithLifecycle()
    val exercisesList by trainViewModel.planBuilderExercises.collectAsStateWithLifecycle()

    var planName by rememberSaveable { mutableStateOf("") }
    var planGoal by rememberSaveable { mutableStateOf("") }
    var isInitialized by rememberSaveable { mutableStateOf(false) }

    // Dialog trigger states
    var showAddDayDialog by rememberSaveable { mutableStateOf(false) }
    var showAddExerciseDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSessionIdForExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sessionToDelete by rememberSaveable { mutableStateOf<PlanSession?>(null) }
    var exerciseToDelete by rememberSaveable { mutableStateOf<PlanExercise?>(null) }

    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(isSaving) {
        if (isSaving) {
            kotlinx.coroutines.delay(2000)
            isSaving = false
        }
    }

    LaunchedEffect(activePlan, dbSessions, dbExercises) {
        if (!isInitialized && activePlan != null) {
            planName = activePlan?.name ?: "My Custom Plan"
            planGoal = activePlan?.goal ?: "Gain Muscle"
            
            trainViewModel.initializePlanBuilder(
                activePlan,
                dbSessions,
                dbExercises
            )
            isInitialized = true
        }
    }

    Scaffold(
        containerColor = DarkBackground,
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true
                            if (planName.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.plan_builder_plan_name_cannot_be_empty), Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val planId = activePlan?.id ?: 1L
                            val finalPlan = WorkoutPlan(
                                id = planId,
                                name = planName.trim(),
                                goal = planGoal.trim().ifEmpty { "General Fitness" },
                                isActive = true,
                                createdAt = activePlan?.createdAt ?: System.currentTimeMillis()
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

                            trainViewModel.updateWorkoutPlan(finalPlan, updatedSessions, updatedExercises)
                            Toast.makeText(context, context.getString(R.string.plan_builder_workout_plan_updated_successfu), Toast.LENGTH_SHORT).show()
                            onNavigateBack()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan Meta Details Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.plan_builder_plan_details),
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangeAccent
                        )

                        OutlinedTextField(
                            value = planName,
                            onValueChange = { planName = it },
                            label = { Text(stringResource(R.string.plan_builder_plan_name), color = SecondaryText) },
                            textStyle = LocalTextStyle.current.copy(color = PrimaryText),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = BorderSubtle,
                                cursorColor = OrangeAccent
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = planGoal,
                            onValueChange = { planGoal = it },
                            label = { Text(stringResource(R.string.plan_builder_plan_goal_e_g_gain_muscle), color = SecondaryText) },
                            textStyle = LocalTextStyle.current.copy(color = PrimaryText),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_goal_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = BorderSubtle,
                                cursorColor = OrangeAccent
                            ),
                            singleLine = true
                        )
                    }
                }
            }

            // Training Sessions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.plan_builder_training_days, sessionsList.size),
                        fontFamily = SyneFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    
                    Button(
                        onClick = {
                            editingSessionId = null
                            showAddDayDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("add_day_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.plan_builder_add_day), fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (sessionsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurface)
                            .border(BorderStroke(1.dp, BorderSubtle))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MutedText,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(stringResource(R.string.plan_builder_no_training_days_added_yet),
                                fontFamily = SyneFamily,
                                fontSize = 13.sp,
                                color = SecondaryText,
                                textAlign = TextAlign.Center
                            )
                            Text(stringResource(R.string.plan_builder_click_add_day_to_construct_you),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MutedText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(sessionsList, key = { _, session -> session.id }) { index, session ->
                    val sessionExercises = exercisesList.filter { it.planSessionId == session.id }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_card_${session.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(12.dp)
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
                                        fontFamily = SyneFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${session.day} • ${sessionExercises.size} exercises",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = SecondaryText
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            trainViewModel.reorderSession(index, true)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else MutedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            trainViewModel.reorderSession(index, false)
                                        },
                                        enabled = index < sessionsList.size - 1,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < sessionsList.size - 1) Color.White else MutedText,
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
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Day",
                                            tint = SecondaryText,
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
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Day",
                                            tint = RedAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Exercises Sub-List
                            if (sessionExercises.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkBackground)
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.plan_builder_no_exercises_tap_add_exercise_),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sessionExercises.forEachIndexed { exIndex, exercise ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkBackground)
                                                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(OrangeAccent.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = exercise.muscleGroup.uppercase(),
                                                            fontFamily = JetBrainsMonoFamily,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = OrangeAccent
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = exercise.name,
                                                        fontFamily = SyneFamily,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "${exercise.sets} sets x ${exercise.repsMin}-${exercise.repsMax} reps • Rest: ${exercise.restSeconds}s",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    color = SecondaryText
                                                )
                                                if (exercise.notes.isNotBlank()) {
                                                    Text(
                                                        text = "Notes: ${exercise.notes}",
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 11.sp,
                                                        color = MutedText,
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
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Exercise",
                                                        tint = SecondaryText,
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
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Exercise",
                                                        tint = RedAccent.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Add Exercise Button
                            OutlinedButton(
                                onClick = {
                                    selectedSessionIdForExercise = session.id
                                    editingExerciseId = null
                                    showAddExerciseDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_exercise_button_${session.id}"),
                                border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(stringResource(R.string.plan_builder_add_exercise),
                                        fontFamily = SyneFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
        val session = editingSessionId?.let { trainViewModel.getSessionById(it) }
        var dayLabel by rememberSaveable { mutableStateOf(session?.label ?: "") }
        var selectedDayOfWeek by rememberSaveable { mutableStateOf(session?.day ?: "Monday") }
        
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        AlertDialog(
            onDismissRequest = { showAddDayDialog = false },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = if (editingSessionId == null) "ADD TRAINING DAY" else "EDIT TRAINING DAY",
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
                        onValueChange = { dayLabel = it },
                        label = { Text(stringResource(R.string.plan_builder_session_name_e_g_upper_a), color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("day_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangeAccent,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = OrangeAccent
                        ),
                        singleLine = true
                    )

                    Text(stringResource(R.string.plan_builder_day_of_the_week),
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daysOfWeek.forEach { day ->
                            val isSelected = selectedDayOfWeek == day
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) OrangeAccent else DarkBackground)
                                    .border(BorderStroke(1.dp, if (isSelected) OrangeAccent else BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { selectedDayOfWeek = day }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = day,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else SecondaryText
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
                            Toast.makeText(context, context.getString(R.string.plan_builder_session_name_is_required), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        if (editingSessionId == null) {
                            val newId = trainViewModel.generateNewSessionId()
                            trainViewModel.addSession(
                                PlanSession(
                                    id = newId,
                                    planId = activePlan?.id ?: 1L,
                                    label = dayLabel.trim(),
                                    day = selectedDayOfWeek,
                                    focus = "General"
                                )
                            )
                        } else {
                            val session = editingSessionId?.let { trainViewModel.getSessionById(it) }
                            if (session != null) {
                                trainViewModel.updateSession(
                                    session.copy(
                                        label = dayLabel.trim(),
                                        day = selectedDayOfWeek
                                    )
                                )
                            }
                        }
                        showAddDayDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    modifier = Modifier.testTag("save_day_confirm_button")
                ) {
                    Text(stringResource(R.string.plan_builder_save), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialog = false }) {
                    Text(stringResource(R.string.plan_builder_cancel), fontFamily = SyneFamily, color = SecondaryText)
                }
            }
        )
    }

    // Add / Edit Exercise Dialog
    if (showAddExerciseDialog) {
        val exercise = editingExerciseId?.let { trainViewModel.getExerciseById(it) }
        var exName by rememberSaveable { mutableStateOf(exercise?.name ?: "") }
        var selectedMuscle by rememberSaveable { mutableStateOf(exercise?.muscleGroup ?: MuscleGroups.ALL.first()) }
        var setsText by rememberSaveable { mutableStateOf(exercise?.sets?.toString() ?: "3") }
        var repsMinText by rememberSaveable { mutableStateOf(exercise?.repsMin?.toString() ?: "8") }
        var repsMaxText by rememberSaveable { mutableStateOf(exercise?.repsMax?.toString() ?: "12") }
        var restText by rememberSaveable { mutableStateOf(exercise?.restSeconds?.toString() ?: "90") }
        var notesText by rememberSaveable { mutableStateOf(exercise?.notes ?: "") }

        var showMuscleDropdown by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = if (editingExerciseId == null) "ADD EXERCISE" else "EDIT EXERCISE",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = exName,
                        onValueChange = { exName = it },
                        label = { Text(stringResource(R.string.plan_builder_exercise_name), color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangeAccent,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = OrangeAccent
                        ),
                        singleLine = true
                    )

                    Text(stringResource(R.string.plan_builder_muscle_group),
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showMuscleDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("muscle_group_dropdown"),
                            border = BorderStroke(1.dp, BorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedMuscle, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                            }
                        }

                        DropdownMenu(
                            expanded = showMuscleDropdown,
                            onDismissRequest = { showMuscleDropdown = false },
                            modifier = Modifier
                                .background(DarkRaised)
                                .border(BorderStroke(1.dp, BorderSubtle))
                        ) {
                            MuscleGroups.ALL.forEach { muscle ->
                                DropdownMenuItem(
                                    text = { Text(muscle, color = Color.White, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp) },
                                    onClick = {
                                        selectedMuscle = muscle
                                        showMuscleDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = setsText,
                            onValueChange = { setsText = it },
                            label = { Text(stringResource(R.string.plan_builder_sets), color = SecondaryText, fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_sets_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = repsMinText,
                            onValueChange = { repsMinText = it },
                            label = { Text(stringResource(R.string.plan_builder_min_reps), color = SecondaryText, fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("exercise_reps_min_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = repsMaxText,
                            onValueChange = { repsMaxText = it },
                            label = { Text(stringResource(R.string.plan_builder_max_reps), color = SecondaryText, fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("exercise_reps_max_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrangeAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = restText,
                        onValueChange = { restText = it },
                        label = { Text(stringResource(R.string.plan_builder_rest_seconds), color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_rest_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangeAccent,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.plan_builder_optional_notes), color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangeAccent,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exName.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.plan_builder_exercise_name_is_required), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val setsVal = setsText.toIntOrNull() ?: 3
                        val minRepsVal = repsMinText.toIntOrNull() ?: 8
                        val maxRepsVal = repsMaxText.toIntOrNull() ?: 12
                        val restVal = restText.toIntOrNull() ?: 90

                        if (setsVal <= 0 || minRepsVal <= 0 || maxRepsVal <= 0 || restVal < 0) {
                            Toast.makeText(context, context.getString(R.string.plan_builder_numeric_values_must_be_valid), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (minRepsVal > maxRepsVal) {
                            Toast.makeText(context, context.getString(R.string.plan_builder_min_reps_greater_than_max), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val targetSessionId = selectedSessionIdForExercise ?: return@Button

                        if (editingExerciseId == null) {
                            val newId = trainViewModel.generateNewSessionId()
                            trainViewModel.addCustomExercise(
                                PlanExercise(
                                    id = newId,
                                    planSessionId = targetSessionId,
                                    name = exName.trim(),
                                    muscleGroup = selectedMuscle,
                                    sets = setsVal,
                                    repsMin = minRepsVal,
                                    repsMax = maxRepsVal,
                                    weight = 0.0,
                                    restSeconds = restVal,
                                    notes = notesText.trim()
                                )
                            )
                        } else {
                            val exercise = editingExerciseId?.let { trainViewModel.getExerciseById(it) }
                            if (exercise != null) {
                                trainViewModel.updateExercise(
                                    exercise.copy(
                                        name = exName.trim(),
                                        muscleGroup = selectedMuscle,
                                        sets = setsVal,
                                        repsMin = minRepsVal,
                                        repsMax = maxRepsVal,
                                        restSeconds = restVal,
                                        notes = notesText.trim()
                                    )
                                )
                            }
                        }
                        showAddExerciseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent),
                    modifier = Modifier.testTag("save_exercise_confirm_button")
                ) {
                    Text(stringResource(R.string.plan_builder_save), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) {
                    Text(stringResource(R.string.plan_builder_cancel), fontFamily = SyneFamily, color = SecondaryText)
                }
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
                        sessionToDelete?.let { trainViewModel.deleteSession(it.id) }
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
                        exerciseToDelete?.let { trainViewModel.deleteExercise(it.id) }
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
