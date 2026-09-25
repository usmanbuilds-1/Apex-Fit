package com.apexfit.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.R
import com.apexfit.app.TrainViewModel
import com.apexfit.app.ui.components.SingleFrontHeatmapCanvas
import com.apexfit.app.data.HeatmapEntry
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.DateTimeUtils
import com.apexfit.app.utils.toDisplayWeight
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun HomeWeightErrorBanner(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val weightLogError by homeViewModel.weightLogError.collectAsStateWithLifecycle()
    weightLogError?.let { errorMsg ->
        LaunchedEffect(errorMsg) {
            delay(3000)
            homeViewModel.clearWeightLogError()
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(RedAccent.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = errorMsg, color = PrimaryText, fontSize = 13.sp)
        }
    }
}

private fun computeGreeting(): String {
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (currentHour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun HomeHeaderSection(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    trainViewModel: TrainViewModel,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val activeWorkoutSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val readiness by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val streakResult by algorithmViewModel.streakResult.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        val greeting = remember(java.time.LocalTime.now().hour / 6) { computeGreeting() }
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
}

@Composable
fun HomeTodayWorkoutCard(
    trainViewModel: TrainViewModel,
    algorithmViewModel: AlgorithmViewModel,
    fitnessViewModel: FitnessViewModel,
    homeViewModel: HomeViewModel,
    onNavigateTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayExercisesState by trainViewModel.todayExercises.collectAsStateWithLifecycle()
    val activePlanSessionsState by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()
    val activeWorkoutSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val readiness by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()

    val todayExercises = (todayExercisesState as? UiState.Success)?.data ?: emptyList()
    val activePlanSessions = (activePlanSessionsState as? UiState.Success)?.data ?: emptyList()

    val todayDayString = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.US) }.format(java.util.Date())
    val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
    val sessionName = todaySession?.label ?: "Rest Day"
    val focusMuscles = (todaySession?.focus ?: "Active Recovery").replace(", ", " • ").replace(",", " • ")

    val estimatedDuration by homeViewModel.estimatedWorkoutDuration.collectAsStateWithLifecycle()
    val finalWorkoutDurationMin = estimatedDuration

    var showCalculationExplanation by rememberSaveable { mutableStateOf(false) }
    val onShowCalculationExplanation = remember { { showCalculationExplanation = true } }
    var isStarting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isStarting) {
        if (isStarting) {
            delay(1000)
            isStarting = false
        }
    }

    val isTodayWorkoutCompleted by remember(completedSessions) {
        derivedStateOf {
            val todayDateStr = DateTimeUtils.todayDateString()
            completedSessions.any { it.date == todayDateStr }
        }
    }

    val lastTrainedDates by homeViewModel.lastTrainedDateMap.collectAsStateWithLifecycle()

    val recoveryHeatmap = remember(lastTrainedDates, todaySession) {
        val todayDateStr = DateTimeUtils.todayDateString()
        val focusText = todaySession?.focus ?: "Chest • Back • Arms"
        val canvasMuscles = listOf(
            "chest", "back", "front_delt", "side_delt", "rear_delt",
            "bicep", "tricep", "quad", "hamstring", "glute", "calf", "core"
        )

        canvasMuscles.associateWith { m ->
            val isInToday = isMuscleInFocus(m, focusText)
            val lastDate = lastTrainedDates[m]
            val daysSince = if (lastDate != null) getDaysSinceDate(lastDate, todayDateStr) else 100

            val (intIntensity, levelString, colorHex) = when {
                isInToday -> Triple(100, "ACTIVE", "#FF9500")
                daysSince <= 2 -> Triple(50, "RECOVERING", "#34D399")
                else -> Triple(0, "NEUTRAL", "#252535")
            }
            HeatmapEntry(volume = 0, intensity = intIntensity, level = levelString, colorHex = colorHex)
        }
    }

    ApexCard(
        elevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("todays_training_premium_card")
    ) {
        Column {
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
                            .clickable(onClick = onShowCalculationExplanation)
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

    if (showCalculationExplanation) {
        ReadinessExplanationDialog(onDismiss = { showCalculationExplanation = false })
    }
}

@Composable
fun HomeAlgorithmCards(
    algorithmViewModel: AlgorithmViewModel,
    modifier: Modifier = Modifier
) {
    val plateauResult by algorithmViewModel.plateauResult.collectAsStateWithLifecycle()
    val fatigueRatio by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()
    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        PlateauCard(plateauResult = plateauResult)
        if (plateauResult.isPlateau) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HomeStreakSection(
    algorithmViewModel: AlgorithmViewModel,
    modifier: Modifier = Modifier
) {
    val streakResult by algorithmViewModel.streakResult.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()
    val isTodayWorkoutCompleted by remember(completedSessions) {
        derivedStateOf {
            val todayDateStr = DateTimeUtils.todayDateString()
            completedSessions.any { it.date == todayDateStr }
        }
    }

    Row(
        modifier = modifier
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
}

@Composable
fun HomeNutritionRingCard(
    fitnessViewModel: FitnessViewModel,
    homeViewModel: HomeViewModel,
    nutritionViewModel: NutritionViewModel,
    trainViewModel: TrainViewModel,
    algorithmViewModel: AlgorithmViewModel,
    onNavigateTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val loggedMealsState by nutritionViewModel.loggedMeals.collectAsStateWithLifecycle()
    val todayNutrition by homeViewModel.todayNutrition.collectAsStateWithLifecycle()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val macroTargetsState by homeViewModel.macroTargets.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val activePlanSessionsState by trainViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()

    val targets = (macroTargetsState as? UiState.Success)?.data
    val activePlanSessions = (activePlanSessionsState as? UiState.Success)?.data ?: emptyList()
    val todayDayString = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.US) }.format(java.util.Date())
    val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }

    val isTodayWorkoutCompleted by remember(completedSessions) {
        derivedStateOf {
            val todayDateStr = DateTimeUtils.todayDateString()
            completedSessions.any { it.date == todayDateStr }
        }
    }
    val isPlannedTrainingToday = remember(todaySession) {
        todaySession != null &&
            !todaySession.label.contains("Rest", ignoreCase = true) &&
            todaySession.focus != "Muscle Recovery & Rest"
    }
    val isTodayTrainingDay = isTodayWorkoutCompleted || isPlannedTrainingToday

    val loggedCaloriesTotal = remember(todayNutrition) {
        todayNutrition.sumOf { it.calories }
    }
    val loggedProteinTotal = remember(todayNutrition) {
        todayNutrition.sumOf { it.protein }.roundToInt()
    }

    val calTarget = (targets?.calories ?: calorieTargetValue).coerceAtLeast(1)
    val calLogged = loggedCaloriesTotal
    val calLeft = (calTarget - calLogged)
    val calPercent = if (calTarget > 0) (calLogged * 100 / calTarget).coerceIn(0, 100) else 0

    val proteinTarget = targets?.protein ?: 0
    val proteinLogged = loggedProteinTotal

    val onNutritionCardClick = remember(onNavigateTo) { { onNavigateTo(2) } }
    val onLogFoodClick = remember(onNavigateTo) { { onNavigateTo(2) } }

    ApexCard(
        modifier = modifier
            .clickable(onClick = onNutritionCardClick)
            .testTag("nutrition_summary_card"),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.size(16.dp)
                    )
                }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkRaised)
                    .clickable(onClick = onLogFoodClick)
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
}

@Composable
fun HomeWeightChartCard(
    fitnessViewModel: FitnessViewModel,
    homeViewModel: HomeViewModel,
    onNavigateTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()
    val goalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()

    val weightHistory = (weightHistoryState as? UiState.Success)?.data ?: emptyList()
    val latestWeight = weightHistory.firstOrNull()?.weight ?: weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG

    var selectedFilter by rememberSaveable { mutableStateOf("7D") }
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var weightInput by rememberSaveable { mutableStateOf("") }

    val onWeightCardClick = remember(onNavigateTo) { { onNavigateTo(3) } }
    val latestDisplayWeight = remember(latestWeight, units) {
        String.format(java.util.Locale.US, "%.1f", latestWeight.toDisplayWeight(units))
    }
    val weightChangeStr = remember(weightHistory, units) {
        if (weightHistory.size >= 2) {
            val change = weightHistory.first().weight.toDisplayWeight(units) - weightHistory[1].weight.toDisplayWeight(units)
            String.format(java.util.Locale.US, "%.1f", Math.abs(change))
        } else null
    }
    val onLogWeightClick = remember(latestDisplayWeight) {
        {
            weightInput = latestDisplayWeight
            showWeightDialog = true
        }
    }

    ApexCard(
        modifier = modifier
            .clickable(onClick = onWeightCardClick)
            .testTag("body_weight_summary_card"),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$latestDisplayWeight $units",
                        fontSize = 28.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (weightHistory.size >= 2 && weightChangeStr != null) {
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
                                text = "$weightChangeStr $units",
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

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val daysToShow = when (selectedFilter) { "7D" -> 7L; "30D" -> 30L; else -> 90L }
                    val cutoffKey = remember(daysToShow) { java.time.LocalDate.now().minusDays(daysToShow).toString() }
                    val cutoff = remember(cutoffKey) {
                        try {
                            java.time.LocalDate.parse(cutoffKey).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (e: Exception) {
                            System.currentTimeMillis() - (daysToShow * 86400000L)
                        }
                    }
                    val chartDateFormatter = remember {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    }
                    val chartData = remember(weightHistory, cutoffKey) {
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

                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val canvasWidthPx = with(density) { maxWidth.toPx() }
                        val canvasHeightPx = with(density) { 56.dp.toPx() }
                        val tenDpPx = with(density) { 10.dp.toPx() }
                        val fiveDpPx = with(density) { 5.dp.toPx() }

                        val canvasPoints = remember(linePoints, chartGeometry, canvasWidthPx, canvasHeightPx) {
                            if (linePoints.size < 2 || chartGeometry == null) emptyList()
                            else {
                                val (minW, _, valRange) = chartGeometry
                                val xSpacing = canvasWidthPx / (linePoints.size - 1)
                                linePoints.mapIndexed { index, weightVal ->
                                    val ptX = index * xSpacing
                                    val pct = (weightVal - minW) / valRange
                                    val ptY = canvasHeightPx - (pct * (canvasHeightPx - tenDpPx) + fiveDpPx).toFloat()
                                    Offset(ptX, ptY)
                                }
                            }
                        }

                        val fillPath = remember(linePoints, canvasPoints, canvasHeightPx) {
                            Path().apply {
                                if (canvasPoints.size >= 2) {
                                    moveTo(canvasPoints.first().x, canvasHeightPx)
                                    lineTo(canvasPoints.first().x, canvasPoints.first().y)
                                    for (i in 0 until canvasPoints.size - 1) {
                                        val p0 = canvasPoints[i]; val p1 = canvasPoints[i + 1]
                                        val cx = (p0.x + p1.x) / 2f
                                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                    }
                                    lineTo(canvasPoints.last().x, canvasHeightPx)
                                    close()
                                }
                            }
                        }

                        val linePath = remember(linePoints, canvasPoints) {
                            Path().apply {
                                if (canvasPoints.size >= 2) {
                                    moveTo(canvasPoints.first().x, canvasPoints.first().y)
                                    lineTo(canvasPoints.first().x, canvasPoints.first().y)
                                    for (i in 0 until canvasPoints.size - 1) {
                                        val p0 = canvasPoints[i]; val p1 = canvasPoints[i + 1]
                                        val cx = (p0.x + p1.x) / 2f
                                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                    }
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
                            if (canvasPoints.size < 2) return@Canvas

                            drawPath(
                                fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(IndigoAccent.copy(alpha = 0.25f), Color.Transparent),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                            drawPath(
                                linePath,
                                color = IndigoAccent,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkRaised)
                    .clickable(onClick = onLogWeightClick)
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

    if (showWeightDialog) {
        LogWeightDialog(
            initialInput = weightInput,
            units = units,
            onDismiss = { showWeightDialog = false },
            onSave = { parsedWeight ->
                fitnessViewModel.logWeight(parsedWeight, preferredUnit = units)
                showWeightDialog = false
            }
        )
    }
}

@Composable
fun HomeAnalyticsSection(
    homeViewModel: HomeViewModel,
    fitnessViewModel: FitnessViewModel,
    modifier: Modifier = Modifier
) {
    val richSessionsFlow by homeViewModel.richSessionsFlow.collectAsStateWithLifecycle(emptyList())
    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()

    // Dedicated analytics container that observes profile metrics and session data
    // without triggering recomposition of the main training or weight cards.
    if (richSessionsFlow.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LogWeightDialog(
    initialInput: String,
    units: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var weightInput by rememberSaveable { mutableStateOf(initialInput) }
    var weightError by rememberSaveable { mutableStateOf("") }
    val weightMin = if (units.lowercase() == "kg") 20.0 else 44.0
    val weightMax = if (units.lowercase() == "kg") 300.0 else 660.0

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            onSave(parsedWeight)
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
            TextButton(onClick = onDismiss) {
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

@Composable
private fun ReadinessExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) {
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

private fun getDaysSinceDate(dateString: String, todayStr: String): Int {
    return try {
        val dateLog = DateTimeUtils.parseDate(dateString) ?: return 100
        val todayDate = DateTimeUtils.parseDate(todayStr) ?: return 100
        val diff = todayDate.time - dateLog.time
        (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    } catch (e: Exception) {
        100
    }
}

private fun isMuscleInFocus(muscle: String, focus: String): Boolean {
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
