package com.apexfit.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import com.apexfit.app.BuildConfig
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.PlanBuilderViewModel
import com.apexfit.app.WorkoutExecutionViewModel
import com.apexfit.app.RestTimerViewModel
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.ui.models.*
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.GoalEngine
import com.apexfit.app.utils.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// No animated destination layer: all screens are drawn directly at full opacity.
internal object ApexScreenRoutes {
    const val SETTINGS = "settings"
    const val PLAN_BUILDER = "plan_builder"
    fun tab(index: Int): String? = TopLevelDestination.entries.getOrNull(index)?.route
    fun isTopLevel(route: String): Boolean = TopLevelDestination.entries.any { it.route == route }
    fun safeReturn(route: String): String =
        TopLevelDestination.entries.firstOrNull { it.route == route }?.route
            ?: TopLevelDestination.HOME.route
}

// This screen host is deliberately non-animated: there is no NavHost,
// AnimatedContent, fade, or scrim between Scaffold and the selected screen.
@Composable
internal fun ApexDirectScreenHost(
    route: String,
    stateHolder: SaveableStateHolder,
    content: @Composable (String) -> Unit
) {
    if (ApexScreenRoutes.isTopLevel(route)) {
        stateHolder.SaveableStateProvider(route) { content(route) }
    } else {
        content(route)
    }
}

// Standard glass-like premium card modifier
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCardSurface)
            .border(
                border = BorderStroke(
                    1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BorderBright,
                            BorderSubtle
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .then(clickableModifier)
            .padding(16.dp)
    ) {
        Column {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApexFitApp(
    fitnessViewModel: FitnessViewModel = hiltViewModel(),
    algorithmViewModel: AlgorithmViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    planBuilderViewModel: PlanBuilderViewModel = hiltViewModel(),
    workoutExecutionViewModel: WorkoutExecutionViewModel = hiltViewModel(),
    restTimerViewModel: RestTimerViewModel = hiltViewModel(),
    progressViewModel: ProgressViewModel = hiltViewModel(),
    nutritionViewModel: NutritionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnboarded by fitnessViewModel.isOnboarded.collectAsStateWithLifecycle()

    var currentRoute by rememberSaveable { mutableStateOf(TopLevelDestination.HOME.route) }
    var returnRoute by rememberSaveable { mutableStateOf(TopLevelDestination.HOME.route) }
    var selectedPlanId by rememberSaveable { mutableLongStateOf(0L) }
    var debugHideTabHeader by remember { mutableStateOf(false) }
    val tabStateHolder = rememberSaveableStateHolder()
    val activeTab = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val navigateToTab: (Int) -> Unit = { index ->
        ApexScreenRoutes.tab(index)?.let { currentRoute = it }
    }
    val openSettings: () -> Unit = {
        if (currentRoute != ApexScreenRoutes.SETTINGS) {
            returnRoute = ApexScreenRoutes.safeReturn(currentRoute)
            currentRoute = ApexScreenRoutes.SETTINGS
        }
    }
    val openPlanBuilder: (Long) -> Unit = { planId ->
        returnRoute = ApexScreenRoutes.safeReturn(currentRoute)
        selectedPlanId = planId
        currentRoute = ApexScreenRoutes.PLAN_BUILDER
    }
    val navigateBack: () -> Unit = {
        currentRoute = when (currentRoute) {
            ApexScreenRoutes.SETTINGS, ApexScreenRoutes.PLAN_BUILDER -> ApexScreenRoutes.safeReturn(returnRoute)
            else -> TopLevelDestination.HOME.route
        }
    }

    val activeSession by workoutExecutionViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val restoredFromDisk by workoutExecutionViewModel.sessionManager.restoredFromDisk.collectAsStateWithLifecycle()
    var hasPromptedResume by rememberSaveable { mutableStateOf(false) }
    var showResumeDialog by rememberSaveable { mutableStateOf(false) }

    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle result
    }

    LaunchedEffect(activeSession, isOnboarded, restoredFromDisk) {
        if (isOnboarded == true && activeSession != null && restoredFromDisk && !hasPromptedResume) {
            showResumeDialog = true
            hasPromptedResume = true
        }
        if (isOnboarded == true && activeSession != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val prefs = context.getSharedPreferences("apex_prefs", Context.MODE_PRIVATE)
                val handled = prefs.getBoolean("notification_permission_handled", false)
                if (!handled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        prefs.edit().putBoolean("notification_permission_handled", true).apply()
                        withContext(Dispatchers.Main) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            containerColor = DarkCardSurface,
            title = {
                Text(
                    text = "RESUME WORKOUT?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Text(
                    text = "You have a workout in progress. Do you want to resume or discard it?",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResumeDialog = false
                        fitnessViewModel.setTrainSubTab(1)
                        navigateToTab(TopLevelDestination.TRAIN.ordinal)
                    }
                ) {
                    Text(stringResource(R.string.onboarding_resume), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = ApexColors.Primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResumeDialog = false
                        workoutExecutionViewModel.cancelActiveWorkout()
                    }
                ) {
                    Text(stringResource(R.string.onboarding_discard), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = ApexColors.Alert)
                }
            }
        )
    }

    if (showNotificationRationale) {
        NotificationRationaleDialog(
            onAllow = {
                showNotificationRationale = false
                coroutineScope.launch(Dispatchers.IO) {
                    val prefs = context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("notification_permission_handled", true).apply()
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSkip = {
                showNotificationRationale = false
                coroutineScope.launch(Dispatchers.IO) {
                    val prefs = context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("notification_permission_handled", true).apply()
                }
            }
        )
    }

    when (isOnboarded) {
        null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.Canvas),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apex_logo),
                    contentDescription = null,
                    tint = ApexColors.Primary,
                    modifier = Modifier.size(32.dp)
                )
                CircularProgressIndicator(
                    color = ApexColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        false -> {
        val preferredUnits by fitnessViewModel.units.collectAsStateWithLifecycle()
        val onboardingSaving by fitnessViewModel.onboardingSaving.collectAsStateWithLifecycle()
        val onboardingError by fitnessViewModel.onboardingError.collectAsStateWithLifecycle()
        OnboardingScreen(
            preferredUnits = preferredUnits,
            isSaving = onboardingSaving,
            saveError = onboardingError,
            onExplore = { fitnessViewModel.completeTrainingSetup() },
            onComplete = { profile -> fitnessViewModel.completeTrainingSetup(profile) }
        )
        } // end false branch
        true -> {
            BackHandler(enabled = currentRoute != TopLevelDestination.HOME.route) { navigateBack() }
            Scaffold(
            containerColor = DarkBackground,
            topBar = {
                if (!debugHideTabHeader && currentRoute in listOf("train", "nutrition", "progress")) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.testTag("app_logo")
                            ) {
                                Text(
                                    text = "APEX ",
                                    fontFamily = SyneFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "FIT",
                                    fontFamily = SyneFamily,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                if (BuildConfig.DEBUG) {
                                    // Visual proof that the new diagnostic APK is installed.
                                    Text(
                                        text = "R15",
                                        fontSize = 9.sp,
                                        color = ApexColors.Amber,
                                        modifier = Modifier.padding(start = 8.dp).testTag("render_build_marker")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = DarkBackground
                        ),
                        actions = {
                            if (BuildConfig.DEBUG) {
                                IconButton(
                                    modifier = Modifier.testTag("toggle_debug_top_bar"),
                                    onClick = { debugHideTabHeader = true }
                                ) {
                                    Text("×", color = ApexColors.Amber, fontSize = 18.sp)
                                }
                            }
                            IconButton(
                                onClick = openSettings,
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PrimaryText)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (currentRoute != ApexScreenRoutes.PLAN_BUILDER && currentRoute != ApexScreenRoutes.SETTINGS) {
                    BottomNavBar(
                        activeTab = activeTab,
                        onTabSelected = { index ->
                            restTimerViewModel.closeRestTimer()
                            workoutExecutionViewModel.closeRirSelector()
                            workoutExecutionViewModel.dismissSessionComplete()
                            navigateToTab(index)
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(DarkBackground)
            ) {
                // Bypass Navigation Compose's AnimatedContent, which can leave the
                // content layer invisible even while the Scaffold bars render.
                // SaveableStateHolder retains each tab's form and scroll state.
                ApexDirectScreenHost(currentRoute, tabStateHolder) { route ->
                when (route) {
                    TopLevelDestination.HOME.route -> {
                        HomeScreen(
                            homeViewModel = homeViewModel,
                            fitnessViewModel = fitnessViewModel,
                            planBuilderViewModel = planBuilderViewModel,
                            workoutExecutionViewModel = workoutExecutionViewModel,
                            onNavigateTo = navigateToTab,
                            onOpenSettings = openSettings
                        )
                    }
                    TopLevelDestination.TRAIN.route -> {
                        TrainTab(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            planBuilderViewModel = planBuilderViewModel,
                            workoutExecutionViewModel = workoutExecutionViewModel,
                            restTimerViewModel = restTimerViewModel,
                            onNavigateToPlanBuilder = openPlanBuilder
                        )
                    }
                    TopLevelDestination.NUTRITION.route -> {
                        NutritionScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            nutritionViewModel = nutritionViewModel,
                            onNavigateTo = navigateToTab
                        )
                    }
                    TopLevelDestination.PROGRESS.route -> {
                        ProgressScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            progressViewModel = progressViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateTo = navigateToTab
                        )
                    }
                    ApexScreenRoutes.PLAN_BUILDER -> PlanBuilderScreen(
                        planBuilderViewModel = planBuilderViewModel,
                        planId = selectedPlanId,
                        onNavigateBack = navigateBack
                    )
                    ApexScreenRoutes.SETTINGS -> SettingsScreen(
                        viewModel = fitnessViewModel,
                        onNavigateBack = navigateBack
                    )
                    else -> {
                        LaunchedEffect(route) { currentRoute = TopLevelDestination.HOME.route }
                    }
                }
                }

                if (BuildConfig.DEBUG) {
                    // Compare this independent draw layer with the routed screen beneath.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(10.dp)
                            .background(Color.White)
                            .testTag("root_content_sentinel")
                    )
                }

                // Render smart rest timer overlay if triggered
                val showRestOverlay by restTimerViewModel.showRestOverlay.collectAsStateWithLifecycle()
                if (showRestOverlay) {
                    BackHandler { restTimerViewModel.closeRestTimer() }
                    RestTimerOverlay(fitnessViewModel, restTimerViewModel)
                }

                // Render smart RIR selector overlay if triggered
                val showRirOverlay by workoutExecutionViewModel.showRirOverlay.collectAsStateWithLifecycle()
                if (showRirOverlay) {
                    BackHandler { workoutExecutionViewModel.closeRirSelector() }
                    RirSelectorOverlay(
                        fitnessViewModel = fitnessViewModel,
                        workoutExecutionViewModel = workoutExecutionViewModel,
                        onTriggerRestTimer = { rpe, exName, muscle, reps, setIdx, totalSets ->
                            restTimerViewModel.triggerRestTimer(
                                rpe = rpe,
                                exerciseName = exName,
                                muscleGroup = muscle,
                                reps = reps,
                                setIndex = setIdx,
                                totalSets = totalSets,
                                currentExerciseIdx = workoutExecutionViewModel.currentExerciseIdx.value,
                                completedExerciseId = workoutExecutionViewModel.rirOverlayState.value.exerciseId
                            )
                        }
                    )
                }

                // Render session complete page
                val showComplete by workoutExecutionViewModel.showSessionCompleteScreen.collectAsStateWithLifecycle()
                if (showComplete) {
                    BackHandler { workoutExecutionViewModel.dismissSessionComplete() }
                    SessionCompleteOverlay(fitnessViewModel, workoutExecutionViewModel)
                }
            }
        }
        } // end true branch
    } // end when
}

@Composable
fun BottomNavBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    // v2.1: full-width bar on Canvas with a hairline top edge (mockup-verified).
    // All four labels always visible. Active item = mint icon + mint label +
    // short 24×4dp signal pill beneath the label; inactive items stay muted.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = ApexColors.Canvas
    ) {
        Column {
            HorizontalDivider(color = ApexColors.BorderSubtle, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = activeTab == destination.ordinal
                    val itemColor = if (isSelected) ApexColors.Primary else ApexColors.TextSecondary

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("nav_tab_${destination.route}")
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { onTabSelected(destination.ordinal) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = destination.label,
                                tint = itemColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = destination.label,
                                fontFamily = InterFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = itemColor,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            // Signal pill — solid under the active tab, invisible otherwise
                            Box(
                                Modifier
                                    .size(width = 24.dp, height = 4.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isSelected) ApexColors.Primary else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRationaleDialog(onAllow: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = DarkCardSurface,
        title = {
            Text(
                text = "Stay on Track with Apex Fit",
                fontFamily = SyneFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryText
            )
        },
        text = {
            Text(
                text = "Apex Fit sends you a daily readiness report, pre-workout nudges, and protein reminders. Allow notifications to get the most out of your training?",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                color = SecondaryText
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.onboarding_allow), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = ApexColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_not_now), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = SecondaryText)
            }
        }
    )
}
