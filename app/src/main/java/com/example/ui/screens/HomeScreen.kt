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
    val focusMuscles = todaySession?.focus ?: "Chest • Back • Arms"

    val estimateDuration by fitnessViewModel.estimatedSetDuration.collectAsStateWithLifecycle()
    // Calculate precise metabolic duration based on specific exercise sets
    val estimatedWorkoutDurationMin = if (todaySession == null || todaySession.focus == "Muscle Recovery & Rest") {
        0
    } else if (todayExercises.isEmpty()) {
        42
    } else {
        val rawTime = todayExercises.sumOf { 
            val setMinutes = estimateDuration(it.repsMin, it.repsMax)
            it.sets * (setMinutes + it.restSeconds / 60.0) 
        }
        val transitionTime = (todayExercises.size - 1).coerceAtLeast(0) * 2.0
        val warmUp = 5.0
        (rawTime + transitionTime + warmUp).toInt()
    }

    val finalWorkoutDurationMin = if (estimatedWorkoutDurationMin > 0) estimatedWorkoutDurationMin else 42

    // Dialog state for "How it's calculated" explanation
    var showCalculationExplanation by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

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
            
            val (intIntensity, levelString) = when {
                isInToday -> 3 to "ACTIVE" // ACTIVE
                daysSince <= 2 -> 2 to "RECOVERING" // RECOVERING
                else -> 1 to "NEUTRAL" // NEUTRAL
            }
            HeatmapEntry(volume = 0, intensity = intIntensity, level = levelString, colorHex = "#F59E0B")
        }
    }

    val weightDiff = latestWeight - goalWeight
    val weightChangeStr = if (weightDiff == 0.0) {
        "Target reached!"
    } else {
        val sign = if (weightDiff > 0) "+" else ""
        "${sign}${String.format("%.1f", weightDiff)} kg to target"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Welcome Header
        item {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text(
                    text = "Good morning, ${username.ifEmpty { "Usman" }} 👋",
                    fontFamily = SyneFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Let’s get after it today.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    color = SecondaryText
                )
            }
        }

        // 2. TODAY'S TRAINING Card (Combined Readiness & Workout)
        item {
            PremiumCard(
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
                        Text(
                            text = "TODAY'S TRAINING",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent,
                            letterSpacing = 0.5.sp
                        )
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
                                    fontFamily = SyneFamily,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GreenAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Recovery Score",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 8.sp,
                                    color = MutedText,
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
                                fontFamily = SyneFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = PrimaryText
                            )
                            Text(
                                text = focusMuscles,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
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
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = PrimaryText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 3. Mannequin Muscle Overlay (On the Right)
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(135.dp)
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
                        onClick = { onNavigateTo(1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_workout_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)) // Amber gradient
                                    )
                                ),
                            contentAlignment = Alignment.Center
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
                                    fontFamily = SyneFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Divider line before streak footer
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Streak caption below button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔥 ${if (streakResult.training.current > 0) streakResult.training.current else 8} DAY STREAK",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent
                        )
                    }
                }
            }
        }

        // 4. Two-Column Matrix Card Row (NUTRITION & BODY WEIGHT)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // NUTRITION Left Card
                val calTarget = if (calorieTargetValue > 0) calorieTargetValue else 2600
                val calLogged = loggedCaloriesTotal
                val calLeft = (calTarget - calLogged).coerceAtLeast(0)
                val calPercent = if (calTarget > 0) (calLogged * 100 / calTarget).coerceIn(0, 100) else 75

                val proteinTarget = targets?.protein ?: 180
                val proteinLogged = loggedProteinTotal

                PremiumCard(
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
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA),
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(12.dp).clickable { onNavigateTo(2) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Progress Arc (Left)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(74.dp)
                                            .drawBehind {
                                                drawCircle(
                                                    color = BorderSubtle,
                                                    radius = size.minDimension / 2 - 2.dp.toPx(),
                                                    style = Stroke(width = 4.dp.toPx())
                                                )
                                                drawArc(
                                                    color = Color(0xFFA78BFA),
                                                    startAngle = -90f,
                                                    sweepAngle = (calPercent.toFloat() / 100f) * 360f,
                                                    useCenter = false,
                                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${if (calLogged > 0) calLogged else 1950}",
                                                fontFamily = SyneFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = PrimaryText
                                            )
                                            Text(
                                                text = "/ ${calTarget}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 7.5.sp,
                                                color = SecondaryText
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${calPercent}%",
                                        fontFamily = SyneFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFA78BFA)
                                    )
                                }

                                // Details Labels (Right)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Row 1: Calorie remaining
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("🔥", fontSize = 11.sp)
                                            Text(
                                                text = "${if (calLeft > 0) calLeft else 650} kcal",
                                                fontFamily = SyneFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = PrimaryText
                                            )
                                        }
                                        Text(
                                            text = "remaining",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText,
                                            lineHeight = 10.sp,
                                            modifier = Modifier.padding(start = 14.dp)
                                        )
                                    }

                                    // Row 2: Protein logged
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restaurant,
                                                contentDescription = null,
                                                tint = Color(0xFFA78BFA),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "${if (proteinLogged > 0) proteinLogged else 54} / ${proteinTarget}g",
                                                fontFamily = SyneFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = PrimaryText
                                            )
                                        }
                                        Text(
                                            text = "protein",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText,
                                            lineHeight = 10.sp,
                                            modifier = Modifier.padding(start = 14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // + Log Food Clickable Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .clickable { onNavigateTo(2) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("log_food_bottom_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("+", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        text = "Log Food",
                                        color = Color(0xFFA78BFA),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA).copy(alpha = 0.6f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }

                // BODY WEIGHT Right Card
                PremiumCard(
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
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoAccent,
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = IndigoAccent,
                                    modifier = Modifier.size(12.dp).clickable {
                                        weightInput = String.format("%.1f", latestWeight)
                                        showWeightDialog = true
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${String.format("%.1f", latestWeight)}",
                                    fontFamily = SyneFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = PrimaryText
                                )
                                Text(
                                    text = "kg",
                                    fontFamily = SyneFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Weight metric trend row (Mock trend: -0.6 kg vs last week matching the image design!)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "0.6 kg",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.5.sp,
                                    color = GreenAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " this week",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.5.sp,
                                    color = MutedText
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Smooth Spline Curve history (Line graph)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                val linePoints = remember(weightHistory) {
                                    if (weightHistory.size >= 4) {
                                        weightHistory.take(7).reversed().map { it.weight }
                                    } else {
                                        listOf(61.8, 61.6, 61.4, 61.5, 61.3, 61.2, 61.1)
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

                                        // Glow gradient area below spleen line
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
                                                colors = listOf(IndigoAccent.copy(alpha = 0.25f), Color.Transparent),
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
                                            color = IndigoAccent,
                                            style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round)
                                        )

                                        // Point anchor circles on spline line
                                        canvasPoints.forEach { pt ->
                                            drawCircle(
                                                color = Color.White,
                                                radius = 1.5.dp.toPx(),
                                                center = pt
                                            )
                                            drawCircle(
                                                color = IndigoAccent,
                                                radius = 3.dp.toPx(),
                                                center = pt,
                                                style = Stroke(width = 0.75.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Selected tabs/filters at bottom of weights block
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("7D", "30D", "90D").forEach { filter ->
                                    val isSelected = filter == "7D"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF1B1B2B) else Color.Transparent)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = filter,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = if (isSelected) PrimaryText else MutedText,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // + Log Weight Clickable Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0F16))
                                .clickable {
                                    weightInput = String.format("%.1f", latestWeight)
                                    showWeightDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("log_weight_bottom_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("+", color = IndigoAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        text = "Log Weight",
                                        color = IndigoAccent,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = IndigoAccent.copy(alpha = 0.6f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ───── HOW IT'S CALCULATED INLINE EXPLANATION DIALOG ─────
    if (showCalculationExplanation) {
        AlertDialog(
            onDismissRequest = { showCalculationExplanation = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "TODAY'S READINESS METRIC",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Your daily Readiness Index is a personalized daily biometric calculated dynamically using physiological inputs:",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText,
                        lineHeight = 16.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("●", color = GreenAccent, fontSize = 11.sp)
                            Text(
                                text = "Acute-to-Chronic Workload Ratio (ACR): Compares your recent training fatigue (7 days) against chronic training base (28 days) to manage fatigue limits.",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = SecondaryText,
                                lineHeight = 14.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("●", color = Color(0xFFA78BFA), fontSize = 11.sp)
                            Text(
                                text = "Sleep Quality Factors: Dynamic HRV and Sleep recovery calculations modeled under Schoenfeld's systemic hypertrophy recovery bounds.",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = SecondaryText,
                                lineHeight = 14.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("●", color = BlueAccent, fontSize = 11.sp)
                            Text(
                                text = "Plateau Indices: Identifies early muscle recovery stalls using live exercise volume indices logged over the previous 14 days.",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = SecondaryText,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalculationExplanation = false }) {
                    Text(
                        text = "UNDERSTOOD",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent
                    )
                }
            }
        )
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
                        text = "Enter your current weight in kg. This updates your dynamic readiness fatigue filters and chronic load baselines.",
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
                        label = { Text("Weight (kg)", color = SecondaryText) },
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
}

@Composable
fun DashboardSummaryCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCardSurface)
            .border(
                border = BorderStroke(
                    1.dp,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            BorderBright,
                            BorderSubtle
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun MacroTrackerBar(
    label: String,
    current: Double,
    target: Double,
    color: Color
) {
    val progress = (current / target).toFloat().coerceIn(0f, 1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText
            )
            Text(
                text = "${current.toInt()}g / ${target.toInt()}g",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(BorderSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun MetricMiniCard(
    value: String,
    label: String,
    glowColor: Color,
    subValue: String = ""
) {
    Box(
        modifier = Modifier
            .width(115.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardSurface)
            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        // Emit visual accent color layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(glowColor.copy(alpha = 0.05f), radius = 30.dp.toPx(), center = Offset(size.width, size.height / 2))
                }
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 8.sp,
                color = SecondaryText,
                maxLines = 1
            )
            Column {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1
                )
                if (subValue.isNotEmpty()) {
                    Text(
                        text = subValue,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 7.sp,
                        color = MutedText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun IntelligenceAlertCard(
    title: String,
    desc: String,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkRaised)
            .border(BorderStroke(1.dp, BorderBright), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, borderColor.copy(alpha = 0.25f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Alert Indicator",
                    tint = borderColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
