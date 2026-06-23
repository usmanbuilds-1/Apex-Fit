package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
    val apiKey by fitnessViewModel.geminiApiKey.collectAsStateWithLifecycle()
    val activeTab by fitnessViewModel.currentTab.collectAsStateWithLifecycle()

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
        OnboardingScreen(onComplete = { name, selectedGoal, currW, goalW, key, height, age, sex ->
            fitnessViewModel.completeOnboarding(name, selectedGoal, currW, goalW, key, height, age, sex)
        })
    } else {
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
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
            },
            bottomBar = {
                BottomNavBar(
                    activeTab = activeTab,
                    onTabSelected = { fitnessViewModel.selectTab(it) }
                )
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
                            onNavigateTo = { tabIndex ->
                                fitnessViewModel.selectTab(tabIndex)
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
                    RestTimerOverlay(fitnessViewModel, trainViewModel)
                }

                // Render smart RIR selector overlay if triggered
                val showRirOverlay by trainViewModel.showRirOverlay.collectAsStateWithLifecycle()
                if (showRirOverlay) {
                    RirSelectorOverlay(fitnessViewModel, trainViewModel)
                }

                // Render session complete page
                val showComplete by trainViewModel.showSessionCompleteScreen.collectAsStateWithLifecycle()
                if (showComplete) {
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
    onComplete: (String, String, Double, Double, String, Double, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("Gain Muscle") }
    var currentWeightStr by remember { mutableStateOf("80") }
    var goalWeightStr by remember { mutableStateOf("80") }
    var heightStr by remember { mutableStateOf("175") }
    var ageStr by remember { mutableStateOf("25") }
    var sexChoice by remember { mutableStateOf("Male") }
    var apiKey by remember { mutableStateOf("") }

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
                        label = { Text("Current Wt (kg)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        label = { Text("Goal Wt (kg)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_height_input"),
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_age_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )
                }

                // Biological Sex Select
                Text(
                    text = "BIOLOGICAL SEX (MIFFLIN-ST JEOR BIOMETRIC OFFSET)",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    for (sex in listOf("Male", "Female")) {
                        val isSelected = sexChoice.equals(sex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AmberAccent else DarkRaised)
                                .border(
                                    border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { sexChoice = sex }
                                .padding(vertical = 12.dp)
                                .testTag("onboarding_sex_${sex.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sex.uppercase(),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0A0A0F) else PrimaryText
                            )
                        }
                    }
                }

                // API Key Input
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key (Optional)", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("onboarding_api_key_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = AmberAccent,
                        unfocusedIndicatorColor = BorderSubtle
                    )
                )
                Text(
                    text = "Get your free API key at api.studio.google.com to power direct science coaching, plan uploads, and photo estimations.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = MutedText,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Launch CTA Button
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            Toast.makeText(context, "Please configure athlete identity", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val cw = currentWeightStr.toDoubleOrNull() ?: com.example.UserDefaults.WEIGHT_KG
                        val gw = goalWeightStr.toDoubleOrNull() ?: com.example.UserDefaults.WEIGHT_KG
                        val ht = heightStr.toDoubleOrNull() ?: com.example.UserDefaults.HEIGHT_CM
                        val ageVal = ageStr.toIntOrNull() ?: com.example.UserDefaults.AGE_YEARS
                        onComplete(name, goalTarget, cw, gw, apiKey, ht, ageVal, sexChoice.lowercase())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_complete_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
                ) {
                    Text(
                        text = "START ATHLETE JOURNEY",
                        fontFamily = SyneFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0A0A0F)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        onComplete("Athlete X", "Maintain", com.example.UserDefaults.WEIGHT_KG, com.example.UserDefaults.WEIGHT_KG, "", com.example.UserDefaults.HEIGHT_CM, com.example.UserDefaults.AGE_YEARS, "male")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Skip and run offline profile demo",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCardSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Subtle top border divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                TabItem("Home", Icons.Filled.Home, Icons.Outlined.Home, 0),
                TabItem("Train", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter, 1),
                TabItem("Nutrition", Icons.Filled.Restaurant, Icons.Outlined.Restaurant, 2),
                TabItem("Progress", Icons.Filled.BarChart, Icons.Outlined.BarChart, 3)
            )

            tabs.forEach { item ->
                val isSelected = activeTab == item.index
                val contentColor = if (isSelected) AmberAccent else MutedText

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(item.index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Active Amber Indicator Bar (top aligned)
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Icon(
                        imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                        contentDescription = item.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.label,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

data class TabItem(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val index: Int
)

// REST TIMER COUNTDOWN OVERLAY
@Composable
fun RestTimerOverlay(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel
) {
    val seconds by trainViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val totalTime by trainViewModel.restTimerTotal.collectAsStateWithLifecycle()
    val lastSetContext by trainViewModel.restTimerLastSetContext.collectAsStateWithLifecycle()
    val nextSetPreview by trainViewModel.restTimerNextSetPreview.collectAsStateWithLifecycle()

    val progress = if (totalTime > 0) seconds.toFloat() / totalTime.toFloat() else 1f

    // Format MM:SS
    val minutesStr = seconds / 60
    val secsStr = seconds % 60
    val formattedTime = String.format(java.util.Locale.US, "%d:%02d", minutesStr, secsStr)

    // Music playing state
    var isMusicPlaying by remember { mutableStateOf(false) }

    // Rhythmic synth beat playing loop if Music is On
    LaunchedEffect(isMusicPlaying, seconds) {
        if (isMusicPlaying && seconds > 0 && seconds % 4 == 0) {
            fitnessViewModel.playMusicSynthNote()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF005050A)) // heavy frosted black glow
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0F14))
                .border(BorderStroke(1.dp, AmberAccent.copy(alpha = 0.2f)), RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            // Screen Title
            Text(
                text = "REST TIMER",
                fontFamily = SyneFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Animated Sweeping Ring + Timer
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        // Background ring
                        drawCircle(
                            color = BorderSubtle,
                            radius = size.minDimension / 2 - 4.dp.toPx(),
                            style = Stroke(width = 8.dp.toPx())
                        )
                        // Active color sweep
                        drawArc(
                            color = AmberAccent,
                            startAngle = -90f,
                            sweepAngle = progress * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "REMAINING",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = MutedText,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Last Completed Set Context Card
            if (lastSetContext.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkRaised)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LAST SET DONE",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedText,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastSetContext,
                            fontFamily = SyneFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Next Set Preview Card
            if (nextSetPreview.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141F18))
                        .border(BorderStroke(1.dp, Color(0xFF2EC46A).copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "UP NEXT",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2EC46A),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nextSetPreview.replace("Next: ", ""),
                            fontFamily = SyneFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Row of Action buttons: [Skip] [+30s] [Ready] [Music On/Off]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Skip Button
                Button(
                    onClick = { fitnessViewModel.closeRestTimer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1616)),
                    border = BorderStroke(1.dp, Color(0xFFE84A4A).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "SKIP",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE84A4A)
                    )
                }

                // Ready Button
                Button(
                    onClick = { fitnessViewModel.closeRestTimer() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "READY",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A0A0F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // +30s Button
                Button(
                    onClick = { fitnessViewModel.addRestTimerSeconds(30) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "+30s",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }

                // Music Toggle
                Button(
                    onClick = { isMusicPlaying = !isMusicPlaying },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMusicPlaying) Color(0xFF1E1B4B) else DarkRaised
                    ),
                    border = BorderStroke(1.dp, if (isMusicPlaying) Color(0xFF6366F1) else BorderSubtle),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isMusicPlaying) "MUSIC ON 🎵" else "MUSIC OFF 🔇",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMusicPlaying) Color(0xFFC7D2FE) else PrimaryText
                    )
                }
            }
        }
    }
}

// SMART RIR TO RPE SELECTOR AND HISTORICAL PATTERN OVERLAY
@Composable
fun RirSelectorOverlay(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel
) {
    val isShowingHistory by trainViewModel.isShowingRirHistory.collectAsStateWithLifecycle()
    val exerciseName by trainViewModel.rirSelectorExerciseName.collectAsStateWithLifecycle()
    val setIndex by trainViewModel.rirSelectorSetIndex.collectAsStateWithLifecycle()
    val weight by trainViewModel.rirSelectorWeight.collectAsStateWithLifecycle()
    val reps by trainViewModel.rirSelectorReps.collectAsStateWithLifecycle()
    val totalSets by trainViewModel.rirSelectorTotalSets.collectAsStateWithLifecycle()
    val selectedRir by trainViewModel.selectedRir.collectAsStateWithLifecycle()
    val history by trainViewModel.rirHistoricalSets.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF205050A)) // premium frosted deep dark layer
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp) // Prevents clipping at top/bottom on smaller phone screens
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0F14))
                .border(BorderStroke(1.dp, AmberAccent.copy(alpha = 0.25f)), RoundedCornerShape(24.dp))
                .verticalScroll(remember(isShowingHistory) { ScrollState(0) })
                .padding(24.dp)
        ) {
            if (!isShowingHistory) {
                // PAGE 1: RIR Selector asking concrete Reps In Reserve
                Text(
                    text = exerciseName.uppercase(),
                    fontFamily = SyneFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "LOGGED: ${weight.toString().replace(".0", "")} lbs × $reps reps (Set ${setIndex + 1}/$totalSets)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(bottom = 20.dp))

                Text(
                    text = "How many MORE reps could you have done?",
                    fontFamily = SyneFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // RIR Options calculated based on the science mapping guidelines
                val rirOptions = listOf(
                    4 to "4 or more left (very easy) → RPE 6",
                    3 to "3 reps left (moderate) → RPE 7",
                    2 to "2 reps left (sweet spot) → RPE 8",
                    1 to "1 rep left (hard) → RPE 9",
                    0 to "0 reps left (maxed out) → RPE 10"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rirOptions.forEach { (rirNum, textDesc) ->
                        val isOptionSelected = selectedRir == rirNum
                        val isSuggested = rirNum == 2

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isOptionSelected) Color(0xFF1E1B4B) else DarkRaised)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isOptionSelected) Color(0xFF6366F1) else if (isSuggested) AmberAccent.copy(alpha = 0.3f) else BorderSubtle.copy(alpha = 0.4f)
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { fitnessViewModel.selectRirOption(rirNum) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isOptionSelected,
                                onClick = { fitnessViewModel.selectRirOption(rirNum) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF818CF8),
                                    unselectedColor = MutedText
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = textDesc,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = if (isOptionSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOptionSelected) Color(0xFFC7D2FE) else PrimaryText
                            )

                            if (isSuggested) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AmberAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SUGGESTED",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberAccent
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { fitnessViewModel.closeRirSelector() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1616)),
                        border = BorderStroke(1.dp, Color(0xFFE84A4A).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "CLOSE",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE84A4A)
                        )
                    }

                    Button(
                        onClick = { fitnessViewModel.confirmRirSelection() },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "CONFIRM",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A0A0F)
                        )
                    }
                }
            } else {
                // PAGE 2: Historical context screen teaching users standard effort feel
                val rpeResult = com.example.utils.ProgressionEngine.calculateRPEFromRIR(selectedRir)

                Text(
                    text = "RIR SELECTION CONFIRMED",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2EC46A),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "$selectedRir Reps Left = RPE $rpeResult",
                    fontFamily = SyneFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

                Text(
                    text = "YOUR LAST ${history.size} ${exerciseName.uppercase()} SESSIONS AT RPE $rpeResult:",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedText,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                )

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkRaised)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No history logged yet at RPE $rpeResult. Train at this effort level consistently to see your progression patterns visualized here!",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MutedText,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        history.forEachIndexed { hIdx, hSet ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkRaised)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Session ${history.size - hIdx}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = MutedText
                                    )
                                    Text(
                                        text = hSet.date,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        color = MutedText
                                    )
                                    if (hSet.repsInReserve != 2) {
                                        Text(
                                            text = "Left: ${hSet.repsInReserve}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = AmberAccent
                                        )
                                    }
                                }

                                Text(
                                    text = "${hSet.weight.toString().replace(".0", "")} lbs × ${hSet.reps} reps",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF233529))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "RPE $rpeResult",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2EC46A)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic progress / informative education insight card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F2016))
                        .border(BorderStroke(1.dp, Color(0xFF1B4D2B)), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "💡 EFFORT PATTERN INSIGHT",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2EC46A),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (history.size > 1 && history.first().weight >= history.last().weight) {
                                "Your strength is increasing at the exact same effort level! This teaches you how true muscle adaptation feels."
                            } else {
                                "Perfect consistency! Over time, logging your Reps In Reserve trains your brain to reliably rate daily intensity."
                            },
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { fitnessViewModel.confirmRirAndNextSet() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CONFIRM & NEXT SET",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A0A0F)
                    )
                }
            }
        }
    }
}

// SESSION COMPLETE OVERLAY OVERRIDE
@Composable
fun SessionCompleteOverlay(
    fitnessViewModel: FitnessViewModel,
    trainViewModel: TrainViewModel
) {
    val stats by trainViewModel.completedStats.collectAsStateWithLifecycle()
    val prsBreak by trainViewModel.completedPRsBroken.collectAsStateWithLifecycle()
    val hyperQ by trainViewModel.completedHypertrophyScore.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SESSION COMPLETE",
                fontFamily = SyneFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GreenAccent,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "ATHLETIC DATA LOG SAVED",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SecondaryText,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                // Volume info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("COMPLETION TIME", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = MutedText)
                        Text("${stats.first} MINS", fontFamily = JetBrainsMonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WORKING INTENSITY", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = MutedText)
                        Text("HQ: ${String.format("%.1f", hyperQ)}/10", fontFamily = JetBrainsMonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL VOLUME", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = MutedText)
                        Text("${stats.second.toInt()} kg", fontFamily = JetBrainsMonoFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                Spacer(modifier = Modifier.height(24.dp))

                // Personal Records Broken section
                Text(
                    text = "PERSONAL RECORDS CONQUERED",
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (prsBreak.isEmpty()) {
                    Text(
                        text = "Standard volume target met. Keep tracking progressively weekly to override previous records.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                } else {
                    prsBreak.forEach { record ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = "New Record", tint = AmberAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NEW RECORD: $record",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                Spacer(modifier = Modifier.height(24.dp))

                // Baz-Valle Effective Sets Summary Breakdown
                Text(
                    text = "BAZ-VALLE EFFECTIVE SETS SUMMARY",
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val sessionCompletedSets by trainViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
                if (sessionCompletedSets.isNotEmpty()) {
                    val setsByExercise = sessionCompletedSets.groupBy { it.exerciseName }
                    setsByExercise.forEach { (exName, sets) ->
                        val totalSessionEff = sets.sumOf { it.effectiveSetValue }
                        val plannedExercise = trainViewModel.activeExercises.value.find { it.name.equals(exName, ignoreCase = true) }
                        val planSets = plannedExercise?.sets ?: sets.size
                        val targetEff = planSets * 0.75

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F0F14))
                                .border(BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = exName.uppercase(),
                                    fontFamily = SyneFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Total: ${String.format("%.2f", totalSessionEff)} / ${String.format("%.2f", targetEff)} Effective Sets (${if (targetEff > 0) (totalSessionEff / targetEff * 100).toInt() else 0}%)",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = AmberAccent
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Set-by-Set Breakdown:",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    color = MutedText,
                                    fontWeight = FontWeight.Bold
                                )

                                sets.forEachIndexed { idx, setVal ->
                                    val zoneLabel = when (setVal.rpe) {
                                        in 0..6 -> "below threshold"
                                        7 -> "building zone"
                                        8 -> "SWEET SPOT"
                                        9 -> "high effort"
                                        10 -> "maximal"
                                        else -> "warmup/other"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Set ${idx + 1}: ${setVal.weight.toInt()} kg ${if (setVal.isWarmup) "warmup" else ""} @ RPE ${setVal.rpe}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = SecondaryText
                                        )
                                        Text(
                                            text = "→ ${String.format("%.2f", setVal.effectiveSetValue)} ($zoneLabel)",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = if (setVal.rpe == 8) AmberAccent else SecondaryText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle.copy(alpha = 0.3f)))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Muscle group weekly MEV check
                                val muscle = sets.firstOrNull()?.muscleGroup ?: "Muscle"
                                val mevMin = 6
                                val mevMax = 10
                                val statusStr = if (totalSessionEff >= mevMin) {
                                    "Met weekly MEV!"
                                } else {
                                    "Need more volume this week"
                                }
                                Column {
                                    Text(
                                        text = "Estimated Weekly Volume Summary (${muscle.uppercase()}):",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = PrimaryText,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Total effective sets: ${String.format("%.2f", totalSessionEff)} (this session)\n" +
                                               "Your MEV Target: $mevMin-$mevMax per week\n" +
                                               "Status: $statusStr",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = if (totalSessionEff >= mevMin) GreenAccent else RedAccent
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No completed training sets recorded in this session.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { fitnessViewModel.dismissSessionComplete() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CLOSE PROFILE LOG",
                        fontFamily = SyneFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A0A0F)
                    )
                }
            }
        }
    }
}
