package com.example.ui.screens

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
import androidx.compose.ui.Alignment
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
import com.example.FitnessViewModel
import com.example.ui.theme.*

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
    val currentEquipment by fitnessViewModel.equipmentAvailable.collectAsStateWithLifecycle()
    val currentWeightVal by fitnessViewModel.currentWeight.collectAsStateWithLifecycle()
    val currentGoalWeight by fitnessViewModel.goalWeight.collectAsStateWithLifecycle()
    val units by fitnessViewModel.units.collectAsStateWithLifecycle()

    val context = LocalContext.current

    var editName by remember(username) { mutableStateOf(username) }
    var editGoal by remember(goal) { mutableStateOf(goal) }
    var editedManualCalValue by remember(manualCalorieVal) { mutableStateOf(manualCalorieVal.toString()) }
    var editHeight by remember(userHeight) { mutableStateOf(userHeight.toString()) }
    var editAge by remember(userAge) { mutableStateOf(userAge.toString()) }
    var editSex by remember(userSex) { mutableStateOf(userSex) }
    var editCurrentWeight by remember(currentWeightVal) { mutableStateOf(currentWeightVal.toString()) }
    var editGoalWeight by remember(currentGoalWeight) { mutableStateOf(currentGoalWeight.toString()) }

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
            label = { Text("Athlete Nickname", color = SecondaryText) },
            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
            modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkRaised,
                unfocusedContainerColor = DarkRaised,
                focusedIndicatorColor = AmberAccent,
                unfocusedIndicatorColor = BorderSubtle
            )
        )

        // Goal Selector
        OutlinedTextField(
            value = editGoal,
            onValueChange = { editGoal = it },
            label = { Text("Goal objective profile", color = SecondaryText) },
            textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
            modifier = Modifier.fillMaxWidth().testTag("settings_goal_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkRaised,
                unfocusedContainerColor = DarkRaised,
                focusedIndicatorColor = AmberAccent,
                unfocusedIndicatorColor = BorderSubtle
            )
        )

        // Calorie Target Value override
        OutlinedTextField(
            value = editedManualCalValue,
            onValueChange = { editedManualCalValue = it },
            label = { Text("Manual Calorie Target limit", color = SecondaryText) },
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
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = AmberAccent,
                    unfocusedIndicatorColor = BorderSubtle
                )
            )
            OutlinedTextField(
                value = editAge,
                onValueChange = { editAge = it },
                label = { Text("Age (yrs)", color = SecondaryText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("settings_age_input"),
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
            val unitOptions = listOf("Metric (kg / cm)" to "kg", "Imperial (lb / in)" to "lb")
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
                        .padding(vertical = 10.dp)
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
            OutlinedTextField(
                value = editCurrentWeight,
                onValueChange = { editCurrentWeight = it },
                label = { Text("Current Weight ($units)", color = SecondaryText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("settings_current_weight_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = AmberAccent,
                    unfocusedIndicatorColor = BorderSubtle
                )
            )
            OutlinedTextField(
                value = editGoalWeight,
                onValueChange = { editGoalWeight = it },
                label = { Text("Goal Weight ($units)", color = SecondaryText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("settings_goal_weight_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = AmberAccent,
                    unfocusedIndicatorColor = BorderSubtle
                )
            )
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
                Text("CANCEL", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = PrimaryText)
            }

            Button(
                onClick = {
                    val hVal = editHeight.toDoubleOrNull() ?: com.example.UserDefaults.HEIGHT_CM
                    val aVal = editAge.toIntOrNull() ?: com.example.UserDefaults.AGE_YEARS
                    val equipmentVal = if (currentEquipment.isNotEmpty()) currentEquipment else "Barbell,Dumbbell,Cable,Machine"
                    fitnessViewModel.updateProfile(
                        name = editName,
                        userGoal = editGoal,
                        targetUnit = units,
                        key = "",
                        equipment = equipmentVal,
                        height = hVal,
                        age = aVal,
                        sex = editSex.lowercase()
                    )
                    val cInt = editedManualCalValue.toIntOrNull() ?: com.example.UserDefaults.CALORIES
                    fitnessViewModel.setManualCalorieTarget(true, cInt)

                    val gW = editGoalWeight.toDoubleOrNull() ?: currentGoalWeight
                    val cW = editCurrentWeight.toDoubleOrNull() ?: currentWeightVal
                    fitnessViewModel.setGoalWeight(gW)
                    fitnessViewModel.setBodyWeight(cW)

                    Toast.makeText(context, "Profile Saved Successfully", Toast.LENGTH_SHORT).show()
                    onNavigateTo(0)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("SAVE PROFILE", fontFamily = SyneFamily, fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0F))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

    var showResetDialog by remember { mutableStateOf(false) }
    var hasStartedReset by remember { mutableStateOf(false) }
    val isResetting by fitnessViewModel.isResetting.collectAsStateWithLifecycle()

    LaunchedEffect(isResetting) {
        if (hasStartedReset && !isResetting) {
            onNavigateTo(0)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = { Text("Reset all data?") },
            text = { Text("This will permanently delete all your workouts, meals, body weight history, personal records, and profile settings. This cannot be undone. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        hasStartedReset = true
                        fitnessViewModel.resetAllData()
                    },
                    enabled = !isResetting
                ) {
                    Text("Reset Everything", color = if (isResetting) Color.Gray else Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !isResetting
                ) {
                    Text("Cancel")
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
        Text(if (isResetting) "RESETTING..." else "RESET TOTAL ATHLETE DIRECTORY DATA", fontFamily = SyneFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
    }
    }
}
