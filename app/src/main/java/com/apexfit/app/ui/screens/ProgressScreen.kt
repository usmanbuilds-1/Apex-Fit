package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.apexfit.app.ui.models.UiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
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
import com.apexfit.app.ui.models.UiFatigueResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.times
import com.apexfit.app.ui.components.ApexSegmentedControl
import com.apexfit.app.ui.components.MuscleHeatmapCanvas
import com.apexfit.app.data.HeatmapEntry
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.cos
import kotlin.math.sin

val AccentSecondary = com.apexfit.app.ui.theme.OrangeAccent // Orange accent for visual hierarchy

@Composable
internal fun ProgressHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Progress", color = PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_apex_chevron), contentDescription = null,
                tint = ApexColors.Primary, modifier = Modifier.size(18.dp))
            Text("APEX FIT", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp)
        }
    }
}

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

    Crossfade(
        targetState = subScreen,
        animationSpec = tween(durationMillis = 150),
        label = "progressSubscreen"
    ) { screen ->
        when (screen) {
            "strength_history" -> ExerciseStrengthScreen(progressViewModel, onBack = { subScreen = "main" })
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
                    onOpenStrength = { subScreen = "strength_history" },
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

@Composable
internal fun FatigueDeloadContent(
    fatigue: UiFatigueResult,
    deload: UiDeloadResult,
    onOpenRecovery: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Fatigue & deload", color = PrimaryText, style = MaterialTheme.typography.headlineSmall)
        if (fatigue.ratio == null) {
            Text(
                "More logged training is needed to summarize current load.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(fatigue.statusLabel, color = PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text(fatigue.recommendation, color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Training-load estimates can guide adjustments. Consider how you feel today.",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall
        )
        if (!deload.urgency.equals("none", ignoreCase = true) && deload.urgency.isNotBlank()) {
            DeloadCard(deloadResult = deload)
        }
        TextButton(onClick = onOpenRecovery) {
            Text("View muscle recovery")
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
    Column(Modifier.fillMaxWidth()) {
        Text("Measurements", style = MaterialTheme.typography.titleLarge, color = PrimaryText)
        Text(
            "Log a body area to see its measurement history.",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )

        val standardParts = listOf("Chest", "Biceps", "Waist", "Thighs", "Calves", "Shoulders")
        val latestMeasurements = remember(measurements) { buildLatestMeasurementsMap(measurements) }
        standardParts.forEachIndexed { index, part ->
            val latest = latestMeasurements[part.lowercase()]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Log or view $part measurements") {
                        onPartClick(part)
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(part, color = PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = latest?.let {
                            "Last logged ${com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(it.date)}"
                        } ?: "No measurement logged",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val displayValue = latest?.let {
                    if (lengthUnit == "in") it.value / 2.54 else it.value
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = displayValue?.let { String.format(java.util.Locale.US, "%.1f %s", it, lengthUnit) } ?: "Log",
                        color = if (displayValue != null) PrimaryText else SecondaryText,
                        style = if (displayValue != null) ApexTextStyles.dataMedium else MaterialTheme.typography.bodyMedium
                    )
                    Text("›", color = SecondaryText, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (index < standardParts.lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}

@Composable
internal fun WeightLogSection(
    inputWeight: String,
    weightError: String?,
    units: String,
    onInputWeightChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Log weight", style = MaterialTheme.typography.titleMedium, color = ApexColors.TextPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = inputWeight,
                onValueChange = onInputWeightChange,
                label = { Text("Weight ($units)") },
                isError = weightError != null,
                supportingText = weightError?.let { { Text(it, color = ApexColors.Alert) } },
                placeholder = {
                    Text(
                        stringResource(R.string.progress_log_weight_index_e_g_78_5),
                        color = ApexColors.TextTertiary
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("log_weight_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ApexColors.SurfaceSunken,
                    unfocusedContainerColor = ApexColors.SurfaceSunken,
                    errorContainerColor = ApexColors.SurfaceSunken,
                    focusedTextColor = ApexColors.TextPrimary,
                    unfocusedTextColor = ApexColors.TextPrimary,
                    errorTextColor = ApexColors.TextPrimary,
                    focusedIndicatorColor = if (weightError != null) ApexColors.Alert else ApexColors.Primary,
                    unfocusedIndicatorColor = if (weightError != null) ApexColors.Alert else ApexColors.BorderSubtle,
                    errorIndicatorColor = ApexColors.Alert,
                    errorLabelColor = ApexColors.Alert,
                    focusedSupportingTextColor = ApexColors.Alert,
                    unfocusedSupportingTextColor = ApexColors.Alert
                )
            )
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("log_weight_button")
            ) {
                Text(stringResource(R.string.progress_save_weight), color = ApexColors.OnGlow)
            }
        }
    }
}

@Composable
internal fun WeightHistorySection(
    entries: List<com.apexfit.app.ui.models.UiWeightEntry>,
    units: String,
    onDelete: (Long) -> Unit,
    onEdit: ((com.apexfit.app.ui.models.UiWeightEntry) -> Unit)? = null
) {
    if (entries.isEmpty()) return
    var visibleCount by rememberSaveable { mutableIntStateOf(10) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = ApexColors.BorderSubtle, modifier = Modifier.padding(bottom = 12.dp))
        Text("Weight history", style = MaterialTheme.typography.titleMedium, color = ApexColors.TextPrimary)
        Text(
            "Your recent weigh-ins.",
            style = MaterialTheme.typography.bodySmall,
            color = ApexColors.TextSecondary,
            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp)
        )
        entries.take(visibleCount).forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${entry.weight.toDisplayWeight(units)} $units",
                        style = ApexTextStyles.dataMedium,
                        color = ApexColors.TextPrimary
                    )
                    Text(
                        "${com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(entry.date)}" +
                            if (entry.time.isNotEmpty()) " · ${com.apexfit.app.utils.formatTimeForDisplay(context, entry.time)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexColors.TextSecondary
                    )
                }
                if (onEdit != null) TextButton(onClick = { onEdit(entry) }) { Text("Edit") }
                IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(ApexIcons.Delete),
                        contentDescription = "Delete weight entry",
                        tint = ApexColors.TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (index < entries.take(visibleCount).lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
        }
        if (visibleCount < entries.size) TextButton(onClick = { visibleCount += 20 }) { Text("Show more") }
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
    onOpenMeasurementDetail: (String) -> Unit,
    onOpenStrength: (() -> Unit)? = null
) {
    // Collect only the history required by the visible section. Room otherwise opens
    // all three queries on every visit to Progress, and an unrelated Loading state
    // previously blocked the entire page.
    var activeSubTab by rememberSaveable { mutableStateOf(0) }
    var showLogWeight by rememberSaveable { mutableStateOf(false) }
    var editingWeightId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isSavingWeight by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = activeSubTab != 0) { activeSubTab = 0 }
    val fatigueInfo by algorithmViewModel.fatigueResult.collectAsStateWithLifecycle()
    val deloadResult by algorithmViewModel.deloadRecommendation.collectAsStateWithLifecycle()
    val records by progressViewModel.personalRecords.collectAsStateWithLifecycle()
    val recovery by progressViewModel.muscleRecoveryStatuses.collectAsStateWithLifecycle()
    val weeklyTarget by fitnessViewModel.weeklyWorkouts.collectAsStateWithLifecycle()
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
    val weightLogError by homeViewModel.weightLogError.collectAsStateWithLifecycle()
    val lengthUnit = if (units == "kg") "cm" else "in"
    val coroutineScope = rememberCoroutineScope()
    if (showLogWeight) {
        val editingEntry = weightHistory.firstOrNull { it.id == editingWeightId }
        LogWeightDialog(initialInput = editingEntry?.let { String.format(java.util.Locale.US, "%.3f", it.weight.toDisplayWeight(units)).trimEnd('0').trimEnd('.') }.orEmpty(), units = units,
            isEditing = editingWeightId != null,
            onDismiss = { showLogWeight = false; editingWeightId = null },
            saveError = weightLogError,
            isSaving = isSavingWeight,
            onSave = { weight ->
                if (!isSavingWeight) {
                    homeViewModel.clearWeightLogError()
                    isSavingWeight = true
                    coroutineScope.launch {
                        try {
                            val saveJob = (if (editingWeightId != null) homeViewModel.editWeightById(requireNotNull(editingWeightId), weight, units)
                                else homeViewModel.logWeight(weight, preferredUnit = units)) ?: return@launch
                            saveJob.join()
                            if (!saveJob.isCancelled && homeViewModel.weightLogError.value == null) {
                                showLogWeight = false
                                editingWeightId = null
                            }
                        } finally {
                            isSavingWeight = false
                        }
                    }
                }
            })
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pendingDeletions = remember { mutableStateMapOf<Long, kotlinx.coroutines.Job>() }

    fun triggerPendingWeightDeletion(id: Long) {
        if (pendingDeletions.containsKey(id)) return
        val job = coroutineScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = "Delete weight entry?",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result != SnackbarResult.ActionPerformed) {
                    val deletion = homeViewModel.deleteWeightById(id)
                    deletion.join()
                    if (deletion.isCancelled || homeViewModel.weightLogError.value != null) {
                        snackbarHostState.showSnackbar("Couldn't delete weight. Try again.")
                    }
                }
            } finally { pendingDeletions.remove(id) }
        }
        pendingDeletions[id] = job
        job.start()
    }

    DisposableEffect(homeViewModel) {
        onDispose {
            // Leaving the page before the undo window ends cancels the pending action.
            pendingDeletions.values.toList().forEach { it.cancel() }
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
            ProgressHeader(Modifier.padding(top = 16.dp))
        }
        if (activeSubTab != 0) item {
            TextButton(onClick = { activeSubTab = 0 }) { Text("Back to Progress") }
        }
        if (activeSubTab == 0) item {
            ProgressOverviewContent(
                weightHistoryState, recordedSessions, java.time.LocalDate.parse(today), goal, units,
                records, weeklyTarget, recovery,
                onLogWeight = {
                    homeViewModel.clearWeightLogError()
                    showLogWeight = true
                },
                onOpenRecords = onOpenPersonalRecords,
                onOpenHistory = onOpenWorkoutHistory,
                onOpenMeasurements = { activeSubTab = 2 },
                onOpenVolume = onOpenMuscleVolume,
                onOpenRecovery = onOpenMuscleRecovery,
                onOpenFatigue = { activeSubTab = 1 },
                onOpenStrength = onOpenStrength
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
            item {
                var inputWeight by rememberSaveable { mutableStateOf("") }
                var weightError by remember { mutableStateOf<String?>(null) }
                var savingInline by remember { mutableStateOf(false) }
                WeightLogSection(
                    inputWeight = inputWeight,
                    weightError = weightError,
                    units = units,
                    onInputWeightChange = { value ->
                        inputWeight = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                        weightError = null
                    },
                    onSave = {
                        val weight = inputWeight.toDoubleOrNull()
                        val minimum = if (units.lowercase() == "kg") 20.0 else 44.1
                        val maximum = if (units.lowercase() == "kg") 300.0 else 661.3
                        when {
                            weight == null -> weightError = "Enter a valid number"
                            weight !in minimum..maximum -> weightError = "Must be $minimum–$maximum $units"
                            else -> {
                                weightError = null
                                if (!savingInline) {
                                    savingInline = true
                                    coroutineScope.launch {
                                        try {
                                            val job = homeViewModel.logWeight(weight, preferredUnit = units) ?: return@launch
                                            job.join()
                                            if (!job.isCancelled && homeViewModel.weightLogError.value == null) inputWeight = ""
                                            else weightError = homeViewModel.weightLogError.value ?: "Save interrupted. Try again."
                                        } finally { savingInline = false }
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (visibleWeights.isNotEmpty()) {
                item { WeightHistorySection(visibleWeights, units, onDelete = ::triggerPendingWeightDeletion, onEdit = { entry ->
                    editingWeightId = entry.id
                    homeViewModel.clearWeightLogError()
                    showLogWeight = true
                }) }
            }
        } // End of activeSubTab == 2

        // Category 1: BIO RECOVERY
        if (activeSubTab == 1 && !isSectionLoading && !isSectionError) {
            item {
                FatigueDeloadContent(
                    fatigue = fatigueInfo,
                    deload = deloadResult,
                    onOpenRecovery = onOpenMuscleRecovery
                )
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

