package com.apexfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AppConstants

@Composable
fun SetLoggingRow(
    ex: PlanExercise,
    sIdx: Int,
    setObj: UiExerciseSet,
    totalSets: Int,
    preferredUnits: String,
    unitSuffix: String,
    isImperial: Boolean,
    lastWeights: Map<String, Double>,
    weightContextLines: Map<String, String>,
    showRirOverlay: Boolean,
    onLogSetState: (exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int) -> Unit,
    onOpenRirSelector: (exerciseId: String, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Local editing state keyed to stable set id to avoid reset when the session model updates
    var rawWeight by rememberSaveable(setObj.id) { mutableStateOf(setObj.weight.toString()) }
    var rawReps by rememberSaveable(setObj.id) { mutableStateOf(setObj.reps.toString()) }
    var selectedRpe by remember(setObj.id) { mutableStateOf(setObj.rpe) }

    LaunchedEffect(setObj.weight) {
        val current = rawWeight.toDoubleOrNull()
        if (current != setObj.weight) {
            rawWeight = setObj.weight.toString()
        }
    }
    LaunchedEffect(setObj.reps) {
        val current = rawReps.toIntOrNull()
        if (current != setObj.reps) {
            rawReps = setObj.reps.toString()
        }
    }

    // Error strings keyed to set id (and set index)
    var weightError by remember(setObj.id, sIdx) { mutableStateOf("") }
    var repsError by remember(setObj.id, sIdx) { mutableStateOf("") }

    val weightMin = 0.25
    val weightMax = if (unitSuffix.lowercase() in listOf("lb", "lbs")) 660.0 else 300.0

    // Commit helpers — call when editing finishes (focus loss or explicit commit)
    val commitWeight: () -> Unit = remember(setObj, onLogSetState) {
        {
            val w = rawWeight.toDoubleOrNull()
            if (w != null && w in weightMin..weightMax) {
                val r = rawReps.toIntOrNull() ?: setObj.reps
                onLogSetState(
                    ex.exerciseId, sIdx, w, r, selectedRpe,
                    setObj.completed, setObj.restTaken
                )
            }
        }
    }
    val commitReps: () -> Unit = remember(setObj, onLogSetState) {
        {
            val r = rawReps.toIntOrNull()
            if (r != null && r in 1..50) {
                val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                onLogSetState(
                    ex.exerciseId, sIdx, w, r, selectedRpe,
                    setObj.completed, setObj.restTaken
                )
            }
        }
    }

    val isSetValid = remember(rawWeight, rawReps, selectedRpe, weightMin, weightMax) {
        val isWeightValid = rawWeight.toDoubleOrNull()?.let { it in weightMin..weightMax } ?: false
        val isRepsValid = rawReps.toIntOrNull()?.let { it in 1..50 } ?: false
        val isRpeValid = selectedRpe in 1..10
        isWeightValid && isRepsValid && isRpeValid
    }

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
                        setObj.isWarmup -> OrangeAccent.copy(alpha = 0.4f)
                        else -> BorderSubtle
                    }
                ),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.imePadding()) {
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
                                .background(OrangeAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "WARMUP",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    } else {
                        Text(
                            text = "SET",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
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
                    Text(stringResource(R.string.train_weight), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
                    BasicTextField(
                        value = rawWeight,
                        onValueChange = { input ->
                            val normalizedInput = input.replace(',', '.')
                            val filtered = normalizedInput.filter { it.isDigit() || it == '.' }
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
                                if (dVal > weightMax) {
                                    finalStr = "$weightMax"
                                    error = "Weight: 0.25–$weightMax $unitSuffix"
                                } else if (dVal < weightMin) {
                                    val isTypingPrefix = clean == "0" || clean == "0." || clean == "0.2"
                                    if (!isTypingPrefix) {
                                        finalStr = "0.25"
                                    }
                                    error = "Weight: 0.25–$weightMax $unitSuffix"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Weight: 0.25–$weightMax $unitSuffix"
                            }

                            // update local buffer only; do NOT push model update on every keystroke
                            rawWeight = finalStr
                            weightError = error
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                        modifier = Modifier
                            .background(DarkRaised, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                            .fillMaxWidth()
                            // commit to ViewModel when focus is lost
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    commitWeight()
                                }
                            },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (rawWeight.isEmpty()) {
                                    val rawSuggested = lastWeights[ex.exerciseId]
                                    val suggested = if (rawSuggested != null) {
                                        if (isImperial) {
                                            Math.round(rawSuggested * AppConstants.KG_TO_LBS * 10.0) / 10.0
                                        } else {
                                            rawSuggested
                                        }
                                    } else {
                                        ex.weight
                                    }
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
                    val contextLineInside = weightContextLines[ex.exerciseId] ?: ""
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
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                    if (weightError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = weightError,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = RedAccent,
                            modifier = Modifier.testTag("weight_error_msg")
                        )
                    }
                }

                // Reps textfield
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.train_reps), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = SecondaryText)
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

                            // update local buffer only; do NOT push model update on every keystroke
                            rawReps = finalStr
                            repsError = error
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                        modifier = Modifier
                            .background(DarkRaised, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    commitReps()
                                }
                            }
                    )
                    if (repsError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = repsError,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = RedAccent,
                            modifier = Modifier.testTag("reps_error_msg")
                        )
                    }
                }
                // Completion Checkbox
                IconButton(
                    enabled = isSetValid && !showRirOverlay,
                    onClick = {
                        if (!showRirOverlay) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newCompleted = !setObj.completed
                            val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                            val r = rawReps.toIntOrNull() ?: setObj.reps

                            if (newCompleted) {
                                // Smart RIR dynamic selector instead of instant log
                                onOpenRirSelector(
                                    ex.exerciseId,
                                    ex.name,
                                    ex.muscleGroup,
                                    sIdx,
                                    w,
                                    r,
                                    totalSets
                                )
                            } else {
                                // Simple uncheck log state
                                onLogSetState(ex.exerciseId, sIdx, w, r, selectedRpe, false, setObj.restTaken)
                            }
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
            Text(stringResource(R.string.train_rpe, selectedRpe), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
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
                            .semantics { selected = isRpeSelected }
                            .clickable(role = Role.Button) {
                                selectedRpe = rpeVal
                                val w = rawWeight.toDoubleOrNull() ?: setObj.weight
                                val r = rawReps.toIntOrNull() ?: setObj.reps
                                onLogSetState(ex.exerciseId, sIdx, w, r, rpeVal, setObj.completed, setObj.restTaken)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rpeVal.toString(),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRpeSelected) Color(0xFF0A0A0F) else zoneColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
