package com.apexfit.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.TrainViewModel
import com.apexfit.app.ProgressViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.ui.models.*
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AlgorithmEngine
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

private val BOTTOM_NAV_ROUTES = listOf("home", "train", "nutrition", "progress")

// Standard glass-like premium card modifier
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
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
    fitnessViewModel: FitnessViewModel = viewModel(factory = FitnessViewModel.Factory),
    algorithmViewModel: AlgorithmViewModel = viewModel(),
    homeViewModel: HomeViewModel = fitnessViewModel.homeVM,
    trainViewModel: TrainViewModel = fitnessViewModel.trainVM,
    progressViewModel: ProgressViewModel = fitnessViewModel.progressVM,
    nutritionViewModel: NutritionViewModel = fitnessViewModel.nutritionVM
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnboarded by fitnessViewModel.isOnboarded.collectAsStateWithLifecycle()
    val activeTab by fitnessViewModel.currentTab.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val activeSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    var hasPromptedResume by rememberSaveable { mutableStateOf(false) }
    var showResumeDialog by rememberSaveable { mutableStateOf(false) }

    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle result
    }

    LaunchedEffect(activeSession, isOnboarded) {
        if (isOnboarded == true && activeSession != null && !hasPromptedResume) {
            showResumeDialog = true
            hasPromptedResume = true
        }
        if (isOnboarded == true && activeSession != null) {
            val handled = withContext(Dispatchers.IO) {
                context.getSharedPreferences("apex_prefs", Context.MODE_PRIVATE)
                    .getBoolean("notification_permission_handled", false)
            }
            if (!handled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    withContext(Dispatchers.IO) {
                        context.getSharedPreferences("apex_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("notification_permission_handled", true).apply()
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
                        navController.navigate("train") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.onboarding_resume), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = AmberAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResumeDialog = false
                        trainViewModel.cancelActiveWorkout()
                    }
                ) {
                    Text(stringResource(R.string.onboarding_discard), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFFE84A4A))
                }
            }
        )
    }

    if (showNotificationRationale) {
        NotificationRationaleDialog(
            onAllow = {
                showNotificationRationale = false
                val prefs = context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("notification_permission_handled", true).apply()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSkip = {
                showNotificationRationale = false
                val prefs = context.getSharedPreferences("apex_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("notification_permission_handled", true).apply()
            }
        )
    }

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val startDest = remember {
        val initialTab = fitnessViewModel.currentTab.value
        if (initialTab in BOTTOM_NAV_ROUTES.indices) BOTTOM_NAV_ROUTES[initialTab] else "home"
    }

    LaunchedEffect(currentRoute) {
        val tab = BOTTOM_NAV_ROUTES.indexOf(currentRoute)
        if (tab != -1 && tab != fitnessViewModel.currentTab.value) {
            fitnessViewModel.selectTab(tab)
        }
    }

    LaunchedEffect(activeTab) {
        val route = BOTTOM_NAV_ROUTES.getOrNull(activeTab) ?: return@LaunchedEffect
        val currentDest = navController.currentBackStackEntry?.destination?.route
        if (currentDest != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    when (isOnboarded) {
        null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        )
        false -> {
        val preferredUnits by fitnessViewModel.units.collectAsStateWithLifecycle()
        OnboardingScreen(
            preferredUnits = preferredUnits,
            onComplete = { name, selectedGoal, currW, goalW, units, height, age, sex, weeklyWorkouts ->
                fitnessViewModel.completeOnboarding(
                    name, selectedGoal, currW, goalW, height, age, sex,
                    units = units,
                    weeklyWorkouts = weeklyWorkouts
                )
                showNotificationRationale = true
            }
        )
        } // end false branch
        true -> {
            Scaffold(
            containerColor = DarkBackground,
            topBar = {
                if (currentRoute != "plan_builder") {
                    CenterAlignedTopAppBar(
                        modifier = Modifier.padding(top = 16.dp),
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
                                    color = AmberAccent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = DarkBackground
                        ),
                        actions = {
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PrimaryText)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (currentRoute != "plan_builder") {
                    BottomNavBar(
                        activeTab = activeTab,
                        onTabSelected = { index ->
                            trainViewModel.closeRestTimer()
                            trainViewModel.closeRirSelector()
                            trainViewModel.dismissSessionComplete()
                            val route = BOTTOM_NAV_ROUTES[index]
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
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None }
                ) {
                    composable("home") {
                        HomeScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            homeViewModel = homeViewModel,
                            trainViewModel = trainViewModel,
                            nutritionViewModel = nutritionViewModel,
                            onNavigateTo = { tabIndex ->
                                val route = BOTTOM_NAV_ROUTES.getOrNull(tabIndex) ?: "home"
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
                    composable("train") {
                        TrainTab(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            trainViewModel = trainViewModel,
                            onNavigateToPlanBuilder = {
                                navController.navigate("plan_builder")
                            }
                        )
                    }
                    composable("plan_builder") {
                        PlanBuilderScreen(
                            trainViewModel = trainViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("nutrition") {
                        NutritionScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            nutritionViewModel = nutritionViewModel,
                            onNavigateTo = { tabIndex ->
                                val route = BOTTOM_NAV_ROUTES.getOrNull(tabIndex) ?: "home"
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
                    composable("progress") {
                        ProgressScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            progressViewModel = progressViewModel,
                            homeViewModel = homeViewModel,
                            onNavigateTo = { tabIndex ->
                                val route = BOTTOM_NAV_ROUTES.getOrNull(tabIndex) ?: "home"
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
                }

                // Render smart rest timer overlay if triggered
                val showRestOverlay by trainViewModel.showRestOverlay.collectAsStateWithLifecycle()
                if (showRestOverlay) {
                    BackHandler { trainViewModel.closeRestTimer() }
                    RestTimerOverlay(fitnessViewModel, trainViewModel)
                }

                // Render smart RIR selector overlay if triggered
                val showRirOverlay by trainViewModel.showRirOverlay.collectAsStateWithLifecycle()
                if (showRirOverlay) {
                    BackHandler { trainViewModel.closeRirSelector() }
                    RirSelectorOverlay(fitnessViewModel, trainViewModel)
                }

                // Render session complete page
                val showComplete by trainViewModel.showSessionCompleteScreen.collectAsStateWithLifecycle()
                if (showComplete) {
                    BackHandler { trainViewModel.dismissSessionComplete() }
                    SessionCompleteOverlay(fitnessViewModel, trainViewModel)
                }
            }
        }

        if (showSettingsSheet) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSettingsSheet = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkBackground,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SettingsScreen(
                            fitnessViewModel = fitnessViewModel,
                            onNavigateTo = { tabIndex ->
                                showSettingsSheet = false
                            }
                        )
                    }
                }
            }
        }
        } // end true branch
    } // end when
}

@Composable
fun OnboardingScreen(
    preferredUnits: String = "kg",
    onComplete: (String, String, Double, Double, String, Double, Int, String, Int) -> Unit
) {
    var selectedUnits by rememberSaveable { mutableStateOf(preferredUnits) }
    val isImperial = selectedUnits.lowercase() in listOf("lb", "lbs")
    val unitLabel = if (isImperial) "lb" else "kg"
    val heightUnitLabel = if (isImperial) "in" else "cm"
    var name by rememberSaveable { mutableStateOf("") }
    var goalTarget by rememberSaveable { mutableStateOf("Gain Muscle") }
    var currentWeightStr by rememberSaveable { mutableStateOf("") }
    var goalWeightStr by rememberSaveable { mutableStateOf("") }
    var heightStr by rememberSaveable { mutableStateOf("") }
    var ageStr by rememberSaveable { mutableStateOf("") }
    var sexChoice by rememberSaveable { mutableStateOf("Male") }
    var weeklyWorkoutsStr by rememberSaveable { mutableStateOf("3") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var goalWeightError by remember { mutableStateOf<String?>(null) }
    var heightError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val liveCw = currentWeightStr.replace(",", ".").toDoubleOrNull() ?: 70.0
    val liveGw = goalWeightStr.replace(",", ".").toDoubleOrNull() ?: liveCw
    val liveHt = heightStr.replace(",", ".").toDoubleOrNull() ?: (if (isImperial) 70.0 else 175.0)

    val liveWeightKg = if (isImperial) liveCw / 2.20462 else liveCw
    val liveGoalKg = if (isImperial) liveGw / 2.20462 else liveGw
    val liveHeightCm = if (isImperial) liveHt * 2.54 else liveHt
    val liveSexStr = sexChoice.lowercase()

    val liveResolvedGoal = when {
        goalTarget == "Gain Muscle" && liveGw < liveCw -> "Recomposition"
        goalTarget == "Lose Fat"   && liveGw > liveCw  -> "Gain Muscle"
        else -> goalTarget
    }

    val timeline = AlgorithmEngine.calcGoalTimeline(
        currentWeightKg  = liveWeightKg,
        goalWeightKg     = liveGoalKg,
        resolvedGoal     = liveResolvedGoal,
        heightCm         = liveHeightCm,
        sex              = liveSexStr
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "APEX FIT",
                fontFamily = SyneFamily,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AmberAccent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Welcome to Apex Fit",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Set Up Your Profile",
                    fontFamily = SyneFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Units toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("kg" to "KG", "lb" to "LB").forEach { (unit, label) ->
                        val selected = selectedUnits.lowercase() == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AmberAccent else DarkRaised)
                                .border(
                                    1.dp,
                                    if (selected) AmberAccent else BorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedUnits = unit
                                    // Reset weight/height/goalWeight to sensible defaults
                                    // for the new unit so the user isn't staring at 80 lb
                                    currentWeightStr = if (unit == "lb") "175" else "80"
                                    goalWeightStr = if (unit == "lb") "175" else "80"
                                    heightStr = if (unit == "lb") "69" else "175"
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontFamily = SyneFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) Color(0xFF0A0A0F) else SecondaryText,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        name = newName.take(40).filter { !it.isISOControl() }
                        nameError = null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    label = { Text(stringResource(R.string.onboarding_your_name), color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("onboarding_name_input"),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = SecondaryText,
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = AmberAccent,
                        unfocusedIndicatorColor = BorderSubtle
                    )
                )
                nameError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

                // Goal Selectors
                Text(
                    text = "Your Goal",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    for (item in listOf("Gain Muscle", "Lose Fat", "Maintain")) {
                        val isSelected = goalTarget == item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AmberAccent else DarkRaised)
                                .border(
                                    border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { goalTarget = item }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item,
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0A0A0F) else PrimaryText
                            )
                        }
                    }
                }

                if (liveResolvedGoal != goalTarget) {
                    Text(
                        text = "Note: Based on your weights, your goal has been adjusted to $liveResolvedGoal.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = AmberAccent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                val goalDescription = when (goalTarget) {
                    "Gain Muscle" -> "↑ ~250 kcal above maintenance  ·  High protein  ·  Progressive overload"
                    "Lose Fat"    -> "↓ ~400 kcal below maintenance  ·  High protein  ·  Preserve muscle"
                    else          -> "At maintenance  ·  Balanced macros  ·  Performance focus"
                }
                Text(
                    text = goalDescription,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Weights Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = currentWeightStr,
                            onValueChange = {
                                currentWeightStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                                weightError = null
                            },
                            label = { Text(stringResource(R.string.onboarding_current_wt, unitLabel), color = SecondaryText) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_weight_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            )
                        )
                        weightError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = goalWeightStr,
                            onValueChange = {
                                goalWeightStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                                goalWeightError = null
                            },
                            label = { Text(stringResource(R.string.onboarding_goal_wt, unitLabel), color = SecondaryText) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_goal_weight_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            )
                        )
                        goalWeightError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                // Biological Profile Row (Height, Age)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = {
                                heightStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                                heightError = null
                            },
                            label = { Text(if (isImperial) "Height (in)" else stringResource(R.string.onboarding_height_cm), color = SecondaryText) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            )
                        )
                        heightError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = {
                                ageStr = it
                                ageError = null
                            },
                            label = { Text(stringResource(R.string.onboarding_age_yrs), color = SecondaryText) },
                            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkRaised,
                                unfocusedContainerColor = DarkRaised,
                                focusedIndicatorColor = AmberAccent,
                                unfocusedIndicatorColor = BorderSubtle
                            )
                        )
                        ageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                }

                // Sex Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("male", "female").forEach { item ->
                        val isSelected = sexChoice.lowercase() == item
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AmberAccent else DarkRaised)
                                .border(
                                    border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { sexChoice = item }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.uppercase(),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0A0A0F) else PrimaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WEEKLY WORKOUTS",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1", "2", "3", "4", "5", "6+").forEach { option ->
                        val selected = weeklyWorkoutsStr == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AmberAccent else DarkRaised)
                                .border(
                                    1.dp,
                                    if (selected) AmberAccent else BorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { weeklyWorkoutsStr = option }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                fontFamily = SyneFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) Color(0xFF0A0A0F) else SecondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = timeline.summaryLine,
                    color = SecondaryText,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        nameError = null
                        weightError = null
                        goalWeightError = null
                        heightError = null
                        ageError = null

                        val trimmedName = name.trim()
                        val weightVal = currentWeightStr.replace(",", ".").toDoubleOrNull()
                        val heightVal = heightStr.replace(",", ".").toDoubleOrNull()
                        val ageVal = ageStr.toIntOrNull()

                        val isImperial = selectedUnits.lowercase() in listOf("lb", "lbs")
                        val minWeight = if (isImperial) 44.0 else 20.0
                        val maxWeightKg = com.apexfit.app.utils.AppConstants.MAX_WEIGHT_KG
                        val maxWeight = if (isImperial) maxWeightKg * 2.20462 else maxWeightKg
                        val minHeight = if (isImperial) 39.0 else 100.0
                        val maxHeight = if (isImperial) 98.0 else 250.0

                        if (trimmedName.isEmpty() || trimmedName.length > 50) {
                            nameError = "Please enter a name (1–50 characters)"
                        } else if (weightVal == null || weightVal < minWeight || weightVal > maxWeight) {
                            val maxWeightDisplay = if (isImperial) (maxWeightKg * 2.20462).toInt() else maxWeightKg.toInt()
                            val minWeightDisplay = if (isImperial) 44 else 20
                            weightError = "Please enter a valid weight ($minWeightDisplay–$maxWeightDisplay)"
                        } else if (heightVal == null || heightVal < minHeight || heightVal > maxHeight) {
                            heightError = if (isImperial) "Please enter a valid height in in (39–98)" else "Please enter a valid height in cm (100–250)"
                        } else if (ageVal == null || ageVal < com.apexfit.app.utils.AppConstants.MIN_AGE || ageVal > 100) {
                            ageError = "Please enter a valid age (${com.apexfit.app.utils.AppConstants.MIN_AGE}–100)"
                        } else {
                            val cw = weightVal
                            val minGoalWeight = if (isImperial) 66.0 else 30.0
                            val maxGoalWeight = if (isImperial) maxWeightKg * 2.20462 else maxWeightKg
                            val gwRaw = goalWeightStr.replace(",", ".").toDoubleOrNull()

                            if (goalWeightStr.isNotBlank() &&
                                (gwRaw == null || gwRaw < minGoalWeight || gwRaw > maxGoalWeight)) {
                                goalWeightError = "Enter a valid goal weight (${minGoalWeight.toInt()}–${maxGoalWeight.toInt()} $selectedUnits)"
                                return@Button
                            }

                            val gw = gwRaw ?: cw

                            val resolvedGoal = when {
                                goalTarget == "Gain Muscle" && gw < cw -> "Recomposition"
                                goalTarget == "Lose Fat"   && gw > cw  -> "Gain Muscle"
                                else -> goalTarget
                            }

                            val ht = heightVal
                            val ag = ageVal
                            val workouts = if (weeklyWorkoutsStr == "6+") 6 else
                                weeklyWorkoutsStr.toIntOrNull() ?: 3

                            val weightKg = if (isImperial) cw / 2.20462 else cw
                            var goalKg = if (isImperial) gw / 2.20462 else gw
                            val heightCm = if (isImperial) ht * 2.54 else ht
                            val sexStr = sexChoice.lowercase()

                            val timeline = AlgorithmEngine.calcGoalTimeline(
                                currentWeightKg  = weightKg,
                                goalWeightKg     = goalKg,
                                resolvedGoal     = resolvedGoal,
                                heightCm         = heightCm,
                                sex              = sexStr
                            )

                            if (timeline.isRealistic == false) {
                                goalKg = timeline.adjustedGoalWeightKg
                            }

                            val finalGoalKg = if (isImperial) goalKg * 2.20462 else goalKg

                            onComplete(trimmedName, resolvedGoal, cw, finalGoalKg, selectedUnits, ht, ag,
                                sexStr, workouts)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_complete_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberAccent,
                        contentColor = Color(0xFF0A0A0F),
                        disabledContainerColor = DarkRaised,
                        disabledContentColor = SecondaryText
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_get_started),
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = DarkBackground
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(DarkCardSurface)
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(32.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Home", Icons.Default.Home, 0),
                Triple("Train", Icons.Default.FitnessCenter, 1),
                Triple("Nutrition", Icons.Default.RestaurantMenu, 2),
                Triple("Progress", Icons.Default.Insights, 3)
            )

            tabs.forEach { (label, icon, index) ->
                val isSelected = activeTab == index
                val labelColor = if (isSelected) OrangeAccent else Color(0xFF9CA3AF)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = labelColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label.uppercase(),
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = labelColor,
                            letterSpacing = 0.8.sp
                        )
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
                Text(stringResource(R.string.onboarding_allow), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = AmberAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_not_now), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = SecondaryText)
            }
        }
    )
}
