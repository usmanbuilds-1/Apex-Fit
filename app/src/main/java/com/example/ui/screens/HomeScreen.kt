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

    // Aggregate meal stats
    val latestWeight = weightHistory.firstOrNull()?.weight ?: weight ?: com.example.UserDefaults.WEIGHT_KG
    
    // Dynamic Mifflin-St Jeor equation to calculate BMR baseline with biological offset
    val sexOffset = if (userSex.equals("female", ignoreCase = true)) -161.0 else 5.0
    val bmrBaseline = (10.0 * latestWeight) + (6.25 * userHeight) - (5.0 * userAge) + sexOffset
    
    // Moderate activity (1.55) as default multi-purpose athlete baseline
    val fallbackTdee = (bmrBaseline * 1.55).toInt()
    val activeTdee = tdeeResult.tdee ?: fallbackTdee

    val suggestedCalories by nutritionViewModel.suggestedCaloricTarget.collectAsStateWithLifecycle()
    // Science: Helms et al. (2014) Daily caloric targets adjusted by objective goals
    val calorieTarget = if (calorieTargetManual) calorieTargetValue else suggestedCalories

    val complianceScores by homeViewModel.complianceScores.collectAsStateWithLifecycle()
    val complianceScore by homeViewModel.complianceScore.collectAsStateWithLifecycle()
    val fatigueRatio by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()
    val injuryRisks by algorithmViewModel.injuryRiskSignals.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome text
        item {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "APEX FIT // V2.0",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "GOOD MORNING, ${username.uppercase()}",
                    fontFamily = SyneFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Today's Session Pill & Energy Progression
        item {
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            val hasWorkout = todaySession != null && todaySession.focus != "Muscle Recovery & Rest"

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasWorkout) AmberAccent else MutedText)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasWorkout) "TODAY'S SESSION" else "REST DAY FOCUS",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A0A0F)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasWorkout) "${todaySession?.label} - ${todaySession?.focus}" else "Focus on complete systemic recovery",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Calorie Ring Card
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CALORIC METABOLIC PROGRESS",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val loggedCalories = loggedMeals.sumOf { it.calories }
                    // Ring
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .drawBehind {
                                // Background circle
                                drawCircle(
                                    color = BorderSubtle,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 10.dp.toPx())
                                )
                                // Active arc
                                val sweep =
                                    (loggedCalories.toFloat() / calorieTarget.toFloat()).coerceIn(0f, 1f) * 360f
                                val ringColor = if (loggedCalories >= calorieTarget) GreenAccent else AmberAccent
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Dynamic glow behind numbers (approx 8% opacity radial circle)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    drawCircle(
                                        color = AmberAccent.copy(alpha = 0.08f),
                                        radius = 60.dp.toPx()
                                    )
                                }
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = loggedCalories.toString(),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = "/ $calorieTarget",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = MutedText
                            )
                            Text(
                                text = "KCAL",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Linear Macro Bars
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val loggedProtein = loggedMeals.sumOf { it.protein }
                        val loggedCarbs = loggedMeals.sumOf { it.carbs }
                        val loggedFat = loggedMeals.sumOf { it.fat }

                        val proteinTarget = (latestWeight * 1.8).toInt().coerceIn(100, 250)
                        val fatTarget = (calorieTarget * 0.25 / 9.0).toInt().coerceIn(45, 120)
                        val carbsTarget = ((calorieTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4).toInt().coerceIn(100, 500)

                        MacroTrackerBar(label = "PROTEIN", current = loggedProtein, target = proteinTarget.toDouble(), color = AmberAccent)
                        MacroTrackerBar(label = "CARBS", current = loggedCarbs, target = carbsTarget.toDouble(), color = BlueAccent)
                        MacroTrackerBar(label = "FAT", current = loggedFat, target = fatTarget.toDouble(), color = RedAccent)
                    }
                }
            }
        }

        // Stats strip horizontal
        item {
            val history by homeViewModel.weightHistory.collectAsStateWithLifecycle()
            val trendWeightVal by algorithmViewModel.trendWeight.collectAsStateWithLifecycle()
            
            val trendWeightDisplay = trendWeightVal ?: latestWeight
            val trendConfidence = when {
                history.size >= 14 -> "High (14d EMA)"
                history.size >= 7 -> "Med (7d EMA)"
                else -> "Low (Need ${14 - history.size}d)"
            }

            val tdeeConfidenceLabel = when {
                tdeeResult.confidence.contains("high") -> "High (Adaptive)"
                tdeeResult.confidence.contains("medium") -> "Med (Adaptive)"
                tdeeResult.confidence.contains("low") && tdeeResult.confidence.contains("Mifflin") -> "Mifflin-St Jeor"
                else -> "Low (Adaptive)"
            }

            val numStreak = streakResult.nutrition.current
            val streakLabel = "$numStreak Days"
            val sessionsPerWk = targets?.weeklyTrainingSessions ?: 4

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
                // Touch targets and density styled according to Material Design 3 guidelines
            ) {
                item {
                    MetricMiniCard(value = "${String.format("%.1f", trendWeightDisplay)} kg", label = "TREND WT", subValue = trendConfidence, glowColor = AmberAccent)
                }
                item {
                    MetricMiniCard(value = "$activeTdee kcal", label = "EST TDEE", subValue = tdeeConfidenceLabel, glowColor = BlueAccent)
                }
                item {
                    MetricMiniCard(value = "$sessionsPerWk Days", label = "SESSIONS/WK", subValue = "Plan Objective", glowColor = GreenAccent)
                }
                item {
                    MetricMiniCard(value = streakLabel, label = "TREK STREAK", subValue = "Adhered Days", glowColor = AmberAccent)
                }
                item {
                    MetricMiniCard(value = "${complianceScore}%", label = "MOMENTUM", subValue = "Overall Adherence", glowColor = GreenAccent)
                }
            }
        }

        // Bio-metric Session Readiness Card
        item {
            val readiness by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()

            if (readiness != null) {
                val rd = readiness!!
                PremiumCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("session_readiness_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BIO-METRIC SESSION READINESS",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = rd.label.uppercase(),
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }
                            
                            val scoreColor = when {
                                rd.score >= 80 -> GreenAccent
                                rd.score >= 50 -> AmberAccent
                                else -> RedAccent
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(scoreColor.copy(alpha = 0.12f))
                                    .border(BorderStroke(2.dp, scoreColor), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${rd.score}",
                                    fontFamily = SyneFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scoreColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = rd.prediction,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText,
                            lineHeight = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = rd.recommendation,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = AmberAccent,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Factors Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(rd.factors) { factor ->
                                val factorColor = when (factor.impact.lowercase()) {
                                    "positive" -> GreenAccent
                                    "negative" -> RedAccent
                                    else -> SecondaryText
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(factorColor.copy(alpha = 0.1f))
                                        .border(BorderStroke(1.dp, factorColor.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(factorColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${factor.name.uppercase()}: ${factor.impact.uppercase()}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = factorColor
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Based on acute-to-chronic workload ratio (ACR) & Schoenfeld recovery limits.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            color = MutedText
                        )
                    }
                }
            } else {
                PremiumCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("session_readiness_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BIO-METRIC SESSION READINESS",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "INSUFFICIENT DATA",
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryText.copy(alpha = 0.12f))
                                    .border(BorderStroke(2.dp, SecondaryText), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    fontFamily = SyneFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SecondaryText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "Science: Cumulative workload readiness requires a 14-day history baseline to establish acute-to-chronic ratio (ACR) calculations under Israetel's and Schoenfeld's recovery indices.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText,
                            lineHeight = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Baseline progress indicators
                        val sessionsCount = completedSessions.size
                        val daysRemaining = (14 - sessionsCount).coerceAtLeast(0)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (sessionsCount >= 14) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (sessionsCount >= 14) GreenAccent else MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Training baseline index: $sessionsCount / 14 days completes ${if (daysRemaining > 0) "($daysRemaining sessions remaining)" else "✔"}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = if (sessionsCount >= 14) PrimaryText else MutedText
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            val yesterdayNutritionLogged = loggedMeals.any { it.date == fitnessViewModel.getDateDaysAgo(1) }
                            Icon(
                                imageVector = if (yesterdayNutritionLogged) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (yesterdayNutritionLogged) GreenAccent else MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Yesterday's nutrition logs check: ${if (yesterdayNutritionLogged) "ACTIVE" else "NOT LOGGED"}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = if (yesterdayNutritionLogged) PrimaryText else MutedText
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "Autoregulation baseline activation pending dynamic ACR calculation window.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = AmberAccent
                        )
                    }
                }
            }
        }

        // Start Workout Card CTA
        item {
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = activePlanSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            val sessionName = todaySession?.label ?: "Science Hypertrophy"
            val focusMuscles = todaySession?.focus ?: "Standard Workout Routine"

            val estimateDuration by fitnessViewModel.estimatedSetDuration.collectAsStateWithLifecycle()
            // Calculate precise metabolic duration based on specific exercise sets
            val estimatedWorkoutDurationMin = if (todaySession == null || todaySession.focus == "Muscle Recovery & Rest") {
                0
            } else if (todayExercises.isEmpty()) {
                75
            } else {
                val rawTime = todayExercises.sumOf { 
                    val setMinutes = estimateDuration(it.repsMin, it.repsMax)
                    it.sets * (setMinutes + it.restSeconds / 60.0) 
                }
                val transitionTime = (todayExercises.size - 1).coerceAtLeast(0) * 2.0
                val warmUp = 5.0
                (rawTime + transitionTime + warmUp).toInt()
            }

            PremiumCard(modifier = Modifier.fillMaxWidth().testTag("start_workout_card")) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AmberAccent.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TODAY'S SESSION",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = sessionName,
                                fontFamily = SyneFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = focusMuscles,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = SecondaryText
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "DURATION",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (estimatedWorkoutDurationMin > 0) "~$estimatedWorkoutDurationMin MIN" else "-- MIN",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 13.sp,
                                color = PrimaryText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Calculated dynamically based on warmups, set volumes, and prescribed rest intervals.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (todaySession != null) {
                                fitnessViewModel.startWorkoutSession(todaySession)
                            } else {
                                // Default Upper A backup triggers immediately
                                val backup = activePlanSessions.firstOrNull { it.id == 101L } ?: PlanSession(101L, 1L, "Upper A", "Monday", "Chest, Back, Arms")
                                fitnessViewModel.startWorkoutSession(backup)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_workout_button")
                    ) {
                        Text(
                            text = "START WORKOUT",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFF0A0A0F)
                        )
                    }
                }
            }
        }

        // Attention alerts dismissible lists at bottom
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ATHLETIC SYSTEM MONITOR",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedText
                )

                // Render dynamic warning blocks
                if (fatigueRatio.ratio >= 1.5) {
                    IntelligenceAlertCard(
                        title = "FATIGUE SYSTEM OVERREACHING WARNING",
                        desc = "Acute calculation exceeds chronic load standard indices (Ratio: ${String.format("%.2f", fatigueRatio.ratio)}). Recommend mandatory 3-5 day deload focus to offset pending CNS overload.",
                        borderColor = RedAccent
                    )
                }

                if (plateauResult.isPlateau) {
                    IntelligenceAlertCard(
                        title = "PLATEAU / STALL AT-RISK INDEX DETECTED",
                        desc = "Science (Israetel 2019): Weight stale for 10+ days despite compliance. Suggested: ${plateauResult.recommendation}",
                        borderColor = RedAccent
                    )
                }

                if (complianceScores.overall < 85) {
                    IntelligenceAlertCard(
                        title = "ATHLETIC COMPLIANCE WARNING",
                        desc = "Workout execution consistency index dropping to ${complianceScores.overall}%. Nutrition and training compliance must be restored to optimize anabolic pathways (Renaissance Diet 2.0).",
                        borderColor = AmberAccent
                    )
                } else {
                    IntelligenceAlertCard(
                        title = "NEW ATHLETIC MILESTONE DETECTED",
                        desc = "Hypertrophy Quality score remains in premium optimal boundaries! High volume compliance is driving steady estimated 1-Rep-Max increases on primary presses.",
                        borderColor = GreenAccent
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
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
