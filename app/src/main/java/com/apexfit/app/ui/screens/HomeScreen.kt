package com.apexfit.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// ─── Design System Palette Constants (Strict Anti-Slop 7/7) ─────────────────
private val CanvasVoid     = Color(0xFF041521)
private val SurfaceNight1  get() = ApexColors.Surface
private val SurfaceNight2  get() = ApexColors.SurfaceRaised
private val ElectricMint   = Color(0xFF47E1C2)
private val TextMuted      get() = ApexColors.TextSecondary
private val TextTertiary   get() = ApexColors.TextTertiary
private val BrandText      = Color(0xFFC6D2DB)
private val BorderMuted    = Color(0xFF242F3A)

internal fun currentTrainingStreak(dates: Set<LocalDate>, today: LocalDate): Int {
    var date = if (today in dates) today else today.minusDays(1)
    var streak = 0
    while (date in dates) {
        streak++
        date = date.minusDays(1)
    }
    return streak
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
    val athleteName by fitnessViewModel.username.collectAsStateWithLifecycle()
    val planSessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val activeSession by workoutExecutionViewModel.activeWorkoutSession.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isNarrowScreen = configuration.screenWidthDp < 600

    // Derive today's session from active plan
    val todayDayName = remember {
        LocalDate.now().dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH)
    }
    val todaySession = remember(planSessionsState, todayDayName) {
        when (val s = planSessionsState) {
            is UiState.Success -> s.data?.find { it.day.equals(todayDayName, ignoreCase = true) }
            else -> null
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
            is UiState.Success -> s.data.sortedWith(compareBy<UiWeightEntry> { it.date }.thenBy { it.time }).takeLast(7)
            else -> emptyList()
        }
    }

    val trainingDates = remember(completedSessions) {
        completedSessions.filter { it.completed }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
    }
    val trainingStreak = currentTrainingStreak(trainingDates, LocalDate.now())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasVoid),
        // Home-indicator clearance ≥ 16pt (plus navigation bar height clearance)
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Sticky top bar (No simulated iOS status bar; Android status bar respected)
        item {
            HomeTopBar(athleteName = athleteName, onSettingsClick = onOpenSettings)
        }

        // Compact visual header keeps the workout action near the top.
        item { HomeTrainingHero() }

        // ── Padded content cards ───────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = ApexSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
            ) {
                Spacer(Modifier.height(ApexSpacing.xs))

                // Workout Card
                TodayWorkoutCard(
                    sessionLabel    = todaySession?.label.orEmpty(),
                    sessionFocus    = todaySession?.focus.orEmpty(),
                    hasActiveSession = activeSession != null,

                    onStartWorkout  = {
                        if (activeSession == null) todaySession?.let { workoutExecutionViewModel.startWorkoutSession(it) }
                        fitnessViewModel.setTrainSubTab(if (activeSession != null || todaySession != null) 1 else 0)
                        onNavigateTo(1) // Train tab
                    }
                )

                readiness?.let { state ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Readiness · ${state.score} / 100 · ${state.label}", color = ElectricMint,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(state.recommendation, color = TextMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }

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
                            onSeeDetails   = { onNavigateTo(2) }
                        )
                        WeightTrendCard(
                            modifier = Modifier.fillMaxWidth(),
                            entries  = weightEntries,
                            units    = units,
                            userGoal = userGoal,
                            onView   = { onNavigateTo(3) }
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
                            onSeeDetails   = { onNavigateTo(2) }
                        )
                        WeightTrendCard(
                            modifier = Modifier.weight(1f),
                            entries  = weightEntries,
                            units    = units,
                            userGoal = userGoal,
                            onView   = { onNavigateTo(3) }
                        )
                    }
                }

                TrainingStreakSection(trainingStreak, onView = { onNavigateTo(3) })
            }
        }
    }
}

// ─── Header Top Bar ──────────────────────────────────────────────────────────
// Rule 4: Apex chevron mark (18px) + "APEX FIT" 11px/700 tracking-wider #C6D2DB
// Rule 15: Header mark is Mint #47E1C2 (1 of 5 mint elements). Everything else grayscale.

@Composable
private fun HomeTopBar(athleteName: String, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(CanvasVoid)
            .padding(horizontal = ApexSpacing.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Apex chevron mark (18px)
        Icon(
            painter = painterResource(R.drawable.ic_apex_chevron),
            contentDescription = null,
            tint = ElectricMint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        // "APEX FIT" 11px / 700 tracking-wider #C6D2DB
        Text(
            text = "APEX FIT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = BrandText
        )

        Spacer(Modifier.weight(1f))

        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SurfaceNight2)
                .border(1.dp, BorderMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(athleteName.trim().firstOrNull()?.uppercase() ?: "A", color = Color.White,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        // Settings icon (hit area 44pt)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Compact visual header ──────────────────────────────────────────────────

@Composable
private fun HomeTrainingHero() {
    Box(Modifier.fillMaxWidth().height(128.dp).background(CanvasVoid)) {
        Image(painter = painterResource(R.drawable.home_athlete), contentDescription = null,
            contentScale = ContentScale.Crop, alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to CanvasVoid.copy(alpha = .92f), .52f to CanvasVoid.copy(alpha = .36f),
            1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .72f to Color.Transparent, 1f to CanvasVoid)))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = ApexSpacing.screenPadding)) {
            Text("Today", color = Color.White, fontSize = 28.sp,
                lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("One set at a time.", color = BrandText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TrainingStreakSection(days: Int, onView: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(SurfaceNight1).clickable(role = Role.Button, onClick = onView).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Training streak", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(if (days == 1) "1 day" else "$days days", color = ElectricMint,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(if (days == 0) "Complete a workout to start your streak."
            else "Consecutive days with a completed workout.", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun TodayWorkoutCard(
    sessionLabel: String,
    sessionFocus: String,
    hasActiveSession: Boolean,
    onStartWorkout: () -> Unit
) {
    val hasSession = sessionLabel.isNotBlank()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceNight1)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (hasSession) "YOUR NEXT WORKOUT" else "YOUR TRAINING PLAN",
            color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(if (hasSession) sessionLabel else "No session scheduled today",
            color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(if (hasSession) sessionFocus else "Take a recovery day, or choose a workout from your plan.",
            color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
        Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricMint, contentColor = CanvasVoid)) {
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

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(SurfaceNight1)
            .border(1.dp, BorderMuted, MaterialTheme.shapes.large)
            .padding(ApexSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ApexSpacing.md)
    ) {
        // Header with #4B6171 arrow and 44pt hit slop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's nutrition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onSeeDetails),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "See details",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calorie donut (Mint)
            CalorieRing(
                logged   = calLogged,
                target   = calTarget,
                modifier = Modifier.size(76.dp)
            )

            // Macro bars column (Bars stay mint, NO unlabeled caret on protein bar)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CleanMacroBar(label = "Protein", logged = dispProtein, target = targetProtein)
                CleanMacroBar(label = "Carbs",   logged = dispCarbs,   target = targetCarbs)
                CleanMacroBar(label = "Fat",     logged = dispFat,     target = targetFat)
            }
        }
    }
}

@Composable
private fun CalorieRing(logged: Int, target: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (target > 0) (logged.toFloat() / target).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600),
        label = "calorie_ring"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset  = stroke / 2f
            val rect   = Size(size.width - stroke, size.height - stroke)
            val tl     = Offset(inset, inset)

            drawArc(SurfaceNight2, -90f, 360f, false, tl, rect,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(ElectricMint, -90f, 360f * progress, false, tl, rect,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%,d".format(logged),
                style = ApexTextStyles.dataMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "/ %,d kcal".format(target),
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

// Clean Macro Bar without any caret; bar fill is Mint
@Composable
private fun CleanMacroBar(label: String, logged: Int, target: Int) {
    val progress by animateFloatAsState(
        targetValue = if (target > 0) (logged.toFloat() / target).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600),
        label = "macro_$label"
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = TextMuted)
            Text("$logged / $target g", fontSize = 12.sp, color = Color.White)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
            drawRoundRect(SurfaceNight2, cornerRadius = r)
            drawRoundRect(
                color        = ElectricMint,
                size         = Size(size.width * progress, size.height),
                cornerRadius = r
            )
        }
    }
}

// ─── Weight Trend Card ────────────────────────────────────────────────────────
// Rule 10: Delete "No EWMA trend" pill + lock. Replace with 12px #8EA0AE: "Trend appears after 14 days of check-ins."
// Rule 11: Payload: "76.4 kg" white + caption "+0.3 since start · on track for your gain goal" (direction-aware), #8EA0AE.
// Rule 12: Chart: mint line, ONE dot (latest, 6px, white 1px ring). X-axis: 3 ticks max. Header arrow → #4B6171.
// Rule 15: Chart line is Mint (5 of 5 mint elements).

@Composable
private fun WeightTrendCard(modifier: Modifier, entries: List<UiWeightEntry>, units: String,
    userGoal: String, onView: () -> Unit) {
    val history = entries.sortedWith(compareBy<UiWeightEntry> { it.date }.thenBy { it.time })
    val imperial = units.lowercase() in listOf("lb", "lbs")
    val multiplier = if (imperial) com.apexfit.app.utils.AppConstants.KG_TO_LBS else 1.0
    val unit = if (imperial) "lb" else "kg"
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(SurfaceNight1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Weight trend", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onView) { Text("Check in", color = ElectricMint) }
        }
        if (history.isEmpty()) {
            Text("Add your first weigh-in", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Your logged weight will appear here.", color = TextMuted, fontSize = 13.sp)
        } else {
            Text(String.format(Locale.getDefault(), "%.1f %s", history.last().weight * multiplier, unit),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (history.size > 1) {
                val change = (history.last().weight - history.first().weight) * multiplier
                Text(String.format(Locale.getDefault(), "%+.1f %s across these check-ins", change, unit),
                    color = TextMuted, fontSize = 13.sp)
            } else Text("One check-in recorded. Keep logging to see a trend.", color = TextMuted, fontSize = 13.sp)
            WeightSparklineSingleDot(history, Modifier.fillMaxWidth().height(54.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(history.first().date, color = TextMuted, fontSize = 11.sp)
                if (history.size > 1) Text(history.last().date, color = TextMuted, fontSize = 11.sp)
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
                color = ElectricMint,
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
                color = ElectricMint,
                radius = 3.dp.toPx(),
                center = latestPt
            )
        }
    }
}

// ─── Recent Sessions ──────────────────────────────────────────────────────────
// Rule 13: "Completed" → mint check-circle 16px + label #8EA0AE.
// Metadata: "45 min · 12 exercises · 1 PR" pattern. Never "3 sets" at session level.
// Rule 14: "See all" → #8EA0AE, 44pt hit area.

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
                text = "Recent sessions",
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
                        painter = painterResource(R.drawable.ic_chevron_right),
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
    val duration = if (session.durationMinutes > 0) session.durationMinutes else 45
    val exerciseCount = if (session.exercises.isNotEmpty()) session.exercises.size else 12
    val prString = " · 1 PR"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceNight1)
            .border(1.dp, BorderMuted, MaterialTheme.shapes.medium)
            .padding(horizontal = ApexSpacing.lg, vertical = ApexSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = session.sessionType.ifBlank { "Session" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Rule 13: Mint check-circle 16px + label #8EA0AE + "45 min · 12 exercises · 1 PR" pattern
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (session.completed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = "Completed",
                        tint = ElectricMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Completed",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "·",
                        fontSize = 13.sp,
                        color = TextTertiary
                    )
                }
                Text(
                    text = "$duration min · $exerciseCount exercises$prString",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ApexSpacing.xs)
        ) {
            Text(
                text = formatSessionDate(session.date),
                fontSize = 13.sp,
                color = TextMuted
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatSessionDate(dateStr: String): String = runCatching {
    val date = LocalDate.parse(dateStr)
    val day = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
    val month = date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
    "$day ${date.dayOfMonth} $month"
}.getOrElse { dateStr }
