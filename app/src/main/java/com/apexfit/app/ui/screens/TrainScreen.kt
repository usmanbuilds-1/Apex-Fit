package com.apexfit.app.ui.screens
import com.apexfit.app.ui.models.UiState

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.TrainViewModel
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.exerciseId
import com.apexfit.app.ui.models.*
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.roundToInt

// TRAIN TAB
@Composable
fun TrainTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    trainViewModel: TrainViewModel,
    onNavigateToPlanBuilder: () -> Unit
) {
    val subTab by fitnessViewModel.trainSubTab.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                        .clickable { fitnessViewModel.setTrainSubTab(index) }
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

        Crossfade(targetState = subTab, label = "trainSubCross") { tab ->
            when (tab) {
                0 -> ProgramSubTab(fitnessViewModel, trainViewModel)
                1 -> WorkoutExecutionSubTab(fitnessViewModel, trainViewModel)
                2 -> NewPlansSubTab(fitnessViewModel, trainViewModel, onNavigateToPlanBuilder)
            }
        }
    }
}

@Composable
fun ProgramSubTab(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel
) {
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedDay by trainViewModel.selectedDayOfWeek.collectAsStateWithLifecycle()
    val selectedDaySessionRaw by trainViewModel.selectedDaySession.collectAsStateWithLifecycle()
    val exercises by trainViewModel.selectedDayExercises.collectAsStateWithLifecycle()

    val sessionManager = trainViewModel.sessionManager
    val isStarting by sessionManager.isStartingSession.collectAsStateWithLifecycle()

    val selectedDaySession = selectedDaySessionRaw

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val scope = rememberCoroutineScope()
    var substJob: Job? by remember { mutableStateOf(null) }
    val substitutionList by trainViewModel.substitutionList.collectAsStateWithLifecycle()
    val activeSubstIndex by trainViewModel.activeSubstIndex.collectAsStateWithLifecycle()

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
                        .clickable { trainViewModel.selectDay(day) }
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
                                    trainViewModel.startWorkoutSession(selectedDaySession)
                                    fitnessViewModel.setTrainSubTab(1)
                                }
                            },
                            enabled = !isStarting,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.train_start_current_workout_run),
                                fontFamily = SyneFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0F)
                            )
                        }
                    }
                }

                // Exercises list
                items(exercises.size, key = { it }) { index ->
                    val ex = exercises[index]

                    var isExpanded by rememberSaveable { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurface)
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .clickable { isExpanded = !isExpanded }
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
                                        text = "${ex.sets} Sets x ${ex.repsMin}-${ex.repsMax} Reps • ${ex.weight} $units",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = AmberAccent
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Expand details",
                                    tint = SecondaryText
                                )
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
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
                                        onClick = {
                                            Toast.makeText(context, context.getString(R.string.train_focus_on_compound_control_lock, ex.name), Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.train_form_guide), fontFamily = SyneFamily, fontSize = 11.sp, color = PrimaryText)
                                    }

                                    // Replace Exercise button suggestion
                                    Button(
                                        onClick = {
                                            trainViewModel.selectSubstIndex(index)
                                            substJob?.cancel()
                                            substJob = scope.launch {
                                                val suggestions = trainViewModel.getSubstitutionSuggestions(ex.muscleGroup, ex.name)
                                                trainViewModel.setSubstitutionList(suggestions)
                                            }
                                        },
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
        }
    }
}

@Composable
fun WorkoutExecutionSubTab(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel
) {
    val activeSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val exercises by trainViewModel.activeExercises.collectAsStateWithLifecycle()
    val currentIdx by trainViewModel.currentExerciseIdx.collectAsStateWithLifecycle()
    val loggedSets by trainViewModel.loggedSets.collectAsStateWithLifecycle()
    val effectiveSetsMap by trainViewModel.effectiveSetsStateFlow.collectAsStateWithLifecycle()
    val warmupComp by trainViewModel.warmupCompleted.collectAsStateWithLifecycle()
    val lastWeights by trainViewModel.lastWeights.collectAsStateWithLifecycle()
    val weightContextLines by trainViewModel.weightContextLines.collectAsStateWithLifecycle()
    val preferredUnits by trainViewModel.units.collectAsStateWithLifecycle()
    val saveError by trainViewModel.saveError.collectAsStateWithLifecycle()
    val showRirOverlay by trainViewModel.showRirOverlay.collectAsStateWithLifecycle()
    val sessionRestoreFailed by trainViewModel.sessionManager.sessionRestoreFailed.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionRestoreFailed) {
        if (sessionRestoreFailed) {
            snackbarHostState.showSnackbar(
                message = "Your previous session could not be restored.",
                actionLabel = "OK",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
        }
    }

    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* permission result ignored — logging never blocked by permission */ }

    var showFinishEarlyDialog by rememberSaveable { mutableStateOf(false) }
    var showNormalFinishFeelDialog by rememberSaveable { mutableStateOf(false) }
    var selectedFeelRating by rememberSaveable { mutableStateOf(4) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    saveError?.let { err ->
        AlertDialog(
            onDismissRequest = { trainViewModel.dismissSaveError() },
            containerColor = DarkCardSurface,
            title = { Text(stringResource(R.string.train_save_failed), color = Color(0xFFE84A4A), fontFamily = SyneFamily, fontWeight = FontWeight.Bold) },
            text = { Text(err, color = SecondaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { trainViewModel.finishWorkoutSession(selectedFeelRating) }) {
                    Text(stringResource(R.string.train_retry_full), color = AmberAccent)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { trainViewModel.savePartialAndExit(selectedFeelRating) }) {
                        Text(stringResource(R.string.train_retry_partial), color = AccentSecondary)
                    }
                    TextButton(onClick = { trainViewModel.dismissSaveError() }) {
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
                            trainViewModel.cancelActiveWorkout()
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
                        trainViewModel.finishWorkoutEarly(selectedFeelRating)
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
                        trainViewModel.finishWorkoutSession(selectedFeelRating)
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
        // active workout runner layout
        val unitSuffix = if (preferredUnits.lowercase() in listOf("lb", "lbs")) "lb" else "kg"
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Warmup checklist card
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PRE-SESSION WARMUP CHECKLIST",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    for ((item, comp) in warmupComp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { trainViewModel.toggleWarmupItem(item) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = comp,
                                onCheckedChange = { trainViewModel.toggleWarmupItem(item) },
                                colors = CheckboxDefaults.colors(checkedColor = AmberAccent, uncheckedColor = MutedText)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = if (comp) MutedText else PrimaryText,
                                textDecoration = if (comp) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                            )
                        }
                    }
                }
            }

            // Current Exercise Layout
            if (currentIdx < exercises.size) {
                val ex = exercises[currentIdx]
                val setsList = loggedSets[ex.exerciseId] ?: emptyList()

                item {
                    var showPlateCalc by rememberSaveable { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "EXERCISE ${currentIdx + 1} OF ${exercises.size}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MutedText
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkRaised)
                                        .clickable { showPlateCalc = !showPlateCalc }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.train_plate_calc), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkRaised)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(ex.muscleGroup.uppercase(), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
                                }
                            }
                        }
                        
                        if (showPlateCalc) {
                            Spacer(modifier = Modifier.height(10.dp))
                            PlateCalculatorCard(initialWeight = ex.weight, units = unitSuffix)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ex.name,
                            fontFamily = SyneFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                        val contextLine = weightContextLines[ex.exerciseId] ?: ""
                        if (contextLine.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = contextLine,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = AmberAccent
                            )
                        }
                    }
                }

                val currEffSetsData = effectiveSetsMap[ex.exerciseId]
                if (currEffSetsData != null) {
                    item {
                        val nextSetIndex = setsList.indexOfFirst { !it.completed }
                        val currentSetNum = if (nextSetIndex != -1) nextSetIndex + 1 else setsList.size
                        val totalSetsNum = setsList.size
                        val lastCompletedSetObj = setsList.lastOrNull { it.completed }
                        val lastSetWeight = lastCompletedSetObj?.weight ?: 0.0
                        val lastSetReps = lastCompletedSetObj?.reps ?: 0

                        val units by fitnessViewModel.units.collectAsStateWithLifecycle()
                        RealTimeEffectiveSetsCard(
                            exName = ex.name,
                            effData = currEffSetsData,
                            currentSetNum = currentSetNum,
                            totalSetsNum = totalSetsNum,
                            lastSetWeight = lastSetWeight,
                            lastSetReps = lastSetReps,
                            units = units
                        )
                    }
                }


                // Row of sets logger inputs
                items(setsList.size, key = { setsList[it].id }) { sIdx ->
                    val setObj = setsList[sIdx]

                    // Local editing state keyed to stable set id to avoid reset when the session model updates
                    var rawWeight by rememberSaveable(setObj.id) { mutableStateOf(setObj.weight.toString()) }
                    var rawReps by rememberSaveable(setObj.id) { mutableStateOf(setObj.reps.toString()) }
                    var selectedRpe by remember(setObj.id) { mutableStateOf(setObj.rpe) }

                    LaunchedEffect(setObj.weight) {
                        val current = rawWeight.toDoubleOrNull()
                        if (current != setObj.weight) {
                            rawWeight = setObj.weight.toString()
                        }
                    }
                    LaunchedEffect(setObj.reps) {
                        val current = rawReps.toIntOrNull()
                        if (current != setObj.reps) {
                            rawReps = setObj.reps.toString()
                        }
                    }

                    // Error strings keyed to set id (and set index)
                    var weightError by remember(setObj.id, sIdx) { mutableStateOf("") }
                    var repsError by remember(setObj.id, sIdx) { mutableStateOf("") }

                    // Commit helpers — call when editing finishes (focus loss or explicit commit)
                    val commitWeight: () -> Unit = {
                        val w = rawWeight.toDoubleOrNull()
                        if (w != null && w in 0.25..500.0) {
                            val r = rawReps.toIntOrNull() ?: setObj.reps
                            trainViewModel.logWorkoutSetState(ex.exerciseId, sIdx, w, r, selectedRpe, 
                                setObj.completed, restTakenSeconds = setObj.restTaken)
                        }
                    }
                    val commitReps: () -> Unit = {
                        val r = rawReps.toIntOrNull()
                        if (r != null && r in 1..50) {
                            val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                            trainViewModel.logWorkoutSetState(ex.exerciseId, sIdx, w, r, selectedRpe, 
                                setObj.completed, restTakenSeconds = setObj.restTaken)
                        }
                    }

                    val isWeightValid = rawWeight.toDoubleOrNull()?.let { it in 0.25..500.0 } ?: false
                    val isRepsValid = rawReps.toIntOrNull()?.let { it in 1..50 } ?: false
                    val isRpeValid = selectedRpe in 1..10
                    val isSetValid = isWeightValid && isRepsValid && isRpeValid

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    setObj.completed -> Color(0xFF0F1E14)
                                    setObj.isWarmup -> Color(0xFF16162F)
                                    else -> DarkCardSurface
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    when {
                                        setObj.completed -> Color(0xFF2EC46A)
                                        setObj.isWarmup -> OrangeAccent.copy(alpha = 0.4f)
                                        else -> BorderSubtle
                                    }
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Column set label
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                                    if (setObj.isWarmup) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(OrangeAccent.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "WARMUP",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E1B4B)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    } else {
                                        Text(
                                            text = "SET",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                    Text(
                                        text = "${sIdx + 1}",
                                        fontFamily = SyneFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            setObj.completed -> GreenAccent
                                            setObj.isWarmup -> Color(0xFFA78BFA)
                                            else -> AmberAccent
                                        }
                                    )
                                }

                                // Weight textfield
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.train_weight), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
                                    BasicTextField(
                                        value = rawWeight,
                                        onValueChange = { input ->
                                            val normalizedInput = input.replace(',', '.')
                                            val filtered = normalizedInput.filter { it.isDigit() || it == '.' }
                                            val clean = if (filtered.count { it == '.' } > 1) {
                                                val firstDot = filtered.indexOf('.')
                                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                                            } else {
                                                filtered
                                            }

                                            var finalStr = clean
                                            var error = ""
                                            val dVal = clean.toDoubleOrNull()
                                            if (dVal != null) {
                                                if (dVal > 500.0) {
                                                    finalStr = "500.0"
                                                    error = "Weight: 0.25–500 $unitSuffix"
                                                } else if (dVal < 0.25) {
                                                    val isTypingPrefix = clean == "0" || clean == "0." || clean == "0.2"
                                                    if (!isTypingPrefix) {
                                                        finalStr = "0.25"
                                                    }
                                                    error = "Weight: 0.25–500 $unitSuffix"
                                                }
                                            } else if (clean.isNotEmpty()) {
                                                error = "Weight: 0.25–500 $unitSuffix"
                                            }

                                            // update local buffer only; do NOT push model update on every keystroke
                                            rawWeight = finalStr
                                            weightError = error
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                                        modifier = Modifier
                                            .background(DarkRaised, RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                            .fillMaxWidth()
                                            // commit to ViewModel when focus is lost
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    commitWeight()
                                                }
                                            },
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (rawWeight.isEmpty()) {
                                                    val rawSuggested = lastWeights[ex.exerciseId]
                                                    val suggested = if (rawSuggested != null) {
                                                        if (preferredUnits.lowercase() in listOf("lb", "lbs")) {
                                                            Math.round(rawSuggested * 2.20462 * 10.0) / 10.0
                                                        } else {
                                                            rawSuggested
                                                        }
                                                    } else {
                                                        ex.weight
                                                    }
                                                    Text(
                                                        text = "${suggested} $preferredUnits",
                                                        color = MutedText,
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    val contextLineInside = weightContextLines[ex.exerciseId] ?: ""
                                    if (contextLineInside.isNotEmpty()) {
                                        val shortNote = if (contextLineInside.contains(" → Suggested:")) {
                                            contextLineInside.substringBefore(" → Suggested:")
                                        } else {
                                            "Beginner Base"
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = shortNote,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = MutedText
                                        )
                                    }
                                    if (weightError.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = weightError,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = RedAccent,
                                            modifier = Modifier.testTag("weight_error_msg")
                                        )
                                    }
                                }

                                // Reps textfield
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.train_reps), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
                                    BasicTextField(
                                        value = rawReps,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() }
                                            var finalStr = filtered
                                            var error = ""
                                            val iVal = filtered.toIntOrNull()
                                            if (iVal != null) {
                                                if (iVal > 50) {
                                                    finalStr = "50"
                                                    error = "Reps: 1–50"
                                                } else if (iVal < 1) {
                                                    finalStr = "1"
                                                    error = "Reps: 1–50"
                                                }
                                            } else if (filtered.isNotEmpty()) {
                                                error = "Reps: 1–50"
                                            }

                                            // update local buffer only; do NOT push model update on every keystroke
                                            rawReps = finalStr
                                            repsError = error
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                                        modifier = Modifier
                                            .background(DarkRaised, RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    commitReps()
                                                }
                                            }
                                    )
                                    if (repsError.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = repsError,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = RedAccent,
                                            modifier = Modifier.testTag("reps_error_msg")
                                        )
                                    }
                                }
                                           // Completion Checkbox
                                IconButton(
                                    enabled = isSetValid && !showRirOverlay,
                                    onClick = {
                                        if (!showRirOverlay) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val newCompleted = !setObj.completed
                                            val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                            val r = rawReps.toIntOrNull() ?: setObj.reps

                                            if (newCompleted) {
                                                // Smart RIR dynamic selector instead of instant log
                                                val timerStartCall = {
                                                    trainViewModel.openRirSelector(
                                                        exerciseId = ex.exerciseId,
                                                        exerciseName = ex.name,
                                                        muscleGroup = ex.muscleGroup,
                                                        setIndex = sIdx,
                                                        weight = w,
                                                        reps = r,
                                                        totalSets = setsList.size
                                                     )
                                                }
                                                timerStartCall()
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context, android.Manifest.permission.POST_NOTIFICATIONS
                                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            } else {
                                                // Simple uncheck log state
                                                trainViewModel.logWorkoutSetState(ex.exerciseId, sIdx, w, r, selectedRpe, false)
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("log_checkmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (setObj.completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                        contentDescription = "Log set",
                                        tint = if (!isSetValid) MutedText.copy(alpha = 0.3f) else if (setObj.completed) GreenAccent else MutedText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Segmented Button select row for RPE (1 to 10)
                            Text(stringResource(R.string.train_rpe, selectedRpe), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkRaised)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                (1..10).forEach { rpeVal ->
                                    val isRpeSelected = selectedRpe == rpeVal
                                    val zoneColor = when (rpeVal) {
                                        in 1..5 -> Color(0xFF2EC46A) // Green zone
                                        in 6..7 -> Color(0xFFFFB300) // Yellow/Amber zone
                                        else -> Color(0xFFEF4444) // Red zone
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isRpeSelected) zoneColor else Color.Transparent)
                                            .clickable {
                                                selectedRpe = rpeVal
                                                val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                                val r = rawReps.toIntOrNull() ?: setObj.reps
                                                trainViewModel.logWorkoutSetState(ex.exerciseId, sIdx, w, r, rpeVal, setObj.completed)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = rpeVal.toString(),
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRpeSelected) Color(0xFF0A0A0F) else zoneColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add extra sets CTA row
                item {
                    val canAddSet = setsList.size < 20
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (canAddSet) {
                                        trainViewModel.addCustomLogSet(ex.exerciseId)
                                    }
                                },
                                enabled = canAddSet,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canAddSet) DarkCardSurface else DarkCardSurface.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (canAddSet) BorderSubtle else BorderSubtle.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.train_add_extra_set), fontFamily = SyneFamily, fontSize = 11.sp, color = if (canAddSet) PrimaryText else MutedText)
                            }

                            Button(
                                onClick = { 
                                    if (currentIdx == exercises.size - 1) {
                                        showNormalFinishFeelDialog = true
                                    } else {
                                        trainViewModel.nextExercise()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (currentIdx == exercises.size - 1) "FINISH WORKOUT" else "NEXT EXERCISE →",
                                    fontFamily = SyneFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A0A0F)
                                )
                            }
                        }

                        if (setsList.size >= 20) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sets: 1–20",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = RedAccent,
                                modifier = Modifier.testTag("sets_error_msg")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { 
                                if (currentIdx == exercises.size - 1) {
                                    showNormalFinishFeelDialog = true
                                } else {
                                    trainViewModel.skipExercise()
                                    Toast.makeText(context, context.getString(R.string.train_exercise_skipped, ex.name), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                            border = BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("skip_exercise_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Skip Exercise",
                                tint = AmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SKIP THIS EXERCISE",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Always available actions at the bottom of the LazyColumn (cancel/complete)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (exercises.isEmpty()) {
                        Button(
                            onClick = { trainViewModel.cancelOrCompleteEmptySession() },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(stringResource(R.string.train_complete_recovery_session),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0F)
                            )
                        }
                    } else {
                        Button(
                            onClick = { showFinishEarlyDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14221A)),
                            border = BorderStroke(1.dp, Color(0xFF2EC46A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("finish_early_button")
                        ) {
                            Text(stringResource(R.string.train_finish_workout_early_save_prog),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2EC46A)
                            )
                        }
                    }
                    Button(
                        onClick = { showExitDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1616)),
                        border = BorderStroke(1.dp, Color(0xFFE84A4A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(stringResource(R.string.train_cancel_current_workout_run),
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE84A4A)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}
}

@Composable
fun NewPlansSubTab(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel,
    onNavigateToPlanBuilder: () -> Unit
) {
    val plans by trainViewModel.workoutPlans.collectAsStateWithLifecycle()
    val activePlanState by trainViewModel.activePlan.collectAsStateWithLifecycle()
    
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
                    Text(stringResource(R.string.train_build_custom_workout_plan),
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
                                trainViewModel.activatePlan(plan.id)
                                Toast.makeText(context, context.getString(R.string.train_plan_activated), Toast.LENGTH_SHORT).show()
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

@Composable
fun RealTimeEffectiveSetsCard(
    exName: String,
    effData: EffectiveSetsData,
    currentSetNum: Int,
    totalSetsNum: Int,
    lastSetWeight: Double,
    lastSetReps: Int,
    units: String
) {
    PremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("effective_sets_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${exName.uppercase()} EFFECTIVE VOLUME",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Effective Sets: ${String.format(java.util.Locale.US, "%.2f", effData.currentEffectiveSets)} / ${String.format(java.util.Locale.US, "%.2f", effData.targetEffectiveSets)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "(${effData.progress.roundToInt()}% complete)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = AmberAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (effData.currentEffectiveSets / effData.targetEffectiveSets).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AmberAccent,
                trackColor = DarkRaised
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (effData.lastSetRPE > 0) {
                val effectivenessPct = when (effData.lastSetRPE) {
                    7 -> "50%"
                    8 -> "75%"
                    9 -> "90%"
                    10 -> "100%"
                    in 5..6 -> "0%"
                    else -> "0%"
                }
                
                val effectivenessZone = when (effData.lastSetRPE) {
                    7 -> "building zone"
                    8 -> "SWEET SPOT"
                    9 -> "high effort"
                    10 -> "maximal"
                    else -> "below threshold"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (effData.lastSetRPE == 8) AmberAccent.copy(alpha = 0.12f) else DarkRaised)
                        .border(
                            BorderStroke(1.dp, if (effData.lastSetRPE == 8) AmberAccent.copy(alpha = 0.25f) else BorderSubtle),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (effData.lastSetRPE == 8) {
                                Icon(Icons.Filled.Star, contentDescription = "Sweet Spot", tint = AmberAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Last Set: ${if (lastSetWeight > 0) "${lastSetWeight.roundToInt()} $units " else ""}${if (lastSetReps > 0 && lastSetWeight <= 0) "$lastSetReps reps " else if (lastSetReps > 0) "× $lastSetReps reps " else ""}@ RPE ${effData.lastSetRPE}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quality: ${String.format(java.util.Locale.US, "%.2f", effData.lastSetEffectiveness)} effective sets (${effectivenessPct} effective — ${effectivenessZone.uppercase()})",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = if (effData.lastSetRPE == 8) AmberAccent else SecondaryText
                        )
                    }
                }
            } else {
                Text(
                    text = "Log a set to view RPE effectiveness zone.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MutedText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle.copy(alpha = 0.5f)))
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Status: " + if (effData.progress >= 100) "Goal complete!" else if (effData.progress > 45) "On track" else "Building momentum",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = GreenAccent
                )
                Text(
                    text = "Next: Set ${currentSetNum}/${totalSetsNum}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
        }
    }
}