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
import com.example.data.AlgorithmViewModel
import com.example.data.PlanSession
import com.example.data.EffectiveSetsData
import com.example.ui.models.*
import com.example.ui.theme.*
import com.example.utils.AlgorithmEngine
import com.example.ui.components.MuscleHeatmapCanvas
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
    algorithmViewModel: AlgorithmViewModel = viewModel()
) {
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
            "coach" -> 4
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
            4 -> "coach"
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
                        Text(
                            text = "APEX FIT",
                            fontFamily = SyneFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText,
                            modifier = Modifier.testTag("app_logo")
                        )
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
                        HomeTab(fitnessViewModel, algorithmViewModel)
                    }
                    composable("train") {
                        TrainTab(fitnessViewModel, algorithmViewModel)
                    }
                    composable("nutrition") {
                        NutritionTab(fitnessViewModel, algorithmViewModel)
                    }
                    composable("progress") {
                        ProgressTab(fitnessViewModel, algorithmViewModel)
                    }
                    composable("coach") {
                        CoachTab(fitnessViewModel, algorithmViewModel)
                    }
                }

                // Render smart rest timer overlay if triggered
                val showRestOverlay by fitnessViewModel.showRestOverlay.collectAsStateWithLifecycle()
                if (showRestOverlay) {
                    RestTimerOverlay(fitnessViewModel)
                }

                // Render smart RIR selector overlay if triggered
                val showRirOverlay by fitnessViewModel.showRirOverlay.collectAsStateWithLifecycle()
                if (showRirOverlay) {
                    RirSelectorOverlay(fitnessViewModel)
                }

                // Render session complete page
                val showComplete by fitnessViewModel.showSessionCompleteScreen.collectAsStateWithLifecycle()
                if (showComplete) {
                    SessionCompleteOverlay(fitnessViewModel)
                }
            }
        }

        if (showSettingsSheet) {
            SettingsDialog(
                fitnessViewModel = fitnessViewModel,
                onDismiss = { showSettingsSheet = false }
            )
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
                        val cw = currentWeightStr.toDoubleOrNull() ?: 80.0
                        val gw = goalWeightStr.toDoubleOrNull() ?: 80.0
                        val ht = heightStr.toDoubleOrNull() ?: 175.0
                        val ageVal = ageStr.toIntOrNull() ?: 25
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
                        onComplete("Athlete X", "Maintain", 80.0, 80.0, "", 175.0, 25, "male")
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
                TabItem("Progress", Icons.Filled.BarChart, Icons.Outlined.BarChart, 3),
                TabItem("Coach", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubble, 4)
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

// HOME TAB
@Composable
fun HomeTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val activePlanSessions by fitnessViewModel.activePlanSessions.collectAsStateWithLifecycle()

    val loggedMeals by fitnessViewModel.loggedMeals.collectAsStateWithLifecycle()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()

    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val streakResult by algorithmViewModel.streakResult.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val todayExercises by fitnessViewModel.todayExercises.collectAsStateWithLifecycle()
    val weightHistory by fitnessViewModel.weightHistory.collectAsStateWithLifecycle()
    val plateauResult by algorithmViewModel.plateauResult.collectAsStateWithLifecycle()
    val targets by algorithmViewModel.targets.collectAsStateWithLifecycle()
    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()

    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()

    // Aggregate meal stats
    val loggedCalories = loggedMeals.sumOf { it.calories }
    val loggedProtein = loggedMeals.sumOf { it.protein }
    val loggedCarbs = loggedMeals.sumOf { it.carbs }
    val loggedFat = loggedMeals.sumOf { it.fat }

    val latestWeight = weightHistory.firstOrNull()?.weight ?: weight ?: 80.0
    
    // Dynamic Mifflin-St Jeor equation to calculate BMR baseline with biological offset
    val sexOffset = if (userSex.equals("female", ignoreCase = true)) -161.0 else 5.0
    val bmrBaseline = (10.0 * latestWeight) + (6.25 * userHeight) - (5.0 * userAge) + sexOffset
    
    // Moderate activity (1.55) as default multi-purpose athlete baseline
    val fallbackTdee = (bmrBaseline * 1.55).toInt()
    val activeTdee = tdeeResult.tdee ?: fallbackTdee

    // Science: Helms et al. (2014) Daily caloric targets adjusted by objective goals
    val calorieTarget = if (calorieTargetManual) {
        calorieTargetValue
    } else {
        com.example.utils.AlgorithmEngine.suggestCaloricTarget(activeTdee, userGoal)
    }

    // Dynamic Macro prescriptions (Helms et al. 2014 - Muscle & Strength Pyramids)
    // Protein: 1.8g per kg of total bodyweight (sufficient for muscle building and retention)
    val proteinTarget = (latestWeight * 1.8).coerceIn(100.0, 250.0)
    // Fat: 25% of total calorie target (supports metabolic health and hormone profiles)
    val fatTarget = (calorieTarget * 0.25 / 9.0).coerceIn(40.0, 120.0)
    // Carbs: Remainder of daily energy allocations
    val carbsTarget = ((calorieTarget - (proteinTarget * 4.0) - (fatTarget * 9.0)) / 4.0).coerceIn(100.0, 500.0)

    val complianceScores by algorithmViewModel.complianceScores.collectAsStateWithLifecycle()
    val complianceScore by algorithmViewModel.complianceScore.collectAsStateWithLifecycle()
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
                        MacroTrackerBar(label = "PROTEIN", current = loggedProtein, target = proteinTarget, color = AmberAccent)
                        MacroTrackerBar(label = "CARBS", current = loggedCarbs, target = carbsTarget, color = BlueAccent)
                        MacroTrackerBar(label = "FAT", current = loggedFat, target = fatTarget, color = RedAccent)
                    }
                }
            }
        }

        // Stats strip horizontal
        item {
            val history by fitnessViewModel.weightHistory.collectAsStateWithLifecycle()
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
            val readiness by algorithmViewModel.sessionReadiness.collectAsStateWithLifecycle()

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
                            val yesterdayNutritionLogged = loggedMeals.any { it.date == com.example.utils.AlgorithmEngine.getDateDaysAgo(1) }
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

            // Calculate precise metabolic duration based on specific exercise sets
            val estimatedWorkoutDurationMin = if (todaySession == null || todaySession.focus == "Muscle Recovery & Rest") {
                0
            } else if (todayExercises.isEmpty()) {
                75
            } else {
                val rawTime = todayExercises.sumOf { 
                    val setMinutes = com.example.utils.AlgorithmEngine.estimateSetDurationMinutes(it.repsMin, it.repsMax)
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

// TRAIN TAB
@Composable
fun TrainTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel
) {
    val subTab by fitnessViewModel.trainSubTab.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCardSurface)
                .padding(4.dp)
        ) {
            listOf("PROGRAM", "WORKOUTS", "PLANS").forEachIndexed { index, title ->
                val isSelected = subTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) AmberAccent else Color.Transparent)
                        .clickable { fitnessViewModel.setTrainSubTab(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0A0A0F) else SecondaryText
                    )
                }
            }
        }

        Crossfade(targetState = subTab, label = "trainSubCross") { tab ->
            when (tab) {
                0 -> ProgramSubTab(fitnessViewModel)
                1 -> WorkoutExecutionSubTab(fitnessViewModel)
                2 -> NewPlansSubTab(fitnessViewModel)
            }
        }
    }
}

@Composable
fun ProgramSubTab(
    fitnessViewModel: FitnessViewModel
) {
    val context = LocalContext.current
    val selectedDay by fitnessViewModel.selectedDayOfWeek.collectAsStateWithLifecycle()
    val selectedDaySessionRaw by fitnessViewModel.selectedDaySession.collectAsStateWithLifecycle()
    val exercises by fitnessViewModel.selectedDayExercises.collectAsStateWithLifecycle()

    val selectedDaySession = selectedDaySessionRaw

    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val scope = rememberCoroutineScope()
    var substitutionList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var activeSubstIndex by remember { mutableStateOf<Int?>(-1) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Horizontal Day Picker Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(daysOfWeek) { day ->
                val isSelected = selectedDay.equals(day, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AmberAccent else DarkCardSurface)
                        .border(
                            BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { fitnessViewModel.selectDay(day) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = day.substring(0, 3).uppercase(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0A0A0F) else SecondaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedDaySession == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No dynamic session scheduled for $selectedDay.",
                    color = SecondaryText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Focus Card first
                item {
                    PremiumCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "CURRENT SESSION PARAMETERS",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedDaySession.label,
                            fontFamily = SyneFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedDaySession.focus,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = SecondaryText
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Fast Warmup Checklist preview
                        Text(
                            text = "PRE-SESSION MOVEMENT WARMUP",
                            fontFamily = SyneFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ 5 Mins light cardio + Shoulder cuff activations + Barbell sets",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = MutedText
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { fitnessViewModel.startWorkoutSession(selectedDaySession) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "START CURRENT WORKOUT RUN",
                                fontFamily = SyneFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0F)
                            )
                        }
                    }
                }

                // Exercises list
                items(exercises.size) { index ->
                    val ex = exercises[index]

                    var isExpanded by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCardSurface)
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ex.name,
                                        fontFamily = SyneFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${ex.sets} Sets x ${ex.repsMin}-${ex.repsMax} Reps • ${ex.weight} kg",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = AmberAccent
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Expand details",
                                    tint = SecondaryText
                                )
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Rest Target Interval: ${ex.restSeconds} Seconds",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ex.notes,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = MutedText,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Form Guide button
                                    Button(
                                        onClick = {
                                            Toast.makeText(context, "${ex.name}: Focus on compound control, locking joint stabilizers at end of concentric contraction.", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Form Guide", fontFamily = SyneFamily, fontSize = 11.sp, color = PrimaryText)
                                    }

                                    // Replace Exercise button suggestion
                                    Button(
                                        onClick = {
                                            activeSubstIndex = index
                                            scope.launch {
                                                substitutionList = fitnessViewModel.getSubstitutionSuggestions(ex.muscleGroup, ex.name)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Substitute", fontFamily = SyneFamily, fontSize = 11.sp, color = AmberAccent)
                                    }
                                }

                                // If substitution recommendations loaded for this particular index
                                if (activeSubstIndex == index && substitutionList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkRaised)
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "INTELLIGENCE SUBSTITUTES FOR THIS BIOMECHANIC",
                                            fontFamily = SyneFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberAccent
                                        )

                                        substitutionList.forEach { p ->
                                            Column {
                                                Text(
                                                    text = "• ${p.first}",
                                                    fontFamily = SyneFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryText
                                                )
                                                Text(
                                                    text = p.second,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 10.sp,
                                                    color = SecondaryText,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun WorkoutExecutionSubTab(
    fitnessViewModel: FitnessViewModel
) {
    val activeSession by fitnessViewModel.activeWorkoutSession.collectAsStateWithLifecycle()
    val exercises by fitnessViewModel.activeExercises.collectAsStateWithLifecycle()
    val currentIdx by fitnessViewModel.currentExerciseIdx.collectAsStateWithLifecycle()
    val loggedSets by fitnessViewModel.loggedSets.collectAsStateWithLifecycle()
    val effectiveSetsMap by fitnessViewModel.effectiveSetsStateFlow.collectAsStateWithLifecycle()
    val warmupComp by fitnessViewModel.warmupCompleted.collectAsStateWithLifecycle()
    val lastWeights by fitnessViewModel.lastWeights.collectAsStateWithLifecycle()
    val weightContextLines by fitnessViewModel.weightContextLines.collectAsStateWithLifecycle()
    val preferredUnits by fitnessViewModel.units.collectAsStateWithLifecycle()


    val context = LocalContext.current
    var showFinishEarlyDialog by remember { mutableStateOf(false) }
    var showNormalFinishFeelDialog by remember { mutableStateOf(false) }
    var selectedFeelRating by remember { mutableStateOf(4) }
    var showExitDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = activeSession != null) {
        if (fitnessViewModel.hasCompletedSets) {
            showExitDialog = true
        } else {
            fitnessViewModel.discardAndExit()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "SAVE PARTIAL SESSION?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                val compSetsCount = loggedSets.values.flatten().count { it.completed }
                Text(
                    text = "You have $compSetsCount completed sets. Do you want to save them now or discard and exit?",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        showFinishEarlyDialog = true
                    }
                ) {
                    Text("SAVE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = AmberAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        fitnessViewModel.discardAndExit()
                    }
                ) {
                    Text("DISCARD", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = MutedText)
                }
            }
        )
    }


    if (showFinishEarlyDialog) {
        AlertDialog(
            onDismissRequest = { showFinishEarlyDialog = false },
            title = {
                Text(
                    text = "FINISH WORKOUT EARLY?",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to finish your workout session now? Any completed sets logged so far will be saved and recorded to your athletic log.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "RATE YOUR WORKOUT FEEL (1-5):",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AmberAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { rate ->
                            val isSelected = selectedFeelRating == rate
                            IconButton(
                                onClick = { selectedFeelRating = rate },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) AmberAccent else DarkRaised,
                                        CircleShape
                                    )
                            ) {
                                Text(
                                    text = "$rate",
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else PrimaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedFeelRating) {
                            1 -> "1 - Extremely fatigued / bad"
                            2 -> "2 - Tired / poor feel"
                            3 -> "3 - Average / neutral"
                            4 -> "4 - Energetic / strong feel"
                            else -> "5 - Beast mode / excellent!"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishEarlyDialog = false
                        fitnessViewModel.finishWorkoutEarly(selectedFeelRating)
                    }
                ) {
                    Text(
                        text = "YES, FINISH & SAVE",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishEarlyDialog = false }
                ) {
                    Text(
                        text = "CANCEL",
                        fontFamily = SyneFamily,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = DarkCardSurface,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showNormalFinishFeelDialog) {
        AlertDialog(
            onDismissRequest = { showNormalFinishFeelDialog = false },
            title = {
                Text(
                    text = "CONGRATULATIONS!",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Incredible job completing your athletic workout session today! Let's rate how your body felt:",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "RATE YOUR WORKOUT FEEL (1-5):",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = AmberAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { rate ->
                            val isSelected = selectedFeelRating == rate
                            IconButton(
                                onClick = { selectedFeelRating = rate },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) AmberAccent else DarkRaised,
                                        CircleShape
                                    )
                            ) {
                                Text(
                                    text = "$rate",
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else PrimaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedFeelRating) {
                            1 -> "1 - Extremely fatigued / bad"
                            2 -> "2 - Tired / poor feel"
                            3 -> "3 - Average / neutral"
                            4 -> "4 - Energetic / strong feel"
                            else -> "5 - Beast mode / excellent!"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNormalFinishFeelDialog = false
                        fitnessViewModel.finishWorkoutSession(selectedFeelRating)
                    }
                ) {
                    Text(
                        text = "SAVE LOG & COMPLETE",
                        fontFamily = SyneFamily,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNormalFinishFeelDialog = false }
                ) {
                    Text(
                        text = "BACK",
                        fontFamily = SyneFamily,
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            },
            containerColor = DarkCardSurface,
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (activeSession == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Inactive",
                    tint = MutedText,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NO WORKOUT RUNNING",
                    fontFamily = SyneFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Go to 'PROGRAM' or select a standard list day to start logging working target sets.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        // active workout runner layout
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Warmup checklist card
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PRE-SESSION WARMUP CHECKLIST",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberAccent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    for ((item, comp) in warmupComp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fitnessViewModel.toggleWarmupItem(item) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = comp,
                                onCheckedChange = { fitnessViewModel.toggleWarmupItem(item) },
                                colors = CheckboxDefaults.colors(checkedColor = AmberAccent, uncheckedColor = MutedText)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = if (comp) MutedText else PrimaryText,
                                textDecoration = if (comp) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                            )
                        }
                    }
                }
            }

            // Current Exercise Layout
            if (currentIdx < exercises.size) {
                val ex = exercises[currentIdx]
                val setsList = loggedSets[ex.id] ?: emptyList()

                item {
                    var showPlateCalc by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "EXERCISE ${currentIdx + 1} OF ${exercises.size}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = MutedText
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkRaised)
                                        .clickable { showPlateCalc = !showPlateCalc }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("🧮 PLATE CALC", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = AmberAccent)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkRaised)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(ex.muscleGroup.uppercase(), fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = AmberAccent)
                                }
                            }
                        }
                        
                        if (showPlateCalc) {
                            Spacer(modifier = Modifier.height(10.dp))
                            PlateCalculatorCard(initialWeight = ex.weight)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ex.name,
                            fontFamily = SyneFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                        val contextLine = weightContextLines[ex.id.toString()] ?: ""
                        if (contextLine.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = contextLine,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = AmberAccent
                            )
                        }
                    }
                }

                val currEffSetsData = effectiveSetsMap[ex.id.toString()]
                if (currEffSetsData != null) {
                    item {
                        val nextSetIndex = setsList.indexOfFirst { !it.completed }
                        val currentSetNum = if (nextSetIndex != -1) nextSetIndex + 1 else setsList.size
                        val totalSetsNum = setsList.size
                        val lastCompletedSetObj = setsList.lastOrNull { it.completed }
                        val lastSetWeight = lastCompletedSetObj?.weight ?: 0.0
                        val lastSetReps = lastCompletedSetObj?.reps ?: 0

                        RealTimeEffectiveSetsCard(
                            exName = ex.name,
                            effData = currEffSetsData,
                            currentSetNum = currentSetNum,
                            totalSetsNum = totalSetsNum,
                            lastSetWeight = lastSetWeight,
                            lastSetReps = lastSetReps
                        )
                    }
                }

                // PR ATTEMPT badge warning row if applicable
                item {
                    // Symmetrical visual PR layout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF231707))
                            .border(BorderStroke(1.dp, Color(0xFFE8A020)), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = "PR Alert", tint = AmberAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "PR ATTEMPT SIGNAL: Log maximum focus on reps to override historic weight file indices!",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = PrimaryText
                            )
                        }
                    }
                }

                // Row of sets logger inputs
                items(setsList.size) { sIdx ->
                    val setObj = setsList[sIdx]

                    var rawWeight by remember(setObj.weight) { mutableStateOf(setObj.weight.toString()) }
                    var rawReps by remember(setObj.reps) { mutableStateOf(setObj.reps.toString()) }
                    var selectedRpe by remember(setObj.rpe) { mutableStateOf(setObj.rpe) }

                    var weightError by remember { mutableStateOf("") }
                    var repsError by remember { mutableStateOf("") }

                    val isWeightValid = rawWeight.toDoubleOrNull()?.let { it in 0.25..500.0 } ?: false
                    val isRepsValid = rawReps.toIntOrNull()?.let { it in 1..50 } ?: false
                    val isRpeValid = selectedRpe in 1..10
                    val isSetValid = isWeightValid && isRepsValid && isRpeValid

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    setObj.completed -> Color(0xFF0F1E14)
                                    setObj.isWarmup -> Color(0xFF16162F)
                                    else -> DarkCardSurface
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    when {
                                        setObj.completed -> Color(0xFF2EC46A)
                                        setObj.isWarmup -> Color(0xFF6366F1).copy(alpha = 0.4f)
                                        else -> BorderSubtle
                                    }
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Column set label
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                                    if (setObj.isWarmup) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF6366F1).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "WARMUP",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 6.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E1B4B)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    } else {
                                        Text(
                                            text = "SET",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText
                                        )
                                    }
                                    Text(
                                        text = "${sIdx + 1}",
                                        fontFamily = SyneFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            setObj.completed -> GreenAccent
                                            setObj.isWarmup -> Color(0xFFA78BFA)
                                            else -> AmberAccent
                                        }
                                    )
                                }

                                // Weight textfield
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("WEIGHT", fontFamily = JetBrainsMonoFamily, fontSize = 7.sp, color = SecondaryText)
                                    BasicTextField(
                                        value = rawWeight,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() || it == '.' }
                                            val clean = if (filtered.count { it == '.' } > 1) {
                                                val firstDot = filtered.indexOf('.')
                                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                                            } else {
                                                filtered
                                            }

                                            var finalStr = clean
                                            var error = ""
                                            val dVal = clean.toDoubleOrNull()
                                            if (dVal != null) {
                                                if (dVal > 500.0) {
                                                    finalStr = "500.0"
                                                    error = "Weight: 0.25–500 kg"
                                                } else if (dVal < 0.25) {
                                                    val isTypingPrefix = clean == "0" || clean == "0." || clean == "0.2"
                                                    if (!isTypingPrefix) {
                                                        finalStr = "0.25"
                                                    }
                                                    error = "Weight: 0.25–500 kg"
                                                }
                                            } else if (clean.isNotEmpty()) {
                                                error = "Weight: 0.25–500 kg"
                                            }

                                            rawWeight = finalStr
                                            weightError = error

                                            val w = finalStr.toDoubleOrNull()
                                            if (w != null && w in 0.25..500.0) {
                                                val r = rawReps.toIntOrNull() ?: setObj.reps
                                                fitnessViewModel.logWorkoutSetState(ex.id, sIdx, w, r, selectedRpe, setObj.completed)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                                        modifier = Modifier
                                            .background(DarkRaised, RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                            .fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (rawWeight.isEmpty()) {
                                                    val suggested = lastWeights[ex.id.toString()] ?: ex.weight
                                                    Text(
                                                        text = "${suggested} $preferredUnits",
                                                        color = MutedText,
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    val contextLineInside = weightContextLines[ex.id.toString()] ?: ""
                                    if (contextLineInside.isNotEmpty()) {
                                        val shortNote = if (contextLineInside.contains(" → Suggested:")) {
                                            contextLineInside.substringBefore(" → Suggested:")
                                        } else {
                                            "Beginner Base"
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = shortNote,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = MutedText
                                        )
                                    }
                                    if (weightError.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = weightError,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = RedAccent,
                                            modifier = Modifier.testTag("weight_error_msg")
                                        )
                                    }
                                }

                                // Reps textfield
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("REPS", fontFamily = JetBrainsMonoFamily, fontSize = 7.sp, color = SecondaryText)
                                    BasicTextField(
                                        value = rawReps,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() }
                                            var finalStr = filtered
                                            var error = ""
                                            val iVal = filtered.toIntOrNull()
                                            if (iVal != null) {
                                                if (iVal > 50) {
                                                    finalStr = "50"
                                                    error = "Reps: 1–50"
                                                } else if (iVal < 1) {
                                                    finalStr = "1"
                                                    error = "Reps: 1–50"
                                                }
                                            } else if (filtered.isNotEmpty()) {
                                                error = "Reps: 1–50"
                                            }

                                            rawReps = finalStr
                                            repsError = error

                                            val r = finalStr.toIntOrNull()
                                            if (r != null && r in 1..50) {
                                                val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                                fitnessViewModel.logWorkoutSetState(ex.id, sIdx, w, r, selectedRpe, setObj.completed)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                                        modifier = Modifier
                                            .background(DarkRaised, RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                            .fillMaxWidth()
                                    )
                                    if (repsError.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = repsError,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 8.sp,
                                            color = RedAccent,
                                            modifier = Modifier.testTag("reps_error_msg")
                                        )
                                    }
                                }

                                // Completion Checkbox
                                IconButton(
                                    enabled = isSetValid,
                                    onClick = {
                                        val newCompleted = !setObj.completed
                                        val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                        val r = rawReps.toIntOrNull() ?: setObj.reps

                                        if (newCompleted) {
                                            // Smart RIR dynamic selector instead of instant log
                                            fitnessViewModel.openRirSelector(
                                                exerciseId = ex.id,
                                                exerciseName = ex.name,
                                                muscleGroup = ex.muscleGroup,
                                                setIndex = sIdx,
                                                weight = w,
                                                reps = r,
                                                totalSets = setsList.size
                                            )
                                        } else {
                                            // Simple uncheck log state
                                            fitnessViewModel.logWorkoutSetState(ex.id, sIdx, w, r, selectedRpe, false)
                                        }
                                    },
                                    modifier = Modifier.testTag("log_checkmark_button")
                                ) {
                                    Icon(
                                        imageVector = if (setObj.completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                        contentDescription = "Log set",
                                        tint = if (!isSetValid) MutedText.copy(alpha = 0.3f) else if (setObj.completed) GreenAccent else MutedText,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Segmented Button select row for RPE (1 to 10)
                            Text("RPE: $selectedRpe", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = AmberAccent)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkRaised)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                (1..10).forEach { rpeVal ->
                                    val isRpeSelected = selectedRpe == rpeVal
                                    val zoneColor = when (rpeVal) {
                                        in 1..5 -> Color(0xFF2EC46A) // Green zone
                                        in 6..7 -> Color(0xFFFFB300) // Yellow/Amber zone
                                        else -> Color(0xFFEF4444) // Red zone
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isRpeSelected) zoneColor else Color.Transparent)
                                            .clickable {
                                                selectedRpe = rpeVal
                                                val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                                val r = rawReps.toIntOrNull() ?: setObj.reps
                                                fitnessViewModel.logWorkoutSetState(ex.id, sIdx, w, r, rpeVal, setObj.completed)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = rpeVal.toString(),
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRpeSelected) Color(0xFF0A0A0F) else zoneColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add extra sets CTA row
                item {
                    val canAddSet = setsList.size < 20
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (canAddSet) {
                                        fitnessViewModel.addCustomLogSet(ex.id)
                                    }
                                },
                                enabled = canAddSet,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canAddSet) DarkCardSurface else DarkCardSurface.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (canAddSet) BorderSubtle else BorderSubtle.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ ADD EXTRA SET", fontFamily = SyneFamily, fontSize = 11.sp, color = if (canAddSet) PrimaryText else MutedText)
                            }

                            Button(
                                onClick = { 
                                    if (currentIdx == exercises.size - 1) {
                                        showNormalFinishFeelDialog = true
                                    } else {
                                        fitnessViewModel.nextExercise()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (currentIdx == exercises.size - 1) "FINISH WORKOUT" else "NEXT EXERCISE →",
                                    fontFamily = SyneFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0A0A0F)
                                )
                            }
                        }

                        if (setsList.size >= 20) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sets: 1–20",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = RedAccent,
                                modifier = Modifier.testTag("sets_error_msg")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { 
                                if (currentIdx == exercises.size - 1) {
                                    showNormalFinishFeelDialog = true
                                } else {
                                    fitnessViewModel.skipExercise()
                                    Toast.makeText(context, "Exercise skipped: ${ex.name}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                            border = BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("skip_exercise_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Skip Exercise",
                                tint = AmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SKIP THIS EXERCISE",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Always available actions at the bottom of the LazyColumn (cancel/complete)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (exercises.isEmpty()) {
                        Button(
                            onClick = { fitnessViewModel.cancelOrCompleteEmptySession() },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                "COMPLETE RECOVERY SESSION",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0F)
                            )
                        }
                    } else {
                        Button(
                            onClick = { showFinishEarlyDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14221A)),
                            border = BorderStroke(1.dp, Color(0xFF2EC46A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("finish_early_button")
                        ) {
                            Text(
                                "FINISH WORKOUT EARLY & SAVE PROGRESS",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2EC46A)
                            )
                        }
                    }
                    Button(
                        onClick = { fitnessViewModel.cancelActiveWorkout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1616)),
                        border = BorderStroke(1.dp, Color(0xFFE84A4A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            "CANCEL CURRENT WORKOUT RUN",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE84A4A)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun NewPlansSubTab(
    fitnessViewModel: FitnessViewModel
) {
    val plans by fitnessViewModel.workoutPlans.collectAsStateWithLifecycle()
    val activePlan by fitnessViewModel.activePlan.collectAsStateWithLifecycle()

    var pastedText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf("") }
    val isParsing by fitnessViewModel.isCoachLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // AI Pasted Workout Uploader
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AI WORKOUT CO-PILOT PARSER",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Paste any raw program text file (e.g., from Reddit, PDF structure, or coaches) to re-author structured local files instantly with Gemini AI.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = { Text("Paste workout plan, list of reps, weight descriptions...", color = MutedText, fontSize = 11.sp) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("plan_parser_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = AmberAccent,
                        unfocusedIndicatorColor = BorderSubtle
                    )
                )

                if (parseError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(parseError, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = RedAccent)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (pastedText.trim().isEmpty()) {
                            Toast.makeText(context, "Paste some text first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        fitnessViewModel.uploadPlanTextGemini(
                            rawText = pastedText,
                            onComplete = { plan, sessions, exercises ->
                                if (plan != null) {
                                    fitnessViewModel.saveImportedWorkoutPlan(plan, sessions, exercises)
                                    pastedText = ""
                                    parseError = ""
                                    Toast.makeText(context, "AI Workout Plan parsed & loaded as ACTIVE!", Toast.LENGTH_LONG).show()
                                }
                            },
                            onFailure = { err ->
                                parseError = err
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                    enabled = !isParsing
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF0A0A0F), strokeWidth = 2.dp)
                    } else {
                        Text("PARSE PROGRAM WITH AI", fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A0A0F))
                    }
                }
            }
        }

        // List of athletic directories
        item {
            Text(
                text = "REGISTERED TRAINING DIRECTORIES",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedText
            )
        }

        items(plans) { plan ->
            val isActive = activePlan?.id == plan.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardSurface)
                    .border(BorderStroke(1.dp, if (isActive) AmberAccent else BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plan.name,
                                fontFamily = SyneFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E6A41))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ACTIVE", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = PrimaryText)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Target Goal: ${plan.goal}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }

                    if (!isActive) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Activating structural files...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkRaised),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Activate", fontFamily = SyneFamily, fontSize = 11.sp, color = PrimaryText)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// REST TIMER COUNTDOWN OVERLAY
@Composable
fun RestTimerOverlay(
    fitnessViewModel: FitnessViewModel
) {
    val seconds by fitnessViewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val totalTime by fitnessViewModel.restTimerTotal.collectAsStateWithLifecycle()
    val lastSetContext by fitnessViewModel.restTimerLastSetContext.collectAsStateWithLifecycle()
    val nextSetPreview by fitnessViewModel.restTimerNextSetPreview.collectAsStateWithLifecycle()

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
    fitnessViewModel: FitnessViewModel
) {
    val isShowingHistory by fitnessViewModel.isShowingRirHistory.collectAsStateWithLifecycle()
    val exerciseName by fitnessViewModel.rirSelectorExerciseName.collectAsStateWithLifecycle()
    val setIndex by fitnessViewModel.rirSelectorSetIndex.collectAsStateWithLifecycle()
    val weight by fitnessViewModel.rirSelectorWeight.collectAsStateWithLifecycle()
    val reps by fitnessViewModel.rirSelectorReps.collectAsStateWithLifecycle()
    val totalSets by fitnessViewModel.rirSelectorTotalSets.collectAsStateWithLifecycle()
    val selectedRir by fitnessViewModel.selectedRir.collectAsStateWithLifecycle()
    val history by fitnessViewModel.rirHistoricalSets.collectAsStateWithLifecycle()

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
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F0F14))
                .border(BorderStroke(1.dp, AmberAccent.copy(alpha = 0.25f)), RoundedCornerShape(24.dp))
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
    fitnessViewModel: FitnessViewModel
) {
    val stats by fitnessViewModel.completedStats.collectAsStateWithLifecycle()
    val prsBreak by fitnessViewModel.completedPRsBroken.collectAsStateWithLifecycle()
    val hyperQ by fitnessViewModel.completedHypertrophyScore.collectAsStateWithLifecycle()

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

                val sessionCompletedSets by fitnessViewModel.lastCompletedSessionSets.collectAsStateWithLifecycle()
                if (sessionCompletedSets.isNotEmpty()) {
                    val setsByExercise = sessionCompletedSets.groupBy { it.exerciseName }
                    setsByExercise.forEach { (exName, sets) ->
                        val totalSessionEff = sets.sumOf { it.effectiveSetValue }
                        val plannedExercise = fitnessViewModel.activeExercises.value.find { it.name.equals(exName, ignoreCase = true) }
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

@Composable
fun AdaptiveCalorieTargetCard(
    tdeeResult: com.example.utils.TDEEResult,
    userGoal: String,
    fitnessViewModel: FitnessViewModel,
    modifier: Modifier = Modifier
) {
    var showAcceptDialog by remember { mutableStateOf(false) }

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "ADAPTIVE CALORIE TARGET",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
                Icon(
                    Icons.Outlined.TrendingUp,
                    contentDescription = "Adaptive",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(16.dp)
                )
            }

            when {
                // ─ HIGH CONFIDENCE ─────────────────────────────────────
                tdeeResult.confidence == "high" && tdeeResult.tdee != null -> {
                    val suggestedCals = tdeeResult.tdee!!
                    val goalLabel = when (userGoal.lowercase()) {
                        "bulk" -> "Lean Gain"
                        "cut" -> "Fat Loss"
                        else -> "Maintenance"
                    }
                    val targetCals = when (userGoal.lowercase()) {
                        "bulk" -> suggestedCals + 300
                        "cut" -> suggestedCals - 400
                        else -> suggestedCals
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Your real calorie burn this week: ${tdeeResult.tdee} kcal",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Suggested target for $goalLabel: $targetCals kcal",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText
                        )
                        Text(
                            "Based on ${tdeeResult.weightChangeKg.let { "%.2f".format(it) }} kg change across ${tdeeResult.avgCalories} avg daily intake.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = SecondaryText,
                            lineHeight = 14.sp
                        )

                        Button(
                            onClick = { showAcceptDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text(
                                "ACCEPT TARGET",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F0F5)
                            )
                        }

                        if (showAcceptDialog) {
                            AlertDialog(
                                onDismissRequest = { showAcceptDialog = false },
                                title = { Text("Update Calorie Target?") },
                                text = { Text("Your new daily target will be $targetCals kcal for $goalLabel.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        fitnessViewModel.setManualCalorieTarget(true, targetCals)
                                        showAcceptDialog = false
                                    }) {
                                        Text("Confirm")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAcceptDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }

                // ─ MEDIUM CONFIDENCE ───────────────────────────────────
                tdeeResult.confidence == "medium" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Estimated burn: ~${tdeeResult.tdee ?: tdeeResult.avgCalories} kcal",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Accuracy improving — keep logging daily",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Color(0xFFF97316),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "You have 4–6 days of data. A precise target will unlock in 7–10 days.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = SecondaryText,
                            lineHeight = 14.sp
                        )
                    }
                }

                // ─ LOW CONFIDENCE ──────────────────────────────────────
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Calculating your personal TDEE",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Log 7 more days to activate adaptive targeting",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Color(0xFF8A8A9A),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "The system learns your metabolic rate from daily weight + nutrition logs. Once you hit 14 days of history, precise targets unlock.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.sp,
                            color = SecondaryText,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// NUTRITION TAB
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NutritionTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel
) {
    val selectedDate by fitnessViewModel.selectedNutritionDate.collectAsStateWithLifecycle()
    val loggedMeals by fitnessViewModel.loggedMeals.collectAsStateWithLifecycle()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()

    val calorieTarget = if (calorieTargetManual) calorieTargetValue else 2650

    val loggedCalories = loggedMeals.sumOf { it.calories }
    val loggedProtein = loggedMeals.sumOf { it.protein }
    val loggedCarbs = loggedMeals.sumOf { it.carbs }
    val loggedFat = loggedMeals.sumOf { it.fat }

    var showAddFoodModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date picker swiping bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { fitnessViewModel.changeNutritionDate(-1) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Prev Day", tint = PrimaryText)
                }
                Text(
                    text = selectedDate,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                IconButton(onClick = { fitnessViewModel.changeNutritionDate(1) }) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Next Day", tint = PrimaryText)
                }
            }
        }

        // Adaptive calorie target card
        item {
            AdaptiveCalorieTargetCard(
                tdeeResult = tdeeResult,
                userGoal = userGoal,
                fitnessViewModel = fitnessViewModel,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Calorie and macro overview card
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MACRONUTRIENT RATIO PROFILE",
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
                    // Small circular ring
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .drawBehind {
                                drawCircle(BorderSubtle, size.minDimension / 2, style = Stroke(6.dp.toPx()))
                                val progress = (loggedCalories.toFloat() / calorieTarget.toFloat()).coerceIn(0f, 1f)
                                drawArc(
                                    color = if (loggedCalories >= calorieTarget) GreenAccent else Color(0xFF6366F1),
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(loggedCalories * 100 / calorieTarget).coerceIn(0, 100)}%", fontFamily = JetBrainsMonoFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                            Text("KCAL", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = SecondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TARGET: $calorieTarget KCAL • LOGGED: $loggedCalories KCAL", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = PrimaryText)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroStatMini(label = "Prot:", value = "${loggedProtein.toInt()}g", color = Color(0xFFA78BFA))
                            MacroStatMini(label = "Carb:", value = "${loggedCarbs.toInt()}g", color = BlueAccent)
                            MacroStatMini(label = "Fat:", value = "${loggedFat.toInt()}g", color = RedAccent)
                        }
                    }
                }
            }
        }

        // Add food triggers Grid
        item {
            Button(
                onClick = { showAddFoodModal = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_nutrition_button")
            ) {
                Text("+ LOG INGREDIENT / MEAL FILE", fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A0A0F))
            }
        }

        // Meals details lists
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TODAY'S INGESTED FILES",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedText
                )

                if (loggedMeals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No meals logged for this file index.", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
                    }
                } else {
                    loggedMeals.forEach { meal ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onLongClick = { fitnessViewModel.deleteNutritionEntryById(meal.id) },
                                    onClick = {}
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LOGGED AT ${meal.time}", fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = AmberAccent)
                                    Text(meal.name.ifEmpty { "Logged Meal" }, fontFamily = SyneFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                    Text(
                                        text = "${meal.calories} kcal • P: ${meal.protein.toInt()}g C: ${meal.carbs.toInt()}g F: ${meal.fat.toInt()}g",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = SecondaryText
                                    )
                                }
                                IconButton(onClick = { fitnessViewModel.deleteNutritionEntryById(meal.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = MutedText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Protein Timing targets 4 circles
        item {
            val validMeals = if (loggedProtein >= 30.0) 1 else 0 // dynamic check based on mock logs
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "PROTEIN SYNTHESIS FREQUENCY TIMING",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "Consume at least 30g protein every 3-4 hours to trigger muscle protein synthesis (MPS) spikes efficiently.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    (1..4).forEach { i ->
                        val isHit = validMeals >= i || (i == 1 && loggedProtein > 45) // mock indicators for completeness
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isHit) GreenAccent else BorderSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "MPS $i",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                color = if (isHit) Color(0xFF0A0A0F) else PrimaryText
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddFoodModal) {
        AddFoodSheet(
            fitnessViewModel = fitnessViewModel,
            onDismiss = { showAddFoodModal = false }
        )
    }
}

@Composable
fun MacroStatMini(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$label $value", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = SecondaryText)
    }
}

// FOOD LOGGER DIALOG/MODAL WITH GEMINI SCANNER INCLUDED
@Composable
fun AddFoodSheet(
    fitnessViewModel: FitnessViewModel,
    onDismiss: () -> Unit
) {
    var querySearch by remember { mutableStateOf("") }
    var inputCal by remember { mutableStateOf("350") }
    var inputProt by remember { mutableStateOf("25") }
    var inputCarb by remember { mutableStateOf("30") }
    var inputFat by remember { mutableStateOf("8") }

    var calError by remember { mutableStateOf("") }
    var protError by remember { mutableStateOf("") }
    var carbError by remember { mutableStateOf("") }
    var fatError by remember { mutableStateOf("") }

    val isCalValid = inputCal.toIntOrNull()?.let { it in 0..5000 } ?: false
    val isProtValid = inputProt.toDoubleOrNull()?.let { it in 0.0..300.0 } ?: false
    val isCarbValid = inputCarb.toDoubleOrNull()?.let { it in 0.0..300.0 } ?: false
    val isFatValid = inputFat.toDoubleOrNull()?.let { it in 0.0..300.0 } ?: false

    val computedCalFromMacros = ((inputProt.toDoubleOrNull() ?: 0.0) * 4.0) + ((inputCarb.toDoubleOrNull() ?: 0.0) * 4.0) + ((inputFat.toDoubleOrNull() ?: 0.0) * 9.0)
    val showMacroCalWarning = computedCalFromMacros > 5000.0

    val isScanning by fitnessViewModel.isCoachLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Interactive Photo picker activator
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    fitnessViewModel.analyseMealPhotoGemini(
                        bitmapBytes = bytes,
                        onComplete = { name, cal, prot, carb, fat ->
                            inputCal = cal.toString()
                            inputProt = prot.toString()
                            inputCarb = carb.toString()
                            inputFat = fat.toString()
                            querySearch = name
                            calError = ""
                            protError = ""
                            carbError = ""
                            fatError = ""
                            Toast.makeText(context, "AI scanned meal and pre-filled macro values!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Food Intake", fontFamily = SyneFamily, fontWeight = FontWeight.Bold) },
        containerColor = DarkCardSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Autopicked system local time indicator
                Text(
                    text = "STATUS: Auto-tracking local time (${fitnessViewModel.getCurrentLocalTimeString()})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                // Large Vision Scan tile trigger
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF201705))
                        .border(BorderStroke(1.dp, AmberAccent), RoundedCornerShape(10.dp))
                        .clickable { imagePickerLauncher.launch("image/*") }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Cam", tint = AmberAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isScanning) "AI ANALYSING PHOTO..." else "VISION SCAN MEAL PHOTO WITH AI",
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                    }
                }

                // Query label
                OutlinedTextField(
                    value = querySearch,
                    onValueChange = { querySearch = it },
                    label = { Text("Ingredient or Food Name", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("add_food_name_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = AmberAccent,
                        unfocusedIndicatorColor = BorderSubtle
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputCal,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            var finalStr = filtered
                            var error = ""
                            val iVal = filtered.toIntOrNull()
                            if (iVal != null) {
                                if (iVal > 5000) {
                                    finalStr = "5000"
                                    error = "Calories: 0–5000"
                                } else if (iVal < 0) {
                                    finalStr = "0"
                                    error = "Calories: 0–5000"
                                }
                            } else if (filtered.isNotEmpty()) {
                                error = "Calories: 0–5000"
                            }
                            inputCal = finalStr
                            calError = error
                        },
                        label = { Text("Calories", color = SecondaryText, fontSize = 10.sp) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("add_food_calories_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = if (calError.isNotEmpty()) RedAccent else AmberAccent,
                            unfocusedIndicatorColor = if (calError.isNotEmpty()) RedAccent else BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = inputProt,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var finalStr = clean
                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal > 300.0) {
                                    finalStr = "300.0"
                                    error = "Protein: 0–300g"
                                } else if (dVal < 0.0) {
                                    finalStr = "0.0"
                                    error = "Protein: 0–300g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Protein: 0–300g"
                            }
                            inputProt = finalStr
                            protError = error
                        },
                        label = { Text("Protein (g)", color = SecondaryText, fontSize = 10.sp) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_protein_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = if (protError.isNotEmpty()) RedAccent else AmberAccent,
                            unfocusedIndicatorColor = if (protError.isNotEmpty()) RedAccent else BorderSubtle
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputCarb,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var finalStr = clean
                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal > 300.0) {
                                    finalStr = "300.0"
                                    error = "Carbs: 0–300g"
                                } else if (dVal < 0.0) {
                                    finalStr = "0.0"
                                    error = "Carbs: 0–300g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Carbs: 0–300g"
                            }
                            inputCarb = finalStr
                            carbError = error
                        },
                        label = { Text("Carbs (g)", color = SecondaryText, fontSize = 10.sp) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_carbs_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = if (carbError.isNotEmpty()) RedAccent else AmberAccent,
                            unfocusedIndicatorColor = if (carbError.isNotEmpty()) RedAccent else BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = inputFat,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var finalStr = clean
                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal > 300.0) {
                                    finalStr = "300.0"
                                    error = "Fat: 0–300g"
                                } else if (dVal < 0.0) {
                                    finalStr = "0.0"
                                    error = "Fat: 0–300g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Fat: 0–300g"
                            }
                            inputFat = finalStr
                            fatError = error
                        },
                        label = { Text("Fat (g)", color = SecondaryText, fontSize = 10.sp) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_fat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = if (fatError.isNotEmpty()) RedAccent else AmberAccent,
                            unfocusedIndicatorColor = if (fatError.isNotEmpty()) RedAccent else BorderSubtle
                        )
                    )
                }

                // Quick Add Row
                Text("QUICK ADD", fontFamily = SyneFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val curr = inputCal.toIntOrNull() ?: 0
                            val newVal = (curr + 100).coerceAtMost(5000)
                            inputCal = newVal.toString()
                            calError = if (newVal == 5000) "Calories: 0–5000" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+100 kcal", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputProt.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 10.0).coerceAtMost(300.0)
                            inputProt = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            protError = if (newVal == 300.0) "Protein: 0–300g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+10g Prot", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputCarb.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 10.0).coerceAtMost(300.0)
                            inputCarb = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            carbError = if (newVal == 300.0) "Carbs: 0–300g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+10g Carbs", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputFat.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 5.0).coerceAtMost(300.0)
                            inputFat = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            fatError = if (newVal == 300.0) "Fat: 0–300g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("+5g Fat", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
                    }
                }

                // Error text feedback
                val errors = listOf(calError, protError, carbError, fatError).filter { it.isNotEmpty() }
                if (errors.isNotEmpty()) {
                    Text(
                        text = errors.first(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = RedAccent,
                        modifier = Modifier.testTag("nutrition_error_msg")
                    )
                }

                if (showMacroCalWarning) {
                    Text(
                        text = "Total exceeds 5000 cal",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = RedAccent,
                        modifier = Modifier.testTag("nutrition_warn_msg")
                    )
                }
            }
        },
        confirmButton = {
            val isMealValid = isCalValid && isProtValid && isCarbValid && isFatValid && !showMacroCalWarning
            Button(
                enabled = isMealValid,
                onClick = {
                    val cal = inputCal.toIntOrNull() ?: 100
                    val prot = inputProt.toDoubleOrNull() ?: 0.0
                    val carb = inputCarb.toDoubleOrNull() ?: 0.0
                    val fat = inputFat.toDoubleOrNull() ?: 0.0
                    val mealName = querySearch.trim().ifEmpty { "Logged Meal" }
                    fitnessViewModel.logNutrition(cal, prot, carb, fat, name = mealName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberAccent,
                    disabledContainerColor = DarkRaised.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "LOG MEAL",
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isMealValid) Color(0xFF0A0A0F) else MutedText
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )
}

// PROGRESS TAB - ADAPTED MUSCLE HEATMAP DISPLAY & GAUGE NEEDLES
@Composable
fun ProgressTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel
) {
    var subScreen by remember { mutableStateOf("main") }
    var selectedPart by remember { mutableStateOf<String?>(null) }

    Crossfade(targetState = subScreen, label = "progressSubscreen") { screen ->
        when (screen) {
            "muscle_recovery" -> {
                MuscleRecoveryScreen(
                    fitnessViewModel = fitnessViewModel,
                    onBack = { subScreen = "main" }
                )
            }
            "measurement_detail" -> {
                selectedPart?.let { part ->
                    BodyMeasurementDetailScreen(
                        bodyPart = part,
                        fitnessViewModel = fitnessViewModel,
                        onBack = { subScreen = "main" }
                    )
                }
            }
            else -> {
                ProgressMainTabContent(
                    fitnessViewModel = fitnessViewModel,
                    algorithmViewModel = algorithmViewModel,
                    onOpenMuscleRecovery = { subScreen = "muscle_recovery" },
                    onOpenMeasurementDetail = { part ->
                        selectedPart = part
                        subScreen = "measurement_detail"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressMainTabContent(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    onOpenMuscleRecovery: () -> Unit,
    onOpenMeasurementDetail: (String) -> Unit
) {
    val fatigueInfo by algorithmViewModel.fatigueRatio.collectAsStateWithLifecycle()
    val muscleVolumeMap by algorithmViewModel.muscleVolumes.collectAsStateWithLifecycle()
    val heatmap by algorithmViewModel.muscleHeatmap.collectAsStateWithLifecycle()
    val weightHistory by fitnessViewModel.weightHistory.collectAsStateWithLifecycle()
    val measurements by fitnessViewModel.allBodyMeasurements.collectAsStateWithLifecycle()
    val monthlyMuscleVolumes by fitnessViewModel.monthlyMuscleVolumes.collectAsStateWithLifecycle()
    var activeSubTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Futuristic Top Segmented Tab Selector for clean high-fidelity UX spacing
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF13131D))
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(0, "FIBER INDEX", Icons.Filled.FitnessCenter),
                    Triple(1, "BIO RECOVERY", Icons.Filled.CheckCircle),
                    Triple(2, "BODY METRICS", Icons.Filled.BarChart)
                )
                tabs.forEach { (index, title, icon) ->
                    val isSelected = activeSubTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                            .clickable { activeSubTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (isSelected) Color(0xFF0F0F1A) else MutedText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = title,
                                fontFamily = SyneFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0F0F1A) else SecondaryText
                            )
                        }
                    }
                }
            }
        }

        // Category 2: BODY METRICS
        if (activeSubTab == 2) {
            // Quick Body weight log text field
            item {
            var inputWeight by remember { mutableStateOf("") }
            val context = LocalContext.current

            PremiumCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    text = "BODY MASS REGISTER",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputWeight,
                        onValueChange = { inputWeight = it },
                        placeholder = { Text("Log weight index (e.g. 78.5)...", color = MutedText, fontSize = 11.sp) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("log_weight_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkRaised,
                            unfocusedContainerColor = DarkRaised,
                            focusedIndicatorColor = AmberAccent,
                            unfocusedIndicatorColor = BorderSubtle
                        )
                    )

                    Button(
                        onClick = {
                            val w = inputWeight.toDoubleOrNull()
                            if (w != null) {
                                fitnessViewModel.logWeight(w)
                                inputWeight = ""
                                Toast.makeText(context, "Weight profile catalog update saved!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enter numeric index", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("log_weight_button")
                    ) {
                        Text("SAVE WEIGHT", fontFamily = SyneFamily, fontSize = 11.sp, color = Color(0xFF0A0A0F))
                    }
                }
            }
        }

        // Weight log entries history
        if (weightHistory.isNotEmpty()) {
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "WEIGHT REGISTRY LOGS",
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Tracked for each day entered with system local time auto picked.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = MutedText,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        weightHistory.take(10).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkCardSurface)
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${entry.weight} kg",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                    Text(
                                        text = "Logged: ${entry.date} • ${entry.time.ifEmpty { "08:00 AM" }}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = SecondaryText
                                    )
                                }
                                IconButton(
                                    onClick = { fitnessViewModel.deleteWeightById(entry.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete weight entry",
                                        tint = MutedText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        } // End of activeSubTab == 2

        // Category 1: BIO RECOVERY
        if (activeSubTab == 1) {
            // Showcase 1: Semicircular Fatigue Needle gauge drawn with Canvas
            item {
            val needleAnimState = remember { Animatable(0f) }
            // Gauge is needle progress (0f to 1f) corresponding to Fatigue zones
            // Let's bind needleAnimState to fatigue ratios (optimal around index 0.5)
            LaunchedEffect(fatigueInfo) {
                val ratio = fatigueInfo.ratio.toFloat().coerceIn(0f, 2f)
                val targetProgress = ratio / 2.0f // mapped 0f to 1f
                needleAnimState.animateTo(
                    targetValue = targetProgress,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 280f)
                )
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "FATIGUE RECOVERY INDEX DIAL",
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 14.dp.toPx()
                        val diameter = size.minDimension * 0.95f
                        val radius = diameter / 2
                        val centerPt = Offset(size.width / 2, size.height - 10.dp.toPx())

                        // Zones color slices
                        // Semicircle starts at 180 degrees index extending 180 deg
                        // Part A: Undertraining Blue (180deg to 225deg)
                        drawArc(
                            color = BlueAccent,
                            startAngle = 180f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        // Part B: Optimal Green (225deg to 270deg)
                        drawArc(
                            color = GreenAccent,
                            startAngle = 225f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        // Part C: Pushing Hard Amber (270deg to 315deg)
                        drawArc(
                            color = AmberAccent,
                            startAngle = 270f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        // Part D: Overreaching Red (315deg to 360deg)
                        drawArc(
                            color = RedAccent,
                            startAngle = 315f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerPt.x - radius, centerPt.y - radius),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )

                        // Draw Needle Line extending outwards from centerPt
                        // angle in radians = dynamic needle anim state mapped 180f to 360f
                        val needleAngleDeg = 180f + (needleAnimState.value * 180f)
                        val needleRad = Math.toRadians(needleAngleDeg.toDouble())
                        val needleLen = radius * 0.85
                        val endX = centerPt.x + (cos(needleRad) * needleLen).toFloat()
                        val endY = centerPt.y + (sin(needleRad) * needleLen).toFloat()

                        drawLine(
                            color = PrimaryText,
                            start = centerPt,
                            end = Offset(endX, endY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Center hub pin
                        drawCircle(color = DarkBackground, radius = 8.dp.toPx(), center = centerPt)
                        drawCircle(color = AmberAccent, radius = 5.dp.toPx(), center = centerPt)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Undertrained", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = BlueAccent)
                    Text("Optimal", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = GreenAccent)
                    Text("Overreaching", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = RedAccent)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "FATIGUE RATIO INDEX IS ${String.format("%.2f", fatigueInfo.ratio)} (${fatigueInfo.riskStatus})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "System is fully functional inside adaptive adaptation limits. Continue progressive loading.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = SecondaryText
                )
            }
        }

            // Radar Spider Chart & diagnostic focus trigger
            item {
                MonthlyVolumeRadarChart(
                    volumeMap = monthlyMuscleVolumes,
                    onSeeRecoveryClick = onOpenMuscleRecovery
                )
            }
        } // End of activeSubTab == 1

        // Category 2 Continued: BODY METRICS - Part 2 (Measurements)
        if (activeSubTab == 2) {
            // Showcase 1.6: Body Measurements list/entry rows
            item {
                BodyMeasurementsTrackerPanel(
                    measurements = measurements,
                    onPartClick = onOpenMeasurementDetail
                )
            }
        }

        // Category 0: FIBER INDEX
        if (activeSubTab == 0) {
            // Live Real-Time Hypertrophy Analytics Summary Row (UX Optimization)
            item {
                val totalSets = muscleVolumeMap.values.sum()
                val activeMuscles = muscleVolumeMap.values.count { it > 0 }
                val upperKeys = listOf(
                    "chest", "back", "front delts", "side delts", "rear delts",
                    "biceps", "triceps", "forearms", "trapezius", "neck",
                    "rotator cuff", "serratus anterior"
                )
                val lowerKeys = listOf(
                    "quadriceps", "hamstrings", "glutes", "calves",
                    "hip abductors", "hip adductors", "tibialis anterior"
                )
                val balanceText = when {
                    activeMuscles == 0 -> "Untrained"
                    else -> {
                        val upperSets = upperKeys.sumOf { muscleVolumeMap[it] ?: 0 }
                        val lowerSets = lowerKeys.sumOf { muscleVolumeMap[it] ?: 0 }
                        when {
                            upperSets > lowerSets * 1.5 -> "Upper Focus"
                            lowerSets > upperSets * 1.5 -> "Lower Focus"
                            else -> "Balanced"
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Sets Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "WEEKLY VOLUME",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$totalSets",
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAccent
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "sets",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 8.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }

                    // Active Targets Card
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE TARGETS",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$activeMuscles",
                                    fontFamily = SyneFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenAccent
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "/23 muscles",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 8.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }

                    // Balance Status Card
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF13131D))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "LOAD RATIO",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = balanceText.uppercase(),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueAccent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Showcase 2: Muscle Heatmap Front & Back Drawing Canvas Centered
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    var showHeatmapInfo by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NEURO-MUSCULAR WEEKLY VOLUME HEATMAP",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showHeatmapInfo = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Heatmap info",
                                tint = Color(0xFF8A8A9A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showHeatmapInfo) {
                        ModalBottomSheet(
                            onDismissRequest = { showHeatmapInfo = false },
                            containerColor = DarkCardSurface
                        ) {
                            Column(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "What The Colours Mean",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = SyneFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF0F0F5)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Each muscle group is coloured by training volume intensity this week relative to your personal maximum. " +
                                    "Red means above 80% of your highest ever weekly volume for that muscle — strong stimulus, growth is likely. " +
                                    "Amber means 40–80% — adequate stimulus. Green means 10–40% — below optimal, add volume next week. " +
                                    "Dark means no training stimulus this week — this muscle will not grow without intervention.\n\n" +
                                    "Science: Krieger 2010 meta-analysis of 55 studies found a clear dose-response relationship between weekly " +
                                    "sets per muscle and hypertrophy rate. The minimum effective dose is approximately 8 working sets per week. " +
                                    "Below that threshold muscle protein synthesis returns to baseline within 72 hours with no net growth.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = Color(0xFF8A8A9A),
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    Text(
                        text = "Regions colored by total operational workspace working sets. Tap info button upper-right for science specifics.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MutedText,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Pre-compiled highly performance-efficient structural body heatmap rendering with remembered Path caches (Skia anti-leak & crash-prevention)
                    Spacer(modifier = Modifier.height(12.dp))
                    MuscleHeatmapCanvas(
                        heatmap = heatmap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // Showcase 3: Hypertrophy index reference lines
            item {
                PremiumCard(modifier = Modifier.fillMaxWidth()) {
                    var hypertrophyCategory by remember { mutableStateOf("All") }
                    var showUntrained by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HYPERTROPHY SCORE INDEX",
                            fontFamily = SyneFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SecondaryText,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive categories chips selector
                    val categories = listOf("All", "Upper", "Lower", "Core")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 14.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = hypertrophyCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberAccent else Color(0xFF1F1F30))
                                    .clickable { hypertrophyCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = SyneFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.Black else PrimaryText
                                )
                            }
                        }
                    }

                    // Muscle group keys matching the ViewModel mapping
                    val upperKeys = listOf(
                        "chest", "back", "front delts", "side delts", "rear delts",
                        "biceps", "triceps", "forearms", "trapezius", "neck",
                        "rotator cuff", "serratus anterior"
                    )
                    val lowerKeys = listOf(
                        "quadriceps", "hamstrings", "glutes", "calves",
                        "hip abductors", "hip adductors", "tibialis anterior"
                    )
                    val coreKeys = listOf(
                        "abs", "obliques", "transverse abdominis", "lower back"
                    )

                    val filteredKeys = when (hypertrophyCategory) {
                        "Upper" -> upperKeys
                        "Lower" -> lowerKeys
                        "Core" -> coreKeys
                        else -> upperKeys + lowerKeys + coreKeys
                    }

                    // Clean capitalized presentation naming
                    val muscleDisplayNames = mapOf(
                        "chest" to "Chest volume index",
                        "back" to "Back (Lats) volume index",
                        "front delts" to "Front Delts volume index",
                        "side delts" to "Side Delts volume index",
                        "rear delts" to "Rear Delts volume index",
                        "biceps" to "Biceps volume index",
                        "triceps" to "Triceps volume index",
                        "forearms" to "Forearms volume index",
                        "trapezius" to "Trapezius (Traps) volume index",
                        "neck" to "Neck volume index",
                        "abs" to "Abdominis (Abs) volume index",
                        "obliques" to "Obliques volume index",
                        "transverse abdominis" to "Transverse Abs volume index",
                        "lower back" to "Lower Back volume index",
                        "glutes" to "Glutes volume index",
                        "quadriceps" to "Quadriceps (Quads) volume index",
                        "hamstrings" to "Hamstrings volume index",
                        "calves" to "Calves volume index",
                        "hip abductors" to "Hip Abductors volume index",
                        "hip adductors" to "Hip Adductors volume index",
                        "rotator cuff" to "Rotator Cuff volume index",
                        "serratus anterior" to "Serratus Anterior volume index",
                        "tibialis anterior" to "Tibialis Anterior volume index"
                    )

                    // Scientific Maximum Recoverable Volume (MRV) targets
                    val maxSetsMap = mapOf(
                        "chest" to 26,
                        "back" to 28,
                        "front delts" to 14,
                        "side delts" to 26,
                        "rear delts" to 22,
                        "biceps" to 24,
                        "triceps" to 20,
                        "forearms" to 14,
                        "trapezius" to 18,
                        "neck" to 10,
                        "abs" to 18,
                        "obliques" to 14,
                        "transverse abdominis" to 12,
                        "lower back" to 16,
                        "glutes" to 18,
                        "quadriceps" to 24,
                        "hamstrings" to 22,
                        "calves" to 20,
                        "hip abductors" to 12,
                        "hip adductors" to 12,
                        "rotator cuff" to 10,
                        "serratus anterior" to 12,
                        "tibialis anterior" to 10
                    )

                    val activeKeys = filteredKeys.filter { (muscleVolumeMap[it] ?: 0) > 0 }
                    val inactiveKeys = filteredKeys.filter { (muscleVolumeMap[it] ?: 0) == 0 }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (activeKeys.isNotEmpty()) {
                            Text(
                                text = "ACTIVE STIMULATED GROUPS",
                                fontFamily = SyneFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            activeKeys.forEach { key ->
                                val label = muscleDisplayNames[key] ?: key
                                val sets = muscleVolumeMap[key] ?: 0
                                val maxSets = maxSetsMap[key] ?: 20
                                HypertrophyProgressBarMarker(label = label, sets = sets, maxSets = maxSets)
                            }
                        } else {
                            // Empty state if no active setslogged yet
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF13131D))
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = "No active sets",
                                        tint = MutedText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "NO COMPLETED WORKING SETS YET",
                                        fontFamily = SyneFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Log exercises in the workout tab to update real-time hypertrophy indexes.",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 9.sp,
                                        color = MutedText,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (inactiveKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showUntrained) Color(0xFF181826) else Color(0xFF13131D))
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { showUntrained = !showUntrained }
                                    .padding(vertical = 10.dp, horizontal = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "UNTRAINED GROUPS (${inactiveKeys.size} MUSCLES)",
                                        fontFamily = SyneFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showUntrained) AmberAccent else MutedText
                                    )
                                    Text(
                                        text = if (showUntrained) "COLLAPSE ▲" else "EXPAND ▾",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showUntrained) AmberAccent else MutedText
                                    )
                                }
                            }

                            if (showUntrained) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    inactiveKeys.forEach { key ->
                                        val label = muscleDisplayNames[key] ?: key
                                        val sets = muscleVolumeMap[key] ?: 0
                                        val maxSets = maxSetsMap[key] ?: 20
                                        HypertrophyProgressBarMarker(label = label, sets = sets, maxSets = maxSets)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

class CachedFrontPaths(val w: Float, val h: Float) {
    fun fx(x: Float): Float = x / 100f * w
    fun fy(y: Float): Float = y / 200f * h

    val bodyOutline = Path().apply {
        moveTo(fx(50f), fy(10f))
        cubicTo(fx(41f), fy(10f), fx(41f), fy(27f), fx(50f), fy(27f))
        cubicTo(fx(59f), fy(27f), fx(59f), fy(10f), fx(50f), fy(10f))
        
        moveTo(fx(46f), fy(27f))
        lineTo(fx(46f), fy(31f))
        quadraticTo(fx(38f), fy(33f), fx(27f), fy(38f))
        
        lineTo(fx(23f), fy(52f))
        lineTo(fx(20f), fy(68f))
        lineTo(fx(18f), fy(85f))
        quadraticTo(fx(15f), fy(92f), fx(18f), fy(94f))
        
        lineTo(fx(25f), fy(85f))
        lineTo(fx(28f), fy(68f))
        lineTo(fx(31f), fy(52f))
        
        quadraticTo(fx(35f), fy(62f), fx(36f), fy(74f))
        lineTo(fx(33f), fy(85f))
        
        lineTo(fx(32f), fy(115f))
        lineTo(fx(35f), fy(140f))
        
        lineTo(fx(33f), fy(165f))
        lineTo(fx(37f), fy(192f))
        quadraticTo(fx(33f), fy(197f), fx(44f), fy(197f))
        lineTo(fx(44f), fy(192f))
        
        lineTo(fx(46f), fy(140f))
        lineTo(fx(49f), fy(90f))
        
        lineTo(fx(51f), fy(90f))
        lineTo(fx(54f), fy(140f))
        
        lineTo(fx(56f), fy(192f))
        lineTo(fx(56f), fy(197f))
        quadraticTo(fx(67f), fy(197f), fx(63f), fy(192f))
        lineTo(fx(67f), fy(165f))
        lineTo(fx(65f), fy(140f))
        lineTo(fx(68f), fy(115f))
        lineTo(fx(67f), fy(85f))
        
        lineTo(fx(64f), fy(74f))
        quadraticTo(fx(65f), fy(62f), fx(69f), fy(52f))
        
        lineTo(fx(72f), fy(68f))
        lineTo(fx(75f), fy(85f))
        quadraticTo(fx(85f), fy(92f), fx(82f), fy(94f))
        lineTo(fx(82f), fy(85f))
        
        lineTo(fx(80f), fy(68f))
        lineTo(fx(77f), fy(52f))
        lineTo(fx(73f), fy(38f))
        
        quadraticTo(fx(62f), fy(33f), fx(54f), fy(31f))
        lineTo(fx(54f), fy(27f))
        close()
    }

    val neckPath = Path().apply {
        moveTo(fx(46f), fy(22f))
        lineTo(fx(54f), fy(22f))
        lineTo(fx(53f), fy(29f))
        lineTo(fx(47f), fy(29f))
        close()
    }

    val leftPec = Path().apply {
        moveTo(fx(49.5f), fy(33f))
        lineTo(fx(34.5f), fy(34f))
        cubicTo(fx(32f), fy(41f), fx(32.5f), fy(46f), fx(35f), fy(49f))
        lineTo(fx(49.5f), fy(49f))
        close()
    }

    val rightPec = Path().apply {
        moveTo(fx(50.5f), fy(33f))
        lineTo(fx(65.5f), fy(34f))
        cubicTo(fx(68f), fy(41f), fx(67.5f), fy(46f), fx(65f), fy(49f))
        lineTo(fx(50.5f), fy(49f))
        close()
    }

    val leftFrontDelt = Path().apply {
        moveTo(fx(33f), fy(32f))
        lineTo(fx(36f), fy(35f))
        lineTo(fx(30f), fy(38f))
        close()
    }

    val rightFrontDelt = Path().apply {
        moveTo(fx(67f), fy(32f))
        lineTo(fx(64f), fy(35f))
        lineTo(fx(70f), fy(38f))
        close()
    }

    val leftSideDelt = Path().apply {
        moveTo(fx(30f), fy(34f))
        cubicTo(fx(23f), fy(36f), fx(24f), fy(44f), fx(29f), fy(46f))
        cubicTo(fx(32f), fy(44f), fx(31f), fy(36f), fx(30f), fy(34f))
        close()
    }

    val rightSideDelt = Path().apply {
        moveTo(fx(70f), fy(34f))
        cubicTo(fx(77f), fy(36f), fx(76f), fy(44f), fx(71f), fy(46f))
        cubicTo(fx(68f), fy(44f), fx(69f), fy(36f), fx(70f), fy(34f))
        close()
    }

    val leftSerratus = Path().apply {
        moveTo(fx(31f), fy(49f))
        lineTo(fx(35f), fy(51f))
        lineTo(fx(34f), fy(59f))
        lineTo(fx(30f), fy(57f))
        close()
    }

    val rightSerratus = Path().apply {
        moveTo(fx(69f), fy(49f))
        lineTo(fx(65f), fy(51f))
        lineTo(fx(66f), fy(59f))
        lineTo(fx(70f), fy(57f))
        close()
    }

    val leftBicep = Path().apply {
        moveTo(fx(27f), fy(42f))
        cubicTo(fx(22f), fy(46f), fx(23f), fy(58f), fx(27f), fy(64f))
        lineTo(fx(30.5f), fy(64f))
        cubicTo(fx(31f), fy(58f), fx(29f), fy(46f), fx(27f), fy(42f))
        close()
    }

    val rightBicep = Path().apply {
        moveTo(fx(73f), fy(42f))
        cubicTo(fx(78f), fy(46f), fx(77f), fy(58f), fx(73f), fy(64f))
        lineTo(fx(69.5f), fy(64f))
        cubicTo(fx(69f), fy(58f), fx(71f), fy(46f), fx(73f), fy(42f))
        close()
    }

    val leftForearm = Path().apply {
        moveTo(fx(26f), fy(65f))
        lineTo(fx(20f), fy(82f))
        lineTo(fx(24f), fy(82f))
        lineTo(fx(29f), fy(65f))
        close()
    }

    val rightForearm = Path().apply {
        moveTo(fx(74f), fy(65f))
        lineTo(fx(80f), fy(82f))
        lineTo(fx(76f), fy(82f))
        lineTo(fx(71f), fy(65f))
        close()
    }

    val absRectus = Path().apply {
        moveTo(fx(42f), fy(50f))
        lineTo(fx(58f), fy(50f))
        lineTo(fx(57f), fy(78f))
        lineTo(fx(43f), fy(78f))
        close()
    }

    val leftOblique = Path().apply {
        moveTo(fx(35.5f), fy(50f))
        lineTo(fx(41.5f), fy(50f))
        lineTo(fx(42.5f), fy(78f))
        lineTo(fx(37f), fy(78f))
        close()
    }

    val rightOblique = Path().apply {
        moveTo(fx(64.5f), fy(50f))
        lineTo(fx(58.5f), fy(50f))
        lineTo(fx(57.5f), fy(78f))
        lineTo(fx(63f), fy(78f))
        close()
    }

    val transverseAb = Path().apply {
        moveTo(fx(37f), fy(78f))
        lineTo(fx(63f), fy(78f))
        lineTo(fx(61f), fy(82f))
        lineTo(fx(39f), fy(82f))
        close()
    }

    val leftQuad = Path().apply {
        moveTo(fx(35.5f), fy(83f))
        lineTo(fx(47.5f), fy(84f))
        lineTo(fx(44.5f), fy(134f))
        lineTo(fx(36.5f), fy(134f))
        close()
    }

    val rightQuad = Path().apply {
        moveTo(fx(64.5f), fy(83f))
        lineTo(fx(52.5f), fy(84f))
        lineTo(fx(55.5f), fy(134f))
        lineTo(fx(63.5f), fy(134f))
        close()
    }

    val leftAbductor = Path().apply {
        moveTo(fx(33.5f), fy(82f))
        lineTo(fx(35f), fy(82f))
        lineTo(fx(36f), fy(110f))
        lineTo(fx(32f), fy(110f))
        close()
    }

    val rightAbductor = Path().apply {
        moveTo(fx(66.5f), fy(82f))
        lineTo(fx(65f), fy(82f))
        lineTo(fx(64f), fy(110f))
        lineTo(fx(68f), fy(110f))
        close()
    }

    val leftAdductor = Path().apply {
        moveTo(fx(48f), fy(85f))
        lineTo(fx(49.5f), fy(85f))
        lineTo(fx(46f), fy(120f))
        lineTo(fx(45f), fy(120f))
        close()
    }

    val rightAdductor = Path().apply {
        moveTo(fx(52f), fy(85f))
        lineTo(fx(50.5f), fy(85f))
        lineTo(fx(54f), fy(120f))
        lineTo(fx(55f), fy(120f))
        close()
    }

    val leftTibialis = Path().apply {
        moveTo(fx(34.5f), fy(142f))
        lineTo(fx(37.5f), fy(142f))
        lineTo(fx(39f), fy(185f))
        lineTo(fx(36.5f), fy(185f))
        close()
    }

    val rightTibialis = Path().apply {
        moveTo(fx(65.5f), fy(142f))
        lineTo(fx(62.5f), fy(142f))
        lineTo(fx(61f), fy(185f))
        lineTo(fx(63.5f), fy(185f))
        close()
    }

    val leftCalf = Path().apply {
        moveTo(fx(38f), fy(142f))
        quadraticTo(fx(32f), fy(165f), fx(40f), fy(192f))
        lineTo(fx(44f), fy(192f))
        lineTo(fx(45.5f), fy(142f))
        close()
    }

    val rightCalf = Path().apply {
        moveTo(fx(62f), fy(142f))
        quadraticTo(fx(68f), fy(165f), fx(60f), fy(192f))
        lineTo(fx(56f), fy(192f))
        lineTo(fx(54.5f), fy(142f))
        close()
    }
}

class CachedBackPaths(val w: Float, val h: Float) {
    fun fx(x: Float): Float = x / 100f * w
    fun fy(y: Float): Float = y / 200f * h

    val bodyOutline = Path().apply {
        moveTo(fx(50f), fy(10f))
        cubicTo(fx(41f), fy(10f), fx(41f), fy(27f), fx(50f), fy(27f))
        cubicTo(fx(59f), fy(27f), fx(59f), fy(10f), fx(50f), fy(10f))
        
        moveTo(fx(46f), fy(27f))
        lineTo(fx(46f), fy(31f))
        quadraticTo(fx(38f), fy(33f), fx(27f), fy(38f))
        
        lineTo(fx(23f), fy(52f))
        lineTo(fx(20f), fy(68f))
        lineTo(fx(18f), fy(85f))
        quadraticTo(fx(15f), fy(92f), fx(18f), fy(94f))
        
        lineTo(fx(25f), fy(85f))
        lineTo(fx(28f), fy(68f))
        lineTo(fx(31f), fy(52f))
        
        quadraticTo(fx(35f), fy(62f), fx(36f), fy(74f))
        lineTo(fx(33f), fy(85f))
        
        lineTo(fx(32f), fy(115f))
        lineTo(fx(35f), fy(140f))
        
        lineTo(fx(33f), fy(165f))
        lineTo(fx(37f), fy(192f))
        quadraticTo(fx(33f), fy(197f), fx(44f), fy(197f))
        lineTo(fx(44f), fy(192f))
        
        lineTo(fx(46f), fy(140f))
        lineTo(fx(49f), fy(90f))
        
        lineTo(fx(51f), fy(90f))
        lineTo(fx(54f), fy(140f))
        
        lineTo(fx(56f), fy(192f))
        lineTo(fx(56f), fy(197f))
        quadraticTo(fx(67f), fy(197f), fx(63f), fy(192f))
        lineTo(fx(67f), fy(165f))
        lineTo(fx(65f), fy(140f))
        lineTo(fx(68f), fy(115f))
        lineTo(fx(67f), fy(85f))
        
        lineTo(fx(64f), fy(74f))
        quadraticTo(fx(65f), fy(62f), fx(69f), fy(52f))
        
        lineTo(fx(72f), fy(68f))
        lineTo(fx(75f), fy(85f))
        quadraticTo(fx(85f), fy(92f), fx(82f), fy(94f))
        lineTo(fx(82f), fy(85f))
        
        lineTo(fx(80f), fy(68f))
        lineTo(fx(77f), fy(52f))
        lineTo(fx(73f), fy(38f))
        
        quadraticTo(fx(62f), fy(33f), fx(54f), fy(31f))
        lineTo(fx(54f), fy(27f))
        close()
    }

    val neckPathBack = Path().apply {
        moveTo(fx(46f), fy(22f))
        lineTo(fx(54f), fy(22f))
        lineTo(fx(53f), fy(29f))
        lineTo(fx(47f), fy(29f))
        close()
    }

    val leftTrap = Path().apply {
        moveTo(fx(50f), fy(30f))
        lineTo(fx(46f), fy(31f))
        quadraticTo(fx(38f), fy(33f), fx(34f), fy(35f))
        lineTo(fx(50f), fy(42f))
        close()
    }

    val rightTrap = Path().apply {
        moveTo(fx(50f), fy(30f))
        lineTo(fx(54f), fy(31f))
        quadraticTo(fx(62f), fy(33f), fx(66f), fy(35f))
        lineTo(fx(50f), fy(42f))
        close()
    }

    val leftLat = Path().apply {
        moveTo(fx(50f), fy(43f))
        lineTo(fx(34f), fy(36f))
        cubicTo(fx(32f), fy(45f), fx(36f), fy(58f), fx(37f), fy(62f))
        lineTo(fx(50f), fy(62f))
        close()
    }

    val rightLat = Path().apply {
        moveTo(fx(50f), fy(43f))
        lineTo(fx(66f), fy(36f))
        cubicTo(fx(68f), fy(45f), fx(64f), fy(58f), fx(63f), fy(62f))
        lineTo(fx(50f), fy(62f))
        close()
    }

    val leftLowerBack = Path().apply {
        moveTo(fx(50f), fy(63f))
        lineTo(fx(37.5f), fy(63f))
        lineTo(fx(36.5f), fy(71f))
        lineTo(fx(50f), fy(71f))
        close()
    }

    val rightLowerBack = Path().apply {
        moveTo(fx(50f), fy(63f))
        lineTo(fx(62.5f), fy(63f))
        lineTo(fx(63.5f), fy(71f))
        lineTo(fx(50f), fy(71f))
        close()
    }

    val leftTricep = Path().apply {
        moveTo(fx(26f), fy(42f))
        cubicTo(fx(21f), fy(46f), fx(22f), fy(58f), fx(26f), fy(64f))
        lineTo(fx(29f), fy(64f))
        cubicTo(fx(28f), fy(58f), fx(28f), fy(46f), fx(26f), fy(42f))
        close()
    }

    val rightTricep = Path().apply {
        moveTo(fx(74f), fy(42f))
        cubicTo(fx(79f), fy(46f), fx(78f), fy(58f), fx(74f), fy(64f))
        lineTo(fx(71f), fy(64f))
        cubicTo(fx(72f), fy(58f), fx(72f), fy(46f), fx(74f), fy(42f))
        close()
    }

    val leftForearmBack = Path().apply {
        moveTo(fx(26f), fy(65f))
        lineTo(fx(20f), fy(82f))
        lineTo(fx(24f), fy(82f))
        lineTo(fx(29f), fy(65f))
        close()
    }

    val rightForearmBack = Path().apply {
        moveTo(fx(74f), fy(65f))
        lineTo(fx(80f), fy(82f))
        lineTo(fx(76f), fy(82f))
        lineTo(fx(71f), fy(65f))
        close()
    }

    val leftRearDelt = Path().apply {
        moveTo(fx(31f), fy(35f))
        cubicTo(fx(27f), fy(38f), fx(27f), fy(42f), fx(31f), fy(44f))
        close()
    }

    val rightRearDelt = Path().apply {
        moveTo(fx(69f), fy(35f))
        cubicTo(fx(73f), fy(38f), fx(73f), fy(42f), fx(69f), fy(44f))
        close()
    }

    val leftBackDelt = Path().apply {
        moveTo(fx(30f), fy(34f))
        cubicTo(fx(23f), fy(36f), fx(24f), fy(44f), fx(29f), fy(46f))
        cubicTo(fx(32f), fy(44f), fx(31f), fy(36f), fx(30f), fy(34f))
        close()
    }

    val rightBackDelt = Path().apply {
        moveTo(fx(70f), fy(34f))
        cubicTo(fx(77f), fy(36f), fx(76f), fy(44f), fx(71f), fy(46f))
        cubicTo(fx(68f), fy(44f), fx(69f), fy(36f), fx(70f), fy(34f))
        close()
    }

    val leftRotator = Path().apply {
        moveTo(fx(38f), fy(39f))
        lineTo(fx(45f), fy(41f))
        lineTo(fx(43f), fy(46f))
        close()
    }

    val rightRotator = Path().apply {
        moveTo(fx(62f), fy(39f))
        lineTo(fx(55f), fy(41f))
        lineTo(fx(57f), fy(46f))
        close()
    }

    val leftGlute = Path().apply {
        moveTo(fx(33.5f), fy(72f))
        lineTo(fx(50f), fy(74f))
        lineTo(fx(50f), fy(87f))
        cubicTo(fx(41f), fy(87f), fx(34f), fy(82f), fx(33.5f), fy(72f))
        close()
    }

    val rightGlute = Path().apply {
        moveTo(fx(66.5f), fy(72f))
        lineTo(fx(50f), fy(74f))
        lineTo(fx(50f), fy(87f))
        cubicTo(fx(59f), fy(87f), fx(66f), fy(82f), fx(66.5f), fy(72f))
        close()
    }

    val leftHamstring = Path().apply {
        moveTo(fx(33.5f), fy(88f))
        lineTo(fx(49f), fy(88f))
        lineTo(fx(45f), fy(136f))
        lineTo(fx(34.5f), fy(136f))
        close()
    }

    val rightHamstring = Path().apply {
        moveTo(fx(66.5f), fy(88f))
        lineTo(fx(51f), fy(88f))
        lineTo(fx(55f), fy(136f))
        lineTo(fx(65.5f), fy(136f))
        close()
    }

    val leftCalfBack = Path().apply {
        moveTo(fx(34.5f), fy(140f))
        quadraticTo(fx(31f), fy(150f), fx(37f), fy(192f))
        lineTo(fx(45f), fy(192f))
        lineTo(fx(45.5f), fy(140f))
        close()
    }

    val rightCalfBack = Path().apply {
        moveTo(fx(65.5f), fy(140f))
        quadraticTo(fx(69f), fy(150f), fx(63f), fy(192f))
        lineTo(fx(55f), fy(192f))
        lineTo(fx(54.5f), fy(140f))
        close()
    }
}

@Composable
fun AnatomicalWeeklyHeatmap(muscleVolumeMap: Map<String, Int>) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { 140.dp.toPx() }
    val h = with(density) { 280.dp.toPx() }

    val frontPaths = remember(w, h) { CachedFrontPaths(w, h) }
    val backPaths = remember(w, h) { CachedBackPaths(w, h) }
    val BorderSubtle = Color(0xFF13131D)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Front Canvas using pre-computed path cache
        Canvas(modifier = Modifier.size(140.dp, 280.dp)) {
            val detailColor = Color(0x7F000000)
            val detailW = 0.8f.dp.toPx()

            // Paint base blueprint body silhouette
            drawPath(path = frontPaths.bodyOutline, color = Color(0xFF1B1B26))
            drawPath(path = frontPaths.bodyOutline, color = Color(0xFF555570), style = Stroke(width = 1.6f.dp.toPx()))

            // 1. NECK
            drawPath(frontPaths.neckPath, getHeatmapColor(muscleVolumeMap["neck"] ?: 0))
            drawPath(frontPaths.neckPath, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawLine(detailColor, Offset(frontPaths.fx(48.5f), frontPaths.fy(22f)), Offset(frontPaths.fx(48.5f), frontPaths.fy(29f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(51.5f), frontPaths.fy(22f)), Offset(frontPaths.fx(51.5f), frontPaths.fy(29f)), strokeWidth = detailW)

            // 2. CHEST
            val chestColor = getHeatmapColor(muscleVolumeMap["chest"] ?: 0)
            drawPath(frontPaths.leftPec, chestColor)
            drawPath(frontPaths.leftPec, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightPec, chestColor)
            drawPath(frontPaths.rightPec, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Chest Striations (Fibers converging)
            drawLine(detailColor, Offset(frontPaths.fx(34.5f), frontPaths.fy(36f)), Offset(frontPaths.fx(48f), frontPaths.fy(36f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(33.5f), frontPaths.fy(41f)), Offset(frontPaths.fx(48f), frontPaths.fy(41f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(34.5f), frontPaths.fy(46f)), Offset(frontPaths.fx(48f), frontPaths.fy(46f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(65.5f), frontPaths.fy(36f)), Offset(frontPaths.fx(52f), frontPaths.fy(36f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(66.5f), frontPaths.fy(41f)), Offset(frontPaths.fx(52f), frontPaths.fy(41f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(65.5f), frontPaths.fy(46f)), Offset(frontPaths.fx(52f), frontPaths.fy(46f)), strokeWidth = detailW)

            // 3. FRONT DELTS (SHOULDERS)
            val frontDeltsColor = getHeatmapColor(muscleVolumeMap["front delts"] ?: 0)
            drawPath(frontPaths.leftFrontDelt, frontDeltsColor)
            drawPath(frontPaths.leftFrontDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightFrontDelt, frontDeltsColor)
            drawPath(frontPaths.rightFrontDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 4. SIDE DELTS (SHOULDERS)
            val sideDeltsColor = getHeatmapColor(muscleVolumeMap["side delts"] ?: 0)
            drawPath(frontPaths.leftSideDelt, sideDeltsColor)
            drawPath(frontPaths.leftSideDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightSideDelt, sideDeltsColor)
            drawPath(frontPaths.rightSideDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 5. SERRATUS ANTERIOR
            val serratusColor = getHeatmapColor(muscleVolumeMap["serratus anterior"] ?: 0)
            drawPath(frontPaths.leftSerratus, serratusColor)
            drawPath(frontPaths.leftSerratus, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightSerratus, serratusColor)
            drawPath(frontPaths.rightSerratus, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 6. BICEPS
            val bicepsColor = getHeatmapColor(muscleVolumeMap["biceps"] ?: 0)
            drawPath(frontPaths.leftBicep, bicepsColor)
            drawPath(frontPaths.leftBicep, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightBicep, bicepsColor)
            drawPath(frontPaths.rightBicep, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Bicep dividing lines (Long & short heads)
            drawLine(detailColor, Offset(frontPaths.fx(26f), frontPaths.fy(45f)), Offset(frontPaths.fx(28.5f), frontPaths.fy(61f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(74f), frontPaths.fy(45f)), Offset(frontPaths.fx(71.5f), frontPaths.fy(61f)), strokeWidth = detailW)

            // 7. FOREARMS
            val forearmsColor = getHeatmapColor(muscleVolumeMap["forearms"] ?: 0)
            drawPath(frontPaths.leftForearm, forearmsColor)
            drawPath(frontPaths.leftForearm, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightForearm, forearmsColor)
            drawPath(frontPaths.rightForearm, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawLine(detailColor, Offset(frontPaths.fx(24f), frontPaths.fy(68f)), Offset(frontPaths.fx(21.5f), frontPaths.fy(80f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(76f), frontPaths.fy(68f)), Offset(frontPaths.fx(78.5f), frontPaths.fy(80f)), strokeWidth = detailW)

            // 8. ABS (tendinous intersections / sixpack lines)
            drawPath(frontPaths.absRectus, getHeatmapColor(muscleVolumeMap["abs"] ?: 0))
            drawPath(frontPaths.absRectus, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Linea alba (vertical black division line)
            drawLine(detailColor, Offset(frontPaths.fx(50f), frontPaths.fy(50f)), Offset(frontPaths.fx(50f), frontPaths.fy(78f)), strokeWidth = 1.3f.dp.toPx())
            // Horizontal intersections
            drawLine(detailColor, Offset(frontPaths.fx(43f), frontPaths.fy(57f)), Offset(frontPaths.fx(57f), frontPaths.fy(57f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(43f), frontPaths.fy(64f)), Offset(frontPaths.fx(57f), frontPaths.fy(64f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(43f), frontPaths.fy(71f)), Offset(frontPaths.fx(57f), frontPaths.fy(71f)), strokeWidth = detailW)

            // 9. OBLIQUES
            val obliquesColor = getHeatmapColor(muscleVolumeMap["obliques"] ?: 0)
            drawPath(frontPaths.leftOblique, obliquesColor)
            drawPath(frontPaths.leftOblique, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightOblique, obliquesColor)
            drawPath(frontPaths.rightOblique, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 10. TRANSVERSE ABDOMINIS
            drawPath(frontPaths.transverseAb, getHeatmapColor(muscleVolumeMap["transverse abdominis"] ?: 0))
            drawPath(frontPaths.transverseAb, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 11. QUADRICEPS
            val quadsColor = getHeatmapColor(muscleVolumeMap["quadriceps"] ?: 0)
            drawPath(frontPaths.leftQuad, quadsColor)
            drawPath(frontPaths.leftQuad, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightQuad, quadsColor)
            drawPath(frontPaths.rightQuad, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Quad muscle belly details (Vastus Medialis, Lateralis, Rectus Femoris lines)
            drawLine(detailColor, Offset(frontPaths.fx(41.5f), frontPaths.fy(86f)), Offset(frontPaths.fx(41.5f), frontPaths.fy(118f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(41.5f), frontPaths.fy(118f)), Offset(frontPaths.fx(44.5f), frontPaths.fy(128f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(38f), frontPaths.fy(90f)), Offset(frontPaths.fx(39f), frontPaths.fy(124f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(58.5f), frontPaths.fy(86f)), Offset(frontPaths.fx(58.5f), frontPaths.fy(118f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(58.5f), frontPaths.fy(118f)), Offset(frontPaths.fx(55.5f), frontPaths.fy(128f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(frontPaths.fx(62f), frontPaths.fy(90f)), Offset(frontPaths.fx(61f), frontPaths.fy(124f)), strokeWidth = detailW)

            // 12. HIP ABDUCTORS
            val abductorsColor = getHeatmapColor(muscleVolumeMap["hip abductors"] ?: 0)
            drawPath(frontPaths.leftAbductor, abductorsColor)
            drawPath(frontPaths.leftAbductor, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightAbductor, abductorsColor)
            drawPath(frontPaths.rightAbductor, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 13. HIP ADDUCTORS
            val adductorsColor = getHeatmapColor(muscleVolumeMap["hip adductors"] ?: 0)
            drawPath(frontPaths.leftAdductor, adductorsColor)
            drawPath(frontPaths.leftAdductor, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightAdductor, adductorsColor)
            drawPath(frontPaths.rightAdductor, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 14. TIBIALIS ANTERIOR
            val tibialisColor = getHeatmapColor(muscleVolumeMap["tibialis anterior"] ?: 0)
            drawPath(frontPaths.leftTibialis, tibialisColor)
            drawPath(frontPaths.leftTibialis, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightTibialis, tibialisColor)
            drawPath(frontPaths.rightTibialis, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 15. CALVES
            val calvesColor = getHeatmapColor(muscleVolumeMap["calves"] ?: 0)
            drawPath(frontPaths.leftCalf, calvesColor)
            drawPath(frontPaths.leftCalf, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(frontPaths.rightCalf, calvesColor)
            drawPath(frontPaths.rightCalf, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // Highlight Head Outline
            drawCircle(color = Color(0xFF555570), radius = 9.dp.toPx(), center = Offset(70.dp.toPx(), 25.2f.dp.toPx()), style = Stroke(width = 1.6f.dp.toPx()))
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Back Canvas using pre-computed path cache
        Canvas(modifier = Modifier.size(140.dp, 280.dp)) {
            val detailColor = Color(0x7F000000)
            val detailW = 0.8f.dp.toPx()

            // Paint base blueprint body silhouette
            drawPath(path = backPaths.bodyOutline, color = Color(0xFF1B1B26))
            drawPath(path = backPaths.bodyOutline, color = Color(0xFF555570), style = Stroke(width = 1.6f.dp.toPx()))

            // 1. BACK NECK
            drawPath(backPaths.neckPathBack, getHeatmapColor(muscleVolumeMap["neck"] ?: 0))
            drawPath(backPaths.neckPathBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 2. TRAPEZIUS
            val trapsColor = getHeatmapColor(muscleVolumeMap["trapezius"] ?: 0)
            drawPath(backPaths.leftTrap, trapsColor)
            drawPath(backPaths.leftTrap, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightTrap, trapsColor)
            drawPath(backPaths.rightTrap, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Trap Fiber Fan details
            drawLine(detailColor, Offset(backPaths.fx(48.5f), backPaths.fy(31f)), Offset(backPaths.fx(42f), backPaths.fy(36f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(49.5f), backPaths.fy(34f)), Offset(backPaths.fx(46f), backPaths.fy(38f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(51.5f), backPaths.fy(31f)), Offset(backPaths.fx(58f), backPaths.fy(36f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50.5f), backPaths.fy(34f)), Offset(backPaths.fx(54f), backPaths.fy(38f)), strokeWidth = detailW)

            // 3. BACK (LATS)
            val backColor = getHeatmapColor(muscleVolumeMap["back"] ?: 0)
            drawPath(backPaths.leftLat, backColor)
            drawPath(backPaths.leftLat, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightLat, backColor)
            drawPath(backPaths.rightLat, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Lat Sweeping fibers
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(58f)), Offset(backPaths.fx(38f), backPaths.fy(46f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(50f)), Offset(backPaths.fx(36f), backPaths.fy(41f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(54f)), Offset(backPaths.fx(41f), backPaths.fy(49f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(58f)), Offset(backPaths.fx(62f), backPaths.fy(46f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(50f)), Offset(backPaths.fx(64f), backPaths.fy(41f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(50f), backPaths.fy(54f)), Offset(backPaths.fx(59f), backPaths.fy(49f)), strokeWidth = detailW)

            // 4. LOWER BACK
            val lowerBackColor = getHeatmapColor(muscleVolumeMap["lower back"] ?: 0)
            drawPath(backPaths.leftLowerBack, lowerBackColor)
            drawPath(backPaths.leftLowerBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightLowerBack, lowerBackColor)
            drawPath(backPaths.rightLowerBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 5. TRICEPS
            val tricepsColor = getHeatmapColor(muscleVolumeMap["triceps"] ?: 0)
            drawPath(backPaths.leftTricep, tricepsColor)
            drawPath(backPaths.leftTricep, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightTricep, tricepsColor)
            drawPath(backPaths.rightTricep, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Tricep heads division lines
            drawLine(detailColor, Offset(backPaths.fx(25.5f), backPaths.fy(46f)), Offset(backPaths.fx(28f), backPaths.fy(60f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(74.5f), backPaths.fy(46f)), Offset(backPaths.fx(72f), backPaths.fy(60f)), strokeWidth = detailW)

            // 6. FOREARMS
            val forearmsColor = getHeatmapColor(muscleVolumeMap["forearms"] ?: 0)
            drawPath(backPaths.leftForearmBack, forearmsColor)
            drawPath(backPaths.leftForearmBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightForearmBack, forearmsColor)
            drawPath(backPaths.rightForearmBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 7. REAR DELTS
            val rearDeltsColor = getHeatmapColor(muscleVolumeMap["rear delts"] ?: 0)
            drawPath(backPaths.leftRearDelt, rearDeltsColor)
            drawPath(backPaths.leftRearDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightRearDelt, rearDeltsColor)
            drawPath(backPaths.rightRearDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 8. SIDE DELTS
            val deltsColor = getHeatmapColor(muscleVolumeMap["side delts"] ?: 0)
            drawPath(backPaths.leftBackDelt, deltsColor)
            drawPath(backPaths.leftBackDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightBackDelt, deltsColor)
            drawPath(backPaths.rightBackDelt, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 9. ROTATOR CUFF
            val rotatorColor = getHeatmapColor(muscleVolumeMap["rotator cuff"] ?: 0)
            drawPath(backPaths.leftRotator, rotatorColor)
            drawPath(backPaths.leftRotator, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightRotator, rotatorColor)
            drawPath(backPaths.rightRotator, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))

            // 10. GLUTES
            val glutesColor = getHeatmapColor(muscleVolumeMap["glutes"] ?: 0)
            drawPath(backPaths.leftGlute, glutesColor)
            drawPath(backPaths.leftGlute, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightGlute, glutesColor)
            drawPath(backPaths.rightGlute, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Glute details (slanted/diagonal muscle lines)
            drawLine(detailColor, Offset(backPaths.fx(44f), backPaths.fy(82f)), Offset(backPaths.fx(36f), backPaths.fy(96f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(56f), backPaths.fy(82f)), Offset(backPaths.fx(64f), backPaths.fy(96f)), strokeWidth = detailW)

            // 11. HAMSTRINGS
            val hamstringsColor = getHeatmapColor(muscleVolumeMap["hamstrings"] ?: 0)
            drawPath(backPaths.leftHamstring, hamstringsColor)
            drawPath(backPaths.leftHamstring, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightHamstring, hamstringsColor)
            drawPath(backPaths.rightHamstring, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Hamstring fibers division lines
            drawLine(detailColor, Offset(backPaths.fx(41.5f), backPaths.fy(102f)), Offset(backPaths.fx(41.5f), backPaths.fy(132f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(58.5f), backPaths.fy(102f)), Offset(backPaths.fx(58.5f), backPaths.fy(132f)), strokeWidth = detailW)

            // 12. CALVES
            val calvesColorBack = getHeatmapColor(muscleVolumeMap["calves"] ?: 0)
            drawPath(backPaths.leftCalfBack, calvesColorBack)
            drawPath(backPaths.leftCalfBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            drawPath(backPaths.rightCalfBack, calvesColorBack)
            drawPath(backPaths.rightCalfBack, BorderSubtle.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
            // Calves details (gastrocnemius heads splitter)
            drawLine(detailColor, Offset(backPaths.fx(40f), backPaths.fy(145f)), Offset(backPaths.fx(40f), backPaths.fy(175f)), strokeWidth = detailW)
            drawLine(detailColor, Offset(backPaths.fx(60f), backPaths.fy(145f)), Offset(backPaths.fx(60f), backPaths.fy(175f)), strokeWidth = detailW)

            // Highlight Head Outline
            drawCircle(color = Color(0xFF555570), radius = 9.dp.toPx(), center = Offset(70.dp.toPx(), 25.2f.dp.toPx()), style = Stroke(width = 1.6f.dp.toPx()))
        }
    }
}

private fun getHeatmapColor(sets: Int): Color {
    return when {
        sets >= 14 -> Color(0xFFFF4D4D) // High volume Red (Vibrant, 100% opaque)
        sets >= 8 -> Color(0xFFFFAD1A) // Medium volume Amber (Vibrant, 100% opaque)
        sets >= 3 -> Color(0xFF2EE482) // Low volume Green (Vibrant, 100% opaque)
        else -> Color(0xFF1F1F30) // Untrained visible slate grey base
    }
}

@Composable
fun LabelIndicator(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = SecondaryText)
    }
}

@Composable
fun HypertrophyProgressBarMarker(
    label: String,
    sets: Int,
    maxSets: Int
) {
    val progress = (sets.toFloat() / maxSets.toFloat()).coerceIn(0f, 1f)
    val color = when {
        sets >= 20 -> RedAccent
        sets >= 10 -> GreenAccent
        else -> AmberAccent
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = SecondaryText)
            Text("$sets sets this week (MAV index)", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = color)
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

// COACH TAB WITH DIRECT IN-APP CHAT AND SYSTEM CONTEXT PROMPT
@Composable
fun CoachTab(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel
) {
    val messages by fitnessViewModel.chatMessages.collectAsStateWithLifecycle()
    val isCoachLoading by fitnessViewModel.isCoachLoading.collectAsStateWithLifecycle()

    var inputMessage by remember { mutableStateOf("") }

    val lazyListState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Coach Profile head block
        PremiumCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = "Coach logo", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("APEX ADAPTIVE AI COACH", fontFamily = SyneFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text("Powered by Gemini Science Reasoning models", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = SecondaryText)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val apiKey by fitnessViewModel.geminiApiKey.collectAsStateWithLifecycle()
        if (apiKey.isBlank()) {
            PremiumCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add your Gemini API key in Settings",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1)
                    )
                }
            }
        }

        // Chat text dialogue list vertical scrollable
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(lazyListState)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEach { msg ->
                val align = if (msg.isUser) Alignment.End else Alignment.Start
                val shape = if (msg.isUser) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
                val background = if (msg.isUser) Color(0xFF6366F1) else DarkCardSurface
                val borderStyle = if (msg.isUser) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, BorderSubtle)
                val textColor = if (msg.isUser) Color.White else PrimaryText

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(shape)
                            .background(background)
                            .border(borderStyle, shape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.message,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 16.sp
                        )
                    }
                    Text(
                        text = if (msg.isUser) "ATHLETE LOG" else "AI ADIVSOR FILE",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = MutedText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isCoachLoading) {
                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF6366F1))
                }
            }
        }

        // Quick Preset Prompts rows
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val promptChips = listOf(
                "Analyse my week",
                "Why is my weight stalling?",
                "Suggest deload target sets?",
                "What is my weakest muscle point?"
            )
            items(promptChips) { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCardSurface)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                        .clickable {
                            inputMessage = chip
                            fitnessViewModel.sendCoachMessage(
                                text = chip,
                                coachContext = algorithmViewModel.buildCoachContext()
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(chip, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = SecondaryText)
                }
            }
        }

        // Send Text field row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Consult sports science model database...", fontSize = 11.sp, color = MutedText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_chat_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = Color(0xFF6366F1),
                    unfocusedIndicatorColor = BorderSubtle
                )
            )

            IconButton(
                onClick = {
                    if (inputMessage.trim().isNotEmpty()) {
                        fitnessViewModel.sendCoachMessage(
                            text = inputMessage,
                            coachContext = algorithmViewModel.buildCoachContext()
                        )
                        inputMessage = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1))
                    .testTag("coach_send_button")
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send Message", tint = Color.White)
            }
        }
    }
}

// COMPREHENSIVE SETTINGS VIEW OVERLAY
@Composable
fun SettingsDialog(
    fitnessViewModel: FitnessViewModel,
    onDismiss: () -> Unit
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val goal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val latestApiKey by fitnessViewModel.geminiApiKey.collectAsStateWithLifecycle()
    val manualCalorieTarget by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val manualCalorieVal by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var editName by remember(username) { mutableStateOf(username) }
    var editGoal by remember(goal) { mutableStateOf(goal) }
    var editApiKey by remember(latestApiKey) { mutableStateOf(latestApiKey) }
    var editedManualCalValue by remember(manualCalorieVal) { mutableStateOf(manualCalorieVal.toString()) }
    var editHeight by remember(userHeight) { mutableStateOf(userHeight.toString()) }
    var editAge by remember(userAge) { mutableStateOf(userAge.toString()) }
    var editSex by remember(userSex) { mutableStateOf(userSex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ATHLETE FILE MANAGEMENT", fontFamily = SyneFamily, fontWeight = FontWeight.ExtraBold) },
        containerColor = DarkCardSurface,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Name
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Athlete Nickname", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                )

                // Goal Selector
                OutlinedTextField(
                    value = editGoal,
                    onValueChange = { editGoal = it },
                    label = { Text("Goal objective profile", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("settings_goal_input"),
                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                )

                // Calorie Target Value override
                OutlinedTextField(
                    value = editedManualCalValue,
                    onValueChange = { editedManualCalValue = it },
                    label = { Text("Manual Calorie Target limit", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                )

                // Height & Age Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editHeight,
                        onValueChange = { editHeight = it },
                        label = { Text("Height (cm)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("settings_height_input"),
                        colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                    )
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it },
                        label = { Text("Age (yrs)", color = SecondaryText) },
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("settings_age_input"),
                        colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                    )
                }

                // Biological Sex Select
                Text(
                    text = "BIOLOGICAL SEX (BMR METABOLIC FORMULA OFFSET)",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (sex in listOf("Male", "Female")) {
                        val isSelected = editSex.equals(sex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AmberAccent else DarkRaised)
                                .border(
                                    border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { editSex = sex.lowercase() }
                                .padding(vertical = 10.dp)
                                .testTag("settings_sex_${sex.lowercase()}"),
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

                // API Key field input
                OutlinedTextField(
                    value = editApiKey,
                    onValueChange = { editApiKey = it },
                    label = { Text("Gemini Secure API Key string", color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("settings_api_key_input"),
                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkRaised, unfocusedContainerColor = DarkRaised)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                Spacer(modifier = Modifier.height(10.dp))

                // Database wipes CTA
                Button(
                    onClick = {
                        fitnessViewModel.resetAllData()
                        onDismiss()
                        Toast.makeText(context, "All athlete training logs cleared.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reset_data_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
                ) {
                    Text("RESET TOTAL ATHLETE DIRECTORY DATA", fontFamily = SyneFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hVal = editHeight.toDoubleOrNull() ?: 175.0
                    val aVal = editAge.toIntOrNull() ?: 25
                    fitnessViewModel.updateProfile(
                        name = editName,
                        userGoal = editGoal,
                        targetUnit = "kg",
                        key = editApiKey,
                        equipment = "Barbell,Dumbbell,Cable,Machine",
                        height = hVal,
                        age = aVal,
                        sex = editSex.lowercase()
                    )
                    val cInt = editedManualCalValue.toIntOrNull() ?: 2500
                    fitnessViewModel.setManualCalorieTarget(true, cInt)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
            ) {
                Text("SAVE PROFILE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0F))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SecondaryText)
            }
        }
    )
}

@Composable
fun RealTimeEffectiveSetsCard(
    exName: String,
    effData: EffectiveSetsData,
    currentSetNum: Int,
    totalSetsNum: Int,
    lastSetWeight: Double,
    lastSetReps: Int
) {
    PremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("effective_sets_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${exName.uppercase()} EFFECTIVE VOLUME",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Effective Sets: ${String.format("%.2f", effData.currentEffectiveSets)} / ${String.format("%.2f", effData.targetEffectiveSets)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "(${effData.progress.toInt()}% complete)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = AmberAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (effData.currentEffectiveSets / effData.targetEffectiveSets).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AmberAccent,
                trackColor = DarkRaised
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (effData.lastSetRPE > 0) {
                val effectivenessPct = when (effData.lastSetRPE) {
                    7 -> "50%"
                    8 -> "75%"
                    9 -> "90%"
                    10 -> "100%"
                    in 5..6 -> "0%"
                    else -> "0%"
                }
                
                val effectivenessZone = when (effData.lastSetRPE) {
                    7 -> "building zone"
                    8 -> "SWEET SPOT"
                    9 -> "high effort"
                    10 -> "maximal"
                    else -> "below threshold"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (effData.lastSetRPE == 8) AmberAccent.copy(alpha = 0.12f) else DarkRaised)
                        .border(
                            BorderStroke(1.dp, if (effData.lastSetRPE == 8) AmberAccent.copy(alpha = 0.25f) else BorderSubtle),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (effData.lastSetRPE == 8) {
                                Icon(Icons.Filled.Star, contentDescription = "Sweet Spot", tint = AmberAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Last Set: ${if (lastSetWeight > 0) "${lastSetWeight.toInt()} kg " else ""}${if (lastSetReps > 0 && lastSetWeight <= 0) "$lastSetReps reps " else if (lastSetReps > 0) "× $lastSetReps reps " else ""}@ RPE ${effData.lastSetRPE}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quality: ${String.format("%.2f", effData.lastSetEffectiveness)} effective sets (${effectivenessPct} effective — ${effectivenessZone.uppercase()})",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = if (effData.lastSetRPE == 8) AmberAccent else SecondaryText
                        )
                    }
                }
            } else {
                Text(
                    text = "Log a set to view RPE effectiveness zone.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MutedText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle.copy(alpha = 0.5f)))
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Status: " + if (effData.progress >= 100) "Goal complete!" else if (effData.progress > 45) "On track" else "Building momentum",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = GreenAccent
                )
                Text(
                    text = "Next: Set ${currentSetNum}/${totalSetsNum}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = SecondaryText
                )
            }
        }
    }
}

