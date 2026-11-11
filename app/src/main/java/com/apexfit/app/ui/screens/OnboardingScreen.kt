package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.data.OnboardingProfile
import com.apexfit.app.ui.theme.ApexColors

private val SetupGround get() = ApexColors.Canvas
private val SetupSurface get() = ApexColors.Surface
private val SetupRaised get() = ApexColors.SurfaceRaised
private val SetupText get() = ApexColors.TextPrimary
private val SetupMuted get() = ApexColors.TextSecondary
private val SetupMint get() = ApexColors.Primary
private val SetupFont = FontFamily(Font(R.font.onboarding_jakarta))
private data class SetupChoice(val value: String, val title: String, val description: String)
private val SetupGoals = listOf(
    SetupChoice("Lose Fat", "Losing", "Reduce body weight."),
    SetupChoice("Recomposition", "Recomp", "Work on body composition."),
    SetupChoice("Gain Muscle", "Gaining", "Increase body weight.")
)
private val SetupExperience = listOf(
    SetupChoice("Beginner", "New to training", "I am learning the movements."),
    SetupChoice("Intermediate", "Finding my rhythm", "I have trained before, but not consistently."),
    SetupChoice("Advanced", "Training regularly", "I have a routine and know the basics.")
)

@Composable
fun OnboardingScreen(
    preferredUnits: String = "kg",
    isSaving: Boolean = false,
    saveError: String? = null,
    onExplore: () -> Unit = {},
    onComplete: (OnboardingProfile) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf<String?>(null) }
    var experience by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("") }
    var imperial by rememberSaveable { mutableStateOf(preferredUnits in listOf("lb", "lbs")) }
    var showDetailsErrors by rememberSaveable { mutableStateOf(false) }
    val heightCm = height.replace(',', '.').toDoubleOrNull()?.let { if (imperial) kotlin.math.round(it * 2.54 * 1000) / 1000 else it }
    val weightKg = weight.replace(',', '.').toDoubleOrNull()?.let { if (imperial) kotlin.math.round(it / 2.20462 * 1000) / 1000 else it }
    val nameError = if (name.trim().isEmpty() || name.trim().length > 50) "Enter a name of 1–50 characters." else null
    val heightError = if (heightCm == null || !heightCm.isFinite() || heightCm !in 100.0..250.0) "Enter a height from ${if (imperial) "39.4 to 98.4 in" else "100 to 250 cm"}." else null
    val weightError = if (weightKg == null || !weightKg.isFinite() || weightKg !in 20.0..300.0) "Enter a weight from ${if (imperial) "44.1 to 661.4 lb" else "20 to 300 kg"}." else null
    val ageError = if (age.toIntOrNull() !in 13..100) "Enter an age from 13 to 100." else null
    val detailsValid = nameError == null && heightError == null && weightError == null && ageError == null && sex.isNotEmpty()
    BackHandler(step > 0 || isSaving) { if (!isSaving) step-- }
    if (step == 0) {
        SetupWelcome(isSaving, saveError, { step = 1 }, onExplore)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(SetupGround)) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (wide) {
                val photo = when (step) {
                    2 -> R.drawable.setup_training
                    3 -> R.drawable.setup_recovery
                    else -> R.drawable.setup_portrait
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(28.dp)) {
                    Image(painterResource(photo), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    SetupBrand(Modifier.padding(24.dp))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step-- }, enabled = !isSaving, modifier = Modifier.testTag("onboarding_back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SetupText)
                        }
                        SetupBrand(Modifier.weight(1f))
                        Text("Your profile", color = SetupMuted, fontSize = 12.sp, fontFamily = SetupFont)
                    }
                    LinearProgressIndicator(progress = { (step - 1) / 5f }, modifier = Modifier.fillMaxWidth().height(4.dp)
                        .padding(top = 0.dp).semantics { contentDescription = "${step - 1} of 5 setup stages complete" },
                        color = SetupMint, trackColor = SetupRaised)
                }
                key(step) {
                    Column(Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(top = 30.dp, bottom = 16.dp)) {
                        when (step) {
                            1 -> {
                                SetupHeading("What are you working toward?", "Pick the goal that matters most right now. You can change it later.")
                                SetupGoals.forEach { choice -> SetupOption(choice, goal == choice.value, !isSaving) { goal = choice.value; step = 2 } }
                                SetupBody("Select a goal to continue.", Modifier.padding(top = 20.dp))
                            }
                            2 -> {
                                SetupHeading("Meet yourself where you are.", "Keep your training experience in your profile. This does not generate a workout plan.")
                                SetupExperience.forEach { choice -> SetupOption(choice, experience == choice.value, !isSaving) { experience = choice.value } }
                                SetupBody("No catching up. Just a place to begin.", Modifier.padding(top = 20.dp))
                            }
                            3 -> {
                                SetupHeading("How often do you train?", "This helps describe your activity for your profile and energy needs.")
                                Text("Training days per week", color = SetupText, fontFamily = SetupFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (2..5).forEach { value ->
                                        Surface(onClick = { days = value }, enabled = !isSaving,
                                            color = if (days == value) SetupText else SetupSurface,
                                            contentColor = if (days == value) SetupGround else SetupText,
                                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)
                                                .testTag("onboarding_frequency_$value")) {
                                            Box(Modifier.heightIn(min = 52.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                                Text("$value days", fontSize = 13.sp, fontFamily = SetupFont, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                SetupBody("Your goal and activity are profile inputs. They do not create or recommend a training plan.", Modifier.padding(top = 24.dp))
                            }
                            4 -> {
                                SetupHeading("A little about you.", "Add your details for your profile, progress tracking, and energy estimates.")
                                SetupField("Name", name, "onboarding_name", !isSaving, error = if (showDetailsErrors) nameError else null) { name = it }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(false to "Metric · cm, kg", true to "Imperial · in, lb").forEach { (value, label) ->
                                        FilterChip(selected = imperial == value, enabled = !isSaving,
                                            onClick = {
                                                if (imperial != value) {
                                                    height = heightCm?.let { String.format(java.util.Locale.US, "%.4f", if (value) it / 2.54 else it).trimEnd('0').trimEnd('.') } ?: height
                                                    weight = weightKg?.let { String.format(java.util.Locale.US, "%.4f", if (value) it * 2.20462 else it).trimEnd('0').trimEnd('.') } ?: weight
                                                    imperial = value
                                                }
                                            }, label = { Text(label, fontFamily = SetupFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = SetupGround, labelColor = SetupMuted,
                                                selectedContainerColor = SetupRaised, selectedLabelColor = SetupMint))
                                    }
                                }
                                SetupField("Height (${if (imperial) "in" else "cm"})", height, "onboarding_height", !isSaving, KeyboardType.Decimal, if (showDetailsErrors) heightError else null) { height = it }
                                SetupField("Weight (${if (imperial) "lb" else "kg"})", weight, "onboarding_weight", !isSaving, KeyboardType.Decimal, if (showDetailsErrors) weightError else null) { weight = it }
                                SetupField("Age (years)", age, "onboarding_age", !isSaving, KeyboardType.Number, if (showDetailsErrors) ageError else null) { age = it }
                                SetupBody("Sex used for calorie estimates", Modifier.padding(top = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("male" to "Male", "female" to "Female").forEach { (value, label) ->
                                        FilterChip(selected = sex == value, onClick = { sex = value }, enabled = !isSaving,
                                            label = { Text(label, fontFamily = SetupFont) },
                                            colors = FilterChipDefaults.filterChipColors(containerColor = SetupGround, labelColor = SetupMuted,
                                                selectedContainerColor = SetupRaised, selectedLabelColor = SetupMint))
                                    }
                                }
                                if (showDetailsErrors && sex.isEmpty()) Text("Choose the sex used for estimates.", color = Color(0xFFFFB4AB), fontFamily = SetupFont)
                                SetupBody("These details stay on this device. You can update them in Settings.", Modifier.padding(top = 16.dp))
                            }
                            5 -> {
                                SetupHeading("Your profile, ready to go.", "A starting point for tracking your nutrition and progress.")
                                SetupReviewRow("Your details", "$name · $age years · ${if (sex == "male") "Male" else "Female"}\n$height ${if (imperial) "in" else "cm"} · $weight ${if (imperial) "lb" else "kg"}", !isSaving) { step = 4 }
                                SetupReviewRow("Goal", SetupGoals.firstOrNull { it.value == goal }?.title ?: "Not selected", !isSaving) { step = 1 }
                                SetupReviewRow("Experience", SetupExperience.firstOrNull { it.value == experience }?.title ?: "Not selected", !isSaving) { step = 2 }
                                SetupReviewRow("Activity", "$days training days per week", !isSaving) { step = 3 }
                                SetupBody("Your details are used for energy estimates. You stay in control of your training plan.", Modifier.padding(top = 20.dp))
                            }
                        }
                        if (saveError != null) Text(saveError, color = Color(0xFFFFB4AB), fontFamily = SetupFont,
                            modifier = Modifier.padding(top = 16.dp).testTag("onboarding_save_error"))
                    }
                }
                if (step > 1) {
                    SetupButton(if (step == 5) "Open APEX FIT" else if (step == 4) "Review my profile" else "Continue",
                        !isSaving && (step != 2 || experience != null) && (step != 5 || goal != null && experience != null && detailsValid),
                        isSaving, Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(bottom = 24.dp)
                            .testTag(if (step == 5) "onboarding_complete_button" else "onboarding_continue")) {
                        if (step == 4 && !detailsValid) showDetailsErrors = true
                        else if (step == 5) onComplete(OnboardingProfile(name = name.trim(), goal = goal!!, experience = experience!!,
                            heightCm = heightCm!!, weightKg = weightKg!!, age = age.toInt(), sex = sex,
                            daysPerWeek = days, units = if (imperial) "lb" else "kg"))
                        else { keyboard?.hide(); step++ }
                    }
                } else Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SetupField(
    label: String, value: String, tag: String, enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text, error: String? = null, onChange: (String) -> Unit
) {
    OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label, fontFamily = SetupFont) }, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = SetupFont, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null,
        supportingText = if (error != null) ({ Text(error, fontFamily = SetupFont) }) else null,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = SetupText, unfocusedTextColor = SetupText,
            focusedBorderColor = SetupMint, unfocusedBorderColor = SetupMuted.copy(alpha = .5f),
            focusedLabelColor = SetupMint, unfocusedLabelColor = SetupMuted, cursorColor = SetupMint,
            focusedContainerColor = SetupSurface, unfocusedContainerColor = SetupSurface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(tag))
}

@Composable
private fun SetupWelcome(saving: Boolean, error: String?, onNext: () -> Unit, onExplore: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(SetupGround)) {
        val wide = maxWidth >= 700.dp
        val welcomePhotoSpace = maxHeight * .43f
        Image(painterResource(R.drawable.setup_opening), null, contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter, modifier = if (wide) Modifier.fillMaxHeight().fillMaxWidth(.52f) else Modifier.fillMaxSize())
        if (!wide) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .4f to Color.Transparent, .7f to SetupGround.copy(alpha = .8f), 1f to SetupGround)))
        SetupBrand(Modifier.safeDrawingPadding().padding(26.dp))
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (wide) Spacer(Modifier.weight(1f))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 24.dp), verticalArrangement = if (wide) Arrangement.Center else Arrangement.Bottom) {
                if (!wide) Spacer(Modifier.heightIn(min = welcomePhotoSpace))
                Text("Build a stronger,\nhealthier you", color = SetupText, fontFamily = SetupFont,
                    fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                SetupBody("Track your training, nutrition, and progress in one place.", Modifier.padding(top = 16.dp, bottom = 24.dp))
                SetupButton("Get started", !saving, false, Modifier.fillMaxWidth().testTag("onboarding_welcome_next"), onNext)
                TextButton(onClick = onExplore, enabled = !saving, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("onboarding_explore")) {
                    Text(if (saving) "Opening…" else "Explore the app", color = SetupMuted, fontFamily = SetupFont, fontSize = 13.sp)
                }
                if (error != null) Text(error, color = Color(0xFFFFB4AB), fontFamily = SetupFont)
                if (wide) SetupBody("No account needed. Your progress stays on this device.", Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun SetupBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_apex_chevron), null, tint = SetupMint, modifier = Modifier.size(24.dp))
        Text("APEX FIT", color = SetupText, fontFamily = SetupFont, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SetupHeading(title: String, description: String) {
    Text(title, color = SetupText, fontFamily = SetupFont, fontSize = 28.sp, lineHeight = 35.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
    SetupBody(description, Modifier.padding(top = 12.dp, bottom = 26.dp))
}

@Composable
private fun SetupBody(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier, color = SetupMuted, fontFamily = SetupFont, fontSize = 15.sp, lineHeight = 24.sp)
}

@Composable
private fun SetupOption(choice: SetupChoice, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Surface(color = if (selected) SetupRaised else SetupGround, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 80.dp).padding(horizontal = 10.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SetupMint, unselectedColor = SetupMuted))
            Column(Modifier.weight(1f)) {
                Text(choice.title, color = SetupText, fontFamily = SetupFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(choice.description, color = SetupMuted, fontFamily = SetupFont, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun SetupButton(label: String, enabled: Boolean, saving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = CircleShape, modifier = modifier.heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SetupMint, contentColor = ApexColors.OnGlow,
            disabledContainerColor = SetupMint.copy(alpha = .35f), disabledContentColor = SetupText.copy(alpha = .5f))) {
        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SetupGround)
        else {
            Text(label, fontFamily = SetupFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SetupReviewRow(label: String, value: String, enabled: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = SetupText, fontFamily = SetupFont, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = SetupMuted, fontFamily = SetupFont, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        }
        TextButton(onChange, enabled = enabled, modifier = Modifier.testTag("onboarding_change_$label")) {
            Text("Change", color = SetupText, fontFamily = SetupFont)
        }
    }
}
