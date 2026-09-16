package com.apexfit.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.apexfit.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.utils.toDisplayWeight
import com.apexfit.app.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew

@Composable
fun SettingsScreen(
    fitnessViewModel: FitnessViewModel,
    onNavigateTo: (Int) -> Unit
) {
    val username by fitnessViewModel.username.collectAsStateWithLifecycle()
    val goal by fitnessViewModel.goal.collectAsStateWithLifecycle()
    val manualCalorieTarget by fitnessViewModel.calorieTargetManual.collectAsStateWithLifecycle()
    val manualCalorieVal by fitnessViewModel.calorieTargetValue.collectAsStateWithLifecycle()
    val userHeight by fitnessViewModel.userHeight.collectAsStateWithLifecycle()
    val userAge by fitnessViewModel.userAge.collectAsStateWithLifecycle()
    val userSex by fitnessViewModel.userSex.collectAsStateWithLifecycle()
    val weeklyWorkouts by fitnessViewModel.weeklyWorkouts.collectAsStateWithLifecycle()
    val currentEquipment by fitnessViewModel.equipmentAvailable.collectAsStateWithLifecycle()
    val currentWeightVal by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val currentGoalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var editName by rememberSaveable { mutableStateOf(username) }
    var editGoal by rememberSaveable { mutableStateOf(goal) }
    var editedManualCalValue by rememberSaveable { mutableStateOf(manualCalorieVal.toString()) }
    var editHeight by rememberSaveable { mutableStateOf(userHeight.toString()) }
    var editAge by rememberSaveable { mutableStateOf(userAge.toString()) }
    var editSex by remember(userSex) { mutableStateOf(userSex) }
    var editIsManual by rememberSaveable { mutableStateOf(manualCalorieTarget) }
    var editCurrentWeight by rememberSaveable { 
        mutableStateOf(currentWeightVal.toDisplayWeight(units).toString()) 
    }
    var editGoalWeight by rememberSaveable { 
        mutableStateOf(currentGoalWeight.toDisplayWeight(units).toString()) 
    }

    var manualCalError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var currentWeightError by remember { mutableStateOf<String?>(null) }
    var goalWeightError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        if (editName.isEmpty()) editName = username
    }
    LaunchedEffect(goal) {
        if (editGoal.isEmpty()) editGoal = goal
    }
    LaunchedEffect(manualCalorieVal) {
        if (editedManualCalValue.isEmpty() || editedManualCalValue == "0") {
            editedManualCalValue = manualCalorieVal.toString()
        }
    }
    LaunchedEffect(userHeight) {
        if (editHeight.isEmpty() || editHeight == "0" || editHeight == "0.0") {
            editHeight = userHeight.toString()
        }
    }
    LaunchedEffect(userAge) {
        if (editAge.isEmpty() || editAge == "0") {
            editAge = userAge.toString()
        }
    }
    LaunchedEffect(currentWeightVal) {
        if (editCurrentWeight.isEmpty() || editCurrentWeight == "0.0") {
            editCurrentWeight = currentWeightVal.toDisplayWeight(units).toString()
        }
    }
    LaunchedEffect(currentGoalWeight) {
        if (editGoalWeight.isEmpty() || editGoalWeight == "0.0") {
            editGoalWeight = currentGoalWeight.toDisplayWeight(units).toString()
        }
    }
    LaunchedEffect(units) {
        if (currentWeightVal > 0.0) {
            editCurrentWeight = currentWeightVal.toDisplayWeight(units).toString()
        }
        if (currentGoalWeight > 0.0) {
            editGoalWeight = currentGoalWeight.toDisplayWeight(units).toString()
        }
        currentWeightError = null
        goalWeightError = null
    }

    val weightMin = if (units == "kg") 30.0 else 66.0
    val weightMax = if (units == "kg") 300.0 else 660.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Name
        Text(
            text = "ATHLETE FILE MANAGEMENT",
            fontFamily = SyneFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PrimaryText,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text(stringResource(R.string.settings_athlete_nickname), color = SecondaryText) },
            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
            modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkRaised,
                unfocusedContainerColor = DarkRaised,
                focusedIndicatorColor = AmberAccent,
                unfocusedIndicatorColor = BorderSubtle
            )
        )

        // Goal Selector — constrained to 3 valid values to prevent silent else-branch hits
        Text(
            text = stringResource(R.string.settings_goal_objective_profile),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            color = SecondaryText,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("settings_goal_input")
        ) {
            listOf("Gain Muscle", "Lose Fat", "Maintain").forEach { option ->
                val isSelected = editGoal == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AmberAccent else DarkRaised)
                        .border(
                            border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { editGoal = option }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0A0A0F) else PrimaryText
                    )
                }
            }
        }

        // Calorie target mode selector
        Text(
            text = "CALORIE TARGET",
            fontFamily = SyneFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SecondaryText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(false to "SMART", true to "MANUAL").forEach { (isManual, label) ->
                val selected = editIsManual == isManual
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) OrangeAccent else DarkRaised)
                        .border(1.dp, if (selected) OrangeAccent else BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { editIsManual = isManual }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = SyneFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selected) Color.Black else SecondaryText,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (editIsManual) {
            OutlinedTextField(
                value = editedManualCalValue,
                onValueChange = {
                    val filtered = it.filter { c -> c.isDigit() }
                    editedManualCalValue = filtered
                    val v = filtered.toIntOrNull()
                    manualCalError = if (v != null && v !in 1000..6000) "Must be 1000-6000" else null
                },
                isError = manualCalError != null,
                label = { Text("Target (1000–6000 kcal)", color = SecondaryText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("settings_manual_calorie_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = if (manualCalError != null) RedAccent else AmberAccent,
                    unfocusedIndicatorColor = if (manualCalError != null) RedAccent else BorderSubtle
                )
            )
        } else {
            Text(
                text = "Apex Fit will calculate your target based on your weight trend and goal.",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = SecondaryText,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        manualCalError?.let {
            Text(
                text = it,
                color = RedAccent,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }

        // Height & Age Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = editHeight,
                onValueChange = { editHeight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                label = { Text(stringResource(R.string.settings_height_cm), color = SecondaryText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("settings_height_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = AmberAccent,
                    unfocusedIndicatorColor = BorderSubtle
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = editAge,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        editAge = filtered
                        val v = filtered.toIntOrNull()
                        ageError = if (v != null && v !in 13..100) "Age must be 13-100" else null
                    },
                    isError = ageError != null,
                    label = { Text(stringResource(R.string.settings_age_yrs), color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("settings_age_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = if (ageError != null) RedAccent else AmberAccent,
                        unfocusedIndicatorColor = if (ageError != null) RedAccent else BorderSubtle
                    )
                )
                ageError?.let {
                    Text(
                        text = it,
                        color = RedAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
            }
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
                        .padding(vertical = 12.dp)
                        .heightIn(min = 48.dp)
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

        // Weekly Workouts Select
        Text(
            text = "WEEKLY WORKOUTS",
            fontFamily = SyneFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SecondaryText,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1", "2", "3", "4", "5", "6+").forEach { option ->
                val optVal = if (option == "6+") 6 else option.toIntOrNull() ?: 3
                val selected = (weeklyWorkouts >= 6 && option == "6+") || (weeklyWorkouts < 6 && weeklyWorkouts.toString() == option)
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
                        .clickable { fitnessViewModel.updateWeeklyWorkouts(optVal) }
                        .padding(vertical = 12.dp)
                        .heightIn(min = 48.dp)
                        .testTag("settings_weekly_workouts_$option"),
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

        // Units Select
        Text(
            text = "UNITS SYSTEM",
            fontFamily = SyneFamily,
            fontSize = 11.sp,
            color = SecondaryText,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val unitOptions = listOf("Metric (kg / cm)" to "kg", "Imperial (lb)" to "lb")
            for ((label, code) in unitOptions) {
                val isSelected = units == code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AmberAccent else DarkRaised)
                        .border(
                            border = BorderStroke(1.dp, if (isSelected) AmberAccent else BorderSubtle),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { fitnessViewModel.setPreferredUnits(code) }
                        .padding(vertical = 12.dp)
                        .heightIn(min = 48.dp)
                        .testTag("settings_unit_$code"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.uppercase(),
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0A0A0F) else PrimaryText
                    )
                }
            }
        }

        // Weight Fields Row
        Text(
            text = "WEIGHT METRICS",
            fontFamily = SyneFamily,
            fontSize = 11.sp,
            color = SecondaryText,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = editCurrentWeight,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                        editCurrentWeight = filtered
                        val v = filtered.toDoubleOrNull()
                        currentWeightError = if (v != null && v !in weightMin..weightMax) "Must be $weightMin-$weightMax $units" else null
                    },
                    isError = currentWeightError != null,
                    label = { Text(stringResource(R.string.settings_current_weight, units), color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("settings_current_weight_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = if (currentWeightError != null) RedAccent else AmberAccent,
                        unfocusedIndicatorColor = if (currentWeightError != null) RedAccent else BorderSubtle
                    )
                )
                currentWeightError?.let {
                    Text(
                        text = it,
                        color = RedAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = editGoalWeight,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                        editGoalWeight = filtered
                        val v = filtered.toDoubleOrNull()
                        goalWeightError = if (v != null && v !in weightMin..weightMax) "Must be $weightMin-$weightMax $units" else null
                    },
                    isError = goalWeightError != null,
                    label = { Text(stringResource(R.string.settings_goal_weight, units), color = SecondaryText) },
                    textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("settings_goal_weight_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkRaised,
                        unfocusedContainerColor = DarkRaised,
                        focusedIndicatorColor = if (goalWeightError != null) RedAccent else AmberAccent,
                        unfocusedIndicatorColor = if (goalWeightError != null) RedAccent else BorderSubtle
                    )
                )
                goalWeightError?.let {
                    Text(
                        text = it,
                        color = RedAccent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save & Dismiss options
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onNavigateTo(0) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(R.string.settings_cancel), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = PrimaryText)
            }

            Button(
                onClick = {
                    val calVal = editedManualCalValue.toIntOrNull() ?: 0
                    if (calVal !in 1000..6000) {
                        Toast.makeText(context, "Calorie target must be 1000-6000", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val ageVal = editAge.toIntOrNull() ?: 0
                    if (ageVal !in 13..100) {
                        Toast.makeText(context, "Age must be 13-100", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val cw = editCurrentWeight.toDoubleOrNull()
                    val gw = editGoalWeight.toDoubleOrNull()
                    if (cw == null || cw !in weightMin..weightMax || gw == null || gw !in weightMin..weightMax) {
                        Toast.makeText(context, "Weight must be $weightMin-$weightMax $units", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val hVal = editHeight.toDoubleOrNull() ?: com.apexfit.app.UserDefaults.HEIGHT_CM
                    val aVal = editAge.toIntOrNull() ?: com.apexfit.app.UserDefaults.AGE_YEARS
                    val equipmentVal = if (currentEquipment.isNotEmpty()) currentEquipment else com.apexfit.app.UserDefaults.DEFAULT_EQUIPMENT
                    fitnessViewModel.updateProfile(
                        name = editName,
                        userGoal = editGoal,
                        targetUnit = units,
                        equipment = equipmentVal,
                        height = hVal,
                        age = aVal,
                        sex = editSex.lowercase()
                    )
                    val cInt = editedManualCalValue.toIntOrNull() ?: com.apexfit.app.UserDefaults.CALORIES
                    fitnessViewModel.setManualCalorieTarget(editIsManual, if (editIsManual) cInt else 0)

                    val gW = editGoalWeight.toDoubleOrNull() ?: currentGoalWeight
                    val cW = editCurrentWeight.toDoubleOrNull() ?: currentWeightVal
                    fitnessViewModel.setGoalWeight(gW, preferredUnit = units)
                    fitnessViewModel.setBodyWeight(cW, preferredUnit = units)

                    Toast.makeText(context, context.getString(R.string.settings_profile_saved_successfully), Toast.LENGTH_SHORT).show()
                    onNavigateTo(0)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(R.string.settings_save_profile), fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0F))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var hasStartedReset by rememberSaveable { mutableStateOf(false) }
    val isResetting by fitnessViewModel.isResetting.collectAsStateWithLifecycle()

    LaunchedEffect(isResetting) {
        if (hasStartedReset && !isResetting) {
            onNavigateTo(0)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_all_data)) },
            text = { Text(stringResource(R.string.settings_this_will_permanently_delete_a)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hasStartedReset = true
                        fitnessViewModel.resetAllData()
                    },
                    enabled = !isResetting
                ) {
                    Text(stringResource(R.string.settings_reset_everything), color = if (isResetting) Color.Gray else Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !isResetting
                ) {
                    Text(stringResource(R.string.settings_cancel_1))
                }
            }
        )
    }

    Button(
        onClick = {
            showResetDialog = true
        },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_data_button"),
        colors = ButtonDefaults.buttonColors(containerColor = if (isResetting) Color.Gray else RedAccent),
        shape = RoundedCornerShape(10.dp),
        enabled = !isResetting
    ) {
        Text(if (isResetting) "RESETTING..." else "RESET TOTAL ATHLETE DIRECTORY DATA", fontFamily = SyneFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
    }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "LEGAL",
            fontFamily = SyneFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SecondaryText,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        val context = LocalContext.current

        PremiumCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(com.apexfit.app.BuildConfig.PRIVACY_POLICY_URL)
                    )
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Privacy Policy", fontFamily = InterFamily, fontSize = 14.sp, color = PrimaryText)
                Icon(Icons.Filled.OpenInNew, contentDescription = "Opens Privacy Policy in browser",
                    tint = SecondaryText, modifier = Modifier.size(16.dp))
            }
        }

        PremiumCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            onClick = {
                fitnessViewModel.exportUserData(context) { uri ->
                    if (uri != null) {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Export Data"))
                    }
                }
            }
        ) {
            Text("Export My Data (CSV)",
                fontFamily = InterFamily, fontSize = 14.sp, color = PrimaryText)
        }

        PremiumCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(com.apexfit.app.BuildConfig.TERMS_URL)
                    )
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Terms of Service", fontFamily = InterFamily, fontSize = 14.sp, color = PrimaryText)
                Icon(Icons.Filled.OpenInNew, contentDescription = "Opens Terms of Service in browser",
                    tint = SecondaryText, modifier = Modifier.size(16.dp))
            }
        }
    }
}