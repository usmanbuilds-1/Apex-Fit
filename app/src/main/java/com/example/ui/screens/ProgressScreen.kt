package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.example.FitnessViewModel
import com.example.ProgressViewModel
import com.example.data.AlgorithmViewModel
import com.example.ui.theme.*
import com.example.ui.models.UiBodyMeasurement
import com.example.data.MuscleRecoveryStatus
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

val AccentSecondary = Color(0xFF6366F1) // Indigo Accent for visual hierarchy

@Composable
fun ProgressScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    progressViewModel: ProgressViewModel,
    onNavigateTo: (Int) -> Unit
) {
    var subScreen by remember { mutableStateOf("main") }
    var selectedPart by remember { mutableStateOf<String?>(null) }

    Crossfade(targetState = subScreen, label = "progressSubscreen") { screen ->
        when (screen) {
            "muscle_recovery" -> {
                MuscleRecoveryScreen(
                    fitnessViewModel = fitnessViewModel,
                    onBack = { subScreen = "main" }
                )
            }
            "measurement_detail" -> {
                selectedPart?.let { part ->
                    BodyMeasurementDetailScreen(
                        bodyPart = part,
                        fitnessViewModel = fitnessViewModel,
                        onBack = { subScreen = "main" }
                    )
                }
            }
            else -> {
                ProgressMainTabContent(
                    fitnessViewModel = fitnessViewModel,
                    algorithmViewModel = algorithmViewModel,
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
                        text = "HYPERTROPHY VOLUME RADAR",
                        fontFamily = SyneFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "30-Day set distribution across primary kinetic chains",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 - 20.dp.toPx()

                    // Draw grid concentric pentagons (3 levels: 0.33, 0.66, 1.0)
                    val levels = listOf(0.33f, 0.66f, 1.0f)
                    levels.forEach { level ->
                        val path = Path()
                        for (i in 0 until 5) {
                            val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
                            val x = center.x + r * level * cos(angle).toFloat()
                            val y = center.y + r * level * sin(angle).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
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
                        val dataPath = Path()
                        for (i in 0 until 5) {
                            val angle = -Math.PI / 2 + (i * 2 * Math.PI / 5)
                            val normalizedVal = (vals[i].toFloat() / maxVal).coerceAtMost(1.0f)
                            val x = center.x + r * normalizedVal * cos(angle).toFloat()
                            val y = center.y + r * normalizedVal * sin(angle).toFloat()
                            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                        }
                        dataPath.close()

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
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (vals[i] > 0) AccentSecondary else MutedText
                        )
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
                    text = "ANALYZE KINETIC RECOVERY MAP",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent
                )
            }
        }
    }
}

@Composable
fun BodyMeasurementsTrackerPanel(
    measurements: List<UiBodyMeasurement>,
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
                text = "BIOMETRIC SYMMETRY TRACKER",
                fontFamily = SyneFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "Click any dimension to log changes or view progressive structural trends",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val standardParts = listOf("Chest", "Biceps", "Waist", "Thighs", "Calves", "Shoulders")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                standardParts.forEach { part ->
                    val latest = measurements
                        .filter { it.bodyPart.equals(part, ignoreCase = true) }
                        .maxByOrNull { it.date }

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
                                    fontSize = 8.sp,
                                    color = MutedText
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (latest != null) "${latest.value} ${latest.unit}" else "-- --",
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

@Composable
fun MuscleRecoveryScreen(
    fitnessViewModel: FitnessViewModel,
    onBack: () -> Unit
) {
    val statuses by fitnessViewModel.muscleRecoveryStatuses.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(DarkRaised, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "ANATOMICAL HYPERTROPHY RECOVERY MAP",
                        fontFamily = SyneFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Real-time biochemical adaptation status of kinetic chains",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = SecondaryText
                    )
                }
            }

            if (statuses.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.OfflineBolt,
                            contentDescription = "Ready",
                            tint = GreenAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ALL SYSTEM KINETIC SYSTEMS ENTIRELY FRESH",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No muscle groups are currently undergoing recovery load. Initiate high intensity stimulation.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "BIOMECHANICAL GROUPS RECOVERY STATE",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    statuses.forEach { status ->
                        val isFresh = status.recoveryPercentage >= 100
                        val colorAccent = if (isFresh) GreenAccent else if (status.recoveryPercentage < 40) RedAccent else AmberAccent

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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colorAccent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = status.muscleGroup.uppercase(),
                                            fontFamily = SyneFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colorAccent.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isFresh) "FULLY CACHED 100%" else "${status.recoveryPercentage}% RECOVERED",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorAccent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Progress Bar
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
                                            .fillMaxWidth(status.recoveryPercentage.toFloat() / 100f)
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(colorAccent.copy(alpha = 0.5f), colorAccent)
                                                )
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "LAST STIMULUS",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText
                                        )
                                        Text(
                                            text = status.lastExercise.ifEmpty { "None recorded" },
                                            fontFamily = SyneFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "ADAPTATION WINDOW",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText
                                        )
                                        Text(
                                            text = if (isFresh) "Fully ready" else "${status.requiredHours} hrs needed",
                                            fontFamily = SyneFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFresh) GreenAccent else PrimaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BodyMeasurementDetailScreen(
    bodyPart: String,
    fitnessViewModel: FitnessViewModel,
    onBack: () -> Unit
) {
    val measurements by fitnessViewModel.allBodyMeasurements.collectAsStateWithLifecycle()
    val filtered = remember(measurements, bodyPart) {
        measurements.filter { it.bodyPart.equals(bodyPart, ignoreCase = true) }.sortedByDescending { it.date }
    }

    var newValueStr by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(DarkRaised, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "${bodyPart.uppercase()} DIMENSIONS",
                        fontFamily = SyneFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Anatomical tracking and systemic hypertrophy symmetry analysis",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = SecondaryText
                    )
                }
            }

            // Visual progressive trace line graph on canvas
            if (filtered.size >= 2) {
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
                            text = "PROGRESSION MAP",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Draw simple elegant path line graph
                        val graphPoints = filtered.sortedBy { it.date }
                        val values = graphPoints.map { it.value.toFloat() }
                        val minVal = values.minOrNull() ?: 0f
                        val maxVal = values.maxOrNull() ?: 0f
                        val spread = if (maxVal - minVal > 0) maxVal - minVal else 1f

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val spacing = w / (graphPoints.size - 1).toFloat()

                            val path = Path()
                            for (index in graphPoints.indices) {
                                val currentVal = graphPoints[index].value.toFloat()
                                val x = index * spacing
                                val y = h - ((currentVal - minVal) / spread) * (h - 20.dp.toPx()) - 10.dp.toPx()

                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }

                                drawCircle(
                                    color = AmberAccent,
                                    radius = 4.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }

                            drawPath(
                                path = path,
                                color = AccentSecondary,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Start: ${filtered.last().value} cm",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                color = MutedText
                            )
                            Text(
                                text = "Current: ${filtered.first().value} cm",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                color = AmberAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Input logger card
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
                        text = "RECORD NEW BIOMETRIC LOG",
                        fontFamily = SyneFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newValueStr,
                            onValueChange = {
                                newValueStr = it
                                inputError = null
                            },
                            label = { Text("Dimension value (cm)", color = SecondaryText, fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = SecondaryText,
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val value = newValueStr.toDoubleOrNull()
                                if (value == null || value <= 0.0) {
                                    inputError = "Configure a valid positive numeric float"
                                    return@Button
                                }
                                fitnessViewModel.logBodyMeasurement(bodyPart, value, "cm")
                                newValueStr = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(
                                text = "LOG",
                                fontFamily = SyneFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0F)
                            )
                        }
                    }

                    inputError?.let { err ->
                        Text(
                            text = err,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = RedAccent,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logs list
            Text(
                text = "HISTORICAL LOG ENTRIES",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dimensions logged yet for $bodyPart",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { measurement ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${measurement.value} ${measurement.unit}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Text(
                                    text = measurement.date,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 8.sp,
                                    color = MutedText
                                )
                            }

                            IconButton(
                                onClick = { fitnessViewModel.deleteBodyMeasurement(measurement.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = RedAccent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
