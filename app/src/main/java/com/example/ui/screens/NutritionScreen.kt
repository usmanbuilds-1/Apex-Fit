package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.models.UiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.R
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FitnessViewModel
import com.example.NutritionViewModel
import com.example.AlgorithmViewModel
import com.example.ui.theme.*
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    nutritionViewModel: NutritionViewModel,
    onNavigateTo: (Int) -> Unit
) {
    val selectedDate by nutritionViewModel.selectedNutritionDate.collectAsStateWithLifecycle()
    val loggedMealsState by nutritionViewModel.loggedMeals.collectAsStateWithLifecycle()
    
    if (loggedMealsState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GreenAccent)
        }
        return
    }
    if (loggedMealsState is UiState.Error) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nutrition_error_loading_meals), color = Color.White)
        }
        return
    }
    
    val loggedMeals: List<com.example.ui.models.UiNutritionEntry> = (loggedMealsState as? UiState.Success)?.data ?: emptyList()
    val calorieTargetManual by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val calorieTargetValue by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val tdeeResult by algorithmViewModel.tdeeResult.collectAsStateWithLifecycle()
    val userGoal by fitnessViewModel.goal.collectAsStateWithLifecycle()

    val calorieTarget = if (calorieTargetManual) calorieTargetValue else 2650

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pendingDeletions = remember { mutableStateMapOf<Long, Job>() }

    val visibleMeals = loggedMeals.filter { it.id !in pendingDeletions.keys }

    val loggedCalories = visibleMeals.sumOf { it.calories }
    val loggedProtein = visibleMeals.sumOf { it.protein }
    val loggedCarbs = visibleMeals.sumOf { it.carbs }
    val loggedFat = visibleMeals.sumOf { it.fat }

    var showAddFoodModal by rememberSaveable { mutableStateOf(false) }

    fun triggerPendingDeletion(meal: com.example.ui.models.UiNutritionEntry) {
        if (pendingDeletions.containsKey(meal.id)) return

        val job = coroutineScope.launch {
            delay(4000)
            fitnessViewModel.deleteNutritionEntryById(meal.id)
            pendingDeletions.remove(meal.id)
        }
        pendingDeletions[meal.id] = job

        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Meal deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeletions[meal.id]?.cancel()
                pendingDeletions.remove(meal.id)
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    text = "Nutrition",
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
                                
                                val ringColor = when {
                                    userGoal.lowercase().contains("cut") -> {
                                        if (loggedCalories <= calorieTarget * 1.05) GreenAccent
                                        else if (loggedCalories <= calorieTarget * 1.15) Color(0xFFF59E0B) // Amber
                                        else RedAccent
                                    }
                                    userGoal.lowercase().contains("bulk") -> {
                                        if (loggedCalories >= calorieTarget * 0.85 && loggedCalories <= calorieTarget * 1.15) GreenAccent
                                        else if (loggedCalories < calorieTarget * 0.85) RedAccent // Under-eating on bulk
                                        else Color(0xFFF59E0B) // Significantly over
                                    }
                                    userGoal.lowercase().contains("maintain") -> {
                                        if (loggedCalories >= calorieTarget * 0.95 && loggedCalories <= calorieTarget * 1.05) GreenAccent
                                        else if (loggedCalories >= calorieTarget * 0.85 && loggedCalories <= calorieTarget * 1.15) Color(0xFFF59E0B)
                                        else RedAccent
                                    }
                                    else -> if (loggedCalories >= calorieTarget) GreenAccent else OrangeAccent
                                }

                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.nutrition_fmt_str, (loggedCalories * 100 / calorieTarget).coerceIn(0, 100)), fontFamily = JetBrainsMonoFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                            Text(stringResource(R.string.nutrition_kcal), fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = SecondaryText)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.nutrition_target_kcal_logged_kcal, calorieTarget, loggedCalories), fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = PrimaryText)
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
                Text(stringResource(R.string.nutrition_log_meal), fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A0A0F))
            }
        }

        // Meals details lists
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Today's Meals",
                    fontFamily = SyneFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MutedText
                )

                if (visibleMeals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.nutrition_no_meals_logged_yet), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
                    }
                } else {
                    visibleMeals.forEach { meal ->
                        key(meal.id) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp)),
                                backgroundContent = {
                                    val color = when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> RedAccent.copy(alpha = 0.8f)
                                        SwipeToDismissBoxValue.StartToEnd -> RedAccent.copy(alpha = 0.8f)
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.White
                                        )
                                    }
                                },
                                content = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkCardSurface)
                                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                                            .clickable(onClick = {})
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(stringResource(R.string.nutrition_logged_at, com.example.utils.formatTimeForDisplay(LocalContext.current, meal.time)), fontFamily = JetBrainsMonoFamily, fontSize = 8.sp, color = AmberAccent)
                                                Text(meal.name.ifEmpty { "Logged Meal" }, fontFamily = SyneFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                                Text(
                                                    text = "${meal.calories} kcal • P: ${meal.protein.toInt()}g C: ${meal.carbs.toInt()}g F: ${meal.fat.toInt()}g",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 11.sp,
                                                    color = SecondaryText
                                                )
                                            }
                                            IconButton(onClick = { triggerPendingDeletion(meal) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = MutedText)
                                            }
                                        }
                                    }
                                }
                            )
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
}

    if (showAddFoodModal) {
        AddFoodSheet(
            fitnessViewModel = fitnessViewModel,
            selectedDate = selectedDate,
            onDismiss = { showAddFoodModal = false }
        )
    }
}

@Composable
fun MacroStatMini(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(stringResource(R.string.nutrition_fmt_str_1, label, value), fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = SecondaryText)
    }
}

@Composable
fun AddFoodSheet(
    fitnessViewModel: FitnessViewModel,
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
    val isProtValid = inputProt.isNotEmpty() && inputProt.toDoubleOrNull()?.let { it in 0.0..500.0 } == true
    val isCarbValid = inputCarb.isNotEmpty() && inputCarb.toDoubleOrNull()?.let { it in 0.0..500.0 } == true
    val isFatValid = inputFat.isNotEmpty() && inputFat.toDoubleOrNull()?.let { it in 0.0..500.0 } == true

    val computedCalFromMacros = ((inputProt.toDoubleOrNull() ?: 0.0) * 4.0) + ((inputCarb.toDoubleOrNull() ?: 0.0) * 4.0) + ((inputFat.toDoubleOrNull() ?: 0.0) * 9.0)
    val showMacroCalWarning = computedCalFromMacros > 5000.0

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_log_food_intake), fontFamily = SyneFamily, fontWeight = FontWeight.Bold) },
        containerColor = DarkCardSurface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "STATUS: Auto-tracking local time (${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = AmberAccent,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                OutlinedTextField(
                    value = querySearch,
                    onValueChange = { querySearch = it },
                    label = { Text(stringResource(R.string.nutrition_ingredient_or_food_name), color = SecondaryText) },
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
                        label = { Text(stringResource(R.string.nutrition_calories), color = SecondaryText, fontSize = 10.sp) },
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
                        label = { Text(stringResource(R.string.nutrition_protein_g), color = SecondaryText, fontSize = 10.sp) },
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
                        label = { Text(stringResource(R.string.nutrition_carbs_g), color = SecondaryText, fontSize = 10.sp) },
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
                        label = { Text(stringResource(R.string.nutrition_fat_g), color = SecondaryText, fontSize = 10.sp) },
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

                Text(stringResource(R.string.nutrition_quick_add), fontFamily = SyneFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
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
                        Text(stringResource(R.string.nutrition_100_kcal), fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
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
                        Text(stringResource(R.string.nutrition_10g_prot), fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
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
                        Text(stringResource(R.string.nutrition_10g_carbs), fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
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
                        Text(stringResource(R.string.nutrition_5g_fat), fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = PrimaryText)
                    }
                }

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
                    fitnessViewModel.logNutrition(cal, prot, carb, fat, date = selectedDate, name = mealName)
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
                Text(stringResource(R.string.nutrition_cancel), color = SecondaryText)
            }
        }
    )
}

@Composable
fun AdaptiveCalorieTargetCard(
    tdeeResult: com.example.utils.TDEEResult,
    userGoal: String,
    fitnessViewModel: FitnessViewModel,
    modifier: Modifier = Modifier
) {
    var showAcceptDialog by rememberSaveable { mutableStateOf(false) }

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.nutrition_adaptive_calorie_target),
                    fontFamily = SyneFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText
                )
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = "Adaptive",
                    tint = OrangeAccent,
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
                        Text(stringResource(R.string.nutrition_your_real_calorie_burn_this_we, tdeeResult.tdee),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_suggested_target_for_kcal, goalLabel, targetCals),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText
                        )
                        Text(stringResource(R.string.nutrition_based_on_tdeeresult_weightchan, tdeeResult.weightChangeKg, tdeeResult.avgCalories),
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
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
                        ) {
                            Text(stringResource(R.string.nutrition_accept_target),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F0F5)
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
                            color = PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_accuracy_improving_keep_loggin),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Color(0xFFF97316),
                            fontWeight = FontWeight.Medium
                        )
                        Text(stringResource(R.string.nutrition_you_have_4_6_days_of_data_a_pr),
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
                        Text(stringResource(R.string.nutrition_calculating_your_personal_tdee),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.nutrition_log_7_more_days_to_activate_ad),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Color(0xFF8A8A9A),
                            fontWeight = FontWeight.Medium
                        )
                        Text(stringResource(R.string.nutrition_the_system_learns_your_metabol),
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

