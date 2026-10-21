package com.apexfit.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.apexfit.app.HomeViewModel
import com.apexfit.app.PlanBuilderViewModel
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.R
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.RichTrainingSession
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.NutritionTargets
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

// ─── HomeScreen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    planBuilderViewModel: PlanBuilderViewModel = hiltViewModel(),
    workoutExecutionViewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onNavigateTo: (Int) -> Unit
) {
    val readiness        by homeViewModel.sessionReadiness.collectAsStateWithLifecycle()
    val loggedCalories   by homeViewModel.loggedCalories.collectAsStateWithLifecycle()
    val loggedProtein    by homeViewModel.loggedProtein.collectAsStateWithLifecycle()
    val loggedCarbs      by homeViewModel.loggedCarbs.collectAsStateWithLifecycle()
    val loggedFat        by homeViewModel.loggedFat.collectAsStateWithLifecycle()
    val macroTargetsState by homeViewModel.macroTargets.collectAsStateWithLifecycle()
    val weightHistoryState by homeViewModel.weightHistory.collectAsStateWithLifecycle()
    val completedSessions by homeViewModel.completedSessions.collectAsStateWithLifecycle(emptyList())
    val estimatedDuration by homeViewModel.estimatedWorkoutDuration.collectAsStateWithLifecycle()
    val units            by homeViewModel.units.collectAsStateWithLifecycle()
    val todayExercises   by homeViewModel.todayExercisesFlow.collectAsStateWithLifecycle(emptyList())
    val planSessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()

    // Derive today's session from the plan
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
            .background(ApexColors.Canvas),
        contentPadding = PaddingValues(bottom = ApexSpacing.xxxl)
    ) {
        // ── Sticky top bar ─────────────────────────────────────────────────
        item {
            HomeTopBar(onSettingsClick = { onNavigateTo(4) })
        }

        // ── Hero section ───────────────────────────────────────────────────
        item {
            HeroSection(readiness = readiness, units = units)
        }

        // ── Padded content ─────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.padding(horizontal = ApexSpacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
            ) {
                Spacer(Modifier.height(ApexSpacing.cardGap))

                // Today's workout card
                TodayWorkoutCard(
                    sessionLabel    = todaySession?.label ?: "Rest day",
                    sessionFocus    = todaySession?.focus ?: "",
                    durationMinutes = estimatedDuration,
                    exercises       = todayExercises,
                    hasSession      = todaySession != null,
                    onStartWorkout  = {
                        todaySession?.let { workoutExecutionViewModel.startWorkoutSession(it) }
                        onNavigateTo(1) // Train tab
                    }
                )

                // Nutrition + Weight side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ApexSpacing.cardGap)
                ) {
                    NutritionCard(
                        modifier        = Modifier.weight(1f),
                        loggedCalories  = loggedCalories,
                        loggedProtein   = loggedProtein,
                        loggedCarbs     = loggedCarbs,
                        loggedFat       = loggedFat,
                        targets         = macroTargets,
                        onSeeDetails    = { onNavigateTo(2) } // Nutrition tab
                    )
                    WeightTrendCard(
                        modifier      = Modifier.weight(1f),
                        entries       = weightEntries,
                        units         = units,
                        onView        = { onNavigateTo(3) } // Progress tab
                    )
                }

                // Recent sessions
                if (recentSessions.isNotEmpty()) {
                    RecentSessionsSection(
                        sessions = recentSessions,
                        onSeeAll = { onNavigateTo(3) }
                    )
                }
            }
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ApexColors.Canvas)
            .padding(horizontal = ApexSpacing.screenPadding, vertical = ApexSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo mark + wordmark
        Icon(
            painter = painterResource(R.drawable.ic_apex_logo),
            contentDescription = null,
            tint = ApexColors.Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(ApexSpacing.xs))
        Text(
            text = "APEX FIT",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = ApexColors.TextPrimary
        )

        Spacer(Modifier.weight(1f))

        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ApexColors.SurfaceRaised)
                .border(1.dp, ApexColors.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "U",
                style = MaterialTheme.typography.labelMedium,
                color = ApexColors.TextSecondary
            )
        }

        Spacer(Modifier.width(ApexSpacing.sm))

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = ApexColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Hero section ─────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(readiness: UiSessionReadiness?, units: String) {
    Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        // Hero image — AsyncImage with bundled placeholder fallback
        AsyncImage(
            model = R.drawable.hero_workout,        // provide hero_workout.jpg in drawable/
            placeholder = painterResource(R.drawable.hero_workout_placeholder),
            error = painterResource(R.drawable.hero_workout_placeholder),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Photo scrim — Night Signal v2.1: imagery bleeds into the void, never boxes itself in.
        // Two layers per mockup: bottom fades into Canvas (readiness legibility);
        // a left-side wash keeps the upper-left text column readable while the
        // subject on the right stays bright.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f  to ApexColors.Canvas.copy(alpha = 0f),
                            0.55f to ApexColors.Canvas.copy(alpha = 0.35f),
                            0.85f to ApexColors.Canvas.copy(alpha = 0.8f),
                            1.0f  to ApexColors.Canvas.copy(alpha = 0.96f)
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
                            0.0f  to ApexColors.Canvas.copy(alpha = 0.45f),
                            0.55f to ApexColors.Canvas.copy(alpha = 0.12f),
                            0.80f to ApexColors.Canvas.copy(alpha = 0f)
                        )
                    )
                )
        )

        // Hero text — upper-left of the photo, per mockup
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = ApexSpacing.screenPadding, top = 36.dp)
        ) {
            Text(
                text = "YOUR TRAINING, IN RHYTHM",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                color = ApexColors.TextSecondary
            )
            Spacer(Modifier.height(ApexSpacing.xs))
            Text(
                text = heroHeadline(readiness),
                style = MaterialTheme.typography.displayLarge,
                color = ApexColors.TextPrimary
            )
            Spacer(Modifier.height(ApexSpacing.xs))
            Text(
                text = heroSubtitle(readiness),
                style = MaterialTheme.typography.titleLarge,
                color = ApexColors.TextSecondary
            )
        }

        // Readiness — overlaps the hero's bottom edge on the scrim, no card (v2.1)
        ReadinessRow(
            readiness = readiness,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = ApexSpacing.screenPadding, vertical = ApexSpacing.md)
        )
    }
}

private fun heroHeadline(readiness: UiSessionReadiness?): String = when {
    readiness == null -> "Loading..."
    readiness.score >= 80 -> "Today, we\ntrain."
    readiness.score >= 60 -> "Let's\nmove."
    else -> "Take it\neasy."
}

private fun heroSubtitle(readiness: UiSessionReadiness?): String = when {
    readiness == null -> ""
    readiness.score >= 80 -> "One set at a time."
    readiness.score >= 60 -> "Stay consistent."
    else -> "Recovery is progress."
}

// ─── Readiness row ────────────────────────────────────────────────────────────

@Composable
private fun ReadinessRow(readiness: UiSessionReadiness?, modifier: Modifier = Modifier) {
    // v2.1: transparent overlay on the scrimmed hero bottom — no card, no border
    // (mockup: text + ring sit directly on the photo's fade, photo visible right)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ApexSpacing.lg)
    ) {
        ReadinessRing(
            score    = readiness?.score ?: 0,
            hasData  = readiness != null,
            modifier = Modifier.size(72.dp)  // mockup ring ~64-72dp, score only
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Readiness",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextSecondary
            )
            Text(
                text = readiness?.label ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = ApexColors.Primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = readiness?.prediction ?: "Calculating your readiness…",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ApexColors.TextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Readiness ring (Canvas arc) · v2 spec ────────────────────────────────────
// Anatomy: 4dp track (SurfaceSunken) + 6dp progress arc in band color
// Score: dataXL centered. Band label: labelMedium CAPS below score.
// Arc draws moment+Coach (420ms). Final frame: Victory scale-pulse 1.0→1.04→1.0.

@Composable
private fun ReadinessRing(score: Int, hasData: Boolean = true, modifier: Modifier = Modifier) {
    val bandColor = ApexColors.readinessBandColor(score, hasData)
    val animatedProgress by animateFloatAsState(
        targetValue   = if (hasData) (score / 100f) else 0f,
        animationSpec = ApexMotion.momentCoach(),
        label         = "readiness_arc"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth    = 4.dp.toPx()
            val progressWidth = 6.dp.toPx()
            val inset    = progressWidth / 2f
            val arcRect  = Size(size.width - progressWidth, size.height - progressWidth)
            val topLeft  = Offset(inset, inset)

            // Track — SurfaceSunken (recessed feel)
            drawArc(
                color      = ApexColors.SurfaceSunken,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcRect,
                style      = Stroke(width = trackWidth, cap = StrokeCap.Round)
            )
            // Progress — band color (Victory/Amber/Alert/Focus)
            if (animatedProgress > 0f) {
                drawArc(
                    color      = bandColor,
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
            text  = if (hasData && score > 0) score.toString() else "—",
            style = ApexTextStyles.dataXL,
            color = ApexColors.TextPrimary
        )
    }
}

// ─── Today's workout card ─────────────────────────────────────────────────────

@Composable
private fun TodayWorkoutCard(
    sessionLabel:    String,
    sessionFocus:    String,
    durationMinutes: Int,
    exercises:       List<PlanExercise>,
    hasSession:      Boolean,
    onStartWorkout:  () -> Unit
) {
    val firstExercise = exercises.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)    // 18dp
            .background(ApexColors.Surface)
            .padding(ApexSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ApexSpacing.md)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "TODAY'S WORKOUT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                    color = ApexColors.TextTertiary
                )
                Spacer(Modifier.height(ApexSpacing.xs))
                Text(
                    text = sessionLabel,
                    style = MaterialTheme.typography.headlineLarge,
                    color = ApexColors.TextPrimary
                )
            }
            if (durationMinutes > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = ApexSpacing.xs)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        tint = ApexColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "~$durationMinutes min",
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexColors.TextSecondary
                    )
                }
            }
        }

        // Muscle group chips
        if (sessionFocus.isNotBlank()) {
            MuscleTagRow(focus = sessionFocus)
        }

        // First exercise preview
        if (firstExercise != null) {
            ExercisePreviewRow(exercise = firstExercise)
        }

        // Start workout CTA — shapeSmall (12dp) per v2 button spec; dimmed on rest days
        val ctaBg = if (hasSession) ApexColors.Primary else ApexColors.SurfaceRaised
        val ctaFg = if (hasSession) ApexColors.OnGlow   else ApexColors.TextTertiary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(ctaBg)
                .clickable(enabled = hasSession, onClick = onStartWorkout)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ApexSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSession) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = ctaFg,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (hasSession) "Start workout" else "Rest day",
                    style = MaterialTheme.typography.labelLarge,
                    color = ctaFg
                )
                if (hasSession) {
                    Spacer(Modifier.width(ApexSpacing.xxl))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = ctaFg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Muscle tag row ───────────────────────────────────────────────────────────

@Composable
private fun MuscleTagRow(focus: String) {
    val muscles = focus.split(",", "·", "/").map { it.trim() }.filter { it.isNotBlank() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(ApexSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        muscles.forEachIndexed { i, muscle ->
            if (i > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexColors.TextTertiary
                )
            }
            Text(
                text = muscle,
                style = MaterialTheme.typography.bodyMedium,
                color = ApexColors.TextSecondary
            )
        }
    }
}

// ─── Exercise preview row ─────────────────────────────────────────────────────

@Composable
private fun ExercisePreviewRow(exercise: PlanExercise) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)   // 14dp
            .background(ApexColors.SurfaceSunken)
            .padding(ApexSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ApexSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exercise silhouette thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.small)   // 10dp
                .background(ApexColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(ApexAssets.silhouetteForMovement(exercise.muscleGroup)),
                contentDescription = null,
                tint = ApexColors.Primary.copy(alpha = 0.8f),
                modifier = Modifier.size(40.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FIRST EXERCISE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.8.sp,
                color = ApexColors.TextTertiary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleLarge,
                color = ApexColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val weightDisplay = if (exercise.weight == 0.0) "BW"
                               else "%.4g %s".format(exercise.weight, exercise.weightUnit)
            Text(
                text = "${exercise.sets} × ${exercise.repsMin}–${exercise.repsMax} reps  ·  $weightDisplay",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextSecondary
            )
            if (exercise.notes.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = exercise.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ApexColors.TextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Nutrition card ───────────────────────────────────────────────────────────

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
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(ApexColors.Surface)
            .padding(ApexSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ApexSpacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's nutrition",
                style = MaterialTheme.typography.titleMedium,
                color = ApexColors.TextPrimary
            )
            Text(
                text = "See details",
                style = MaterialTheme.typography.labelSmall,
                color = ApexColors.Primary,
                modifier = Modifier.clickable(onClick = onSeeDetails)
            )
        }

        // Calorie donut
        CalorieRing(
            logged  = loggedCalories,
            target  = targets?.calories ?: 2000,
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
        )

        // Macro bars
        val targetProtein = targets?.protein?.toInt() ?: 120
        val targetCarbs   = targets?.carbs?.toInt()   ?: 250
        val targetFat     = targets?.fat?.toInt()     ?: 70

        // v2.1: all three bars share the single glow (mockup-verified)
        MacroBar(label = "Protein", logged = loggedProtein, target = targetProtein, fillColor = ApexColors.Primary)
        MacroBar(label = "Carbs",   logged = loggedCarbs,   target = targetCarbs,   fillColor = ApexColors.Primary)
        MacroBar(label = "Fat",     logged = loggedFat,     target = targetFat,     fillColor = ApexColors.Primary)
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
            val stroke = 5.dp.toPx()
            val inset  = stroke / 2f
            val rect   = Size(size.width - stroke, size.height - stroke)
            val tl     = Offset(inset, inset)

            drawArc(ApexColors.SurfaceRaised, -90f, 360f, false, tl, rect,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(ApexColors.Primary, -90f, 360f * progress, false, tl, rect,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = logged.toString(),
                style = ApexTextStyles.dataMedium.copy(fontSize = 16.sp),
                color = ApexColors.TextPrimary
            )
            Text(
                text = "/ ${target} kcal",
                style = MaterialTheme.typography.labelSmall,
                color = ApexColors.TextTertiary,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, logged: Int, target: Int, fillColor: androidx.compose.ui.graphics.Color) {
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
            Text(label, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            Text("$logged / $target g", style = MaterialTheme.typography.bodySmall, color = ApexColors.TextTertiary)
        }
        // Track 8dp height, shapeFull, SurfaceSunken per §7.4
        Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            drawRoundRect(ApexColors.SurfaceSunken, cornerRadius = r)
            drawRoundRect(
                color        = fillColor,
                size         = Size(size.width * progress, size.height),
                cornerRadius = r
            )
        }
    }
}

// ─── Weight trend card ────────────────────────────────────────────────────────

@Composable
private fun WeightTrendCard(
    modifier:  Modifier,
    entries:   List<UiWeightEntry>,
    units:     String,
    onView:    () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(ApexColors.Surface)
            .padding(ApexSpacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ApexSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Weight trend", style = MaterialTheme.typography.titleMedium, color = ApexColors.TextPrimary)
            Text(
                text = "View",
                style = MaterialTheme.typography.labelSmall,
                color = ApexColors.Primary,
                modifier = Modifier.clickable(onClick = onView)
            )
        }

        if (entries.isNotEmpty()) {
            val latest = entries.last().weight
            val start  = entries.first().weight
            Row(horizontalArrangement = Arrangement.spacedBy(ApexSpacing.xl)) {
                Column {
                    Text(
                        text = "%.1f %s".format(latest, units),
                        style = ApexTextStyles.dataMedium.copy(fontSize = 18.sp),
                        color = ApexColors.TextPrimary
                    )
                    Text("Latest", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary)
                }
                Column {
                    Text(
                        text = "%.1f %s".format(start, units),
                        style = ApexTextStyles.dataMedium.copy(fontSize = 18.sp),
                        color = ApexColors.TextPrimary
                    )
                    Text("Start", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary)
                }
            }

            WeightSparkline(entries = entries, modifier = Modifier.fillMaxWidth().height(48.dp))

            // v2.1: date axis under the sparkline (mockup shows Oct 6-12 range)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val first = entries.first().date
                val mid   = entries[entries.size / 2].date
                val last  = entries.last().date
                Text(formatAxisDate(first), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = ApexColors.TextTertiary)
                Text(formatAxisDate(mid),   style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = ApexColors.TextTertiary)
                Text(formatAxisDate(last),  style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = ApexColors.TextTertiary)
            }
        } else {
            Text(
                text = "Log your weight to see trends",
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextTertiary
            )
        }
    }
}

@Composable
private fun WeightSparkline(entries: List<UiWeightEntry>, modifier: Modifier = Modifier) {
    val primary = ApexColors.Primary
    Canvas(modifier = modifier) {
        if (entries.size < 2) return@Canvas
        val weights = entries.map { it.weight.toFloat() }
        val minW = weights.min()
        val maxW = weights.max()
        val range = (maxW - minW).coerceAtLeast(0.5f)
        val stepX = size.width / (entries.size - 1)

        // Line
        entries.forEachIndexed { i, e ->
            if (i == 0) return@forEachIndexed
            val x0 = (i - 1) * stepX
            val y0 = size.height * (1f - (entries[i - 1].weight.toFloat() - minW) / range)
            val x1 = i * stepX
            val y1 = size.height * (1f - (e.weight.toFloat() - minW) / range)
            drawLine(primary, Offset(x0, y0), Offset(x1, y1), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }

        // Dots
        entries.forEachIndexed { i, e ->
            val x = i * stepX
            val y = size.height * (1f - (e.weight.toFloat() - minW) / range)
            drawCircle(ApexColors.Primary, radius = 3.5.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}

// ─── Recent sessions ──────────────────────────────────────────────────────────

@Composable
private fun RecentSessionsSection(sessions: List<RichTrainingSession>, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ApexSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent sessions",
                style = MaterialTheme.typography.headlineSmall,
                color = ApexColors.TextPrimary
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelSmall,
                color = ApexColors.Primary,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }

        sessions.forEach { session ->
            SessionRow(session = session)
        }
    }
}

@Composable
private fun SessionRow(session: RichTrainingSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)   // 14dp
            .background(ApexColors.Surface)
            .padding(horizontal = ApexSpacing.lg, vertical = ApexSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = session.sessionType.ifBlank { "Session" },
                style = MaterialTheme.typography.titleMedium,
                color = ApexColors.TextPrimary
            )
            val meta = buildString {
                if (session.durationMinutes > 0) append("${session.durationMinutes} min")
                if (session.exercises.isNotEmpty()) {
                    if (isNotEmpty()) append("  ·  ")
                    append("${session.exercises.size} exercises")
                }
                if (session.completed) {
                    if (isNotEmpty()) append("  ·  ")
                    append("Completed")
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = ApexColors.TextSecondary)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ApexSpacing.xs)
        ) {
            Text(
                text = formatSessionDate(session.date),
                style = MaterialTheme.typography.bodySmall,
                color = ApexColors.TextSecondary
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ApexColors.TextTertiary,
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

private fun formatAxisDate(dateStr: String): String = runCatching {
    val date = LocalDate.parse(dateStr)
    "${date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)} ${date.dayOfMonth}"
}.getOrElse { dateStr.takeLast(5) }
