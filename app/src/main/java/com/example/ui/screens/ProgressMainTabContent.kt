package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FitnessViewModel
import com.example.data.AlgorithmViewModel
import com.example.ui.components.MuscleHeatmapCanvas
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressMainTabContent(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    onOpenMuscleRecovery: () -> Unit,
    onOpenMeasurementDetail: (String) -> Unit
) {
    val fatigueInfo by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()
    val muscleVolumeMap by algorithmViewModel.muscleVolumes.collectAsStateWithLifecycle()
    val heatmap by algorithmViewModel.muscleHeatmap.collectAsStateWithLifecycle()
    val weightHistory by fitnessViewModel.weightHistory.collectAsStateWithLifecycle()
    val measurements by fitnessViewModel.allBodyMeasurements.collectAsStateWithLifecycle()
    val monthlyMuscleVolumes by fitnessViewModel.monthlyMuscleVolumes.collectAsStateWithLifecycle()
    var activeSubTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                    Triple(0, "FIBER INDEX", Icons.Filled.FitnessCenter),
                    Triple(1, "BIO RECOVERY", Icons.Filled.CheckCircle),
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
                            .padding(vertical = 10.dp),
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
                                fontSize = 9.sp,
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
                var inputWeight by remember { mutableStateOf("") }
                val context = LocalContext.current

                PremiumCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(
                        text = "BODY MASS REGISTER",
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
                            onValueChange = { inputWeight = it },
                            placeholder = { Text("Log weight index (e.g. 78.5)...", color = MutedText, fontSize = 11.sp) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                if (w != null) {
                                    fitnessViewModel.logWeight(w)
                                    inputWeight = ""
                                    Toast.makeText(context, "Weight profile catalog update saved!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Enter numeric index", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("log_weight_button")
                        ) {
                            Text("SAVE WEIGHT", fontFamily = SyneFamily, fontSize = 11.sp, color = Color(0xFF0A0A0F))
                        }
                    }
                }
            }

            // Weight log entries history
            if (weightHistory.isNotEmpty()) {
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
                            fontSize = 9.sp,
                            color = MutedText,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            weightHistory.take(10).forEach { entry ->
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
                                            text = "${entry.weight} kg",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                        Text(
                                            text = "Logged: ${entry.date} • ${entry.time.ifEmpty { "08:00 AM" }}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 10.sp,
                                            color = SecondaryText
                                        )
                                    }
                                    IconButton(
                                        onClick = { fitnessViewModel.deleteWeightById(entry.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete weight entry",
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
        } // End of activeSubTab == 2

        // Category 1: BIO RECOVERY
        if (activeSubTab == 1) {
            // Showcase 1: Semicircular Fatigue Needle gauge drawn with Canvas
            item {
                val needleAnimState = remember { Animatable(0f) }
                // Gauge is needle progress (0f to 1f) corresponding to Fatigue zones
                // Let's bind needleAnimState to fatigue ratios (optimal around index 0.5)
                LaunchedEffect(fatigueInfo) {
                    val ratio = fatigueInfo.ratio.toFloat().coerceIn(0f, 2f)
                    val targetProgress = ratio / 2.0f // mapped 0f to 1f
                    needleAnimState.animateTo(
                        targetValue = targetProgress,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f)
                    )
                }

                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "FATIGUE RECOVERY INDEX DIAL",
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
                        Canvas(modifier = Modifier.fillMaxSize()) {
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
                        Text("Undertrained", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = BlueAccent)
                        Text("Optimal", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = GreenAccent)
                        Text("Overreaching", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = RedAccent)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "FATIGUE RATIO INDEX IS ${String.format("%.2f", fatigueInfo.ratio)} (${fatigueInfo.riskStatus})",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "System is fully functional inside adaptive adaptation limits. Continue progressive loading.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryText
                    )
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
                            upperSets > lowerSets * 1.5 -> "Upper Focus"
                            lowerSets > upperSets * 1.5 -> "Lower Focus"
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
                                fontSize = 8.sp,
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
                                    fontSize = 8.sp,
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
                                fontSize = 8.sp,
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
                                    fontSize = 8.sp,
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
                                text = "LOAD RATIO",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = balanceText.uppercase(),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Showcase 2: Muscle Heatmap Front & Back Drawing Canvas Centered
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    var showHeatmapInfo by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NEURO-MUSCULAR WEEKLY VOLUME HEATMAP",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showHeatmapInfo = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Heatmap info",
                                tint = Color(0xFF8A8A9A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showHeatmapInfo) {
                        ModalBottomSheet(
                            onDismissRequest = { showHeatmapInfo = false },
                            containerColor = DarkCardSurface
                        ) {
                            Column(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "What The Colours Mean",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF0F0F5)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Each muscle group is coloured by training volume intensity this week relative to your personal maximum. " +
                                    "Red means above 80% of your highest ever weekly volume for that muscle — strong stimulus, growth is likely. " +
                                    "Amber means 40–80% — adequate stimulus. Green means 10–40% — below optimal, add volume next week. " +
                                    "Dark means no training stimulus this week — this muscle will not grow without intervention.\n\n" +
                                    "Science: Krieger 2010 meta-analysis of 55 studies found a clear dose-response relationship between weekly " +
                                    "sets per muscle and hypertrophy rate. The minimum effective dose is approximately 8 working sets per week. " +
                                    "Below that threshold muscle protein synthesis returns to baseline within 72 hours with no net growth.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = Color(0xFF8A8A9A),
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    Text(
                        text = "Regions colored by total operational workspace working sets. Tap info button upper-right for science specifics.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MutedText,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    MuscleHeatmapCanvas(
                        heatmap = heatmap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // Showcase 3: Hypertrophy index reference lines
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    var hypertrophyCategory by remember { mutableStateOf("All") }
                    var showUntrained by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HYPERTROPHY SCORE INDEX",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive categories chips selector
                    val categories = listOf("All", "Upper", "Lower", "Core")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 14.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = hypertrophyCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberAccent else Color(0xFF1F1F30))
                                    .clickable { hypertrophyCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = SyneFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.Black else PrimaryText
                                )
                            }
                        }
                    }

                    // Muscle group keys matching the ViewModel mapping
                    val upperKeys = listOf(
                        "chest", "back", "front delts", "side delts", "rear delts",
                        "biceps", "triceps", "forearms", "trapezius", "neck",
                        "rotator cuff", "serratus anterior"
                    )
                    val lowerKeys = listOf(
                        "quadriceps", "hamstrings", "glutes", "calves",
                        "hip abductors", "hip adductors", "tibialis anterior"
                    )
                    val coreKeys = listOf(
                        "abs", "obliques", "transverse abdominis", "lower back"
                    )

                    val filteredKeys = when (hypertrophyCategory) {
                        "Upper" -> upperKeys
                        "Lower" -> lowerKeys
                        "Core" -> coreKeys
                        else -> upperKeys + lowerKeys + coreKeys
                    }

                    // Clean capitalized presentation naming
                    val muscleDisplayNames = mapOf(
                        "chest" to "Chest volume index",
                        "back" to "Back (Lats) volume index",
                        "front delts" to "Front Delts volume index",
                        "side delts" to "Side Delts volume index",
                        "rear delts" to "Rear Delts volume index",
                        "biceps" to "Biceps volume index",
                        "triceps" to "Triceps volume index",
                        "forearms" to "Forearms volume index",
                        "trapezius" to "Trapezius (Traps) volume index",
                        "neck" to "Neck volume index",
                        "abs" to "Abdominis (Abs) volume index",
                        "obliques" to "Obliques volume index",
                        "transverse abdominis" to "Transverse Abs volume index",
                        "lower back" to "Lower Back volume index",
                        "glutes" to "Glutes volume index",
                        "quadriceps" to "Quadriceps (Quads) volume index",
                        "hamstrings" to "Hamstrings volume index",
                        "calves" to "Calves volume index",
                        "hip abductors" to "Hip Abductors volume index",
                        "hip adductors" to "Hip Adductors volume index",
                        "rotator cuff" to "Rotator Cuff volume index",
                        "serratus anterior" to "Serratus Anterior volume index",
                        "tibialis anterior" to "Tibialis Anterior volume index"
                    )

                    // Scientific Maximum Recoverable Volume (MRV) targets
                    val maxSetsMap = mapOf(
                        "chest" to 26,
                        "back" to 28,
                        "front delts" to 14,
                        "side delts" to 26,
                        "rear delts" to 22,
                        "biceps" to 24,
                        "triceps" to 20,
                        "forearms" to 14,
                        "trapezius" to 18,
                        "neck" to 10,
                        "abs" to 18,
                        "obliques" to 14,
                        "transverse abdominis" to 12,
                        "lower back" to 16,
                        "glutes" to 18,
                        "quadriceps" to 24,
                        "hamstrings" to 22,
                        "calves" to 20,
                        "hip abductors" to 12,
                        "hip adductors" to 12,
                        "rotator cuff" to 10,
                        "serratus anterior" to 12,
                        "tibialis anterior" to 10
                    )

                    val activeKeys = filteredKeys.filter { (muscleVolumeMap[it] ?: 0) > 0 }
                    val inactiveKeys = filteredKeys.filter { (muscleVolumeMap[it] ?: 0) == 0 }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (activeKeys.isNotEmpty()) {
                            Text(
                                text = "ACTIVE STIMULATED GROUPS",
                                fontFamily = SyneFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            activeKeys.forEach { key ->
                                val label = muscleDisplayNames[key] ?: key
                                val sets = muscleVolumeMap[key] ?: 0
                                val maxSets = maxSetsMap[key] ?: 20
                                HypertrophyProgressBarMarker(label = label, sets = sets, maxSets = maxSets)
                            }
                        } else {
                            // Empty state if no active setslogged yet
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF13131D))
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = "No active sets",
                                        tint = MutedText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "NO COMPLETED WORKING SETS YET",
                                        fontFamily = SyneFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Log exercises in the workout tab to update real-time hypertrophy indexes.",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 9.sp,
                                        color = MutedText,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (inactiveKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showUntrained) Color(0xFF181826) else Color(0xFF13131D))
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { showUntrained = !showUntrained }
                                    .padding(vertical = 10.dp, horizontal = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "UNTRAINED GROUPS (${inactiveKeys.size} MUSCLES)",
                                        fontFamily = SyneFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showUntrained) AmberAccent else MutedText
                                    )
                                    Text(
                                        text = if (showUntrained) "COLLAPSE ▲" else "EXPAND ▾",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showUntrained) AmberAccent else MutedText
                                    )
                                }
                            }

                            if (showUntrained) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    inactiveKeys.forEach { key ->
                                        val label = muscleDisplayNames[key] ?: key
                                        val sets = muscleVolumeMap[key] ?: 0
                                        val maxSets = maxSetsMap[key] ?: 20
                                        HypertrophyProgressBarMarker(label = label, sets = sets, maxSets = maxSets)
                                    }
                                }
                            }
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
fun HypertrophyProgressBarMarker(label: String, sets: Int, maxSets: Int) {
    val percent = (sets.toFloat() / maxSets.toFloat()).coerceIn(0f, 1f)
    val color = when {
        sets >= maxSets -> RedAccent
        sets >= maxSets / 2 -> AmberAccent
        else -> GreenAccent
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = PrimaryText
            )
            Text(
                text = "$sets/$maxSets SETS",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkRaised)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
