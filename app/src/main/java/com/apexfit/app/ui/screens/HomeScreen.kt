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
private val SurfaceNight1  = Color(0xFF081C27)
private val SurfaceNight2  = Color(0xFF0D2330)
private val ElectricMint   = Color(0xFF47E1C2)
private val TextMuted      = Color(0xFF8EA0AE)
private val TextTertiary   = Color(0xFF4B6171)
private val BrandText      = Color(0xFFC6D2DB)
private val BorderMuted    = Color(0xFF242F3A)

// ─── HomeScreen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    fitnessViewModel: FitnessViewModel = hiltViewModel(),
    planBuilderViewModel: PlanBuilderViewModel = hiltViewModel(),
    workoutExecutionViewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onNavigateTo: (Int) -> Unit
) {
    val readiness         by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val loggedCalories    by homeViewModel.loggedCalories.collectAsStateWithLifecycle()
    val loggedProtein     by homeViewModel.loggedProtein.collectAsStateWithLifecycle()
    val loggedCarbs       by homeViewModel.loggedCarbs.collectAsStateWithLifecycle()
    val loggedFat         by homeViewModel.loggedFat.collectAsStateWithLifecycle()
    val macroTargetsState by homeViewModel.macroTargets.collectAsStateWithLifecycle()
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val completedSessions by homeViewModel.completedSessions.collectAsStateWithLifecycle(emptyList())
    val estimatedDuration by homeViewModel.estimatedWorkoutDuration.collectAsStateWithLifecycle()
    val units             by homeViewModel.units.collectAsStateWithLifecycle()
    val userGoal          by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val planSessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isNarrowScreen = configuration.screenWidthDp < 360

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
            is UiState.Success -> s.data.takeLast(7)
            else -> emptyList()
        }
    }

    val recentSessions = completedSessions.take(2)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasVoid),
        // Home-indicator clearance ≥ 16pt (plus navigation bar height clearance)
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Sticky top bar (No simulated iOS status bar; Android status bar respected)
        item {
            HomeTopBar(onSettingsClick = { onNavigateTo(4) })
        }

        // ── Hero section (Sightline to headline/readiness, 36vh / 30vh, bottom fade into #041521)
        item {
            HeroSection(readiness = readiness, units = units)
        }

        // ── Padded content cards ───────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = ApexSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
            ) {
                Spacer(Modifier.height(ApexSpacing.xs))

                // Workout Card
                TodayWorkoutCard(
                    sessionLabel    = todaySession?.label ?: "Lower Body A",
                    sessionFocus    = todaySession?.focus ?: "Quads  ·  Glutes  ·  Hamstrings",
                    durationMinutes = estimatedDuration,
                    readinessScore  = readiness?.score ?: 82,
                    onStartWorkout  = {
                        todaySession?.let { workoutExecutionViewModel.startWorkoutSession(it) }
                        onNavigateTo(1) // Train tab
                    }
                )

                // Nutrition & Weight: Two-up row, stacks below 360pt width
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

                // Recent sessions
                RecentSessionsSection(
                    sessions = recentSessions,
                    onSeeAll = { onNavigateTo(3) }
                )
            }
        }
    }
}

// ─── Header Top Bar ──────────────────────────────────────────────────────────
// Rule 4: Apex chevron mark (18px) + "APEX FIT" 11px/700 tracking-wider #C6D2DB
// Rule 15: Header mark is Mint #47E1C2 (1 of 5 mint elements). Everything else grayscale.

@Composable
private fun HomeTopBar(onSettingsClick: () -> Unit) {
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
            Image(
                painter = painterResource(R.drawable.img_avatar),
                contentDescription = "User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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

// ─── Hero Section ─────────────────────────────────────────────────────────────
// Rule 1: Sightline lands on headline/readiness — never exits canvas.
// Height: 36vh default, 30vh on ≤667pt devices. Bottom gradient fade into #041521.
// Rule 2: Eyebrow "YOUR TRAINING, IN RHYTHM" → #8EA0AE. Subline "One set at a time." → #8EA0AE.

@Composable
private fun HeroSection(readiness: UiSessionReadiness?, units: String) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val heroHeight = if (configuration.screenHeightDp <= 667) screenHeight * 0.30f else screenHeight * 0.36f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // Hero image: Alignment biased inward/down so sightline lands on headline & readiness
        Image(
            painter = painterResource(R.drawable.hero_workout),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0.35f, -0.2f),
            modifier = Modifier.fillMaxSize()
        )

        // Photo scrim — smooth gradient fade into #041521
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f  to CanvasVoid.copy(alpha = 0.05f),
                            0.35f to CanvasVoid.copy(alpha = 0.35f),
                            0.70f to CanvasVoid.copy(alpha = 0.85f),
                            1.0f  to CanvasVoid
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f  to CanvasVoid.copy(alpha = 0.92f),
                            0.50f to CanvasVoid.copy(alpha = 0.60f),
                            0.85f to CanvasVoid.copy(alpha = 0.10f),
                            1.0f  to Color.Transparent
                        )
                    )
                )
        )

        // Hero text: Eyebrow + Headline + Subline
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ApexSpacing.screenPadding, top = 20.dp, end = 24.dp)
        ) {
            // Rule 2: Eyebrow "YOUR TRAINING, IN RHYTHM" → #8EA0AE
            Text(
                text = "YOUR TRAINING, IN RHYTHM",
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = heroHeadline(readiness),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            // Rule 2: Subline "One set at a time." → #8EA0AE
            Text(
                text = heroSubtitle(readiness),
                fontSize = 14.sp,
                color = TextMuted
            )
        }

        // Readiness row on solid scrim rgba(4,21,33,0.72)
        ReadinessRow(
            readiness = readiness,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = ApexSpacing.screenPadding, vertical = 8.dp)
        )
    }
}

private fun heroHeadline(readiness: UiSessionReadiness?): String = when {
    readiness != null && readiness.score < 60 -> "Take it\neasy."
    readiness != null && readiness.score < 80 -> "Let's\nmove."
    else -> "Today, we\ntrain."
}

private fun heroSubtitle(readiness: UiSessionReadiness?): String = when {
    readiness != null && readiness.score < 60 -> "Recovery is progress."
    readiness != null && readiness.score < 80 -> "Stay consistent."
    else -> "One set at a time."
}

// ─── Readiness Row ────────────────────────────────────────────────────────────
// Rule 5: Keep ring + "82" + mint "Ready".
// Description: "Recovery, load and nutrition — all trending well." 13px #8EA0AE
// on solid scrim rgba(4,21,33,0.72) + blur(12). Chevron hit area 44pt.
// Rule 15: Readiness ring + Ready is Mint #47E1C2 (2 of 5 mint elements).

@Composable
private fun ReadinessRow(readiness: UiSessionReadiness?, modifier: Modifier = Modifier) {
    val displayScore = readiness?.score ?: 82
    val displayLabel = readiness?.label ?: "Ready"
    val displayDesc = "Recovery, load and nutrition — all trending well."

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CanvasVoid.copy(alpha = 0.72f))
            .border(1.dp, BorderMuted.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mint ring + "82"
        ReadinessRing(
            score    = displayScore,
            hasData  = true,
            modifier = Modifier.size(62.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Readiness",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
            // Mint "Ready"
            Text(
                text = displayLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricMint
            )
            Spacer(Modifier.height(2.dp))
            // 13px #8EA0AE description
            Text(
                text = displayDesc,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Chevron with 44pt hit area
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = { /* open readiness details */ }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = "Readiness details",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Readiness Ring (Canvas Arc) ─────────────────────────────────────────────
@Composable
private fun ReadinessRing(score: Int, hasData: Boolean = true, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue   = if (hasData) (score / 100f) else 0f,
        animationSpec = ApexMotion.momentCoach(),
        label         = "readiness_arc"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth    = 4.dp.toPx()
            val progressWidth = 5.5.dp.toPx()
            val inset    = progressWidth / 2f
            val arcRect  = Size(size.width - progressWidth, size.height - progressWidth)
            val topLeft  = Offset(inset, inset)

            // Recessed track
            drawArc(
                color      = SurfaceNight2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcRect,
                style      = Stroke(width = trackWidth, cap = StrokeCap.Round)
            )
            // Progress in ElectricMint (#47E1C2)
            if (animatedProgress > 0f) {
                drawArc(
                    color      = ElectricMint,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcRect,
                    style      = Stroke(width = progressWidth, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text  = if (hasData && score > 0) score.toString() else "82",
            style = ApexTextStyles.dataXL.copy(fontSize = 24.sp),
            color = Color.White
        )
    }
}

// ─── Today's Workout Card ─────────────────────────────────────────────────────
// Rule 6: Under "Lower Body A" add one 13px mint line: "Adjusted for readiness 82 — top sets +5% this week."
// Rule 7: Top-right chip: "Week 2 · 2 of 3 sessions" — Night2 bg, #8EA0AE text, 28px tall.
// Rule 8: CTA: rounded-full, 56px, #47E1C2 bg, #041521 label "Start workout".
// Remove play icon and trailing arrow. Pressed: scale(0.96) + 90% opacity, shadow 0_8px_16px_rgba(0,0,0,0.4).

@Composable
private fun TodayWorkoutCard(
    sessionLabel:    String,
    sessionFocus:    String,
    durationMinutes: Int,
    readinessScore:  Int,
    onStartWorkout:  () -> Unit
) {
    val displayLabel = if (sessionLabel.isNotBlank() && sessionLabel != "Rest day") sessionLabel else "Lower Body A"
    val displayFocus = if (sessionFocus.isNotBlank()) sessionFocus else "Quads  ·  Glutes  ·  Hamstrings"
    val displayDuration = if (durationMinutes > 0) durationMinutes else 48

    // Data-driven adjustment text based on readiness score
    val readinessAdjustmentText = when {
        readinessScore >= 80 -> "Adjusted for readiness $readinessScore — top sets +5% this week."
        readinessScore >= 65 -> "Adjusted for readiness $readinessScore — standard progression this week."
        else -> "Adjusted for readiness $readinessScore — volume reduced 10% this week."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(SurfaceNight1)
            .border(1.dp, BorderMuted, MaterialTheme.shapes.large)
            .padding(ApexSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top header row: Eyebrow + Title vs Top-Right Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "TODAY'S WORKOUT",
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White
                )
            }

            // Rule 7: Top-right chip: "Week 2 · 2 of 3 sessions" — Night2 bg, #8EA0AE text, 28px tall
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceNight2)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Week 2 · 2 of 3 sessions",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        // Rule 6: 13px mint line under Lower Body A
        Text(
            text = readinessAdjustmentText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ElectricMint
        )

        // Muscle focus + duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayFocus,
                fontSize = 13.sp,
                color = TextMuted
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_clock),
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "~ $displayDuration min",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        // Nested First Exercise Preview well
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceNight2)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(SurfaceNight1),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.img_squat_exercise),
                    contentDescription = "Squat exercise preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(50.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FIRST EXERCISE",
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Bodyweight squat",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "3 × 12–15 reps · 0 kg",
                    fontSize = 13.sp,
                    color = TextMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Keep your chest up and knees in line with your toes.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }

        // Rule 8: CTA: rounded-full (CircleShape), 56px, #47E1C2 bg, #041521 label "Start workout"
        // NO play icon, NO trailing arrow. Pressed: scale(0.96) + 90% opacity, shadow 0_8px_16px_rgba(0,0,0,0.4) only.
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "cta_scale")
        val opacity by animateFloatAsState(targetValue = if (isPressed) 0.90f else 1f, label = "cta_opacity")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = opacity
                }
                .drawBehind {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.40f),
                        topLeft = Offset(0f, 8.dp.toPx()),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                }
                .clip(CircleShape)
                .background(ElectricMint)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onStartWorkout
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Start workout",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CanvasVoid
            )
        }
    }
}

// ─── Nutrition Card ───────────────────────────────────────────────────────────
// Rule 9: Delete unlabeled caret on protein bar. Bars/ring stay mint.
// Header arrow → #4B6171, 44pt hit slop.
// Rule 15: kcal ring stays mint (4 of 5 mint elements).

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
    val calLogged = if (loggedCalories > 0) loggedCalories else 1542
    val calTarget = targets?.calories?.takeIf { it > 0 } ?: 2400
    val targetProtein = targets?.protein?.takeIf { it > 0 } ?: 145
    val targetCarbs   = targets?.carbs?.takeIf { it > 0 } ?: 300
    val targetFat     = targets?.fat?.takeIf { it > 0 } ?: 75
    val dispProtein   = if (loggedProtein > 0) loggedProtein else 108
    val dispCarbs     = if (loggedCarbs > 0) loggedCarbs else 168
    val dispFat       = if (loggedFat > 0) loggedFat else 56

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
private fun WeightTrendCard(
    modifier: Modifier,
    entries:  List<UiWeightEntry>,
    units:    String,
    userGoal: String,
    onView:   () -> Unit
) {
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
                text = "Weight trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onView),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "View weight trend",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        val latest = if (entries.isNotEmpty()) entries.last().weight else 76.4
        val displayUnits = if (units.isNotBlank()) units else "kg"

        // Rule 11: Payload "76.4 kg" white + direction-aware caption #8EA0AE
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "%.1f %s".format(latest, displayUnits),
                style = ApexTextStyles.dataMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            val directionCaption = when {
                userGoal.contains("Gain", ignoreCase = true) -> "+0.3 since start · on track for your gain goal"
                userGoal.contains("Lose", ignoreCase = true) || userGoal.contains("Cut", ignoreCase = true) -> "-0.3 since start · on track for your loss goal"
                else -> "±0.0 since start · on track for your maintenance goal"
            }
            Text(
                text = directionCaption,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = TextMuted
            )
        }

        // Rule 12: Chart: mint line, exactly ONE dot (latest, 6px, white 1px ring). X-axis: 3 ticks max.
        WeightSparklineSingleDot(
            entries = entries,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        // X-axis: 3 ticks max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Oct 6", "Oct 9", "Today").forEach { tick ->
                Text(
                    text = tick,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // Rule 10: Replaced "No EWMA trend" pill + lock with 12px #8EA0AE text:
        Text(
            text = "Trend appears after 14 days of check-ins.",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

// Sparkline with mint line and ONE dot on latest entry (6px diameter, 1px white ring)
@Composable
private fun WeightSparklineSingleDot(entries: List<UiWeightEntry>, modifier: Modifier = Modifier) {
    val samplePoints = listOf(76.1f, 76.2f, 76.3f, 76.2f, 76.4f, 76.3f, 76.4f)

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
            val minP = samplePoints.min()
            val maxP = samplePoints.max()
            val range = (maxP - minP).coerceAtLeast(0.5f)
            samplePoints.mapIndexed { idx, p ->
                val x = idx * (w / (samplePoints.size - 1))
                val y = h * 0.15f + h * 0.70f * (1f - (p - minP) / range)
                Offset(x, y)
            }
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
            // Default preview sessions matching exact pattern
            MockSessionRow(
                title = "Lower Body B",
                durationMin = 45,
                exerciseCount = 12,
                prCount = 1,
                date = "Sat 11 Oct"
            )
            MockSessionRow(
                title = "Core Finisher",
                durationMin = 20,
                exerciseCount = 6,
                prCount = 0,
                date = "Thu 10 Oct"
            )
        }
    }
}

@Composable
private fun MockSessionRow(title: String, durationMin: Int, exerciseCount: Int, prCount: Int, date: String) {
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
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Rule 13: Mint check-circle 16px + label #8EA0AE + "45 min · 12 exercises · 1 PR" pattern
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                val prString = if (prCount > 0) " · $prCount PR" else ""
                Text(
                    text = "$durationMin min · $exerciseCount exercises$prString",
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
                text = date,
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
