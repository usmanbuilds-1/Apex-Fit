package com.apexfit.app.ui.screens
import com.apexfit.app.ui.theme.ApexIcons
import androidx.compose.ui.res.painterResource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.data.OnboardingProfile
import com.apexfit.app.data.NutritionTargets
import com.apexfit.app.data.Exercise
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ArchivoFamily
import com.apexfit.app.utils.MacroEngine
import com.apexfit.app.utils.AppConstants
import com.apexfit.app.utils.StarterPlanBuilder
import com.apexfit.app.utils.TdeeEngine

private val SetupGround get() = ApexColors.Canvas
private val SetupSurface get() = ApexColors.Surface
private val SetupRaised get() = ApexColors.SurfaceRaised
private val SetupText get() = ApexColors.TextPrimary
private val SetupMuted get() = ApexColors.TextSecondary
private val SetupAccent get() = ApexColors.Primary
private val SetupFont = ArchivoFamily
private data class SetupChoice(val value: String, val title: String, val description: String)
private val SetupGoals = listOf(
    SetupChoice("Gain Muscle", "Build muscle", "Gain size and strength"),
    SetupChoice("Lose Fat", "Lose fat", "Reduce body fat while keeping muscle"),
    SetupChoice("Recomposition", "Recomposition", "Build muscle while leaning out"),
    SetupChoice("Maintain", "Maintain", "Stay where you are")
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
    onComplete: (OnboardingProfile) -> Unit,
    exerciseCatalog: List<Exercise> = emptyList()
) {
    OnboardingContent(preferredUnits, isSaving, saveError, onExplore, onComplete,
        exerciseCatalog = exerciseCatalog)
}

@Composable
internal fun OnboardingContent(
    preferredUnits: String,
    isSaving: Boolean,
    saveError: String?,
    onExplore: () -> Unit,
    onComplete: (OnboardingProfile) -> Unit,
    initialStep: Int = 0,
    initialProfile: OnboardingProfile? = null,
    exerciseCatalog: List<Exercise> = emptyList(),
    initialExpandedSession: Int = -1
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var goal by rememberSaveable { mutableStateOf(initialProfile?.goal) }
    var experience by rememberSaveable { mutableStateOf(initialProfile?.experience) }
    var days by rememberSaveable { mutableIntStateOf(initialProfile?.daysPerWeek ?: 3) }
    var name by rememberSaveable { mutableStateOf(initialProfile?.name.orEmpty()) }
    val initialImperial = preferredUnits in listOf("lb", "lbs")
    var height by rememberSaveable {
        mutableStateOf(initialProfile?.heightCm?.let { if (initialImperial) it / 2.54 else it }?.toString().orEmpty())
    }
    var weight by rememberSaveable {
        mutableStateOf(initialProfile?.weightKg?.let { if (initialImperial) it * 2.20462 else it }?.toString().orEmpty())
    }
    var age by rememberSaveable { mutableStateOf(initialProfile?.age?.toString().orEmpty()) }
    var sex by rememberSaveable { mutableStateOf(initialProfile?.sex.orEmpty()) }
    var imperial by rememberSaveable { mutableStateOf(initialImperial) }
    var showDetailsErrors by rememberSaveable { mutableStateOf(false) }
    val heightCm = height.replace(',', '.').toDoubleOrNull()?.let { if (imperial) kotlin.math.round(it * 2.54 * 1000) / 1000 else it }
    val weightKg = weight.replace(',', '.').toDoubleOrNull()?.let { if (imperial) kotlin.math.round(it / 2.20462 * 1000) / 1000 else it }
    val nameError = if (name.trim().isEmpty() || name.trim().length > 50) "Enter a name of 1–50 characters." else null
    val heightError = if (heightCm == null || !heightCm.isFinite() || heightCm !in 100.0..250.0) "Enter a height from ${if (imperial) "39.4 to 98.4 in" else "100 to 250 cm"}." else null
    val weightError = if (weightKg == null || !weightKg.isFinite() || weightKg !in 20.0..300.0) "Enter a weight from ${if (imperial) "44.1 to 661.4 lb" else "20 to 300 kg"}." else null
    val ageError = if (age.toIntOrNull() !in AppConstants.MIN_AGE..AppConstants.MAX_AGE) {
        "Apex Fit is currently for adults aged 18 to 100."
    } else null
    val detailsValid = nameError == null && heightError == null && weightError == null && ageError == null && sex.isNotEmpty()
    val profile = if (detailsValid && goal != null && experience != null) OnboardingProfile(
        name = name.trim(), goal = goal!!, experience = experience!!,
        heightCm = heightCm!!, weightKg = weightKg!!,
        age = age.toInt(), sex = sex, daysPerWeek = days, units = if (imperial) "lb" else "kg"
    ) else null
    BackHandler(step > 0 || isSaving) { if (!isSaving) step-- }
    if (step == 0) {
        SetupWelcome(isSaving, saveError, { step = 1 }, onExplore)
        return
    }
    Box(Modifier.fillMaxSize().background(SetupGround), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(bottom = 88.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step-- }, enabled = !isSaving, modifier = Modifier.testTag("onboarding_back")) {
                            Icon(painterResource(ApexIcons.ArrowLeft), "Back", tint = SetupText)
                        }
                        SetupBrand(Modifier.weight(1f))
                        Text("Step $step / 5", color = SetupMuted, fontSize = 12.sp, fontFamily = SetupFont)
                    }
                    LinearProgressIndicator(progress = { step / 5f }, modifier = Modifier.fillMaxWidth().height(4.dp)
                        .padding(top = 0.dp).semantics { contentDescription = "${step - 1} of 5 setup stages complete" },
                        color = SetupAccent, trackColor = SetupRaised)
                }
                key(step) {
                    Box(Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(top = 30.dp, bottom = 16.dp)) {
                        when (step) {
                            1 -> {
                                SetupHeading("What do you want to work toward?", "We’ll build your training and nutrition around this.")
                                SetupGoals.forEach { choice -> SetupOption(choice, goal == choice.value, !isSaving) { goal = choice.value } }
                            }
                            2 -> {
                                SetupHeading("Personal details", "Add your details for your profile, progress tracking, and energy estimates.")
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
                                                selectedContainerColor = SetupRaised, selectedLabelColor = SetupAccent))
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
                                                selectedContainerColor = SetupRaised, selectedLabelColor = SetupAccent))
                                    }
                                }
                                if (showDetailsErrors && sex.isEmpty()) Text("Choose the sex used for estimates.", color = Color(0xFFFFB4AB), fontFamily = SetupFont)
                                SetupBody("These details stay on this device. You can update them in Settings.", Modifier.padding(top = 16.dp))
                            }
                            3 -> {
                                SetupHeading("What’s your training experience?", "Your first week starts at a manageable workload.")
                                SetupExperience.forEach { choice -> SetupOption(choice, experience == choice.value, !isSaving) { experience = choice.value } }
                            }
                            4 -> {
                                SetupHeading("How often do you train?", "Choose a number of days you can train most weeks.")
                                Text("Training days per week", color = SetupText, fontFamily = SetupFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (2..5).forEach { value ->
                                        Surface(onClick = { days = value }, enabled = !isSaving,
                                            color = if (days == value) ApexColors.PrimaryMist else SetupSurface,
                                            contentColor = if (days == value) SetupAccent else SetupText,
                                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)
                                                .semantics { selected = days == value }
                                                .testTag("onboarding_frequency_$value")) {
                                            Box(Modifier.heightIn(min = 52.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                                Text("$value days", fontSize = 13.sp, fontFamily = SetupFont, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                val sessions = remember(days) { StarterPlanBuilder.templates(days) }
                                Spacer(Modifier.height(24.dp))
                                Text("$days days / week", style = MaterialTheme.typography.titleLarge)
                                SetupBody(when (days) {
                                    2, 3 -> "Full body"
                                    4 -> "Upper / Lower"
                                    else -> "Upper / Lower + Push / Pull / Legs"
                                }, Modifier.padding(top = 4.dp, bottom = 16.dp))
                                Text("First week", style = MaterialTheme.typography.titleMedium)
                                sessions.forEach { session ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(session.day.take(3), color = SetupMuted, modifier = Modifier.width(36.dp))
                                        Text(session.label, color = SetupText)
                                    }
                                    HorizontalDivider(color = ApexColors.BorderSubtle)
                                }
                            }
                            5 -> profile?.let { SetupReady(it, exerciseCatalog, initialExpandedSession) }
                        }
                        if (saveError != null) Text(saveError, color = Color(0xFFFFB4AB), fontFamily = SetupFont,
                            modifier = Modifier.padding(top = 16.dp).testTag("onboarding_save_error"))
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .safeDrawingPadding().imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            SetupButton(
                if (step == 5) "Go to Today" else "Continue",
                !isSaving && (step != 1 || goal != null) && (step != 3 || experience != null) &&
                    (step != 5 || profile != null),
                isSaving, Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .testTag(if (step == 5) "onboarding_complete_button" else "onboarding_continue")
            ) {
                if (step == 2 && !detailsValid) showDetailsErrors = true
                else if (step == 5) profile?.let(onComplete)
                else { keyboard?.hide(); step++ }
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
            focusedBorderColor = SetupAccent, unfocusedBorderColor = SetupMuted.copy(alpha = .5f),
            focusedLabelColor = SetupAccent, unfocusedLabelColor = SetupMuted, cursorColor = SetupAccent,
            focusedContainerColor = SetupSurface, unfocusedContainerColor = SetupSurface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag(tag))
}

@Composable
private fun SetupWelcome(saving: Boolean, error: String?, onNext: () -> Unit, onExplore: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(SetupGround)) {
        val wide = maxWidth >= 700.dp
        val welcomePhotoSpace = maxHeight * .43f
        Image(painterResource(R.drawable.welcome_barbell_deadlift_13106612), null, contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter, modifier = if (wide) Modifier.fillMaxHeight().fillMaxWidth(.52f) else Modifier.fillMaxSize())
        if (!wide) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .4f to Color.Transparent, .7f to SetupGround.copy(alpha = .8f), 1f to SetupGround)))
        SetupBrand(Modifier.safeDrawingPadding().padding(26.dp))
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (wide) Spacer(Modifier.weight(1f))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 24.dp), verticalArrangement = if (wide) Arrangement.Center else Arrangement.Bottom) {
                if (!wide) Spacer(Modifier.heightIn(min = welcomePhotoSpace))
                Text("Every rep. Every meal. Every PR.", color = SetupText, fontFamily = SetupFont,
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
        Icon(painterResource(R.drawable.ic_apex_chevron), null, tint = SetupAccent, modifier = Modifier.size(24.dp))
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
    Column {
        Row(
            Modifier.fillMaxWidth().background(if (selected) ApexColors.PrimaryMist else SetupGround)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .heightIn(min = 62.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(choice.title, color = if (selected) SetupAccent else SetupText, fontFamily = SetupFont,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(choice.description, color = SetupMuted, fontFamily = SetupFont, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp))
            }
            if (selected) Text("✓", color = SetupAccent)
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}

@Composable
private fun SetupButton(label: String, enabled: Boolean, saving: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SetupAccent, contentColor = ApexColors.OnGlow,
            disabledContainerColor = SetupAccent.copy(alpha = .35f), disabledContentColor = SetupText.copy(alpha = .5f))) {
        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SetupGround)
        else {
            Text(label, fontFamily = SetupFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SetupReady(
    profile: OnboardingProfile,
    exerciseCatalog: List<Exercise> = emptyList(),
    initialExpandedSession: Int = -1
) {
    val sessions = remember(profile.daysPerWeek, profile.goal, profile.experience, exerciseCatalog) {
        StarterPlanBuilder.preview(profile.daysPerWeek, profile.goal, profile.experience, exerciseCatalog)
    }
    var expandedSession by rememberSaveable(profile.daysPerWeek) { mutableIntStateOf(initialExpandedSession) }
    val targets = remember(profile) { startingTargets(profile) }
    val program = when (profile.daysPerWeek) {
        2, 3 -> "Full body"
        4 -> "Upper / Lower"
        else -> "Upper / Lower + Push / Pull / Legs"
    }
    Text("Your plan is ready", style = MaterialTheme.typography.headlineLarge, color = SetupText)
    Spacer(Modifier.height(14.dp))
    Text("$program · ${profile.daysPerWeek} days", style = MaterialTheme.typography.headlineSmall, color = SetupText)
    SetupBody(SetupGoals.firstOrNull { it.value == profile.goal }?.title ?: profile.goal)
    Spacer(Modifier.height(20.dp))
    Text("First week", style = MaterialTheme.typography.titleLarge, color = SetupText)
    SetupBody("Planned sets and rep ranges · about 2–3 reps in reserve",
        Modifier.padding(top = 6.dp, bottom = 4.dp))
    if (sessions == null) {
        SetupBody(
            if (exerciseCatalog.isEmpty()) "Loading your exercise plan…"
            else "The exercise plan preview is unavailable right now. You can still continue; the app will prepare the plan when you finish setup.",
            Modifier.padding(vertical = 12.dp)
        )
    } else {
        sessions.forEachIndexed { index, session ->
            val expanded = expandedSession == index
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (expanded) "Hide ${session.label} exercises" else "Show ${session.label} exercises",
                            onClick = { expandedSession = if (expanded) -1 else index }
                        ).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(session.day.take(3), color = SetupMuted, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(session.label, style = MaterialTheme.typography.titleMedium, color = SetupText)
                        Text("${session.exercises.size} exercises · ${session.exercises.sumOf { it.prescription.sets }} working sets",
                            style = MaterialTheme.typography.bodySmall, color = SetupMuted)
                    }
                    Text(if (expanded) "Hide" else "View", color = SetupAccent,
                        style = MaterialTheme.typography.labelMedium)
                }
                if (expanded) {
                    session.exercises.forEach { exercise ->
                        Column(Modifier.fillMaxWidth().padding(start = 50.dp, top = 5.dp, bottom = 9.dp)) {
                            Text(exercise.name, color = SetupText, style = MaterialTheme.typography.bodyMedium)
                            val reps = if (exercise.prescription.reps.first == exercise.prescription.reps.last) {
                                "${exercise.prescription.reps.first} reps"
                            } else {
                                "${exercise.prescription.reps.first}–${exercise.prescription.reps.last} reps"
                            }
                            SetupBody("${exercise.prescription.sets} sets · $reps · ${exercise.prescription.restSeconds}s rest")
                        }
                    }
                }
            }
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Starting targets", style = MaterialTheme.typography.titleLarge, color = SetupText)
    SetupBody("%,d kcal · %d g protein".format(targets.calories, targets.protein), Modifier.padding(top = 8.dp))
    SetupBody("You can adjust your plan and targets later.", Modifier.padding(top = 16.dp))
}

private fun startingTargets(profile: OnboardingProfile): NutritionTargets {
    val tdee = TdeeEngine.calcAdaptiveTDEE(
        weightLog = emptyList(), nutritionLog = emptyList(), heightCm = profile.heightCm,
        ageYears = profile.age, biologicalSex = profile.sex, weeklyWorkouts = profile.daysPerWeek,
        fallbackWeightKg = profile.weightKg
    ).tdee ?: com.apexfit.app.UserDefaults.CALORIES
    val calories = TdeeEngine.suggestCaloricTarget(tdee, profile.goal, profile.weightKg,
        profile.targetWeightKg, profile.heightCm, profile.age, profile.sex)
    return MacroEngine.calcMacroTargets(calories, profile.weightKg, profile.goal, profile.heightCm,
        profile.sex, profile.daysPerWeek)
}
