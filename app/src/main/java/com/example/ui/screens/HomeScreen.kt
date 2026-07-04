package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.FitnessViewModel
import com.example.AlgorithmViewModel
import com.example.HomeViewModel
import com.example.TrainViewModel
import com.example.NutritionViewModel
import com.example.ui.models.*
import com.example.ui.theme.*
import com.example.utils.AlgorithmEngine
import com.example.ui.components.MuscleHeatmapCanvas
import com.example.ui.components.SingleFrontHeatmapCanvas
import com.example.utils.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0E1A)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(1.dp, Color(0xFF2A2A3E))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun HomeScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    homeViewModel: HomeViewModel,
    trainViewModel: TrainViewModel,
    nutritionViewModel: NutritionViewModel,
    onNavigateTo: (Int) -> Unit
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val activePlanSessions by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()

    val loggedMeals by nutritionViewModel.loggedMeals.collectAsStateWithLifecycle()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()

    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val streakResult by algorithmViewModel.streakResult.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val todayExercises by trainViewModel.todayExercises.collectAsStateWithLifecycle()
    val weightHistory by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val plateauResult by algorithmViewModel.plateauResult.collectAsStateWithLifecycle()
    val targets by algorithmViewModel.targets.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()

    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()

    val readiness by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val heatmap by algorithmViewModel.muscleHeatmap.collectAsStateWithLifecycle()
    val complianceScores by homeViewModel.complianceScores.collectAsStateWithLifecycle()
    val fatigueRatio by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()
    val allPRs by algorithmViewModel.allPRs.collectAsStateWithLifecycle()

    // Aggregate meal stats
    val latestWeight = weightHistory.firstOrNull()?.weight ?: weight ?: com.example.UserDefaults.WEIGHT_KG

    val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
    val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
    val sessionName = todaySession?.label ?: "Upper A"
    val focusMuscles = (todaySession?.focus ?: "Chest • Back • Arms").replace(", ", " • ").replace(",", " • ")

    // Calculate precise metabolic duration based on specific exercise sets
    val estimatedWorkoutDurationMin = if (todaySession == null || todaySession.focus == "Muscle Recovery & Rest") {
        0
    } else if (todayExercises.isEmpty()) {
        42
    } else {
        val rawTime = todayExercises.sumOf { 
            val setMinutes = com.example.utils.AlgorithmEngine.estimateSetDurationMinutes(it.repsMin, it.repsMax)
            it.sets * (setMinutes + it.restSeconds / 60.0) 
        }
        val transitionTime = (todayExercises.size - 1).coerceAtLeast(0) * 2.0
        val warmUp = 5.0
        (rawTime + transitionTime + warmUp).toInt()
    }

    val finalWorkoutDurationMin = if (estimatedWorkoutDurationMin > 0) estimatedWorkoutDurationMin else 42

    val context = LocalContext.current

    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    var showCalculationExplanation by remember { mutableStateOf(false) }

    val allNutritionHistory by homeViewModel.allNutritionHistory.collectAsStateWithLifecycle()
    val goalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val richSessionsFlow by homeViewModel.richSessionsFlow.collectAsStateWithLifecycle(emptyList())

    val todayDateStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    val loggedCaloriesTotal = remember(allNutritionHistory, todayDateStr) {
        allNutritionHistory.filter { it.date == todayDateStr }.sumOf { it.calories }
    }
    val loggedProteinTotal = remember(allNutritionHistory, todayDateStr) {
        allNutritionHistory.filter { it.date == todayDateStr }.sumOf { it.protein }.toInt()
    }

    val isTodayWorkoutCompleted = remember(completedSessions, todayDateStr) {
        completedSessions.any { it.date == todayDateStr }
    }

    val lastTrainedDates = remember(richSessionsFlow) {
        val mapping = mutableMapOf<String, String>()
        val sortedSessions = richSessionsFlow.sortedByDescending { it.date }
        for (session in sortedSessions) {
            val dateStr = session.date
            for (exercise in session.exercises) {
                val exerciseMuscle = exercise.muscleGroup.lowercase().trim()
                val matchedCanvasMuscle = when {
                    exerciseMuscle.contains("chest") || exerciseMuscle.contains("pectoral") -> "chest"
                    exerciseMuscle.contains("back") && !exerciseMuscle.contains("lower") -> "back"
                    exerciseMuscle.contains("front delt") || exerciseMuscle.contains("front_delt") || exerciseMuscle.contains("anterior delt") -> "front_delt"
                    exerciseMuscle.contains("rear delt") || exerciseMuscle.contains("rear_delt") || exerciseMuscle.contains("posterior delt") -> "rear_delt"
                    exerciseMuscle.contains("side delt") || exerciseMuscle.contains("side_delt") || exerciseMuscle.contains("lateral") || exerciseMuscle.contains("shoulder") || exerciseMuscle.contains("delt") -> "side_delt"
                    exerciseMuscle.contains("bicep") -> "bicep"
                    exerciseMuscle.contains("tricep") -> "tricep"
                    exerciseMuscle.contains("quad") || exerciseMuscle.contains("thigh") -> "quad"
                    exerciseMuscle.contains("hamstring") -> "hamstring"
                    exerciseMuscle.contains("glute") -> "glute"
                    exerciseMuscle.contains("calf") || exerciseMuscle.contains("calves") -> "calf"
                    exerciseMuscle.contains("core") || exerciseMuscle.contains("abs") || exerciseMuscle.contains("abdom") -> "core"
                    else -> null
                }
                if (matchedCanvasMuscle != null && !mapping.containsKey(matchedCanvasMuscle)) {
                    mapping[matchedCanvasMuscle] = dateStr
                }
            }
        }
        mapping
    }

    fun getDaysSince(dateString: String, todayStr: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dateLog = sdf.parse(dateString) ?: return 100
            val todayDate = sdf.parse(todayStr) ?: return 100
            val diff = todayDate.time - dateLog.time
            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            100
        }
    }

    fun isMuscleInTodaySession(muscle: String, focus: String): Boolean {
        val f = focus.lowercase()
        val m = muscle.lowercase()
        return when (m) {
            "chest" -> f.contains("chest") || f.contains("pec")
            "back" -> f.contains("back") || f.contains("lat") || f.contains("lats")
            "front_delt" -> f.contains("front") || f.contains("delt") || f.contains("shoulder") || f.contains("arms") || f.contains("upper")
            "side_delt" -> f.contains("side") || f.contains("lateral") || f.contains("delt") || f.contains("shoulder") || f.contains("arms") || f.contains("upper")
            "rear_delt" -> f.contains("rear") || f.contains("posterior") || f.contains("delt") || f.contains("shoulder") || f.contains("arms") || f.contains("upper")
            "bicep" -> f.contains("bicep") || f.contains("arm") || f.contains("upper")
            "tricep" -> f.contains("tricep") || f.contains("arm") || f.contains("upper")
            "quad" -> f.contains("quad") || f.contains("leg") || f.contains("thigh") || f.contains("lower")
            "hamstring" -> f.contains("hamstring") || f.contains("leg") || f.contains("thigh") || f.contains("lower")
            "glute" -> f.contains("glute") || f.contains("butt") || f.contains("leg") || f.contains("lower")
            "calf" -> f.contains("calf") || f.contains("calves") || f.contains("lower")
            "core" -> f.contains("core") || f.contains("abs") || f.contains("oblique") || f.contains("abdominal")
            else -> false
        }
    }

    val recoveryHeatmap = remember(lastTrainedDates, todaySession, todayDateStr) {
        val focusText = todaySession?.focus ?: "Chest • Back • Arms"
        val canvasMuscles = listOf(
            "chest", "back", "front_delt", "side_delt", "rear_delt", 
            "bicep", "tricep", "quad", "hamstring", "glute", "calf", "core"
        )
        
        canvasMuscles.associateWith { m ->
            val isInToday = isMuscleInTodaySession(m, focusText)
            val lastDate = lastTrainedDates[m]
            val daysSince = if (lastDate != null) getDaysSince(lastDate, todayDateStr) else 100
            
            val (intIntensity, levelString, colorHex) = when {
                isInToday -> Triple(100, "ACTIVE", "#FF9500") // ACTIVE (Orange)
                daysSince <= 2 -> Triple(50, "RECOVERING", "#34D399") // RECOVERING (Green)
                else -> Triple(0, "NEUTRAL", "#252535") // NEUTRAL
            }
            HeatmapEntry(volume = 0, intensity = intIntensity, level = levelString, colorHex = colorHex)
        }
    }

    val weightDiff = latestWeight - goalWeight
    val weightChangeStr = if (weightDiff == 0.0) {
        "Target reached!"
    } else {
        val sign = if (weightDiff > 0) "+" else ""
        "${sign}${String.format("%.1f", weightDiff)} $units to target"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp)
    ) {
        // 1. Welcome Header
        Column(modifier = Modifier.fillMaxWidth()) {
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when (currentHour) {
                    in 0..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    in 17..21 -> "Good evening"
                    else -> "Good night"
                }
                val displayName = username.ifEmpty { "Athlete" }
                val annotatedGreeting = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(greeting)
                    }
                    withStyle(style = SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, color = Color.White)) {
                        append(", $displayName 👋")
                    }
                }
                Text(
                    text = annotatedGreeting,
                    modifier = Modifier.testTag("home_greeting_text")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Let's get after it today.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. TODAY'S TRAINING Card (Combined Readiness & Workout)
            ApexCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("todays_training_premium_card")
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TODAY'S TRAINING",
                                style = MaterialTheme.typography.titleLarge,
                                color = AmberAccent,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "How readiness is calculated",
                                tint = AmberAccent.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { showCalculationExplanation = true }
                                    .testTag("readiness_info_icon")
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Circular Progress Indicator (Left side)
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = BorderSubtle,
                                        radius = size.minDimension / 2 - 3.dp.toPx(),
                                        style = Stroke(width = 6.dp.toPx())
                                    )
                                    drawArc(
                                        color = GreenAccent,
                                        startAngle = -90f,
                                        sweepAngle = (readiness?.score ?: 82).toFloat() / 100f * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${readiness?.score ?: 82}%",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = GreenAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Recovery Score",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 2. Details Column (Center-Left side)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = sessionName,
                                style = MaterialTheme.typography.displayMedium,
                                color = PrimaryText
                            )
                            Text(
                                text = focusMuscles,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Time Badge Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF0F0F16))
                                    .border(BorderStroke(0.5.dp, BorderSubtle), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                             ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "~$finalWorkoutDurationMin min",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PrimaryText
                                    )
                                }
                            }
                        }

                        // 3. Mannequin Muscle Overlay (On the Right)
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(120.dp)
                                .align(Alignment.CenterVertically)
                        ) {
                            SingleFrontHeatmapCanvas(
                                heatmap = recoveryHeatmap,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // START WORKOUT CTA Button
                    Button(
                        onClick = {
                            if (todaySession != null) {
                                fitnessViewModel.startWorkoutSession(todaySession)
                                onNavigateTo(1)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "No workout scheduled for today. Enjoy your rest day!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9500),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_workout_button"),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "START WORKOUT",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. STREAK Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (streakResult.training.current > 0) {
                    Text(
                        text = "🔥 ${streakResult.training.current} DAY STREAK",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500)
                    )
                } else {
                    Text(
                        text = "Log your first workout to start a streak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Two-Column Matrix Card Row (NUTRITION & BODY WEIGHT)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // NUTRITION Left Card
                val calTarget = calorieTargetValue.coerceAtLeast(0) // Use 0 if not set
                val calLogged = loggedCaloriesTotal
                val calLeft = (calTarget - calLogged)
                val calPercent = if (calTarget > 0) (calLogged * 100 / calTarget).coerceIn(0, 100) else 0

                val proteinTarget = targets?.protein ?: 0
                val proteinLogged = loggedProteinTotal

                ApexCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("nutrition_summary_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NUTRITION",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8A2BE2),
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF8A2BE2),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onNavigateTo(2) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Circular progress ring showing nutrition completion
                            Box(
                                modifier = Modifier
                                    .size(114.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .drawBehind {
                                        drawCircle(
                                            color = Color(0xFF1E1E2E),
                                            radius = size.minDimension / 2 - 2.dp.toPx(),
                                            style = Stroke(width = 6.dp.toPx())
                                        )
                                        if (calTarget > 0) {
                                            drawArc(
                                                color = Color(0xFF8A2BE2),
                                                startAngle = -90f,
                                                sweepAngle = (calPercent.toFloat() / 100f) * 360f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format(java.util.Locale.US, "%,d", calLogged),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "/ ${if (calTarget > 0) String.format(java.util.Locale.US, "%,d", calTarget) else "2,600"} kcal",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%,d", calLeft.coerceAtLeast(0))} kcal remaining",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$proteinLogged / ${if (proteinTarget > 0) proteinTarget else 180}g protein",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // + Log Food Button at Bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .clickable { onNavigateTo(2) }
                                .padding(vertical = 10.dp)
                                .testTag("log_food_bottom_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF8A2BE2),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Log Food",
                                color = Color(0xFF8A2BE2),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // BODY WEIGHT Right Card
                ApexCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("body_weight_summary_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BODY WEIGHT",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8A2BE2),
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF8A2BE2),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onNavigateTo(3) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", latestWeight)} kg",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                              )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Downward trend info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (weightDiff == 0.0) "0.6" else String.format(java.util.Locale.US, "%.1f", Math.abs(weightDiff))} kg",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80)
                                )
                                Text(
                                    text = "this week",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Smooth Line Graph (Spleen spline curves)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                val linePoints = remember(weightHistory) {
                                    if (weightHistory.size >= 2) {
                                        weightHistory.take(7).reversed().map { it.weight }
                                    } else {
                                        listOf(61.8, 61.5, 61.6, 61.3, 61.4, 61.1, 61.2) // Elegant mock fallback path
                                    }
                                }
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (linePoints.size >= 2) {
                                        val minW = linePoints.minOrNull() ?: 60.0
                                        val maxW = linePoints.maxOrNull() ?: 62.0
                                        val valRange = if (maxW == minW) 1.0 else (maxW - minW)
                                        val xSpacing = size.width / (linePoints.size - 1)

                                        val canvasPoints = linePoints.mapIndexed { index, weightVal ->
                                            val ptX = index * xSpacing
                                            val pct = (weightVal - minW) / valRange
                                            val ptY = size.height - (pct * (size.height - 10.dp.toPx()) + 5.dp.toPx()).toFloat()
                                            Offset(ptX, ptY)
                                        }

                                        // Glow gradient area below spline line
                                        val gradientPath = Path().apply {
                                            moveTo(canvasPoints.first().x, size.height)
                                            lineTo(canvasPoints.first().x, canvasPoints.first().y)
                                            for (i in 0 until canvasPoints.size - 1) {
                                                val p0 = canvasPoints[i]
                                                val p1 = canvasPoints[i + 1]
                                                val conPtX1 = (p0.x + p1.x) / 2
                                                val conPtY1 = p0.y
                                                val conPtX2 = (p0.x + p1.x) / 2
                                                val conPtY2 = p1.y
                                                cubicTo(conPtX1, conPtY1, conPtX2, conPtY2, p1.x, p1.y)
                                            }
                                            lineTo(canvasPoints.last().x, size.height)
                                            close()
                                        }
                                        drawPath(
                                            path = gradientPath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color(0xFF8A2BE2).copy(alpha = 0.25f), Color.Transparent),
                                                startY = canvasPoints.minOfOrNull { it.y } ?: 0f,
                                                endY = size.height
                                            )
                                        )

                                        // Smooth stroke curve
                                        val chartPath = Path().apply {
                                            moveTo(canvasPoints.first().x, canvasPoints.first().y)
                                            for (i in 0 until canvasPoints.size - 1) {
                                                val p0 = canvasPoints[i]
                                                val p1 = canvasPoints[i + 1]
                                                val conPtX1 = (p0.x + p1.x) / 2
                                                val conPtY1 = p0.y
                                                val conPtX2 = (p0.x + p1.x) / 2
                                                val conPtY2 = p1.y
                                                cubicTo(conPtX1, conPtY1, conPtX2, conPtY2, p1.x, p1.y)
                                            }
                                        }
                                        drawPath(
                                            path = chartPath,
                                            color = Color(0xFF8A2BE2),
                                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                        )

                                        // Point anchor circles on spline line
                                        canvasPoints.forEach { pt ->
                                            drawCircle(
                                                color = Color.White,
                                                radius = 1.5.dp.toPx(),
                                                center = pt
                                            )
                                            drawCircle(
                                                color = Color(0xFF8A2BE2),
                                                radius = 3.dp.toPx(),
                                                center = pt,
                                                style = Stroke(width = 0.75.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Time range pill selector: "7D", "30D", "90D"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("7D", "30D", "90D").forEach { filter ->
                                    val isSelected = filter == "7D"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF8A2BE2) else Color.Transparent)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = filter,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 9.sp,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // + Log Weight Button at Bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .clickable {
                                    weightInput = String.format(java.util.Locale.US, "%.1f", latestWeight)
                                    showWeightDialog = true
                                }
                                .padding(vertical = 10.dp)
                                .testTag("log_weight_bottom_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF8A2BE2),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Log Weight",
                                color = Color(0xFF8A2BE2),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
    }



    // ───── LOG WEIGHT DIALOG ─────
    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "LOG BODY WEIGHT",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your current weight in $units. This updates your dynamic readiness fatigue filters and chronic load baselines.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { input ->
                            if (input.count { it == '.' } <= 1 && input.all { it.isDigit() || it == '.' }) {
                                weightInput = input
                            }
                        },
                        label = { Text("Weight ($units)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_weight_input_field"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = IndigoAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedWeight = weightInput.toDoubleOrNull()
                        if (parsedWeight != null && parsedWeight > 0.0) {
                            fitnessViewModel.logWeight(parsedWeight)
                            showWeightDialog = false
                        }
                    }
                ) {
                    Text(
                        text = "SAVE",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = IndigoAccent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text(
                        text = "CANCEL",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText
                    )
                }
            }
        )
    }

    if (showCalculationExplanation) {
        AlertDialog(
            onDismissRequest = { showCalculationExplanation = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "HOW READINESS IS CALCULATED",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Daily Recovery Score",
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AmberAccent
                        )
                        Text(
                            text = "Readiness is calculated from your training load, nutrition compliance, and sleep (if tracked).",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            lineHeight = 15.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Muscle Readiness",
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AmberAccent
                        )
                        Text(
                            text = "Muscle readiness reflects recovery time needed per muscle group based on training intensity.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            lineHeight = 15.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Systemic Readiness",
                            fontFamily = SyneFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AmberAccent
                        )
                        Text(
                            text = "Systemic readiness reflects overall CNS fatigue from recent training volume.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText,
                            lineHeight = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCalculationExplanation = false }
                ) {
                    Text(
                        text = "OK",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent
                    )
                }
            }
        )
    }
}


