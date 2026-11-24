package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.apexfit.app.ui.models.UiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.PlanBuilderViewModel
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.ui.theme.*
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

private data class MealTotals(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    nutritionViewModel: NutritionViewModel,
    planBuilderViewModel: PlanBuilderViewModel = hiltViewModel(),
    onNavigateTo: (Int) -> Unit
) {
    val selectedDate by nutritionViewModel.selectedNutritionDate.collectAsStateWithLifecycle()
    val loggedMealsState by nutritionViewModel.loggedMeals.collectAsStateWithLifecycle()
    
    if (loggedMealsState is UiState.Loading) {
        Box(Modifier.fillMaxSize().background(ApexColors.Canvas), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ApexColors.Primary)
        }
        return
    }
    if (loggedMealsState is UiState.Error) {
        Box(Modifier.fillMaxSize().background(ApexColors.Canvas), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nutrition_error_loading_meals), color = ApexColors.TextSecondary)
        }
        return
    }
    
    val loggedMeals: List<com.apexfit.app.ui.models.UiNutritionEntry> = (loggedMealsState as? UiState.Success)?.data ?: emptyList()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val hasCalorieTarget = if (calorieTargetManual) calorieTargetValue > 0 else (tdeeResult.tdee ?: 0) > 0
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val currentWeight by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val goalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val weeklyWorkouts by fitnessViewModel.weeklyWorkouts.collectAsStateWithLifecycle()
    val foodPreferences by fitnessViewModel.nutritionPreferences.collectAsStateWithLifecycle()

    val completedSessions by algorithmViewModel.completedSessions.collectAsStateWithLifecycle()
    val activePlanSessionsState by planBuilderViewModel.activePlanSessions.collectAsStateWithLifecycle()
    val activePlanSessions: List<com.apexfit.app.data.PlanSession> = (activePlanSessionsState as? UiState.Success)?.data ?: emptyList()

    var todayDateStr by remember { mutableStateOf(com.apexfit.app.utils.DateTimeUtils.todayDateString()) }

    LaunchedEffect(Unit) {
        while (true) {
            val newDate = com.apexfit.app.utils.DateTimeUtils.todayDateString()
            if (newDate != todayDateStr) todayDateStr = newDate
            kotlinx.coroutines.delay(60_000L)
        }
    }

    val selectedDayString = remember(selectedDate) {
        runCatching { java.time.LocalDate.parse(selectedDate).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) }.getOrNull()
    }
    val isWorkoutCompletedToday = remember(completedSessions, selectedDate) {
        completedSessions.any { it.date == selectedDate && it.isCompleted }
    }
    val todayPlannedSession = remember(activePlanSessions, selectedDate, todayDateStr, selectedDayString) {
        if (selectedDate == todayDateStr) activePlanSessions.firstOrNull { it.day.equals(selectedDayString, ignoreCase = true) } else null
    }
    val isPlannedTrainingToday = remember(todayPlannedSession) {
        todayPlannedSession != null &&
            !todayPlannedSession.label.contains("Rest", ignoreCase = true) &&
            todayPlannedSession.focus != "Muscle Recovery & Rest"
    }
    val isTodayTrainingDay = isWorkoutCompletedToday || isPlannedTrainingToday

    // Apply goal adjustment so non-manual users see their actual goal-adjusted
    // target, not raw maintenance calories.
    val suggestedFromTdee = remember(tdeeResult.tdee, userGoal, currentWeight, goalWeight, userHeight, userAge, userSex) {
        com.apexfit.app.utils.TdeeEngine.suggestCaloricTarget(
            tdee            = tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES,
            goal            = userGoal,
            currentWeightKg = currentWeight,
            goalWeightKg    = goalWeight,
            heightCm        = userHeight,
            ageYears        = userAge,
            sex             = userSex
        )
    }
    val baseCalorieTarget = (if (calorieTargetManual) calorieTargetValue else suggestedFromTdee)
        .coerceAtLeast(1)

    val cycledTargets = remember(baseCalorieTarget, weeklyWorkouts, userGoal, currentWeight, userHeight, userSex) {
        com.apexfit.app.utils.MacroEngine.calcCycledTargets(
            weeklyCalorieTarget    = baseCalorieTarget,
            weeklyTrainingSessions = weeklyWorkouts,
            goal                   = userGoal,
            bodyWeightKg           = currentWeight,
            heightCm               = userHeight,
            sex                    = userSex
        )
    }

    val calorieTarget = if (isTodayTrainingDay) cycledTargets.trainingDayCalories else cycledTargets.restDayCalories
    val targetProtein = if (isTodayTrainingDay) cycledTargets.trainingDayProtein else cycledTargets.restDayProtein
    val targetCarbs = if (isTodayTrainingDay) cycledTargets.trainingDayCarbs else cycledTargets.restDayCarbs
    val targetFat = cycledTargets.fat

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pendingDeletions = remember { mutableStateMapOf<Long, Job>() }
    DisposableEffect(nutritionViewModel) {
        onDispose {
            // Leaving the screen dismisses Undo and commits pending deletions.
            pendingDeletions.forEach { (id, job) ->
                job.cancel()
                nutritionViewModel.deleteNutritionEntryById(id)
            }
            pendingDeletions.clear()
        }
    }

    val visibleMeals = remember(loggedMeals, pendingDeletions.keys.toSet()) {
        loggedMeals.filter { it.id !in pendingDeletions.keys }
    }

    val mealTotals = remember(visibleMeals, pendingDeletions.keys.toSet()) {
        MealTotals(
            calories = visibleMeals.sumOf { it.calories },
            protein = visibleMeals.sumOf { it.protein },
            carbs = visibleMeals.sumOf { it.carbs },
            fat = visibleMeals.sumOf { it.fat }
        )
    }
    val loggedCalories = mealTotals.calories
    val loggedProtein = mealTotals.protein
    val loggedCarbs = mealTotals.carbs
    val loggedFat = mealTotals.fat

    var showAddFoodModal by rememberSaveable { mutableStateOf(false) }

    fun triggerPendingDeletion(meal: com.apexfit.app.ui.models.UiNutritionEntry) {
        if (pendingDeletions.containsKey(meal.id)) return

        pendingDeletions[meal.id] = coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Meal deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result != SnackbarResult.ActionPerformed) {
                nutritionViewModel.deleteNutritionEntryById(meal.id)
            }
            pendingDeletions.remove(meal.id)
        }
    }

    Scaffold(
        containerColor = ApexColors.Canvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NutritionAddFoodBar(onAddFood = { showAddFoodModal = true })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            NutritionHeader(
                date = selectedDate,
                onPreviousDay = { nutritionViewModel.changeNutritionDate(-1) },
                onNextDay = { if (selectedDate != todayDateStr) nutritionViewModel.changeNutritionDate(1) },
                canGoForward = selectedDate != todayDateStr,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            NutritionDailySummary(
                calories = loggedCalories,
                target = calorieTarget.takeIf { hasCalorieTarget },
                trainingDay = isTodayTrainingDay,
                macros = listOf(
                    "Protein" to "${loggedProtein.roundToInt()}${if (hasCalorieTarget) " / $targetProtein" else ""} g",
                    "Carbs" to "${loggedCarbs.roundToInt()}${if (hasCalorieTarget) " / $targetCarbs" else ""} g",
                    "Fat" to "${loggedFat.roundToInt()}${if (hasCalorieTarget) " / $targetFat" else ""} g"
                )
            )
        }

        // Meals details lists
        item {
            Text(
                text = if (selectedDate == todayDateStr) "Today's meals" else "Meals for ${com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(selectedDate)}",
                style = MaterialTheme.typography.titleLarge,
                color = ApexColors.TextPrimary
            )
        }

        if (visibleMeals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.nutrition_no_meals_logged_yet), color = ApexColors.TextSecondary)
                }
            }
        } else {
            items(
                items = visibleMeals,
                key = { meal -> meal.id }
            ) { meal ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                            triggerPendingDeletion(meal)
                            true
                        } else {
                            false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundContent = {
                        val color = when (dismissState.targetValue) {
                            SwipeToDismissBoxValue.EndToStart -> ApexColors.Alert.copy(alpha = 0.8f)
                            SwipeToDismissBoxValue.StartToEnd -> ApexColors.Alert.copy(alpha = 0.8f)
                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color)
                                .padding(horizontal = 20.dp),
                            contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Icon(
                                painter = painterResource(ApexIcons.Delete),
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    },
                    content = {
                        NutritionMealRow(
                            meal = meal,
                            onDelete = { triggerPendingDeletion(meal) }
                        )
                    }
                )
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = ApexColors.BorderSubtle)
                Text("Food preferences", style = MaterialTheme.typography.titleMedium, color = ApexColors.TextPrimary)
                Text("${foodPreferences.protein} · ${foodPreferences.restrictions} · ${foodPreferences.budget}",
                    color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("Supplement preference: ${foodPreferences.supplements}", color = ApexColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
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

        // Protein Timing
        item {
            ProteinTimingCard(todayEntries = visibleMeals)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

    if (showAddFoodModal) {
        AddFoodSheet(
            fitnessViewModel = fitnessViewModel,
            nutritionViewModel = nutritionViewModel,
            selectedDate = selectedDate,
            onDismiss = { showAddFoodModal = false }
        )
    }
}

@Composable
internal fun NutritionHeader(
    date: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    canGoForward: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Nutrition",
                color = ApexColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(R.drawable.ic_apex_chevron),
                    contentDescription = null,
                    tint = ApexColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "APEX FIT",
                    color = ApexColors.TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatNutritionDate(date),
                color = ApexColors.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousDay, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(painterResource(ApexIcons.ArrowLeft), contentDescription = "Previous day", tint = ApexColors.TextSecondary)
                }
                IconButton(
                    onClick = onNextDay,
                    enabled = canGoForward,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        painterResource(ApexIcons.ArrowRight),
                        contentDescription = "Next day",
                        tint = if (canGoForward) ApexColors.TextSecondary else ApexColors.TextTertiary
                    )
                }
            }
        }
    }
}

private fun formatNutritionDate(dateIso: String): String {
    val date = runCatching { java.time.LocalDate.parse(dateIso) }.getOrNull()
        ?: return com.apexfit.app.utils.DateTimeUtils.formatDateForDisplay(dateIso)
    val locale = java.util.Locale.getDefault()
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
    return "$weekday, ${date.dayOfMonth} $month"
}

@Composable
internal fun NutritionAddFoodBar(onAddFood: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ApexColors.Canvas)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Button(
            onClick = onAddFood,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ApexColors.Primary,
                contentColor = ApexColors.OnGlow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = 56.dp)
                .testTag("add_nutrition_button")
        ) {
            Text(stringResource(R.string.nutrition_add_food), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun NutritionMealRow(
    meal: com.apexfit.app.ui.models.UiNutritionEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    meal.name.ifEmpty { "Logged Meal" },
                    color = ApexColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    com.apexfit.app.utils.formatTimeForDisplay(context, meal.time),
                    color = ApexColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${meal.calories} kcal",
                    color = ApexColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "P ${meal.protein.roundToInt()} · C ${meal.carbs.roundToInt()} · F ${meal.fat.roundToInt()} g",
                    color = ApexColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    painterResource(ApexIcons.Delete),
                    contentDescription = "Delete ${meal.name.ifEmpty { "meal" }}",
                    tint = ApexColors.TextSecondary
                )
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}

@Composable
internal fun NutritionDailySummary(
    calories: Int,
    target: Int?,
    trainingDay: Boolean,
    macros: List<Pair<String, String>>
) {
    val numbers = java.text.NumberFormat.getIntegerInstance()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(numbers.format(calories), style = ApexTextStyles.dataXL, color = ApexColors.TextPrimary)
                Text(
                    if (target == null) "kcal" else "/ ${numbers.format(target)} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = ApexColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            val remaining = target?.minus(calories)
            if (remaining != null) {
                Text(
                    when {
                        remaining > 0 -> "${numbers.format(remaining)} remaining"
                        remaining < 0 -> "${numbers.format(-remaining)} over"
                        else -> "Target reached"
                    },
                    color = ApexColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (calories.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = ApexColors.Primary,
                trackColor = ApexColors.SurfaceRaised
            )
            Text(
                if (trainingDay) "Training day target" else "Rest day target",
                color = ApexColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("Set a calorie target in Settings.", color = ApexColors.TextSecondary)
        }
        macros.forEachIndexed { index, (name, value) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                Text(value, color = ApexColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
            }
            if (index != macros.lastIndex) HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
}

@Composable
fun AddFoodSheet(
    fitnessViewModel: FitnessViewModel,
    nutritionViewModel: NutritionViewModel = hiltViewModel(),
    selectedDate: String,
    onDismiss: () -> Unit
) {
    var querySearch by rememberSaveable { mutableStateOf("") }
    var inputCal by rememberSaveable { mutableStateOf("") }
    var inputProt by rememberSaveable { mutableStateOf("") }
    var inputCarb by rememberSaveable { mutableStateOf("") }
    var inputFat by rememberSaveable { mutableStateOf("") }

    var calError by rememberSaveable { mutableStateOf("") }
    var protError by rememberSaveable { mutableStateOf("") }
    var carbError by rememberSaveable { mutableStateOf("") }
    var fatError by rememberSaveable { mutableStateOf("") }

    val isCalValid = inputCal.isNotEmpty() && inputCal.toIntOrNull()?.let { it in 0..5000 } == true
    val isProtValid = inputProt.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..500.0 } == true
    val isCarbValid = inputCarb.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..1000.0 } == true
    val isFatValid = inputFat.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..500.0 } == true

    val computedCalFromMacros = ((inputProt.replace(',', '.').toDoubleOrNull() ?: 0.0) * com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_PROTEIN) + ((inputCarb.replace(',', '.').toDoubleOrNull() ?: 0.0) * com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_CARB) + ((inputFat.replace(',', '.').toDoubleOrNull() ?: 0.0) * com.apexfit.app.utils.AppConstants.CALORIES_PER_GRAM_FAT)
    val showMacroCalWarning = computedCalFromMacros > 5000.0

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.nutrition_log_food_intake),
                color = ApexColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        containerColor = ApexColors.Surface,
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = run {
                        val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
                        "Logged at ${java.time.LocalTime.now().format(fmt)}"
                    },
                    fontSize = 11.sp,
                    color = ApexColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                OutlinedTextField(
                    value = querySearch,
                    onValueChange = { querySearch = it },
                    label = { Text(stringResource(R.string.nutrition_ingredient_or_food_name), color = ApexColors.TextSecondary) },
                    textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("add_food_name_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ApexColors.SurfaceRaised,
                        unfocusedContainerColor = ApexColors.SurfaceRaised,
                        focusedIndicatorColor = ApexColors.Primary,
                        unfocusedIndicatorColor = ApexColors.BorderSubtle
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
                        label = { Text(stringResource(R.string.nutrition_calories), color = ApexColors.TextSecondary, fontSize = 11.sp) },
                        textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("add_food_calories_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ApexColors.SurfaceRaised,
                            unfocusedContainerColor = ApexColors.SurfaceRaised,
                            focusedIndicatorColor = if (calError.isNotEmpty()) ApexColors.Alert else ApexColors.Primary,
                            unfocusedIndicatorColor = if (calError.isNotEmpty()) ApexColors.Alert else ApexColors.BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = inputProt,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal !in 0.0..500.0) {
                                    error = "Protein: 0–500g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Protein: 0–500g"
                            }
                            inputProt = clean
                            protError = error
                        },
                        label = { Text(stringResource(R.string.nutrition_protein_g), color = ApexColors.TextSecondary, fontSize = 11.sp) },
                        textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_protein_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ApexColors.SurfaceRaised,
                            unfocusedContainerColor = ApexColors.SurfaceRaised,
                            focusedIndicatorColor = if (protError.isNotEmpty()) ApexColors.Alert else ApexColors.Primary,
                            unfocusedIndicatorColor = if (protError.isNotEmpty()) ApexColors.Alert else ApexColors.BorderSubtle
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputCarb,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal !in 0.0..1000.0) {
                                    error = "Carbs: 0–1000g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Carbs: 0–1000g"
                            }
                            inputCarb = clean
                            carbError = error
                        },
                        label = { Text(stringResource(R.string.nutrition_carbs_g), color = ApexColors.TextSecondary, fontSize = 11.sp) },
                        textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_carbs_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ApexColors.SurfaceRaised,
                            unfocusedContainerColor = ApexColors.SurfaceRaised,
                            focusedIndicatorColor = if (carbError.isNotEmpty()) ApexColors.Alert else ApexColors.Primary,
                            unfocusedIndicatorColor = if (carbError.isNotEmpty()) ApexColors.Alert else ApexColors.BorderSubtle
                        )
                    )
                    OutlinedTextField(
                        value = inputFat,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                            val clean = if (filtered.count { it == '.' } > 1) {
                                val firstDot = filtered.indexOf('.')
                                filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
                            } else {
                                filtered
                            }

                            var error = ""
                            val dVal = clean.toDoubleOrNull()
                            if (dVal != null) {
                                if (dVal !in 0.0..500.0) {
                                    error = "Fat: 0–500g"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Fat: 0–500g"
                            }
                            inputFat = clean
                            fatError = error
                        },
                        label = { Text(stringResource(R.string.nutrition_fat_g), color = ApexColors.TextSecondary, fontSize = 11.sp) },
                        textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("add_food_fat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ApexColors.SurfaceRaised,
                            unfocusedContainerColor = ApexColors.SurfaceRaised,
                            focusedIndicatorColor = if (fatError.isNotEmpty()) ApexColors.Alert else ApexColors.Primary,
                            unfocusedIndicatorColor = if (fatError.isNotEmpty()) ApexColors.Alert else ApexColors.BorderSubtle
                        )
                    )
                }

                Text(stringResource(R.string.nutrition_quick_add), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ApexColors.TextPrimary)
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
                        border = BorderStroke(1.dp, ApexColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.Primary),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text(stringResource(R.string.nutrition_100_kcal), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ApexColors.TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputProt.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 10.0).coerceAtMost(500.0)
                            inputProt = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            protError = if (newVal == 500.0) "Protein: 0–500g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApexColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.Primary),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text(stringResource(R.string.nutrition_10g_prot), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ApexColors.TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputCarb.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 10.0).coerceAtMost(1000.0)
                            inputCarb = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            carbError = if (newVal == 1000.0) "Carbs: 0–1000g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApexColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.Primary),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text(stringResource(R.string.nutrition_10g_carbs), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ApexColors.TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            val curr = inputFat.toDoubleOrNull() ?: 0.0
                            val newVal = (curr + 5.0).coerceAtMost(500.0)
                            inputFat = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                            fatError = if (newVal == 500.0) "Fat: 0–500g" else ""
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApexColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.Primary),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text(stringResource(R.string.nutrition_5g_fat), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ApexColors.TextPrimary)
                    }
                }

                val errors = listOf(calError, protError, carbError, fatError).filter { it.isNotEmpty() }
                if (errors.isNotEmpty()) {
                    Text(
                        text = errors.first(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = ApexColors.Alert,
                        modifier = Modifier.testTag("nutrition_error_msg")
                    )
                }

                if (showMacroCalWarning) {
                    Text(
                        text = "Total exceeds 5000 cal",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = ApexColors.Alert,
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
                    val cal = inputCal.toIntOrNull() ?: 0
                    val prot = inputProt.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val carb = inputCarb.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val fat = inputFat.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val mealName = querySearch.trim().ifEmpty { "Logged Meal" }
                    nutritionViewModel.logNutrition(cal, prot, carb, fat, date = selectedDate, name = mealName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexColors.Primary,
                    disabledContainerColor = ApexColors.SurfaceRaised.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "LOG MEAL",
                    fontWeight = FontWeight.Bold,
                    color = if (isMealValid) ApexColors.OnGlow else ApexColors.TextTertiary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nutrition_cancel), color = ApexColors.TextSecondary)
            }
        }
    )
}

@Composable
fun AdaptiveCalorieTargetCard(
    tdeeResult: com.apexfit.app.utils.TDEEResult,
    userGoal: String,
    fitnessViewModel: FitnessViewModel,
    modifier: Modifier = Modifier
) {
    var showAcceptDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.nutrition_adaptive_calorie_target),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ApexColors.TextPrimary
                )
                Icon(
                    painter = painterResource(ApexIcons.TrendingUp),
                    contentDescription = "Adaptive",
                    tint = ApexColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            when {
                // ─ HIGH CONFIDENCE ─────────────────────────────────────
                tdeeResult.confidence == "high" && tdeeResult.tdee != null -> {
                    val (targetCals, goalLabel) = remember(tdeeResult.tdee, userGoal) {
                        val suggestedCals = tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES
                        val label = when (userGoal) {
                            "Gain Muscle" -> "Lean Gain"
                            "Lose Fat" -> "Fat Loss"
                            else -> "Maintenance"
                        }
                        val cals = when (userGoal) {
                            "Gain Muscle" -> suggestedCals + 300
                            "Lose Fat" -> suggestedCals - 400
                            else -> suggestedCals
                        }
                        cals to label
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.nutrition_your_real_calorie_burn_this_we, tdeeResult.tdee),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.Victory,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_suggested_target_for_kcal, goalLabel, targetCals),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextPrimary
                        )
                        Text(stringResource(R.string.nutrition_based_on_tdeeresult_weightchan, tdeeResult.weightChangeKg, tdeeResult.avgCalories),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextSecondary,
                            lineHeight = 14.sp
                        )

                        Button(
                            onClick = { showAcceptDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary)
                        ) {
                            Text(stringResource(R.string.nutrition_accept_target),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = ApexColors.TextPrimary
                            )
                        }

                        if (showAcceptDialog) {
                            AlertDialog(
                                onDismissRequest = { showAcceptDialog = false },
                                title = { Text(stringResource(R.string.nutrition_update_calorie_target)) },
                                text = { Text(stringResource(R.string.nutrition_your_new_daily_target_will_be_, targetCals, goalLabel)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        fitnessViewModel.setManualCalorieTarget(true, targetCals)
                                        showAcceptDialog = false
                                    }) {
                                        Text(stringResource(R.string.nutrition_confirm))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAcceptDialog = false }) {
                                        Text(stringResource(R.string.nutrition_cancel))
                                    }
                                }
                            )
                        }
                    }
                }

                // ─ MEDIUM CONFIDENCE ───────────────────────────────────
                tdeeResult.confidence == "medium" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.nutrition_estimated_burn_kcal, tdeeResult.tdee ?: tdeeResult.avgCalories),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_accuracy_improving_keep_loggin),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.Amber,
                            fontWeight = FontWeight.Medium
                        )
                        Text(stringResource(R.string.nutrition_you_have_4_6_days_of_data_a_pr),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                // ─ LOW CONFIDENCE ──────────────────────────────────────
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.nutrition_calculating_your_personal_tdee),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_log_7_more_days_to_activate_ad),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(stringResource(R.string.nutrition_the_system_learns_your_metabol),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ApexColors.TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}

private fun partitionMealsByTime(loggedMeals: List<com.apexfit.app.ui.models.UiNutritionEntry>): Triple<List<com.apexfit.app.ui.models.UiNutritionEntry>, List<com.apexfit.app.ui.models.UiNutritionEntry>, List<com.apexfit.app.ui.models.UiNutritionEntry>> {
    val morning = mutableListOf<com.apexfit.app.ui.models.UiNutritionEntry>()
    val afternoon = mutableListOf<com.apexfit.app.ui.models.UiNutritionEntry>()
    val evening = mutableListOf<com.apexfit.app.ui.models.UiNutritionEntry>()
    for (entry in loggedMeals) {
        when {
            entry.time in "06:00"..<"12:00" -> morning.add(entry)
            entry.time in "12:00"..<"16:00" -> afternoon.add(entry)
            entry.time >= "20:00" -> evening.add(entry)
        }
    }
    return Triple(morning, afternoon, evening)
}

@Composable
fun ProteinTimingCard(todayEntries: List<com.apexfit.app.ui.models.UiNutritionEntry>) {
    val (morningMeals, afternoonMeals, eveningMeals) = remember(todayEntries) { partitionMealsByTime(todayEntries) }
    val preWorkoutProtein = remember(morningMeals) { morningMeals.sumOf { it.protein } }
    val postWorkoutProtein = remember(afternoonMeals) { afternoonMeals.sumOf { it.protein } }
    val eveningProtein = remember(eveningMeals) { eveningMeals.sumOf { it.protein } }

    val tip = when {
        postWorkoutProtein < 20 -> "Log a post-workout meal with 20+ g protein"
        eveningProtein > 50     -> "Consider spreading evening protein across earlier meals"
        preWorkoutProtein < 10  -> "A small protein source before training may help"
        else                    -> "Good protein distribution today"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Protein timing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ApexColors.TextPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = ApexColors.TextSecondary,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("06:00–12:00", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary)
                Text("${preWorkoutProtein.roundToInt()} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ApexColors.TextPrimary)
            }
            Column {
                Text("12:00–16:00", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary)
                Text("${postWorkoutProtein.roundToInt()} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ApexColors.TextPrimary)
            }
            Column {
                Text("20:00+", style = MaterialTheme.typography.labelSmall, color = ApexColors.TextTertiary)
                Text("${eveningProtein.roundToInt()} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ApexColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}


