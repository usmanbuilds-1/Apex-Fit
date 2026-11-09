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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val activeTab = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

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
                        navController.navigate("train") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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

    val startDest = "home"

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
            Scaffold(
            containerColor = DarkBackground,
            topBar = {
                if (currentRoute in listOf("train", "nutrition", "progress")) {
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
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = DarkBackground
                        ),
                        actions = {
                            IconButton(
                                onClick = { navController.navigate("settings") },
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PrimaryText)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (currentRoute != "plan_builder" && currentRoute != "settings") {
                    BottomNavBar(
                        activeTab = activeTab,
                        onTabSelected = { index ->
                            restTimerViewModel.closeRestTimer()
                            workoutExecutionViewModel.closeRirSelector()
                            workoutExecutionViewModel.dismissSessionComplete()
                            val route = TopLevelDestination.entries[index].route
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
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
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) }
                ) {
                    composable(
                        route = "home",
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(200)) }
                    ) {
                        val navigateToTab: (Int) -> Unit = { tabIndex ->
                            val route = TopLevelDestination.entries.getOrNull(tabIndex)?.route ?: TopLevelDestination.HOME.route
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        HomeScreen(
                            homeViewModel = homeViewModel,
                            fitnessViewModel = fitnessViewModel,
                            planBuilderViewModel = planBuilderViewModel,
                            workoutExecutionViewModel = workoutExecutionViewModel,
                            onNavigateTo = navigateToTab,
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable(
                        route = "train",
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(200)) }
                    ) {
                        TrainTab(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            planBuilderViewModel = planBuilderViewModel,
                            workoutExecutionViewModel = workoutExecutionViewModel,
                            restTimerViewModel = restTimerViewModel,
                            onNavigateToPlanBuilder = {
                                navController.navigate("plan_builder")
                            }
                        )
                    }
                    composable(
                        route = "plan_builder",
                        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                    ) {
                        PlanBuilderScreen(
                            planBuilderViewModel = planBuilderViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(
                        route = "nutrition",
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(200)) }
                    ) {
                        NutritionScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            nutritionViewModel = nutritionViewModel,
                            onNavigateTo = { tabIndex ->
                                val route = TopLevelDestination.entries.getOrNull(tabIndex)?.route ?: TopLevelDestination.HOME.route
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(
                        route = "progress",
                        enterTransition = { fadeIn(tween(200)) },
                        exitTransition = { fadeOut(tween(200)) }
                    ) {
                        ProgressScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            progressViewModel = progressViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateTo = { tabIndex ->
                                val route = TopLevelDestination.entries.getOrNull(tabIndex)?.route ?: TopLevelDestination.HOME.route
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = fitnessViewModel,
                            onNavigateBack = { navController.navigateUp() }
                        )
                    }
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
