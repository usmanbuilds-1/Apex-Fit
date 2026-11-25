package com.apexfit.app.ui.screens
import android.view.Gravity
import android.view.WindowManager
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.PlanBuilderViewModel
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.R
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.NutritionTargets
import com.apexfit.app.utils.SessionHeroCategory
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// ─── Design System Palette Constants (Strict Anti-Slop 7/7) ─────────────────
private val CanvasVoid     get() = ApexColors.Canvas
private val SurfaceNight1  get() = ApexColors.Surface
private val SurfaceNight2  get() = ApexColors.SurfaceRaised
private val MineralBlue get() = ApexColors.Primary
private val TextMuted      get() = ApexColors.TextSecondary
private val TextTertiary   get() = ApexColors.TextTertiary
private val BorderMuted get() = ApexColors.BorderSubtle

internal fun currentWeekTrainingSessionCount(sessions: List<RichTrainingSession>, today: LocalDate): Int {
    val weekStart = today.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)
    )
    return sessions.count { session ->
        if (!session.completed) return@count false
        val date = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: return@count false
        !date.isBefore(weekStart) && !date.isAfter(today)
    }
}

internal fun todayTrainingWeekSummary(completedSessions: Int, weeklyTarget: Int): String {
    val completed = completedSessions.coerceAtLeast(0)
    val target = weeklyTarget.coerceAtLeast(0)
    return when {
        target > 0 -> "$completed of $target ${if (target == 1) "session" else "sessions"} this week"
        completed == 1 -> "1 session completed this week"
        else -> "$completed sessions completed this week"
    }
}

internal fun todayExerciseCountLabel(exerciseCount: Int): String? = when {
    exerciseCount == 1 -> "1 exercise"
    exerciseCount > 1 -> "$exerciseCount exercises"
    else -> null
}

// ─── HomeScreen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    fitnessViewModel: FitnessViewModel = hiltViewModel(),
    planBuilderViewModel: PlanBuilderViewModel = hiltViewModel(),
    workoutExecutionViewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onNavigateTo: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val readiness         by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val loggedCalories    by homeViewModel.loggedCalories.collectAsStateWithLifecycle()
    val loggedProtein     by homeViewModel.loggedProtein.collectAsStateWithLifecycle()
    val loggedCarbs       by homeViewModel.loggedCarbs.collectAsStateWithLifecycle()
    val loggedFat         by homeViewModel.loggedFat.collectAsStateWithLifecycle()
    val macroTargetsState by homeViewModel.macroTargets.collectAsStateWithLifecycle()
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val completedSessions by homeViewModel.completedSessions.collectAsStateWithLifecycle(emptyList())
    val units             by homeViewModel.units.collectAsStateWithLifecycle()
    val userGoal          by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val heroCategory by homeViewModel.todayHeroCategory.collectAsStateWithLifecycle()
    val planSessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val todayExercises by homeViewModel.todayExercisesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSession by workoutExecutionViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val todayDate          by homeViewModel.todayDate.collectAsStateWithLifecycle()
    val estimatedWorkoutDuration by homeViewModel.estimatedWorkoutDuration.collectAsStateWithLifecycle()

    // Derive today's session from active plan
    val todayDayName = remember(todayDate) {
        LocalDate.parse(todayDate).dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH)
    }
    val todaySession = remember(planSessionsState, todayDayName) {
        when (val s = planSessionsState) {
            is UiState.Success -> s.data?.find { it.day.equals(todayDayName, ignoreCase = true) }
            else -> null
        }
    }
    val weeklySessionTarget = remember(planSessionsState) {
        when (val state = planSessionsState) {
            is UiState.Success -> state.data?.count { it.day.isNotBlank() } ?: 0
            else -> 0
        }
    }

    val macroTargets = remember(macroTargetsState) {
        when (val s = macroTargetsState) {
            is UiState.Success -> s.data
            else -> null
        }
    }

    val weightEntries = remember(weightHistoryState) {
        when (val s = weightHistoryState) {
            is UiState.Success -> s.data.sortedWith(com.apexfit.app.utils.BodyWeightHistory.uiChronological)
            else -> emptyList()
        }
    }

    val completedSessionsThisWeek = remember(completedSessions, todayDate) {
        currentWeekTrainingSessionCount(completedSessions, LocalDate.parse(todayDate))
    }

    var showLogWeight by rememberSaveable { mutableStateOf(false) }
    var isSavingWeight by rememberSaveable { mutableStateOf(false) }
    val weightLogError by homeViewModel.weightLogError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    TodayContent(
        heroCategory = heroCategory,
        todayDate = todayDate,
        sessionLabel = todaySession?.label,
        sessionFocus = todaySession?.focus.orEmpty(),
        scheduledExerciseCount = todayExercises.size,
        weeklySessionTarget = weeklySessionTarget,
        hasActiveSession = activeSession != null,
        estimatedWorkoutDuration = estimatedWorkoutDuration,
        readiness = readiness,
        loggedCalories = loggedCalories,
        loggedProtein = loggedProtein,
        loggedCarbs = loggedCarbs,
        loggedFat = loggedFat,
        macroTargets = macroTargets,
        weightEntries = weightEntries,
        units = units,
        userGoal = userGoal,
        completedSessions = completedSessions,
        completedSessionsThisWeek = completedSessionsThisWeek,
        onOpenSettings = onOpenSettings,
        onNutrition = { onNavigateTo(2) },
        onProgress = { onNavigateTo(3) },
        onLogWeight = {
            homeViewModel.clearWeightLogError()
            showLogWeight = true
        },
        onStartWorkout = {
            if (activeSession == null) todaySession?.let(workoutExecutionViewModel::startWorkoutSession)
            fitnessViewModel.setTrainSubTab(if (activeSession != null || todaySession != null) 1 else 0)
            onNavigateTo(1)
        }
    )
    if (showLogWeight) {
        LogWeightDialog(initialInput = "", units = units,
            onDismiss = { showLogWeight = false },
            saveError = weightLogError,
            isSaving = isSavingWeight,
            onSave = { weight ->
                if (!isSavingWeight) {
                    homeViewModel.clearWeightLogError()
                    isSavingWeight = true
                    coroutineScope.launch {
                        try {
                            val saveJob = homeViewModel.logWeight(weight, preferredUnit = units)
                                ?: return@launch
                            saveJob.join()
                            if (!saveJob.isCancelled && homeViewModel.weightLogError.value == null) {
                                showLogWeight = false
                            }
                        } finally {
                            isSavingWeight = false
                        }
                    }
                }
            })
    }
}

@Composable
internal fun TodayContent(
    heroCategory: SessionHeroCategory,
    todayDate: String,
    sessionLabel: String? = null,
    sessionFocus: String = "",
    scheduledExerciseCount: Int = 0,
    weeklySessionTarget: Int = 0,
    hasActiveSession: Boolean = false,
    estimatedWorkoutDuration: Int = 0,
    readiness: UiSessionReadiness? = null,
    loggedCalories: Int = 0,
    loggedProtein: Int = 0,
    loggedCarbs: Int = 0,
    loggedFat: Int = 0,
    macroTargets: NutritionTargets? = null,
    weightEntries: List<UiWeightEntry> = emptyList(),
    units: String = "kg",
    userGoal: String = "",
    completedSessions: List<RichTrainingSession> = emptyList(),
    completedSessionsThisWeek: Int = 0,
    onOpenSettings: () -> Unit,
    onNutrition: () -> Unit,
    onProgress: () -> Unit,
    onLogWeight: () -> Unit = onProgress,
    onStartWorkout: () -> Unit
) {
    var showReadinessExplanation by remember { mutableStateOf(false) }
    var showDurationExplanation by remember { mutableStateOf(false) }
    val isNarrowScreen = LocalConfiguration.current.screenWidthDp < 600

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasVoid),
        // Home-indicator clearance ≥ 16pt (plus navigation bar height clearance)
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            HomeTrainingHero(heroCategory, sessionLabel != null, todayDate, onOpenSettings)
        }

        // ── Today's training and logged details ────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = ApexSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
            ) {
                Spacer(Modifier.height(4.dp))

                TodayWorkoutCard(
                    sessionLabel = sessionLabel.orEmpty(),
                    sessionFocus = sessionFocus,
                    scheduledExerciseCount = scheduledExerciseCount,
                    weeklySessionTarget = weeklySessionTarget,
                    hasActiveSession = hasActiveSession,
                    estimatedDurationMin = estimatedWorkoutDuration,
                    onDurationInfo = { showDurationExplanation = true },
                    readiness = readiness,
                    completedThisWeek = completedSessionsThisWeek,
                    onReadinessInfo = { showReadinessExplanation = true },

                    onStartWorkout = onStartWorkout
                )

                // Nutrition and weight use logged values, never sample data.
                if (isNarrowScreen) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NutritionCard(
                            modifier       = Modifier.fillMaxWidth(),
                            loggedCalories = loggedCalories,
                            loggedProtein  = loggedProtein,
                            loggedCarbs    = loggedCarbs,
                            loggedFat      = loggedFat,
                            targets        = macroTargets,
                            onSeeDetails   = onNutrition
                        )
                        WeightTrendCard(
                            modifier = Modifier.fillMaxWidth(),
                            entries  = weightEntries,
                            units    = units,
                            userGoal = userGoal,
                            onView   = onLogWeight
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
                    ) {
                        NutritionCard(
                            modifier       = Modifier.weight(1f),
                            loggedCalories = loggedCalories,
                            loggedProtein  = loggedProtein,
                            loggedCarbs    = loggedCarbs,
                            loggedFat      = loggedFat,
                            targets        = macroTargets,
                            onSeeDetails   = onNutrition
                        )
                        WeightTrendCard(
                            modifier = Modifier.weight(1f),
                            entries  = weightEntries,
                            units    = units,
                            userGoal = userGoal,
                            onView   = onLogWeight
                        )
                    }
                }

                HorizontalDivider(color = BorderMuted)
                RecentSessionsSection(completedSessions.filter { it.completed }.sortedByDescending { it.date }.take(2), onSeeAll = onProgress)
            }
        }
    }

    if (showReadinessExplanation) {
        TodayReadinessExplanationDialog(
            readiness = readiness,
            onDismiss = { showReadinessExplanation = false }
        )
    }

    if (showDurationExplanation) {
        WorkoutDurationExplanationDialog(
            estimatedMinutes = estimatedWorkoutDuration,
            onDismiss = { showDurationExplanation = false }
        )
    }
}

// ─── Header Top Bar ──────────────────────────────────────────────────────────
// Rule 4: Apex chevron mark (18px) + "APEX FIT" 11px/700 tracking-wider #C6D2DB
@Composable
private fun HomeTrainingHero(
    category: SessionHeroCategory,
    hasScheduledSession: Boolean,
    date: String,
    onOpenSettings: () -> Unit
) {
    val photo = when (category) {
        SessionHeroCategory.UPPER_PULL -> R.drawable.today_upper_supported_row_13106582
        SessionHeroCategory.LOWER_QUAD -> R.drawable.today_lower_barbell_squat_38771191
        SessionHeroCategory.RECOVERY ->
            if (hasScheduledSession) null else R.drawable.today_recovery_band_mobility_20199285
        else -> null
    }
    val displayDate = remember(date) {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    Box(Modifier.fillMaxWidth().background(CanvasVoid)
        .heightIn(min = if (photo == null) 148.dp else 320.dp)) {
        if (photo != null) {
            Image(
                painter = painterResource(photo), contentDescription = null,
                contentScale = ContentScale.Crop, alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to CanvasVoid.copy(alpha = .55f),
                .35f to CanvasVoid.copy(alpha = .20f),
                .66f to CanvasVoid.copy(alpha = .24f),
                1f to CanvasVoid
            )))
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = ApexSpacing.screenPadding, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_apex_chevron), null,
                    tint = MineralBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("APEX FIT", color = PrimaryText, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(ApexIcons.Settings), "Settings", tint = PrimaryText)
                }
            }
            Text("Today", color = PrimaryText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(displayDate, color = PrimaryText, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        }
    }
}

@Composable
private fun TodayWorkoutCard(
    sessionLabel: String,
    sessionFocus: String,
    scheduledExerciseCount: Int,
    weeklySessionTarget: Int,
    hasActiveSession: Boolean,
    estimatedDurationMin: Int,
    onDurationInfo: () -> Unit,
    readiness: UiSessionReadiness?,
    completedThisWeek: Int,
    onReadinessInfo: () -> Unit,
    onStartWorkout: () -> Unit
) {
    val hasSession = sessionLabel.isNotBlank()
    val exerciseCountLabel = todayExerciseCountLabel(scheduledExerciseCount)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasSession) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    sessionLabel,
                    modifier = Modifier.weight(1f),
                    color = PrimaryText,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                "No session scheduled today",
                color = PrimaryText,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(if (hasSession) sessionFocus else "Take a recovery day, or choose a workout from your plan.",
            color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
        if (hasSession) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (estimatedDurationMin > 0) {
                        Text(
                            "~$estimatedDurationMin min",
                            modifier = Modifier.heightIn(min = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Estimated workout time",
                                    onClick = onDurationInfo
                                )
                                .wrapContentHeight(Alignment.CenterVertically),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                    }
                    if (estimatedDurationMin > 0 && exerciseCountLabel != null) {
                        Text("·", color = TextMuted, fontSize = 13.sp)
                    }
                    exerciseCountLabel?.let { label ->
                        Text(label, color = TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
                Row(
                    modifier = Modifier.heightIn(min = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "About the readiness estimate",
                            onClick = onReadinessInfo
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (readiness == null) {
                        Text("Building readiness baseline", color = TextMuted, fontSize = 13.sp)
                    } else if (readiness.label.contains("baseline", ignoreCase = true)) {
                        Text(readiness.label, color = TextMuted, fontSize = 13.sp)
                    } else {
                        Text(
                            readiness.score.coerceIn(0, 100).toString(),
                            color = MineralBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val readinessLabel = if (readiness.label.contains("estimate", ignoreCase = true)) {
                            readiness.label
                        } else {
                            "${readiness.label} estimate"
                        }
                        Text(readinessLabel, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (hasSession) {
            Text(
                todayTrainingWeekSummary(completedThisWeek, weeklySessionTarget),
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        } else {
            Text(
                todayTrainingWeekSummary(completedThisWeek, weeklySessionTarget),
                color = TextMuted, fontSize = 12.sp
            )
        }
        Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MineralBlue, contentColor = ApexColors.OnGlow)) {
            Text(if (hasActiveSession) "Resume workout" else if (hasSession) "Start workout" else "View training plan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun NutritionCard(
    modifier:        Modifier,
    loggedCalories:  Int,
    loggedProtein:   Int,
    loggedCarbs:     Int,
    loggedFat:       Int,
    targets:         NutritionTargets?,
    onSeeDetails:    () -> Unit
) {
    val calLogged = loggedCalories.coerceAtLeast(0)
    val calTarget = targets?.calories?.takeIf { it > 0 } ?: 0
    val targetProtein = targets?.protein?.takeIf { it > 0 } ?: 0
    val targetCarbs = targets?.carbs?.takeIf { it > 0 } ?: 0
    val targetFat = targets?.fat?.takeIf { it > 0 } ?: 0
    val dispProtein = loggedProtein.coerceAtLeast(0)
    val dispCarbs = loggedCarbs.coerceAtLeast(0)
    val dispFat = loggedFat.coerceAtLeast(0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = BorderMuted)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Nutrition", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = PrimaryText)
            TextButton(onClick = onSeeDetails) { Text("Details →", color = TextMuted) }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("%,d".format(calLogged), style = ApexTextStyles.dataMedium.copy(fontSize = 22.sp),
                    color = PrimaryText)
                Text(
                    if (calTarget > 0) "/ %,d kcal".format(calTarget) else "kcal logged",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            if (calTarget > 0) {
                val remaining = calTarget - calLogged
                Text(
                    if (remaining >= 0) "%,d remaining".format(remaining)
                    else "%,d over".format(-remaining),
                    color = TextMuted, fontSize = 12.sp
                )
            }
        }
        if (calTarget > 0) {
            LinearProgressIndicator(
                progress = { (calLogged.toFloat() / calTarget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MineralBlue,
                trackColor = SurfaceNight2,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        TodayMacroRow("Protein", dispProtein, targetProtein)
        TodayMacroRow("Carbs", dispCarbs, targetCarbs)
        TodayMacroRow("Fat", dispFat, targetFat)
    }
}

@Composable
private fun TodayMacroRow(label: String, logged: Int, target: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(
            if (target > 0) "$logged / $target g" else "$logged g",
            style = ApexTextStyles.dataMedium.copy(fontSize = 13.sp),
            color = PrimaryText
        )
    }
}

@Composable
private fun WeightTrendCard(modifier: Modifier, entries: List<UiWeightEntry>, units: String,
    userGoal: String, onView: () -> Unit) {
    val history = com.apexfit.app.utils.BodyWeightHistory.dailyUi(entries).takeLast(7)
    val latest = entries.maxWithOrNull(com.apexfit.app.utils.BodyWeightHistory.uiChronological)
    val imperial = units.lowercase() in listOf("lb", "lbs")
    val multiplier = if (imperial) com.apexfit.app.utils.AppConstants.KG_TO_LBS else 1.0
    val unit = if (imperial) "lb" else "kg"
    Column(modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = BorderMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Weight", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onView) { Text("Log weight", color = MineralBlue) }
        }
        if (history.isEmpty()) {
            Text("Add your first weigh-in", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Your logged weight will appear here.", color = TextMuted, fontSize = 13.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(String.format(Locale.getDefault(), "%.1f %s", (latest?.weight ?: history.last().weight) * multiplier, unit),
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (history.size > 1) {
                    val change = (history.last().weight - history.first().weight) * multiplier
                    Text(String.format(Locale.getDefault(), "%+.1f %s across daily averages", change, unit),
                        color = TextMuted, fontSize = 13.sp)
                } else Text("One measurement day recorded.", color = TextMuted, fontSize = 13.sp)
            }

            val datedWeighIns = history.map { it.date }.distinct().size
            if (datedWeighIns >= 3) {
                WeightSparklineSingleDot(history, Modifier.fillMaxWidth().height(54.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(history.first().date, color = TextMuted, fontSize = 11.sp)
                    Text(history.last().date, color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WeightSparklineSingleDot(entries: List<UiWeightEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle guide lines
        drawLine(
            color = BorderMuted.copy(alpha = 0.5f),
            start = Offset(0f, h * 0.35f),
            end = Offset(w, h * 0.35f),
            strokeWidth = 1f
        )
        drawLine(
            color = BorderMuted.copy(alpha = 0.5f),
            start = Offset(0f, h * 0.75f),
            end = Offset(w, h * 0.75f),
            strokeWidth = 1f
        )

        val points = if (entries.size >= 2) {
            val minW = entries.minOf { it.weight.toFloat() }
            val maxW = entries.maxOf { it.weight.toFloat() }
            val range = (maxW - minW).coerceAtLeast(0.6f)
            entries.mapIndexed { idx, e ->
                val x = idx * (w / (entries.size - 1))
                val y = h * 0.15f + h * 0.70f * (1f - (e.weight.toFloat() - minW) / range)
                Offset(x, y)
            }
        } else {
            listOf(Offset(w / 2f, h / 2f))
        }

        // Draw mint line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = MineralBlue,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Rule 12: ONE dot (latest, 6px = 3dp radius, white 1px ring)
        if (points.isNotEmpty()) {
            val latestPt = points.last()
            // 1px white ring
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = latestPt,
                style = Stroke(width = 1.dp.toPx())
            )
            // 6px mint dot
            drawCircle(
                color = MineralBlue,
                radius = 3.dp.toPx(),
                center = latestPt
            )
        }
    }
}

// ─── Recent Sessions ──────────────────────────────────────────────────────────
// Recent-session metadata must be derived from persisted session data only.
// Never insert placeholder duration, exercise counts, or PR counts.
// "See all" keeps a full-size touch target.

@Composable
private fun RecentSessionsSection(sessions: List<RichTrainingSession>, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ApexSpacing.sm)) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent training",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Rule 14: "See all" → #8EA0AE, 44pt hit area
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .clickable(role = Role.Button, onClick = onSeeAll),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "See all",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Icon(
                        painter = painterResource(ApexIcons.ChevronRight),
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (sessions.isNotEmpty()) {
            sessions.forEach { session ->
                SessionRow(session = session)
            }
        } else {
            Text("Your completed workouts will appear here.", color = TextMuted,
                fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun SessionRow(session: RichTrainingSession) {
    val metadata = buildList {
        add(formatSessionDate(session.date))
        if (session.durationMinutes > 0) add("${session.durationMinutes} min")
        if (session.exercises.isNotEmpty()) {
            val count = session.exercises.size
            add("$count exercise${if (count == 1) "" else "s"}")
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(session.sessionType.ifBlank { "Workout" }, style = MaterialTheme.typography.titleMedium,
            color = PrimaryText)
        Text(metadata.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
    HorizontalDivider(color = BorderMuted)
}

@Composable
internal fun TodayReadinessExplanationDialog(
    readiness: UiSessionReadiness?,
    onDismiss: () -> Unit
) {
    TodayExplanationSheet(title = "Readiness estimate", onDismiss = onDismiss) {
        if (readiness == null) {
            Text(
                "Log training and nutrition to build a readiness estimate. Until then, use how you feel today to guide your effort.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        } else {
            Text(
                readiness.recommendation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            readiness.confidence?.let {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Training history", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What influenced it", style = MaterialTheme.typography.titleSmall, color = PrimaryText)
                Text(
                    "Recent muscle training, overall training load, and logged nutrition. The model weights and recovery timing are estimates, not calibrated measurements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            readiness.factors.forEachIndexed { index, factor ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            factor.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            factor.value,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryText,
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < readiness.factors.lastIndex) {
                        HorizontalDivider(color = BorderMuted)
                    }
                }
            }
            if (readiness.prediction.isNotBlank()) {
                Text(readiness.prediction, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            Text(
                "This is not a measurement of recovery. Consider sleep, stress, soreness, symptoms, and how you feel before choosing your effort.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
internal fun WorkoutDurationExplanationDialog(
    estimatedMinutes: Int,
    onDismiss: () -> Unit
) {
    TodayExplanationSheet(title = "Workout time estimate", onDismiss = onDismiss) {
        if (estimatedMinutes > 0) {
            Text("~$estimatedMinutes min", style = MaterialTheme.typography.headlineSmall, color = PrimaryText)
        }
        Text(
            "Estimated from your planned sets, rep targets, and rest periods. Actual time can vary.",
            style = MaterialTheme.typography.bodyLarge,
            color = PrimaryText
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Not included", style = MaterialTheme.typography.titleSmall, color = PrimaryText)
            Text(
                "Equipment waiting, walking between stations, plate changes, interruptions, and breaks.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayExplanationSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.84f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setGravity(Gravity.BOTTOM)
            dialogWindow?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            dialogWindow?.setDimAmount(0.38f)
        }
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxSheetHeight),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = ApexColors.Surface,
            contentColor = ApexColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = PrimaryText)
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = ApexColors.TextSecondary)
                    }
                }
                content()
            }
        }
    }
}

private fun formatSessionDate(dateStr: String): String = runCatching {
    val date = LocalDate.parse(dateStr)
    val day = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
    val month = date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
    "$day ${date.dayOfMonth} $month"
}.getOrElse { dateStr }
