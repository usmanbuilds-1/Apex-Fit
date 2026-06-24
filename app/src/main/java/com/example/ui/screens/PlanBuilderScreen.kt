@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.TrainViewModel
import com.example.data.WorkoutPlan
import com.example.data.PlanSession
import com.example.data.PlanExercise
import com.example.ui.theme.*
import com.example.utils.MuscleGroups

@Composable
fun PlanBuilderScreen(
    trainViewModel: TrainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dbSessions by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val dbExercises by trainViewModel.allPlanExercises.collectAsStateWithLifecycle()
    val activePlan by trainViewModel.activePlan.collectAsStateWithLifecycle()

    var isInitialized by remember { mutableStateOf(false) }

    // Screen local states
    var planName by remember { mutableStateOf("") }
    var planGoal by remember { mutableStateOf("") }
    val sessionsList = remember { mutableStateListOf<PlanSession>() }
    val exercisesList = remember { mutableStateListOf<PlanExercise>() }

    // Dialog trigger states
    var showAddDayDialog by remember { mutableStateOf(false) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var selectedSessionIdForExercise by remember { mutableStateOf<Long?>(null) }
    var editingSession by remember { mutableStateOf<PlanSession?>(null) }
    var editingExercise by remember { mutableStateOf<PlanExercise?>(null) }

    LaunchedEffect(activePlan, dbSessions, dbExercises) {
        if (!isInitialized) {
            planName = activePlan?.name ?: "My Custom Plan"
            planGoal = activePlan?.goal ?: "Gain Muscle"
            sessionsList.clear()
            sessionsList.addAll(dbSessions)
            exercisesList.clear()
            val sessionIds = dbSessions.map { it.id }.toSet()
            exercisesList.addAll(dbExercises.filter { it.planSessionId in sessionIds })
            isInitialized = true
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "CUSTOM PLAN BUILDER",
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
                            if (planName.isBlank()) {
                                Toast.makeText(context, "Plan name cannot be empty", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Workout plan updated successfully!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("save_plan_button")
                    ) {
                        Text(
                            "SAVE",
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.Bold,
                            color = IndigoAccent,
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
                        Text(
                            "PLAN DETAILS",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoAccent
                        )

                        OutlinedTextField(
                            value = planName,
                            onValueChange = { planName = it },
                            label = { Text("Plan Name", color = SecondaryText) },
                            textStyle = LocalTextStyle.current.copy(color = PrimaryText),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSubtle,
                                cursorColor = IndigoAccent
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = planGoal,
                            onValueChange = { planGoal = it },
                            label = { Text("Plan Goal (e.g. Gain Muscle)", color = SecondaryText) },
                            textStyle = LocalTextStyle.current.copy(color = PrimaryText),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_goal_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSubtle,
                                cursorColor = IndigoAccent
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
                    Text(
                        "TRAINING DAYS (${sessionsList.size})",
                        fontFamily = SyneFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    
                    Button(
                        onClick = {
                            editingSession = null
                            showAddDayDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("add_day_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("ADD DAY", fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text(
                                "No training days added yet.",
                                fontFamily = SyneFamily,
                                fontSize = 13.sp,
                                color = SecondaryText,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Click 'ADD DAY' to construct your split.",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MutedText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(sessionsList) { index, session ->
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
                                            if (index > 0) {
                                                val temp = sessionsList[index]
                                                sessionsList[index] = sessionsList[index - 1]
                                                sessionsList[index - 1] = temp
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else MutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < sessionsList.size - 1) {
                                                val temp = sessionsList[index]
                                                sessionsList[index] = sessionsList[index + 1]
                                                sessionsList[index + 1] = temp
                                            }
                                        },
                                        enabled = index < sessionsList.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < sessionsList.size - 1) Color.White else MutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit Day
                                    IconButton(
                                        onClick = {
                                            editingSession = session
                                            showAddDayDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Day",
                                            tint = SecondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Day
                                    IconButton(
                                        onClick = {
                                            sessionsList.removeAt(index)
                                            exercisesList.removeAll { it.planSessionId == session.id }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Day",
                                            tint = RedAccent,
                                            modifier = Modifier.size(16.dp)
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
                                    Text(
                                        "No exercises. Tap 'Add Exercise' to populate.",
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
                                                            .background(IndigoAccent.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = exercise.muscleGroup.uppercase(),
                                                            fontFamily = JetBrainsMonoFamily,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = IndigoAccent
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
                                                        fontSize = 10.sp,
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
                                                        editingExercise = exercise
                                                        showAddExerciseDialog = true
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Exercise",
                                                        tint = SecondaryText,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        exercisesList.remove(exercise)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Exercise",
                                                        tint = RedAccent.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(14.dp)
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
                                    editingExercise = null
                                    showAddExerciseDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_exercise_button_${session.id}"),
                                border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoAccent)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(
                                        "ADD EXERCISE",
                                        fontFamily = SyneFamily,
                                        fontSize = 10.sp,
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
        var dayLabel by remember { mutableStateOf(editingSession?.label ?: "") }
        var selectedDayOfWeek by remember { mutableStateOf(editingSession?.day ?: "Monday") }
        
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        AlertDialog(
            onDismissRequest = { showAddDayDialog = false },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = if (editingSession == null) "ADD TRAINING DAY" else "EDIT TRAINING DAY",
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
                        label = { Text("Session Name (e.g. Upper A)", color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("day_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoAccent,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = IndigoAccent
                        ),
                        singleLine = true
                    )

                    Text(
                        "DAY OF THE WEEK",
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
                                    .background(if (isSelected) IndigoAccent else DarkBackground)
                                    .border(BorderStroke(1.dp, if (isSelected) IndigoAccent else BorderSubtle), RoundedCornerShape(8.dp))
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
                            Toast.makeText(context, "Session name is required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        if (editingSession == null) {
                            val newId = System.currentTimeMillis() + kotlin.random.Random.nextInt(100)
                            sessionsList.add(
                                PlanSession(
                                    id = newId,
                                    planId = activePlan?.id ?: 1L,
                                    label = dayLabel.trim(),
                                    day = selectedDayOfWeek,
                                    focus = "General"
                                )
                            )
                        } else {
                            val index = sessionsList.indexOfFirst { it.id == editingSession!!.id }
                            if (index != -1) {
                                sessionsList[index] = sessionsList[index].copy(
                                    label = dayLabel.trim(),
                                    day = selectedDayOfWeek
                                )
                            }
                        }
                        showAddDayDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                    modifier = Modifier.testTag("save_day_confirm_button")
                ) {
                    Text("SAVE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialog = false }) {
                    Text("CANCEL", fontFamily = SyneFamily, color = SecondaryText)
                }
            }
        )
    }

    // Add / Edit Exercise Dialog
    if (showAddExerciseDialog) {
        var exName by remember { mutableStateOf(editingExercise?.name ?: "") }
        var selectedMuscle by remember { mutableStateOf(editingExercise?.muscleGroup ?: MuscleGroups.ALL.first()) }
        var setsText by remember { mutableStateOf(editingExercise?.sets?.toString() ?: "3") }
        var repsMinText by remember { mutableStateOf(editingExercise?.repsMin?.toString() ?: "8") }
        var repsMaxText by remember { mutableStateOf(editingExercise?.repsMax?.toString() ?: "12") }
        var restText by remember { mutableStateOf(editingExercise?.restSeconds?.toString() ?: "90") }
        var notesText by remember { mutableStateOf(editingExercise?.notes ?: "") }

        var showMuscleDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            containerColor = DarkRaised,
            title = {
                Text(
                    text = if (editingExercise == null) "ADD EXERCISE" else "EDIT EXERCISE",
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
                        label = { Text("Exercise Name", color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoAccent,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = IndigoAccent
                        ),
                        singleLine = true
                    )

                    Text(
                        "MUSCLE GROUP",
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
                            label = { Text("Sets", color = SecondaryText, fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_sets_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = repsMinText,
                            onValueChange = { repsMinText = it },
                            label = { Text("Min Reps", color = SecondaryText, fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("exercise_reps_min_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = repsMaxText,
                            onValueChange = { repsMaxText = it },
                            label = { Text("Max Reps", color = SecondaryText, fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("exercise_reps_max_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IndigoAccent,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = restText,
                        onValueChange = { restText = it },
                        label = { Text("Rest (seconds)", color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_rest_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoAccent,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Optional Notes", color = SecondaryText) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exercise_notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoAccent,
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
                            Toast.makeText(context, "Exercise name is required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val setsVal = setsText.toIntOrNull() ?: 3
                        val minRepsVal = repsMinText.toIntOrNull() ?: 8
                        val maxRepsVal = repsMaxText.toIntOrNull() ?: 12
                        val restVal = restText.toIntOrNull() ?: 90

                        if (setsVal <= 0 || minRepsVal <= 0 || maxRepsVal <= 0 || restVal < 0) {
                            Toast.makeText(context, "Numeric values must be valid", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val targetSessionId = selectedSessionIdForExercise ?: return@Button

                        if (editingExercise == null) {
                            val newId = System.currentTimeMillis() + kotlin.random.Random.nextInt(100)
                            exercisesList.add(
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
                            val index = exercisesList.indexOfFirst { it.id == editingExercise!!.id }
                            if (index != -1) {
                                exercisesList[index] = exercisesList[index].copy(
                                    name = exName.trim(),
                                    muscleGroup = selectedMuscle,
                                    sets = setsVal,
                                    repsMin = minRepsVal,
                                    repsMax = maxRepsVal,
                                    restSeconds = restVal,
                                    notes = notesText.trim()
                                )
                            }
                        }
                        showAddExerciseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                    modifier = Modifier.testTag("save_exercise_confirm_button")
                ) {
                    Text("SAVE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) {
                    Text("CANCEL", fontFamily = SyneFamily, color = SecondaryText)
                }
            }
        )
    }
}
