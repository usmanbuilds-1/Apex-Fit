package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.apexfit.app.ui.models.UiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.ui.theme.*
import com.apexfit.app.ui.models.UiBodyMeasurement
import com.apexfit.app.ui.models.UiDeloadResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.times
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.components.MuscleHeatmapCanvas
import com.apexfit.app.data.HeatmapEntry
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.cos
import kotlin.math.sin

val AccentSecondary = com.apexfit.app.ui.theme.OrangeAccent // Orange accent for visual hierarchy

@Composable
fun ProgressScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    progressViewModel: ProgressViewModel,
    homeViewModel: HomeViewModel,
    onNavigateTo: (Int) -> Unit
) {
    var subScreen by rememberSaveable { mutableStateOf("main") }
    var selectedPart by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = subScreen != "main") {
        subScreen = "main"
    }

    Crossfade(targetState = subScreen, label = "progressSubscreen") { screen ->
        when (screen) {
            "personal_records" -> {
                PersonalRecordsScreen(progressViewModel = progressViewModel, onBack = { subScreen = "main" })
            }
            "workout_history" -> {
                WorkoutHistoryScreen(progressViewModel = progressViewModel, onBack = { subScreen = "main" })
            }
            "muscle_volume" -> MuscleVolumeScreen(homeViewModel, onBack = { subScreen = "main" })
            "muscle_recovery" -> {
                MuscleRecoveryScreen(
                    progressViewModel = progressViewModel,
                    onBack = { subScreen = "main" }
                )
            }
            "measurement_detail" -> {
                selectedPart?.let { part ->
                    BodyMeasurementDetailScreen(
                        progressViewModel = progressViewModel,
                        bodyPart = part,
                        onBack = { subScreen = "main" }
                    )
                }
            }
            else -> {
                ProgressMainTabContent(
                    fitnessViewModel = fitnessViewModel,
                    algorithmViewModel = algorithmViewModel,
                    progressViewModel = progressViewModel,
                    homeViewModel = homeViewModel,
                    onOpenMuscleRecovery = { subScreen = "muscle_recovery" },
                    onOpenMuscleVolume = { subScreen = "muscle_volume" },
                    onOpenWorkoutHistory = { subScreen = "workout_history" },
                    onOpenPersonalRecords = { subScreen = "personal_records" },
                    onOpenMeasurementDetail = { part ->
                        selectedPart = part
                        subScreen = "measurement_detail"
                    }
                )
            }
        }
    }
}

@Composable
fun DeloadCard(
    deloadResult: UiDeloadResult,
    modifier: Modifier = Modifier
) {
    if (deloadResult.urgency.equals("none", ignoreCase = true) || deloadResult.urgency.isEmpty()) return

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Deload recommended", style = MaterialTheme.typography.titleMedium)
        Text(deloadResult.recommendation, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        deloadResult.protocol.forEach { action ->
            Text(action, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun buildLatestMeasurementsMap(measurements: List<UiBodyMeasurement>): Map<String, UiBodyMeasurement> {
    return measurements
        .groupBy { it.bodyPart.lowercase() }
        .mapValues { (_, list) -> list.maxByOrNull { it.date } ?: list.first() }
}

@Composable
fun BodyMeasurementsTrackerPanel(
    measurements: List<UiBodyMeasurement>,
    lengthUnit: String,
    onPartClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCardSurface)
            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Body Measurements",
                fontFamily = SyneFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "Tap a measurement to log or view trends",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val standardParts = listOf("Chest", "Biceps", "Waist", "Thighs", "Calves", "Shoulders")
            val latestMeasurements = remember(measurements) { buildLatestMeasurementsMap(measurements) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                standardParts.forEach { part ->
                    val latest = latestMeasurements[part.lowercase()]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkRaised)
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .clickable { onPartClick(part) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentSecondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconRes = when (part) {
                                    "Chest" -> R.drawable.ic_accessibility
                                    "Biceps" -> R.drawable.ic_fitness_center
                                    "Waist" -> R.drawable.ic_line_weight
                                    "Thighs" -> R.drawable.ic_directions_run
                                    "Calves" -> R.drawable.ic_directions_walk
                                    else -> R.drawable.ic_accessibility_new
                                }
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = part,
                                    tint = AccentSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = part.uppercase(),
                                    fontFamily = SyneFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Text(
                                    text = if (latest != null) "Last logged: ${latest.date}" else "No custom log recorded",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val displayValue = if (latest != null) {
                                if (lengthUnit == "in") latest.value / 2.54 else latest.value
                            } else 0.0
                            val formattedValue = String.format(java.util.Locale.US, "%.1f", displayValue)
                            Text(
                                text = if (latest != null) "$formattedValue $lengthUnit" else "-- --",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (latest != null) AmberAccent else MutedText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_right),
                                contentDescription = "Detail",
                                tint = MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressMainTabContent(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    progressViewModel: ProgressViewModel,
    homeViewModel: HomeViewModel,
    onOpenMuscleRecovery: () -> Unit,
    onOpenMuscleVolume: () -> Unit,
    onOpenWorkoutHistory: () -> Unit,
    onOpenPersonalRecords: () -> Unit,
    onOpenMeasurementDetail: (String) -> Unit
) {
    // Collect only the history required by the visible section. Room otherwise opens
    // all three queries on every visit to Progress, and an unrelated Loading state
    // previously blocked the entire page.
    var activeSubTab by rememberSaveable { mutableStateOf(0) }
    var showLogWeight by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = activeSubTab != 0) { activeSubTab = 0 }
    val fatigueInfo by algorithmViewModel.fatigueResult.collectAsStateWithLifecycle()
    val deloadResult by algorithmViewModel.deloadRecommendation.collectAsStateWithLifecycle()
    val records by progressViewModel.personalRecords.collectAsStateWithLifecycle()
    val today by homeViewModel.todayDate.collectAsStateWithLifecycle()
    val goal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val recordedSessions by homeViewModel.completedSessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val weightHistoryState: UiState<List<com.apexfit.app.ui.models.UiWeightEntry>> =
        if (activeSubTab != 1) homeViewModel.weightHistory.collectAsStateWithLifecycle().value
        else UiState.Success(emptyList())
    val measurementsState: UiState<List<com.apexfit.app.ui.models.UiBodyMeasurement>> =
        if (activeSubTab == 2) progressViewModel.allBodyMeasurements.collectAsStateWithLifecycle().value
        else UiState.Success(emptyList())
    val visibleDataStates: List<UiState<*>> = when (activeSubTab) {
        2 -> listOf(weightHistoryState, measurementsState)
        else -> emptyList()
    }
    val isSectionLoading = visibleDataStates.any { it is UiState.Loading }
    val isSectionError = visibleDataStates.any { it is UiState.Error }

    val weightHistory: List<com.apexfit.app.ui.models.UiWeightEntry> = (weightHistoryState as? UiState.Success)?.data ?: emptyList()
    val measurements: List<com.apexfit.app.ui.models.UiBodyMeasurement> = (measurementsState as? UiState.Success)?.data ?: emptyList()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val lengthUnit = if (units == "kg") "cm" else "in"
    if (showLogWeight) {
        LogWeightDialog(initialInput = "", units = units,
            onDismiss = { showLogWeight = false },
            onSave = { weight ->
                homeViewModel.logWeight(weight, preferredUnit = units)
                showLogWeight = false
            })
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pendingDeletions = remember { mutableStateMapOf<Long, kotlinx.coroutines.Job>() }

    fun triggerPendingWeightDeletion(id: Long) {
        if (pendingDeletions.containsKey(id)) return
        val job = coroutineScope.launch {
            delay(4000)
            homeViewModel.deleteWeightById(id)
            pendingDeletions.remove(id)
        }
        pendingDeletions[id] = job
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Weight entry deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeletions[id]?.cancel()
                pendingDeletions.remove(id)
            }
        }
    }

    DisposableEffect(homeViewModel) {
        onDispose {
            pendingDeletions.forEach { (id, job) -> job.cancel(); homeViewModel.deleteWeightById(id) }
            pendingDeletions.clear()
        }
    }
    val deletionKeys = pendingDeletions.keys.toSet()
    val filteredWeights = remember(weightHistory, deletionKeys) { weightHistory.filter { it.id !in deletionKeys } }
    val visibleWeights = filteredWeights

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Text("Progress", color = PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp))
        }
        if (activeSubTab != 0) item {
            TextButton(onClick = { activeSubTab = 0 }) { Text("Back to Progress") }
        }
        if (activeSubTab == 0) item {
            ProgressOverviewContent(
                weightHistoryState, recordedSessions, java.time.LocalDate.parse(today), goal, units,
                records.size,
                if (fatigueInfo.ratio == null) "Recovery estimates need more training history."
                else "Recent training load: " + fatigueInfo.statusLabel.lowercase(),
                onLogWeight = { showLogWeight = true },
                onOpenRecords = onOpenPersonalRecords,
                onOpenHistory = onOpenWorkoutHistory,
                onOpenMeasurements = { activeSubTab = 2 },
                onOpenVolume = onOpenMuscleVolume,
                onOpenRecovery = onOpenMuscleRecovery,
                onOpenFatigue = { activeSubTab = 1 }
            )
        }

        if (isSectionLoading || isSectionError) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSectionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ApexColors.Primary, strokeWidth = 2.dp)
                        Text("Loading progress…", color = SecondaryText)
                    } else {
                        Text(stringResource(R.string.progress_error_loading_progress_data), color = ApexColors.Alert)
                    }
                }
            }
        }

        // Category 2: BODY METRICS
        if (activeSubTab == 2 && !isSectionLoading && !isSectionError) {
            // Quick Body weight log text field
            item {
                var inputWeight by rememberSaveable { mutableStateOf("") }
                var weightError by remember { mutableStateOf<String?>(null) }

                PremiumCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(
                        text = "Weight Log",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = inputWeight,
                            onValueChange = {
                                inputWeight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                                weightError = null
                            },
                            isError = weightError != null,
                            supportingText = weightError?.let { { Text(it, color = RedAccent) } },
                            placeholder = { Text(stringResource(R.string.progress_log_weight_index_e_g_78_5), color = MutedText, fontSize = 11.sp) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("log_weight_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = if (weightError != null) RedAccent else AmberAccent,
                                unfocusedIndicatorColor = if (weightError != null) RedAccent else BorderSubtle
                            )
                        )

                        Button(
                            onClick = {
                                val w = inputWeight.toDoubleOrNull()
                                val weightMin = if (units.lowercase() == "kg") 20.0 else 44.0
                                val weightMax = if (units.lowercase() == "kg") 300.0 else 660.0
                                when {
                                    w == null ->
                                        weightError = "Enter a valid number"
                                    w !in weightMin..weightMax ->
                                        weightError = "Must be $weightMin–$weightMax $units"
                                    else -> {
                                        weightError = null
                                        homeViewModel.logWeight(w, preferredUnit = units)
                                        inputWeight = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("log_weight_button")
                        ) {
                            Text(stringResource(R.string.progress_save_weight), fontFamily = SyneFamily, fontSize = 11.sp, color = ApexColors.OnGlow)
                         }
                    }
                }
            }

            // Weight log entries history
            if (visibleWeights.isNotEmpty()) {
                item {
                    PremiumCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "WEIGHT REGISTRY LOGS",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "Entries use your local date.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MutedText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleWeights.take(10).forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkCardSurface)
                                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${entry.weight.toDisplayWeight(units)} $units",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                            Text(
                                                text = "Logged: ${entry.date}${if (entry.time.isNotEmpty()) " • ${com.apexfit.app.utils.formatTimeForDisplay(LocalContext.current, entry.time)}" else ""}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 11.sp,
                                                color = SecondaryText
                                            )
                                    }
                                    IconButton(
                                        onClick = { triggerPendingWeightDeletion(entry.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete weight entry",
                                            tint = MutedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // End of activeSubTab == 2

        // Category 1: BIO RECOVERY
        if (activeSubTab == 1 && !isSectionLoading && !isSectionError) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fatigue & deload", color = PrimaryText, style = MaterialTheme.typography.headlineSmall)
                    if (fatigueInfo.ratio == null) {
                        Text("More logged training is needed to summarize current load.", color = SecondaryText)
                    } else {
                        Text(fatigueInfo.statusLabel, color = PrimaryText, style = MaterialTheme.typography.titleLarge)
                        Text(fatigueInfo.recommendation, color = SecondaryText)
                    }
                    Text("Training-load estimates can guide adjustments. Consider how you feel today.",
                        color = SecondaryText)
                    TextButton(onClick = onOpenMuscleRecovery) { Text("View muscle recovery") }
                }
            }

            if (deloadResult.urgency != "none") {
                item {
                    DeloadCard(deloadResult = deloadResult)
                }
            }

        } // End of activeSubTab == 1

        // Category 2 Continued: BODY METRICS - Part 2 (Measurements)
        if (activeSubTab == 2 && !isSectionLoading && !isSectionError) {
            // Showcase 1.6: Body Measurements list/entry rows
            item {
                BodyMeasurementsTrackerPanel(
                    measurements = measurements,
                    lengthUnit = lengthUnit,
                    onPartClick = onOpenMeasurementDetail
                )
            }
        }

        }
    }
}
