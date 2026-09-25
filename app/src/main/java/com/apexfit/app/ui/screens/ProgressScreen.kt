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
import android.widget.Toast
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

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkRaised,
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Amber left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(AmberAccent)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "DELOAD RECOMMENDED",
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberAccent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deloadResult.recommendation,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = SecondaryText
                )
                if (deloadResult.protocol.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    deloadResult.protocol.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "→ ",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = AmberAccent
                            )
                            Text(
                                text = action,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = PrimaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyVolumeRadarChart(
    volumeMap: Map<String, Int>,
    onSeeRecoveryClick: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Muscle Volume",
                        fontFamily = SyneFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Sets per muscle group (last 30 days)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
                IconButton(
                    onClick = onSeeRecoveryClick,
                    modifier = Modifier.background(DarkRaised, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "View Recovery",
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val axes = listOf("Chest", "Back", "Delts", "Legs", "Arms")
            val chestVal = volumeMap["chest"] ?: 0
            val backVal = volumeMap["back"] ?: 0
            val deltsVal = (volumeMap["front delts"] ?: 0) + (volumeMap["side delts"] ?: 0) + (volumeMap["rear delts"] ?: 0)
            val legsVal = (volumeMap["quadriceps"] ?: 0) + (volumeMap["hamstrings"] ?: 0) + (volumeMap["glutes"] ?: 0) + (volumeMap["calves"] ?: 0)
            val armsVal = (volumeMap["biceps"] ?: 0) + (volumeMap["triceps"] ?: 0)
            val vals = listOf(chestVal, backVal, deltsVal, legsVal, armsVal)

            val maxVal = (vals.maxOrNull() ?: 0).coerceAtLeast(10).toFloat()

            val hasData = vals.any { it > 0 }
            if (!hasData) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No training data yet", fontSize = 16.sp, color = MutedText)
                    Spacer(Modifier.height(8.dp))
                    Text("Log a workout to see your muscle volume distribution", fontSize = 12.sp, color = MutedText)
                }
            } else {
                currentMaxVal = maxVal
                val density = androidx.compose.ui.platform.LocalDensity.current
                val size = with(density) { Size(160.dp.toPx(), 160.dp.toPx()) }

                val gridPaths = remember(maxVal, size) {
                    // Build the 3 static pentagon grid paths here
                    listOf(
                        buildPentagonPath(size, maxVal * 0.33f),
                        buildPentagonPath(size, maxVal * 0.66f), 
                        buildPentagonPath(size, maxVal)
                    )
                }
                val dataPath = remember(vals, size, maxVal) {
                    buildDataPentagonPath(vals, size, maxVal)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(160.dp)
                            .semantics {
                                val description = axes.mapIndexed { i, axis ->
                                    "$axis: ${vals[i]} sets"
                                }.joinToString(", ")
                                contentDescription = "Muscle volume radar chart. $description"
                            }
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val r = size.minDimension / 2 - 20.dp.toPx()

                        // Draw grid concentric pentagons
                        gridPaths.forEach { path ->
                            drawPath(
                                path = path,
                                color = BorderSubtle.copy(alpha = 0.4f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        // Draw axes lines from center to outer points
                        for (i in 0 until 5) {
                            val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
                            val x = center.x + r * cos(angle).toFloat()
                            val y = center.y + r * sin(angle).toFloat()
                            drawLine(
                                color = BorderSubtle.copy(alpha = 0.4f),
                                start = center,
                                end = Offset(x, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Plot active data path
                        if (maxVal > 0) {
                            // Drawing filled path with alpha
                            drawPath(
                                path = dataPath,
                                color = AccentSecondary.copy(alpha = 0.25f)
                            )
                            // Outer path stroke line
                            drawPath(
                                path = dataPath,
                                color = AccentSecondary,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Superimpose Label texts nicely spaced around outer edge
                    for (i in 0 until 5) {
                        val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
                        val xOffset = (80 * cos(angle)).toFloat().dp
                        val yOffset = (80 * sin(angle)).toFloat().dp

                        Box(
                            modifier = Modifier
                                .offset(x = xOffset, y = yOffset)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkBackground.copy(alpha = 0.7f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${axes[i]}: ${vals[i]}s",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vals[i] > 0) AccentSecondary else MutedText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer CTA button
            Button(
                onClick = onSeeRecoveryClick,
                colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "View Recovery Map",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent
                )
            }
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
                                val icon = when (part) {
                                    "Chest" -> Icons.Filled.Accessibility
                                    "Biceps" -> Icons.Filled.FitnessCenter
                                    "Waist" -> Icons.Filled.LineWeight
                                    "Thighs" -> Icons.Filled.DirectionsRun
                                    "Calves" -> Icons.Filled.DirectionsWalk
                                    else -> Icons.Filled.AccessibilityNew
                                }
                                Icon(
                                    imageVector = icon,
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
                                imageVector = Icons.Filled.ChevronRight,
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
    onOpenMeasurementDetail: (String) -> Unit
) {
    val fatigueInfo by algorithmViewModel.fatigueResult.collectAsStateWithLifecycle()
    val deloadResult by algorithmViewModel.deloadRecommendation.collectAsStateWithLifecycle()
    val muscleVolumeMap by algorithmViewModel.muscleVolumes.collectAsStateWithLifecycle()
    val heatmap by algorithmViewModel.muscleHeatmap.collectAsStateWithLifecycle()
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val measurementsState by progressViewModel.allBodyMeasurements.collectAsStateWithLifecycle()
    val monthlyMuscleVolumesState by progressViewModel.monthlyMuscleVolumes.collectAsStateWithLifecycle()

    if (weightHistoryState is UiState.Loading || measurementsState is UiState.Loading || monthlyMuscleVolumesState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BlueAccent)
        }
        return
    }

    if (weightHistoryState is UiState.Error || measurementsState is UiState.Error || monthlyMuscleVolumesState is UiState.Error) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.progress_error_loading_progress_data), color = Color.White)
        }
        return
    }

    val weightHistory: List<com.apexfit.app.ui.models.UiWeightEntry> = (weightHistoryState as? UiState.Success)?.data ?: emptyList()
    val measurements: List<com.apexfit.app.ui.models.UiBodyMeasurement> = (measurementsState as? UiState.Success)?.data ?: emptyList()
    val monthlyMuscleVolumes: Map<String, Int> = (monthlyMuscleVolumesState as? UiState.Success)?.data ?: emptyMap()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val lengthUnit = if (units == "kg") "cm" else "in"
    var activeSubTab by rememberSaveable { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pendingDeletions = remember { mutableStateMapOf<Long, kotlinx.coroutines.Job>() }

    fun triggerPendingWeightDeletion(id: Long) {
        if (pendingDeletions.containsKey(id)) return
        val job = coroutineScope.launch {
            delay(4000)
            fitnessViewModel.deleteWeightById(id)
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

    val deletionKeys = remember(pendingDeletions) { pendingDeletions.keys.toSet() }
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
        // Futuristic Top Segmented Tab Selector for clean high-fidelity UX spacing
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF13131D))
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(0, "Muscle Volume", Icons.Filled.FitnessCenter),
                    Triple(1, "Recovery", Icons.Filled.CheckCircle),
                    Triple(2, "BODY METRICS", Icons.Filled.BarChart)
                )
                tabs.forEach { (index, title, icon) ->
                    val isSelected = activeSubTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                            .clickable { activeSubTab = index }
                            .padding(vertical = 12.dp)
                            .heightIn(min = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (isSelected) Color(0xFF0F0F1A) else MutedText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = title,
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0F0F1A) else SecondaryText
                            )
                        }
                    }
                }
            }
        }

        // Category 2: BODY METRICS
        if (activeSubTab == 2) {
            // Quick Body weight log text field
            item {
                var inputWeight by rememberSaveable { mutableStateOf("") }
                val context = LocalContext.current

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
                            onValueChange = { inputWeight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                            placeholder = { Text(stringResource(R.string.progress_log_weight_index_e_g_78_5), color = MutedText, fontSize = 11.sp) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("log_weight_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            )
                        )

                        Button(
                            onClick = {
                                val w = inputWeight.toDoubleOrNull()
                                val weightMin = if (units.lowercase() == "kg") 20.0 else 44.0
                                val weightMax = if (units.lowercase() == "kg") 300.0 else 660.0
                                when {
                                    w == null -> 
                                        Toast.makeText(context, "Enter a valid number", Toast.LENGTH_SHORT).show()
                                    w !in weightMin..weightMax -> 
                                        Toast.makeText(context, "Must be $weightMin–$weightMax $units", Toast.LENGTH_SHORT).show()
                                    else -> {
                                        fitnessViewModel.logWeight(w, preferredUnit = units)
                                        inputWeight = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("log_weight_button")
                        ) {
                            Text(stringResource(R.string.progress_save_weight), fontFamily = SyneFamily, fontSize = 11.sp, color = Color(0xFF0F0F1A))
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
                            text = "Tracked for each day entered with system local time auto picked.",
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
        if (activeSubTab == 1) {
            // Showcase 1: Semicircular Fatigue Needle gauge drawn with Canvas
            item {
                val needleAnimState = remember { Animatable(0f) }
                // Gauge is needle progress (0f to 1f) corresponding to Fatigue zones
                // Let's bind needleAnimState to fatigue ratios (optimal around index 0.5)
                LaunchedEffect(fatigueInfo) {
                    val ratio = (fatigueInfo.ratio ?: 0.0).toFloat().coerceIn(0f, 2f)
                    val targetProgress = ratio / 2.0f // mapped 0f to 1f
                    needleAnimState.animateTo(
                        targetValue = targetProgress,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f)
                    )
                }

                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Fatigue Index",
                        fontFamily = SyneFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = "Fatigue gauge showing ${fatigueInfo.ratio ?: "unknown"} ratio, status: ${fatigueInfo.statusLabel}"
                                }
                        ) {
                            val strokeW = 14.dp.toPx()
                            val diameter = size.minDimension * 0.95f
                            val radius = diameter / 2
                            val centerPt = Offset(size.width / 2, size.height - 10.dp.toPx())

                            // Zones color slices
                            // Semicircle starts at 180 degrees index extending 180 deg
                            // Part A: Undertraining Blue (180deg to 225deg)
                            drawArc(
                                color = BlueAccent,
                                startAngle = 180f,
                                sweepAngle = 45f,
                                useCenter = false,
                                topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            // Part B: Optimal Green (225deg to 270deg)
                            drawArc(
                                color = GreenAccent,
                                startAngle = 225f,
                                sweepAngle = 45f,
                                useCenter = false,
                                topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            // Part C: Pushing Hard Amber (270deg to 315deg)
                            drawArc(
                                color = AmberAccent,
                                startAngle = 270f,
                                sweepAngle = 45f,
                                useCenter = false,
                                topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )
                            // Part D: Overreaching Red (315deg to 360deg)
                            drawArc(
                                color = RedAccent,
                                startAngle = 315f,
                                sweepAngle = 45f,
                                useCenter = false,
                                topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                            )

                            // Draw Needle Line extending outwards from centerPt
                            // angle in radians = dynamic needle anim state mapped 180f to 360f
                            val needleAngleDeg = 180f + (needleAnimState.value * 180f)
                            val needleRad = Math.toRadians(needleAngleDeg.toDouble())
                            val needleLen = radius * 0.85
                            val endX = centerPt.x + (cos(needleRad) * needleLen).toFloat()
                            val endY = centerPt.y + (sin(needleRad) * needleLen).toFloat()

                            drawLine(
                                color = PrimaryText,
                                start = centerPt,
                                end = Offset(endX, endY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Center hub pin
                            drawCircle(color = DarkBackground, radius = 8.dp.toPx(), center = centerPt)
                            drawCircle(color = AmberAccent, radius = 5.dp.toPx(), center = centerPt)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.progress_undertrained), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = BlueAccent)
                        Text(stringResource(R.string.progress_optimal), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = GreenAccent)
                        Text(stringResource(R.string.progress_overreaching), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = RedAccent)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "FATIGUE RATIO INDEX IS ${String.format(java.util.Locale.US, "%.2f", fatigueInfo.ratio ?: 0.0)} (${fatigueInfo.statusLabel})",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = fatigueInfo.recommendation,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }

            if (deloadResult.urgency != "none") {
                item {
                    DeloadCard(deloadResult = deloadResult)
                }
            }

            // Radar Spider Chart & diagnostic focus trigger
            item {
                MonthlyVolumeRadarChart(
                    volumeMap = monthlyMuscleVolumes,
                    onSeeRecoveryClick = onOpenMuscleRecovery
                )
            }
        } // End of activeSubTab == 1

        // Category 2 Continued: BODY METRICS - Part 2 (Measurements)
        if (activeSubTab == 2) {
            // Showcase 1.6: Body Measurements list/entry rows
            item {
                BodyMeasurementsTrackerPanel(
                    measurements = measurements,
                    lengthUnit = lengthUnit,
                    onPartClick = onOpenMeasurementDetail
                )
            }
        }

        // Category 0: FIBER INDEX
        if (activeSubTab == 0) {
            // Live Real-Time Hypertrophy Analytics Summary Row (UX Optimization)
            item {
                val totalSets = muscleVolumeMap.values.sum()
                val activeMuscles = muscleVolumeMap.values.count { it > 0 }
                val upperKeys = listOf(
                    "chest", "back", "front delts", "side delts", "rear delts",
                    "biceps", "triceps", "forearms", "trapezius", "neck",
                    "rotator cuff", "serratus anterior"
                )
                val lowerKeys = listOf(
                    "quadriceps", "hamstrings", "glutes", "calves",
                    "hip abductors", "hip adductors", "tibialis anterior"
                )
                val balanceText = when {
                    activeMuscles == 0 -> "Untrained"
                    else -> {
                        val upperSets = upperKeys.sumOf { muscleVolumeMap[it] ?: 0 }
                        val lowerSets = lowerKeys.sumOf { muscleVolumeMap[it] ?: 0 }
                        when {
                            upperSets.toDouble() > lowerSets * 1.5 -> "Upper Focus"
                            lowerSets.toDouble() > upperSets * 1.5 -> "Lower Focus"
                            else -> "Balanced"
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Sets Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "WEEKLY VOLUME",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$totalSets",
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccent
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "sets",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }

                    // Active Targets Card
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE TARGETS",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$activeMuscles",
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenAccent
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "/23 muscles",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }

                    // Balance Status Card
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "BALANCE",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = balanceText,
                                fontFamily = SyneFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
}

private var currentMaxVal: Float = 10f

private fun buildPentagonPath(size: Size, scale: Float): Path {
    val center = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2 - (size.minDimension * 0.125f)
    val level = if (scale > 1.0f && currentMaxVal > 0f) {
        (scale / currentMaxVal).coerceIn(0f, 1f)
    } else {
        scale.coerceIn(0f, 1f)
    }
    val path = Path()
    for (i in 0 until 5) {
        val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
        val x = center.x + r * level * cos(angle).toFloat()
        val y = center.y + r * level * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun buildDataPentagonPath(vals: List<Int>, size: Size, maxVal: Float): Path {
    val dataPath = Path()
    if (maxVal <= 0f) return dataPath
    val center = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2 - (size.minDimension * 0.125f)
    for (i in 0 until 5) {
        val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
        val normalizedVal = if (i < vals.size) (vals[i].toFloat() / maxVal).coerceAtMost(1.0f) else 0f
        val x = center.x + r * normalizedVal * cos(angle).toFloat()
        val y = center.y + r * normalizedVal * sin(angle).toFloat()
        if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
    }
    dataPath.close()
    return dataPath
}

