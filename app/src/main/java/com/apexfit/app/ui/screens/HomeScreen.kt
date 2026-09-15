package com.apexfit.app.ui.screens

import com.apexfit.app.ui.models.UiState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.TrainViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.ui.models.*
import com.apexfit.app.ui.theme.*
import com.apexfit.app.ui.components.SingleFrontHeatmapCanvas
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.*
import com.apexfit.app.utils.toDisplayWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCardSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

private val com.apexfit.app.ui.models.UiPlateauResult.isPlateaued: Boolean get() = this.isPlateau

@Composable
private fun PlateauCard(plateauResult: com.apexfit.app.ui.models.UiPlateauResult) {
    if (!plateauResult.isPlateaued) return

    val daysText = if (plateauResult.daysStalled > 0)
        "${plateauResult.daysStalled} days without progress"
    else "Progress has stalled"

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkRaised,
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
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
                    text = "PLATEAU DETECTED",
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberAccent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = daysText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = SecondaryText
                )
                if (plateauResult.interventions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    plateauResult.interventions.take(3).forEach { action ->
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
fun HomeScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    homeViewModel: HomeViewModel,
    trainViewModel: TrainViewModel,
    nutritionViewModel: NutritionViewModel,
    onNavigateTo: (Int) -> Unit
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val activeWorkoutSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val activePlanSessionsState by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val activePlanSessions: List<com.apexfit.app.data.PlanSession> = (activePlanSessionsState as? UiState.Success)?.data ?: emptyList()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()

    val loggedMealsState by nutritionViewModel.loggedMeals.collectAsStateWithLifecycle()
    val loggedMeals: List<com.apexfit.app.ui.models.UiNutritionEntry> = (loggedMealsState as? UiState.Success)?.data ?: emptyList()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()

    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val streakResult by algorithmViewModel.streakResult.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val todayExercisesState by trainViewModel.todayExercises.collectAsStateWithLifecycle()
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val macroTargetsState by homeViewModel.macroTargets.collectAsStateWithLifecycle()
    val plateauResult by algorithmViewModel.plateauResult.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()
    val targets = (macroTargetsState as? UiState.Success)?.data

    if (todayExercisesState is UiState.Loading || weightHistoryState is UiState.Loading || macroTargetsState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IndigoAccent)
        }
        return
    }

    if (todayExercisesState is UiState.Error || weightHistoryState is UiState.Error || macroTargetsState is UiState.Error) {
        val msg = (todayExercisesState as? UiState.Error)?.message
            ?: (weightHistoryState as? UiState.Error)?.message
            ?: (macroTargetsState as? UiState.Error)?.message ?: "Unknown error"
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.home_failed_to_load, msg), color = Color.White)
        }
        return
    }

    val todayExercises = (todayExercisesState as UiState.Success).data
    val weightHistory = (weightHistoryState as UiState.Success).data
    val macroTargets = (macroTargetsState as UiState.Success).data

    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()

    val readiness by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val complianceScores by homeViewModel.complianceScores.collectAsStateWithLifecycle()
    val fatigueRatio by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()

    var selectedFilter by rememberSaveable { mutableStateOf("7D") }

    // Aggregate meal stats
    val latestWeight = weightHistory.firstOrNull()?.weight ?: weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG

    val todayDayString = remember {
        java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
    }
    val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
    val sessionName = todaySession?.label ?: "Rest Day"
    val focusMuscles = (todaySession?.focus ?: "Active Recovery").replace(", ", " • ").replace(",", " • ")

    // Calculate precise metabolic duration based on specific exercise sets
    val estimatedWorkoutDurationMin = if (todaySession == null || todaySession.focus == "Muscle Recovery & Rest") {
        0
    } else if (todayExercises.isEmpty()) {
        42
    } else {
        val rawTime = todayExercises.sumOf { 
            val setMinutes = com.apexfit.app.utils.AlgorithmEngine.estimateSetDurationMinutes(it.repsMin, it.repsMax)
            it.sets * (setMinutes + it.restSeconds / 60.0) 
        }
        val transitionTime = (todayExercises.size - 1).coerceAtLeast(0) * 2.0
        val warmUp = 5.0
        (rawTime + transitionTime + warmUp).roundToInt()
    }

    val finalWorkoutDurationMin = estimatedWorkoutDurationMin

    val context = LocalContext.current

    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var showCalculationExplanation by rememberSaveable { mutableStateOf(false) }

    var isStarting by remember { mutableStateOf(false) }
    LaunchedEffect(isStarting) {
        if (isStarting) {
            kotlinx.coroutines.delay(1000)
            isStarting = false
        }
    }

    val todayNutrition by homeViewModel.todayNutrition.collectAsStateWithLifecycle()
    val goalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val richSessionsFlow by homeViewModel.richSessionsFlow.collectAsStateWithLifecycle(emptyList())

    val todayDateStr = remember {
        com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }

    val loggedCaloriesTotal = remember(todayNutrition) {
        todayNutrition.sumOf { it.calories }
    }
    val loggedProteinTotal = remember(todayNutrition) {
        todayNutrition.sumOf { it.protein }.roundToInt()
    }

    val isTodayWorkoutCompleted = remember(completedSessions, todayDateStr) {
        completedSessions.any { it.date == todayDateStr }
    }
    val isPlannedTrainingToday = remember(todaySession) {
        todaySession != null &&
            !todaySession.label.contains("Rest", ignoreCase = true) &&
            todaySession.focus != "Muscle Recovery & Rest"
    }
    val isTodayTrainingDay = isTodayWorkoutCompleted || isPlannedTrainingToday

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
            val dateLog = com.apexfit.app.utils.DateTimeUtils.parseDate(dateString) ?: return 100
            val todayDate = com.apexfit.app.utils.DateTimeUtils.parseDate(todayStr) ?: return 100
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

    val latestDisplayWeight = latestWeight.toDisplayWeight(units)
    val goalDisplayWeight = goalWeight.toDisplayWeight(units)
    val weightDiff = latestDisplayWeight - goalDisplayWeight
    val weightChangeStr = if (weightDiff == 0.0) {
        "Target reached!"
    } else {
        val sign = if (weightDiff > 0) "+" else ""
        "${sign}${String.format(java.util.Locale.US, "%.1f", weightDiff)} $units to target"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Let's get after it today.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. TODAY'S TRAINING Card (Combined Readiness & Workout)
        ApexCard(
            elevation = 2.dp,
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TODAY'S TRAINING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberAccent,
                            letterSpacing = 1.5.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "How readiness is calculated",
                            tint = AmberAccent.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showCalculationExplanation = true }
                                .testTag("readiness_info_icon")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Details Column (Left side)
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = sessionName,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            lineHeight = 30.sp
                        )
                        Text(
                            text = focusMuscles,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = SecondaryText,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Time Badge Pill
                        if (finalWorkoutDurationMin > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkRaised)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "~$finalWorkoutDurationMin min",
                                    fontSize = 12.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }
                        }
                    }

                    // 2. Circular Progress Indicator (Center)
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .semantics {
                                val r = readiness
                                contentDescription = if (r != null)
                                    "Recovery readiness ${r.score} percent"
                                else
                                    "Recovery readiness not yet available"
                            }
                            .drawBehind {
                                val strokeWidthPx = 10.dp.toPx()
                                val diameter = size.minDimension - strokeWidthPx
                                val topLeftOffset = Offset(
                                    x = (size.width - diameter) / 2,
                                    y = (size.height - diameter) / 2
                                )
                                val arcSize = Size(diameter, diameter)

                                drawCircle(
                                    color = DarkRaised,
                                    radius = diameter / 2,
                                    style = Stroke(width = strokeWidthPx)
                                )
                                drawCircle(
                                    color = BorderSubtle.copy(alpha = 0.25f),
                                    radius = (diameter / 2) + 6.dp.toPx(),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawArc(
                                    color = if (readiness != null) GreenAccent else BorderSubtle,
                                    startAngle = -90f,
                                    sweepAngle = (readiness?.score?.toFloat() ?: 0f) / 100f * 360f,
                                    useCenter = false,
                                    topLeft = topLeftOffset,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val currentReadiness = readiness
                        if (currentReadiness != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${currentReadiness.score}%", fontSize = 24.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.ExtraBold, color = PrimaryText)
                                Text("RECOVERY", fontSize = 12.sp, fontFamily = SyneFamily, fontWeight = FontWeight.ExtraBold, color = GreenAccent, letterSpacing = 1.sp)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text("Log a\nsession", fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, color = SecondaryText, textAlign = TextAlign.Center, lineHeight = 14.sp)
                            }
                        }
                    }

                    // 3. Mannequin Muscle Overlay (On the Right)
                    SingleFrontHeatmapCanvas(
                        modifier = Modifier
                            .width(72.dp)
                            .height(120.dp)
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(8.dp)),
                        heatmap = recoveryHeatmap
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // START WORKOUT CTA Button
                Button(
                    onClick = {
                        if (activeWorkoutSession != null) {
                            onNavigateTo(1)
                            return@Button
                        }
                        if (isStarting) return@Button
                        if (isTodayWorkoutCompleted) {
                            onNavigateTo(1)
                        } else if (todaySession == null) {
                            onNavigateTo(1)
                        } else {
                            isStarting = true
                            fitnessViewModel.startWorkoutSession(todaySession)
                        }
                    },
                    enabled = !isStarting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            activeWorkoutSession != null -> OrangeAccent
                            isTodayWorkoutCompleted -> GreenAccent
                            todaySession == null -> DarkRaised
                            else -> OrangeAccent
                        },
                        contentColor = when {
                            activeWorkoutSession != null -> Color.Black
                            isTodayWorkoutCompleted -> Color.Black
                            todaySession == null -> SecondaryText
                            else -> Color.Black
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_workout_button"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when {
                                activeWorkoutSession != null -> Icons.Default.PlayCircle
                                isTodayWorkoutCompleted -> Icons.Default.CheckCircle
                                todaySession == null -> Icons.Default.SelfImprovement
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            tint = when {
                                activeWorkoutSession != null -> Color.Black
                                isTodayWorkoutCompleted -> Color.Black
                                todaySession == null -> SecondaryText
                                else -> Color.Black
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                activeWorkoutSession != null -> "RESUME WORKOUT"
                                isTodayWorkoutCompleted -> "WORKOUT DONE  ✓"
                                todaySession == null -> "LOG CUSTOM WORKOUT"
                                else -> "START WORKOUT"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                activeWorkoutSession != null -> Color.Black
                                isTodayWorkoutCompleted -> Color.Black
                                todaySession == null -> SecondaryText
                                else -> Color.Black
                            },
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Plateau card — only visible when progress has stalled
        PlateauCard(plateauResult = plateauResult)

        if (plateauResult.isPlateaued) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. STREAK Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                streakResult.training.current >= 7 -> {
                    Text("🔥 Perfect week — all sessions done!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
                }
                streakResult.training.current > 0 -> {
                    val n = streakResult.training.current
                    Text("🔥 $n session${if (n == 1) "" else "s"} this week", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
                }
                isTodayWorkoutCompleted -> {
                    Text("🔥 First session logged this week!", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OrangeAccent)
                }
                else -> {
                    Text("Log a workout to track your weekly sessions", fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

            // 4. Two-Column Matrix Card Row (NUTRITION & BODY WEIGHT)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // NUTRITION Left Card
                // Use the goal-adjusted adaptive target from sharedTargetsFlow.
                // targets.calories is already goal-adjusted for non-manual users
                // and respects the stored manual value for manual users.
                // Fall back to calorieTargetValue only if targets hasn't loaded yet.
                val calTarget = (targets?.calories ?: calorieTargetValue).coerceAtLeast(1)
                val calLogged = loggedCaloriesTotal
                val calLeft = (calTarget - calLogged)
                val calPercent = if (calTarget > 0) (calLogged * 100 / calTarget).coerceIn(0, 100) else 0

                val proteinTarget = targets?.protein ?: 0
                val proteinLogged = loggedProteinTotal

                ApexCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigateTo(2) }
                        .testTag("nutrition_summary_card"),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
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
                                        text = "NUTRITION",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoAccent,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isTodayTrainingDay) AmberAccent.copy(alpha = 0.2f) else DarkRaised
                                    ) {
                                        Text(
                                            text = if (isTodayTrainingDay) "TRAIN" else "REST",
                                            fontSize = 9.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTodayTrainingDay) AmberAccent else SecondaryText,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = IndigoAccent.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(16.dp)
                                )
                            }

                            // Circular progress ring showing nutrition completion
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .drawBehind {
                                        val strokeWidthPx = 8.dp.toPx()
                                        drawCircle(
                                            color = DarkRaised,
                                            radius = size.minDimension / 2 - strokeWidthPx / 2,
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                        if (calTarget > 0) {
                                            drawArc(
                                                color = IndigoAccent,
                                                startAngle = -90f,
                                                sweepAngle = (calPercent.toFloat() / 100f) * 360f,
                                                useCenter = false,
                                                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                                                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format(java.util.Locale.US, "%,d", calLogged),
                                        fontSize = 20.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (calTarget > 0) "/ ${String.format(java.util.Locale.US, "%,d", calTarget)}" else "—",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (calTarget > 0) "${String.format(java.util.Locale.US, "%,d", calLeft.coerceAtLeast(0))} kcal left" else "No target",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Egg, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (proteinTarget > 0) "$proteinLogged / ${proteinTarget}g protein" else "$proteinLogged / — protein",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // + Log Food Button at Bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkRaised)
                                .clickable { onNavigateTo(2) }
                                .testTag("log_food_bottom_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = IndigoAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOG FOOD",
                                color = IndigoAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // BODY WEIGHT Right Card
                ApexCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onNavigateTo(3) }
                        .testTag("body_weight_summary_card"),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BODY WEIGHT",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoAccent,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = IndigoAccent.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(16.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", latestWeight.toDisplayWeight(units))} $units",
                                    fontSize = 28.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                if (weightHistory.size >= 2) {
                                    val change = weightHistory.first().weight.toDisplayWeight(units) - weightHistory[1].weight.toDisplayWeight(units)
                                    val isPositiveChange = when {
                                        userGoal.equals("Gain Muscle", ignoreCase = true) -> change > 0
                                        else -> change < 0
                                    }
                                    val deltaColor = if (isPositiveChange) GreenAccent else RedAccent
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (change < 0) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = deltaColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else if (change > 0) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = deltaColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", Math.abs(change))} $units",
                                            fontSize = 12.sp,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Normal,
                                            color = deltaColor
                                        )
                                        Text(
                                            text = "this week",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Log weigh-ins",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Text(
                                text = "WEIGHT ($units)",
                                fontSize = 10.sp,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Smooth Line Graph (Spleen spline curves)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val daysToShow = when (selectedFilter) { "7D" -> 7; "30D" -> 30; else -> 90 }
                                val cutoff = System.currentTimeMillis() - (daysToShow.toLong() * 86400000L)
                                val chartDateFormatter = remember {
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                }
                                val chartData = remember(weightHistory, cutoff) {
                                    weightHistory.filter { entry ->
                                        try {
                                            val d = chartDateFormatter.parse(entry.date)
                                            (d?.time ?: 0L) >= cutoff
                                        } catch (e: Exception) { true }
                                    }
                                }
                                if (chartData.size >= 2) {
                                    val entries = remember(chartData) {
                                        chartData.reversed()
                                    }
                                    val linePoints = remember(entries, units) { entries.map { it.weight.toDisplayWeight(units) } }
                                    val minWeight = linePoints.minOrNull() ?: 60.0
                                    val maxWeight = linePoints.maxOrNull() ?: 62.0
                                    val chartGeometry = remember(linePoints) {
                                        if (linePoints.size < 2) return@remember null
                                        val minW = linePoints.minOrNull() ?: 60.0
                                        val maxW = linePoints.maxOrNull() ?: 62.0
                                        val valRange = if (maxW == minW) 1.0 else maxW - minW
                                        Triple(minW, maxW, valRange)
                                    }

                                    val linePath = remember(linePoints) {
                                        Path().apply {
                                            if (linePoints.size >= 2) {
                                                val minW = linePoints.minOrNull() ?: 60.0
                                                val maxW = linePoints.maxOrNull() ?: 62.0
                                                val valRange = if (maxW == minW) 1.0 else maxW - minW
                                                val ratioPoints = linePoints.mapIndexed { index, weightVal ->
                                                    val rx = index.toFloat() / (linePoints.size - 1)
                                                    val pct = ((weightVal - minW) / valRange).toFloat()
                                                    val ry = 1f - (pct * (46f / 56f) + (5f / 56f))
                                                    Offset(rx, ry)
                                                }
                                                moveTo(ratioPoints.first().x, ratioPoints.first().y)
                                                for (i in 0 until ratioPoints.size - 1) {
                                                    val p0 = ratioPoints[i]
                                                    val p1 = ratioPoints[i + 1]
                                                    val conPtX1 = (p0.x + p1.x) / 2
                                                    val conPtY1 = p0.y
                                                    val conPtX2 = (p0.x + p1.x) / 2
                                                    val conPtY2 = p1.y
                                                    cubicTo(conPtX1, conPtY1, conPtX2, conPtY2, p1.x, p1.y)
                                                }
                                            }
                                        }
                                    }
                                    val fillPath = remember(linePoints) {
                                        Path().apply {
                                            if (linePoints.size >= 2) {
                                                val minW = linePoints.minOrNull() ?: 60.0
                                                val maxW = linePoints.maxOrNull() ?: 62.0
                                                val valRange = if (maxW == minW) 1.0 else maxW - minW
                                                val ratioPoints = linePoints.mapIndexed { index, weightVal ->
                                                    val rx = index.toFloat() / (linePoints.size - 1)
                                                    val pct = ((weightVal - minW) / valRange).toFloat()
                                                    val ry = 1f - (pct * (46f / 56f) + (5f / 56f))
                                                    Offset(rx, ry)
                                                }
                                                moveTo(ratioPoints.first().x, 1f)
                                                lineTo(ratioPoints.first().x, ratioPoints.first().y)
                                                for (i in 0 until ratioPoints.size - 1) {
                                                    val p0 = ratioPoints[i]
                                                    val p1 = ratioPoints[i + 1]
                                                    val conPtX1 = (p0.x + p1.x) / 2
                                                    val conPtY1 = p0.y
                                                    val conPtX2 = (p0.x + p1.x) / 2
                                                    val conPtY2 = p1.y
                                                    cubicTo(conPtX1, conPtY1, conPtX2, conPtY2, p1.x, p1.y)
                                                }
                                                lineTo(ratioPoints.last().x, 1f)
                                                close()
                                             }
                                        }
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .semantics {
                                                contentDescription = "Weight trend chart showing ${entries.size} entries from ${entries.firstOrNull()?.date ?: "N/A"} to ${entries.lastOrNull()?.date ?: "N/A"}, ranging from ${minWeight}kg to ${maxWeight}kg"
                                            }
                                    ) {
                                        if (linePoints.size < 2 || chartGeometry == null) return@Canvas
                                        val (minW, maxW, valRange) = chartGeometry
                                        val xSpacing = size.width / (linePoints.size - 1)

                                        val canvasPoints = linePoints.mapIndexed { index, weightVal ->
                                            val ptX = index * xSpacing
                                            val pct = (weightVal - minW) / valRange
                                            val ptY = size.height - (pct * (size.height - 10.dp.toPx()) + 5.dp.toPx()).toFloat()
                                            Offset(ptX.toFloat(), ptY)
                                        }

                                        scale(scaleX = size.width, scaleY = size.height, pivot = Offset.Zero) {
                                            drawPath(
                                                path = fillPath,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(IndigoAccent.copy(alpha = 0.25f), Color.Transparent),
                                                    startY = 0f,
                                                    endY = 1f
                                                )
                                            )
                                            drawPath(
                                                path = linePath,
                                                color = IndigoAccent,
                                                style = Stroke(width = 3.dp.toPx() / size.height, cap = StrokeCap.Round)
                                            )
                                        }

                                        // Point anchor circles on spline line
                                        canvasPoints.forEach { pt ->
                                            drawCircle(
                                                color = DarkCardSurface,
                                                radius = 4.dp.toPx(),
                                                center = pt
                                            )
                                            drawCircle(
                                                color = IndigoAccent,
                                                radius = 4.dp.toPx(),
                                                center = pt,
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "No weight history yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Time range pill selector: "7D", "30D", "90D"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkRaised, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("7D", "30D", "90D").forEach { filter ->
                                    val isSelected = filter == selectedFilter
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) IndigoAccent else Color.Transparent)
                                            .clickable { selectedFilter = filter }
                                            .padding(vertical = 12.dp)
                                            .heightIn(min = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = filter,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // + Log Weight Button at Bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkRaised)
                                .clickable {
                                    weightInput = String.format(java.util.Locale.US, "%.1f", latestWeight.toDisplayWeight(units))
                                    showWeightDialog = true
                                }
                                .testTag("log_weight_bottom_button"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = IndigoAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LOG WEIGHT",
                                color = IndigoAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
    }



    // ───── LOG WEIGHT DIALOG ─────
    if (showWeightDialog) {
        var weightError by rememberSaveable { mutableStateOf("") }
        val weightMin = if (units.lowercase() == "kg") 20.0 else 44.0
        val weightMax = if (units.lowercase() == "kg") 300.0 else 660.0

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { 
            delay(100)
            try { focusRequester.requestFocus() } catch (_: Exception) {} 
        }

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
                        fontSize = 12.sp,
                        color = SecondaryText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { input ->
                            val normalized = input.replace(',', '.')
                            if (normalized.count { it == '.' } <= 1 && normalized.all { it.isDigit() || it == '.' }) {
                                weightInput = normalized
                            }
                        },
                        label = { Text(stringResource(R.string.home_weight, units), color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("dialog_weight_input_field"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = OrangeAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )

                    if (weightError.isNotEmpty()) {
                        Text(
                            text = weightError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedWeight = weightInput.toDoubleOrNull()
                        when {
                            parsedWeight == null -> weightError = "Enter a valid number"
                            parsedWeight !in weightMin..weightMax -> 
                                weightError = "Must be $weightMin–$weightMax $units"
                            else -> {
                                fitnessViewModel.logWeight(parsedWeight, preferredUnit = units)
                                weightError = ""
                                showWeightDialog = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = "SAVE",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = OrangeAccent
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
                            text = "Readiness is estimated from your training load and nutrition compliance.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
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
                            fontSize = 12.sp,
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
                            text = "Systemic readiness reflects your overall training load and recovery.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
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

