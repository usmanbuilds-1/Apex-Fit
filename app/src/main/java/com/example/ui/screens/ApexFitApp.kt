// name=app/src/main/java/com/example/ui/screens/ApexFitApp.kt
package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import com.example.HomeViewModel
import com.example.TrainViewModel
import com.example.ProgressViewModel
import com.example.NutritionViewModel
import com.example.AlgorithmViewModel
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination

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
    fitnessViewModel: FitnessViewModel = viewModel(),
    algorithmViewModel: AlgorithmViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    trainViewModel: TrainViewModel = viewModel(),
    progressViewModel: ProgressViewModel = viewModel(),
    nutritionViewModel: NutritionViewModel = viewModel()
) {
    // Link specialized ViewModels to fitnessViewModel
    fitnessViewModel.homeVM = homeViewModel
    fitnessViewModel.trainVM = trainViewModel
    fitnessViewModel.progressVM = progressViewModel
    fitnessViewModel.nutritionVM = nutritionViewModel

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnboarded by fitnessViewModel.isOnboarded.collectAsStateWithLifecycle()
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val goal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val gWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val activeTab by fitnessViewModel.currentTab.collectAsStateWithLifecycle()

    val activeSession by trainViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    var hasPromptedResume by rememberSaveable { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeSession, isOnboarded) {
        if (isOnboarded && activeSession != null && !hasPromptedResume) {
            showResumeDialog = true
            hasPromptedResume = true
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
                        fitnessViewModel.selectTab(1) // navigate to Train tab
                    }
                ) {
                    Text("RESUME", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = AmberAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResumeDialog = false
                        trainViewModel.cancelActiveWorkout()
                    }
                ) {
                    Text("DISCARD", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFFE84A4A))
                }
            }
        )
    }

    var showSettingsSheet by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Sync navController changes back to ViewModel to keep currentTab state up to date on back press
    LaunchedEffect(currentRoute) {
        val tabIndex = when (currentRoute) {
            "home" -> 0
            "train" -> 1
            "nutrition" -> 2
            "progress" -> 3
            else -> null
        }
        if (tabIndex != null && tabIndex != activeTab) {
            fitnessViewModel.selectTab(tabIndex)
        }
    }

    // Sync ViewModel changes back to NavController (e.g. programmatically selected tab)
    LaunchedEffect(activeTab) {
        val targetRoute = when (activeTab) {
            0 -> "home"
            1 -> "train"
            2 -> "nutrition"
            3 -> "progress"
            else -> "home"
        }
        if (navController.currentDestination != null && currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (!isOnboarded) {
        val preferredUnits by fitnessViewModel.units.collectAsStateWithLifecycle()
        OnboardingScreen(
            preferredUnits = preferredUnits,
            onComplete = { name, selectedGoal, currW, goalW, key, height, age, sex ->
                fitnessViewModel.completeOnboarding(name, selectedGoal, currW, goalW, key, height, age, sex)
            }
        )
    } else {
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
                        onTabSelected = {
                            trainViewModel.closeRestTimer()
                            trainViewModel.closeRirSelector()
                            trainViewModel.dismissSessionComplete()
                            fitnessViewModel.selectTab(it)
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
                    startDestination = "home",
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
                                fitnessViewModel.selectTab(tabIndex)
                            }
                        )
                    }
                    composable("train") {
                        TrainScreen(
                            fitnessViewModel = fitnessViewModel,
                            algorithmViewModel = algorithmViewModel,
                            trainViewModel = trainViewModel,
                            onNavigateToPlanBuilder = {
                                navController.navigate("plan_builder")
                            },
                            onNavigateTo = { tabIndex ->
                                fitnessViewModel.selectTab(tabIndex)
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
                                fitnessViewModel.selectTab(tabIndex)
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
                                fitnessViewModel.selectTab(tabIndex)
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
                    color = com.example.ui.theme.DarkBackground,
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
    }
}

@Composable
fun OnboardingScreen(
    preferredUnits: String = "kg",
    onComplete: (String, String, Double, Double, String, Double, Int, String) -> Unit
) {
    val unitLabel = if (preferredUnits.lowercase() in listOf("lb", "lbs")) "lb" else "kg"
    var name by rememberSaveable { mutableStateOf("") }
    var goalTarget by rememberSaveable { mutableStateOf("Gain Muscle") }
    var currentWeightStr by rememberSaveable { mutableStateOf("80") }
    var goalWeightStr by rememberSaveable { mutableStateOf("80") }
    var heightStr by rememberSaveable { mutableStateOf("175") }
    var ageStr by rememberSaveable { mutableStateOf("25") }
    var sexChoice by rememberSaveable { mutableStateOf("Male") }

    val context = LocalContext.current

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
                text = "ELITE TRAINING & METABOLIC ENGINEERING",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "INITIALIZE ATHLETE PROFILE",
                    fontFamily = SyneFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What is your name?", color = SecondaryText) },
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

                // Goal Selectors
                Text(
                    text = "TRAINING OBJECTIVE",
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

                // Weights Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = currentWeightStr,
                        onValueChange = { currentWeightStr = it },
                        label = { Text("Current Wt ($unitLabel)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_weight_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = goalWeightStr,
                        onValueChange = { goalWeightStr = it },
                        label = { Text("Goal Wt ($unitLabel)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_goal_weight_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
                }

                // Biological Profile Row (Height, Age)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("Height (cm)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Age (yrs)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
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

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val cw = currentWeightStr.toDoubleOrNull() ?: com.example.UserDefaults.WEIGHT_KG
                        val gw = goalWeightStr.toDoubleOrNull() ?: com.example.UserDefaults.WEIGHT_KG
                        val ht = heightStr.toDoubleOrNull() ?: com.example.UserDefaults.HEIGHT_CM
                        val ag = ageStr.toIntOrNull() ?: com.example.UserDefaults.AGE_YEARS
                        onComplete(name, goalTarget, cw, gw, "", ht, ag, sexChoice.lowercase())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_complete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "ENTER THE ARENA",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A0A0F),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(64.dp).padding(top = 8.dp),
        containerColor = DarkCardSurface
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontFamily = SyneFamily) }
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = "Train") },
            label = { Text("Train", fontFamily = SyneFamily) }
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Nutrition") },
            label = { Text("Nutrition", fontFamily = SyneFamily) }
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Default.Insights, contentDescription = "Progress") },
            label = { Text("Progress", fontFamily = SyneFamily) }
        )
    }
}
