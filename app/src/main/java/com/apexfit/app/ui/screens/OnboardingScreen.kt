package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.data.OnboardingProfile

private val SetupGround = Color(0xFF101617)
private val SetupSurface = Color(0xFF1B2223)
private val SetupRaised = Color(0xFF283031)
private val SetupText = Color(0xFFF3F5F3)
private val SetupMuted = Color(0xFFADB7B8)
private val SetupMint = Color(0xFF47E1C2)
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
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf<String?>(null) }
    var experience by rememberSaveable { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    BackHandler(step > 0 || isSaving) { if (!isSaving) step-- }
    if (step == 0) {
        SetupWelcome(isSaving, saveError, { step = 1 }, onExplore)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(SetupGround)) {
        val wide = maxWidth >= 700.dp
        val welcomePhotoSpace = maxHeight * .43f
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
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
                    LinearProgressIndicator(progress = { (step - 1) / 4f }, modifier = Modifier.fillMaxWidth().height(4.dp)
                        .padding(top = 0.dp).semantics { contentDescription = "${step - 1} of 4 setup stages complete" },
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
                                SetupHeading("Your profile, ready to go.", "A starting point for tracking your nutrition and progress.")
                                SetupReviewRow("Goal", SetupGoals.firstOrNull { it.value == goal }?.title ?: "Not selected", !isSaving) { step = 1 }
                                SetupReviewRow("Experience", SetupExperience.firstOrNull { it.value == experience }?.title ?: "Not selected", !isSaving) { step = 2 }
                                SetupReviewRow("Activity", "$days training days per week", !isSaving) { step = 3 }
                                SetupBody("TDEE estimates also need your body details and activity settings. No calorie target has been calculated here.", Modifier.padding(top = 20.dp))
                            }
                        }
                        if (saveError != null) Text(saveError, color = Color(0xFFFFB4AB), fontFamily = SetupFont,
                            modifier = Modifier.padding(top = 16.dp).testTag("onboarding_save_error"))
                    }
                }
                if (step > 1) {
                    SetupButton(if (step == 4) "Open APEX FIT" else if (step == 3) "Review my profile" else "Continue",
                        !isSaving && (step != 2 || experience != null) && (step != 4 || goal != null && experience != null),
                        isSaving, Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(bottom = 24.dp)
                            .testTag(if (step == 4) "onboarding_complete_button" else "onboarding_continue")) {
                        if (step == 4) onComplete(OnboardingProfile(goal = goal!!, experience = experience!!,
                            daysPerWeek = days, units = if (preferredUnits in listOf("lb", "lbs")) "lb" else "kg"))
                        else step++
                    }
                } else Spacer(Modifier.height(20.dp))
            }
        }
    }
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
        colors = ButtonDefaults.buttonColors(containerColor = SetupMint, contentColor = Color(0xFF06231E),
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
