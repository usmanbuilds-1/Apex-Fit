package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FitnessViewModel
import com.example.ui.models.UiBodyMeasurement
import com.example.data.MuscleRecoveryStatus
import com.example.ui.theme.SyneFamily
import com.example.ui.theme.JetBrainsMonoFamily

// Elegant colors to match theme
val DarkSlate = Color(0xFF0F0F1A)
val CardColor = Color(0xFF1A1A2E)
val AccentColor63 = Color(0xFF6366F1)
val AccentSecondary = Color(0xFFA78BFA)
val BrightGreen = Color(0xFF34D399)
val BrightAmber = Color(0xFFFBBF24)
val BrightRed = Color(0xFFEF4444)

// ==========================================
// 1. MUSCLE RECOVERY SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleRecoveryScreen(
    fitnessViewModel: FitnessViewModel,
    onBack: () -> Unit
) {
    val recoveryStatuses by fitnessViewModel.muscleRecoveryStatuses.collectAsStateWithLifecycle()
    
    // Calculate overall recovery average
    val overallRecovery = remember(recoveryStatuses) {
        if (recoveryStatuses.isEmpty()) 100
        else (recoveryStatuses.map { it.recoveryPercentage }.average()).toInt()
    }

    Scaffold(
        containerColor = DarkSlate,
        topBar = {
            TopAppBar(
                title = { Text("MUSCLE RECOVERY", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSlate)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall progress circle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentColor63.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            // Circular track
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color(0xFF131324),
                                    style = Stroke(width = 12.dp.toPx())
                                )
                                drawArc(
                                    color = when {
                                        overallRecovery >= 80 -> BrightGreen
                                        overallRecovery >= 50 -> BrightAmber
                                        else -> BrightRed
                                    },
                                    startAngle = -90f,
                                    sweepAngle = (overallRecovery / 100f) * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$overallRecovery%",
                                    fontFamily = SyneFamily,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "RECOVERED",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    color = Color(0xFF8A8A9A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Average recovery across all muscle groups based on last 7 days of training.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF8A8A9A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // Group muscles into categories
            // Push muscles: Chest, Shoulders, Triceps
            // Pull muscles: Back, Biceps
            // Legs: Quads, Hamstrings, Glutes, Calves
            // Core: Abs, Lower Back
            val categories = listOf(
                "PUSH MUSCLES" to listOf("Chest", "Shoulders", "Triceps"),
                "PULL MUSCLES" to listOf("Back", "Biceps"),
                "LEGS" to listOf("Quads", "Hamstrings", "Glutes", "Calves"),
                "CORE & LOWER BACK" to listOf("Abs", "Lower Back")
            )

            categories.forEach { (catTitle, catMuscles) ->
                item {
                    Text(
                        text = catTitle,
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Filter statuses for muscles in this category
                val matchedStatuses = recoveryStatuses.filter { it.muscleGroup in catMuscles }
                
                items(matchedStatuses.size) { index ->
                    val status = matchedStatuses[index]
                    MuscleGroupRow(status = status)
                }
            }
            
            // science justification reference text
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "RECOVERY MODEL: Physical recovery durations are calculated based on physiological models of muscle fiber rebuild cycles (Damas et al., 2016). Heavy loads (RPE >= 8 or sets > 5) scale recovery window parameters up to 72 hours.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Color(0xFF5A5A6A),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
fun MuscleGroupRow(status: MuscleRecoveryStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("muscle_row_${status.muscleGroup.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniature Anatomical Body Illustration Thumbnail focusing on this muscle highlight
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0F1A))
            ) {
                AnatomicalBodyIllustration(
                    modifier = Modifier.fillMaxSize(),
                    highlightedMuscle = status.muscleGroup
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name and last exercise log
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.muscleGroup.uppercase(),
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (status.lastExercise != "No recent exercises") status.lastExercise else "Untrained (No history found)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Color(0xFF8A8A9A),
                    maxLines = 1
                )
                if (status.lastTrainingDate != null) {
                    Text(
                        text = "Last: ${status.lastTrainingDate} (${status.requiredHours}h window)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = Color(0xFF5A5A6A)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Recovery Badge progress pill
            val pct = status.recoveryPercentage
            val badgeBg = when {
                pct >= 80 -> Color(0xFF104E33)
                pct >= 50 -> Color(0xFF543E10)
                else -> Color(0xFF4C1010)
            }
            val badgeText = when {
                pct >= 80 -> BrightGreen
                pct >= 50 -> BrightAmber
                else -> BrightRed
            }
            val badgeWord = when {
                pct >= 80 -> "Recovered"
                pct >= 50 -> "Rebuilding"
                else -> "Damaged"
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$pct%",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                    Text(
                        text = badgeWord.uppercase(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeText.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}


// ==========================================
// 2. BODY MEASUREMENT DETAIL SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementDetailScreen(
    bodyPart: String,
    fitnessViewModel: FitnessViewModel,
    onBack: () -> Unit
) {
    val measurements by fitnessViewModel.allBodyMeasurements.collectAsStateWithLifecycle()
    val partEntries = remember(measurements, bodyPart) {
        measurements.filter { it.bodyPart.lowercase() == bodyPart.lowercase() }.sortedBy { it.date }
    }
    
    val latestValue = partEntries.lastOrNull()?.value ?: 0.0

    var inputValue by remember { mutableStateOf("") }
    var inputDate by remember { mutableStateOf(getTodayDateRawString()) }
    val context = LocalContext.current

    Scaffold(
        containerColor = DarkSlate,
        topBar = {
            TopAppBar(
                title = { Text("${bodyPart.uppercase()} METRIC FILE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSlate)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Trend summary metric box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "CURRENT PROFILE METRIC",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                color = Color(0xFF8A8A9A),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (latestValue > 0.0) "$latestValue cm" else "Not logged",
                                fontFamily = SyneFamily,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = AccentSecondary
                            )
                        }
                        
                        // Calculated total delta
                        if (partEntries.size >= 2) {
                            val earliest = partEntries.first().value
                            val latest = partEntries.last().value
                            val delta = latest - earliest
                            val indicatorColor = if (delta >= 0f) BrightGreen else BrightAmber
                            val trendString = if (delta >= 0f) "+${"%.1f".format(delta)} cm" else "${"%.1f".format(delta)} cm"
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(indicatorColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "TOTAL DELTA",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        color = indicatorColor
                                    )
                                    Text(
                                        text = trendString,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = indicatorColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Line Graph Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1F1F35))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PROGRESSION MAP",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        if (partEntries.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No progression history logged yet.", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = Color(0xFF5A5A6A))
                            }
                        } else {
                            MeasurementLineChart(
                                entries = partEntries,
                                modifier = Modifier.fillMaxWidth().height(170.dp)
                            )
                        }
                    }
                }
            }

            // Logging form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentColor63.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "REGISTER NEW LOG ENTRY",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentColor63,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = inputValue,
                                onValueChange = { inputValue = it },
                                label = { Text("Value index (cm)", color = Color(0xFF8A8A9A), fontSize = 10.sp) },
                                textStyle = TextStyle(color = Color.White, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("measurement_input_${bodyPart.lowercase()}"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F0F1A),
                                    unfocusedContainerColor = Color(0xFF0F0F1A),
                                    focusedIndicatorColor = AccentColor63,
                                    unfocusedIndicatorColor = Color(0xFF131324)
                                )
                            )

                            OutlinedTextField(
                                value = inputDate,
                                onValueChange = { inputDate = it },
                                label = { Text("Log date", color = Color(0xFF8A8A9A), fontSize = 10.sp) },
                                textStyle = TextStyle(color = Color.White, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = Modifier.weight(1f).testTag("measurement_date_${bodyPart.lowercase()}"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F0F1A),
                                    unfocusedContainerColor = Color(0xFF0F0F1A),
                                    focusedIndicatorColor = AccentColor63,
                                    unfocusedIndicatorColor = Color(0xFF131324)
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                val v = inputValue.toDoubleOrNull()
                                if (v != null && inputDate.trim().matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                    fitnessViewModel.logBodyMeasurement(
                                        bodyPart = bodyPart,
                                        value = v,
                                        unit = "cm",
                                        date = inputDate.trim()
                                    )
                                    inputValue = ""
                                    Toast.makeText(context, "Logged profile update index details!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Enter numeric index & valid date (YYYY-MM-DD)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("measurement_save_${bodyPart.lowercase()}"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor63),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE ATHLETE METRIC INDEX", fontFamily = SyneFamily, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }

            // History breakdown logs list
            item {
                Text(
                    text = "HISTORIC INDEX DATABASE",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8A8A9A),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            
            val sortedList = partEntries.sortedByDescending { it.date }
            
            items(sortedList.size) { index ->
                val entry = sortedList[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardColor)
                        .border(1.dp, Color(0xFF1F1F35), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${entry.value} cm",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Logged on: ${entry.date} • Unit: ${entry.unit}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF8A8A9A)
                        )
                    }
                    
                    IconButton(
                        onClick = { fitnessViewModel.deleteBodyMeasurement(entry.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete measurement entry",
                            tint = Color(0xFF8A8A9A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// Draw custom high fidelity progression line chart using native compose primitives
@Composable
fun MeasurementLineChart(
    entries: List<UiBodyMeasurement>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
        val w = size.width
        val h = size.height
        
        val vals = entries.map { it.value }
        val minVal = (vals.minOrNull() ?: 1.0) - 1.0
        val maxVal = (vals.maxOrNull() ?: 100.0) + 1.0
        val valRange = maxVal - minVal
        
        // Grid vertical lines & tags
        val pointsCount = entries.size
        val xInterval = w / maxOf(1, pointsCount - 1)
        
        // Draw coordinate horizontal scales grid
        drawLine(
            color = Color(0x1F8A8A9A),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(w, 0f),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0x1F8A8A9A),
            start = androidx.compose.ui.geometry.Offset(0f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w, h * 0.5f),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0x1F8A8A9A),
            start = androidx.compose.ui.geometry.Offset(0f, h),
            end = androidx.compose.ui.geometry.Offset(w, h),
            strokeWidth = 1f
        )

        // Plot path logic
        val coordinates = entries.mapIndexed { idx, item ->
            val scaleX = idx * xInterval
            val scaleY = (1f - ((item.value - minVal) / valRange).toFloat()) * h
            androidx.compose.ui.geometry.Offset(scaleX, scaleY)
        }
        
        // Connected Bezier curve or exact straight lines showing high-fidelity technical look
        val chartPath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, coordinates.first().y)
                for (i in 1 until coordinates.size) {
                    lineTo(coordinates[i].x, coordinates[i].y)
                }
            }
        }
        
        // Draw linear gradient background below line
        if (coordinates.isNotEmpty()) {
            val fillPath = Path().apply {
                addPath(chartPath)
                lineTo(coordinates.last().x, h)
                lineTo(coordinates.first().x, h)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AccentSecondary.copy(alpha = 0.35f), Color.Transparent)
                )
            )
        }
        
        // Draw baseline path
        drawPath(
            path = chartPath,
            color = AccentSecondary,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Draw individual data anchor circles
        coordinates.forEachIndexed { index, point ->
            // Anchor glow circle click indicator
            drawCircle(
                color = AccentColor63,
                radius = 4.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = point
            )
        }
    }
}


// ==========================================
// 3. RADAR CHART AND MAIN BODY MEASUREMENTS SECTIONS
// ==========================================
@Composable
fun MonthlyVolumeRadarChart(
    volumeMap: Map<String, Int>,
    onSeeRecoveryClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1F1F35))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "THIS MONTH",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSecondary
                    )
                    Text(
                        text = "Identify imbalances across muscle volumes.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = Color(0xFF8A8A9A)
                    )
                }
                
                // See where to focus more linking directly to muscle recovery
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF131324))
                        .clickable { onSeeRecoveryClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RECOVERY DIAGNOSTIC",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentColor63
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Draw Radar Spider Chart with Canvas
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .background(Color(0xFF131324), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = size.width * 0.40f
                
                val axes = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")
                val pointsCount = axes.size
                
                // Draw Concentric Reference guidelines (25%, 50%, 75%, 100%)
                for (rIdx in 1..4) {
                    val radius = maxRadius * (rIdx / 4f)
                    val concentricPath = Path().apply {
                        for (i in 0 until pointsCount) {
                            val theta = i * 2f * Math.PI.toFloat() / pointsCount
                            val px = cx + radius * Math.cos(theta.toDouble()).toFloat()
                            val py = cy + radius * Math.sin(theta.toDouble()).toFloat()
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(
                        path = concentricPath,
                        color = Color(0x1F8A8A9A),
                        style = Stroke(width = 0.8f)
                    )
                }
                
                // Draw Axis line spines
                for (i in 0 until pointsCount) {
                    val theta = i * 2f * Math.PI.toFloat() / pointsCount
                    val spineX = cx + maxRadius * Math.cos(theta.toDouble()).toFloat()
                    val spineY = cy + maxRadius * Math.sin(theta.toDouble()).toFloat()
                    drawLine(
                        color = Color(0x1F8A8A9A),
                        start = androidx.compose.ui.geometry.Offset(cx, cy),
                        end = androidx.compose.ui.geometry.Offset(spineX, spineY),
                        strokeWidth = 1f
                    )
                }
                
                // Calculate dynamic data polygon
                // Cap sets at 16 sets = 100% of radius scale
                val dataCoordinates = mutableListOf<androidx.compose.ui.geometry.Offset>()
                for (i in 0 until pointsCount) {
                    val axis = axes[i]
                    val count = volumeMap[axis] ?: 0
                    val fraction = (count.toFloat() / 16f).coerceIn(0.08f, 1.0f)
                    val radiusVal = maxRadius * fraction
                    
                    val theta = i * 2f * Math.PI.toFloat() / pointsCount
                    val dx = cx + radiusVal * Math.cos(theta.toDouble()).toFloat()
                    val dy = cy + radiusVal * Math.sin(theta.toDouble()).toFloat()
                    dataCoordinates.add(androidx.compose.ui.geometry.Offset(dx, dy))
                }
                
                val dataPath = Path().apply {
                    if (dataCoordinates.isNotEmpty()) {
                        moveTo(dataCoordinates.first().x, dataCoordinates.first().y)
                        for (j in 1 until dataCoordinates.size) {
                            lineTo(dataCoordinates[j].x, dataCoordinates[j].y)
                        }
                        close()
                    }
                }
                
                // Fill polygon
                drawPath(
                    path = dataPath,
                    color = AccentColor63.copy(alpha = 0.30f)
                )
                // Draw border line
                drawPath(
                    path = dataPath,
                    color = AccentColor63,
                    style = Stroke(width = 1.5f)
                )
                
                // Center core point
                drawCircle(color = AccentSecondary, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
            }
            
            // Row legend text labels representating each of the 6 axes
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val axes = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")
                axes.forEach { axis ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = axis.uppercase(),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${volumeMap[axis] ?: 0} sets",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = AccentSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "SEE WHERE TO FOCUS MORE ➜",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentColor63,
                modifier = Modifier
                    .clickable { onSeeRecoveryClick() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}


@Composable
fun BodyMeasurementsTrackerPanel(
    measurements: List<UiBodyMeasurement>,
    onPartClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1F1F35))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "BODY ANATOMY METRIC ARCHIVE",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Select any segment point below to catalog histories, audit lines, and log updates.",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Color(0xFF8A8A9A),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val bodyParts = listOf(
                "Neck", "Shoulders", "Chest", "Left Bicep", "Right Bicep",
                "Left Forearm", "Right Forearm", "Waist", "Hips",
                "Left Thigh", "Right Thigh", "Left Calf", "Right Calf"
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bodyParts.forEach { part ->
                    val partEntries = measurements.filter { it.bodyPart.lowercase() == part.lowercase() }.sortedBy { it.date }
                    val latestEntry = partEntries.lastOrNull()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF121224))
                            .clickable { onPartClick(part) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = part.uppercase(),
                                fontFamily = SyneFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            // Simple trend delta indicator
                            if (partEntries.size >= 2) {
                                val latest = partEntries.last().value
                                val previous = partEntries[partEntries.size - 2].value
                                when {
                                    latest > previous -> Icon(Icons.Default.TrendingUp, contentDescription = "Up", tint = BrightGreen, modifier = Modifier.size(14.dp))
                                    latest < previous -> Icon(Icons.Default.TrendingDown, contentDescription = "Down", tint = BrightAmber, modifier = Modifier.size(14.dp))
                                    else -> Icon(Icons.Default.TrendingFlat, contentDescription = "Flat", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        
                        Text(
                            text = if (latestEntry != null) "${latestEntry.value} cm" else "—",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentSecondary
                        )
                    }
                }
            }
        }
    }
}


// Simple auxiliary clock raw string representation helper to satisfy system constraints
private fun getTodayDateRawString(): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
}
fun Toast.dummy(): Int = 1 // utility mapping
