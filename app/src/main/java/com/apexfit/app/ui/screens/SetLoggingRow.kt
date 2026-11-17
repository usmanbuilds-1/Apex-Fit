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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    onCompleteSet: (exerciseId: String, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int, rpe: Int) -> Unit,
    nightLedgerPresentation: Boolean = false,
    previousSet: UiExerciseSet? = null,
    displaySetNumber: Int = sIdx + 1,
    displayTotalSets: Int = totalSets
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Local editing state keyed to stable set id to avoid reset when the session model updates
    var rawWeight by rememberSaveable(ex.exerciseId, setObj.id) { mutableStateOf(setObj.weight.toString()) }
    var rawReps by rememberSaveable(ex.exerciseId, setObj.id) { mutableStateOf(setObj.reps.toString()) }
    var selectedRpe by remember(ex.exerciseId, setObj.id, setObj.rpe) { mutableStateOf(setObj.rpe) }

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
    var weightError by remember(ex.exerciseId, setObj.id, sIdx) { mutableStateOf("") }
    var repsError by remember(ex.exerciseId, setObj.id, sIdx) { mutableStateOf("") }

    val weightMin = 0.0
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

    val isSetValid = remember(rawWeight, rawReps, weightMin, weightMax) {
        val isWeightValid = rawWeight.toDoubleOrNull()?.let { it in weightMin..weightMax } ?: false
        val isRepsValid = rawReps.toIntOrNull()?.let { it in 1..50 } ?: false
        isWeightValid && isRepsValid
    }

    if (nightLedgerPresentation) {
        val step = if (isImperial) 2.5 else 1.25
        val currentWeight = rawWeight.toDoubleOrNull() ?: setObj.weight
        val currentReps = rawReps.toIntOrNull() ?: setObj.reps
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Set $displaySetNumber of $displayTotalSets", color = ApexColors.TextPrimary,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Target ${ex.repsMin}–${ex.repsMax} reps", color = ApexColors.TextSecondary,
                    fontSize = 13.sp)
            }
            val previousText = when {
                previousSet != null -> "Previous ${previousSet.weight} $unitSuffix × ${previousSet.reps} · RPE ${previousSet.rpe}"
                lastWeights[ex.exerciseId] != null -> {
                    val lastWeight = lastWeights.getValue(ex.exerciseId)
                    val displayed = if (isImperial) lastWeight * AppConstants.KG_TO_LBS else lastWeight
                    "Previous ${String.format(java.util.Locale.US, "%.1f", displayed)} $unitSuffix"
                }
                else -> null
            }
            previousText?.let {
                Text(it, color = ApexColors.TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            } ?: Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weight", color = ApexColors.TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(10.dp)).background(ApexColors.SurfaceSunken)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val value = (currentWeight - step).coerceAtLeast(0.0)
                                val rounded = kotlin.math.round(value / step) * step
                                rawWeight = String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
                                weightError = ""
                                onLogSetState(ex.exerciseId, sIdx, rounded, currentReps, selectedRpe,
                                    setObj.completed, setObj.restTaken)
                            }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)
                        ) { Text("−", color = ApexColors.TextSecondary, fontSize = 22.sp) }
                        BasicTextField(
                            value = rawWeight,
                            onValueChange = { value ->
                                val filtered = value.replace(',', '.').filter { it.isDigit() || it == '.' }
                                rawWeight = filtered
                                val parsed = filtered.toDoubleOrNull()
                                weightError = when {
                                    filtered.isNotEmpty() && parsed == null -> "Enter a valid weight"
                                    parsed != null && parsed !in weightMin..weightMax -> "Weight: 0–$weightMax $unitSuffix"
                                    else -> ""
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                                .onFocusChanged { if (!it.isFocused) commitWeight() },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (rawWeight.isEmpty()) Text("0", color = ApexColors.TextTertiary, fontSize = 16.sp)
                                    inner()
                                }
                            }
                        )
                        Text(unitSuffix, color = ApexColors.TextSecondary, fontSize = 13.sp)
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val value = currentWeight + step
                                val rounded = kotlin.math.round(value / step) * step
                                rawWeight = String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
                                weightError = ""
                                onLogSetState(ex.exerciseId, sIdx, rounded, currentReps, selectedRpe,
                                    setObj.completed, setObj.restTaken)
                            }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)
                        ) { Text("+", color = ApexColors.TextSecondary, fontSize = 22.sp) }
                    }
                    if (weightError.isNotEmpty()) Text(weightError, color = ApexColors.Alert,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reps", color = ApexColors.TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(10.dp)).background(ApexColors.SurfaceSunken)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val value = (currentReps - 1).coerceAtLeast(1)
                                rawReps = value.toString(); repsError = ""
                                onLogSetState(ex.exerciseId, sIdx, currentWeight, value, selectedRpe,
                                    setObj.completed, setObj.restTaken)
                            }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)
                        ) { Text("−", color = ApexColors.TextSecondary, fontSize = 22.sp) }
                        BasicTextField(
                            value = rawReps,
                            onValueChange = { value ->
                                val filtered = value.filter(Char::isDigit)
                                rawReps = filtered
                                val parsed = filtered.toIntOrNull()
                                repsError = if (parsed != null && parsed !in 1..50) "Reps: 1–50" else ""
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(color = ApexColors.TextPrimary, fontFamily = JetBrainsMonoFamily,
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                                .onFocusChanged { if (!it.isFocused) commitReps() }
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val value = (currentReps + 1).coerceAtMost(50)
                                rawReps = value.toString(); repsError = ""
                                onLogSetState(ex.exerciseId, sIdx, currentWeight, value, selectedRpe,
                                    setObj.completed, setObj.restTaken)
                            }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)
                        ) { Text("+", color = ApexColors.TextSecondary, fontSize = 22.sp) }
                    }
                    if (repsError.isNotEmpty()) Text(repsError, color = ApexColors.Alert,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (setObj.completed) {
                        onLogSetState(ex.exerciseId, sIdx, currentWeight, currentReps, selectedRpe,
                            false, setObj.restTaken)
                    } else if (isSetValid && !showRirOverlay) {
                        onCompleteSet(ex.exerciseId, ex.name, ex.muscleGroup, sIdx, currentWeight,
                            currentReps, totalSets, selectedRpe)
                    }
                },
                enabled = isSetValid && (!showRirOverlay || setObj.completed),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("log_checkmark_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary,
                    contentColor = ApexColors.OnGlow)
            ) {
                Text(if (setObj.completed) "Undo logged set" else "Log set",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (setObj.completed) {
                Text("RPE ${setObj.rpe} · RIR ${setObj.repsInReserve}", color = ApexColors.TextSecondary,
                    fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    setObj.completed -> ApexColors.VictorySoft
                    setObj.isWarmup -> ApexColors.SurfaceSunken
                    else -> DarkCardSurface
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        setObj.completed -> ApexColors.Victory
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
                                color = ApexColors.OnGlow
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
                            setObj.isWarmup -> ApexColors.Chart3
                            else -> AmberAccent
                        }
                    )
                }

                // Weight textfield
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.train_weight), fontFamily = InterFamily, fontSize = 13.sp, color = SecondaryText)
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
                                    error = "Weight: 0–$weightMax $unitSuffix"
                                } else if (dVal < weightMin) {
                                    val isTypingPrefix = clean == "0" || clean == "0." || clean == "0.2"
                                    if (!isTypingPrefix) {
                                        finalStr = "0"
                                    }
                                    error = "Weight: 0–$weightMax $unitSuffix"
                                }
                            } else if (clean.isNotEmpty()) {
                                error = "Weight: 0–$weightMax $unitSuffix"
                            }

                            // update local buffer only; do NOT push model update on every keystroke
                            rawWeight = finalStr
                            weightError = error
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(DarkRaised, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 12.dp)
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
                    Text(stringResource(R.string.train_reps), fontFamily = InterFamily, fontSize = 13.sp, color = SecondaryText)
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
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(DarkRaised, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 12.dp)
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
                                onCompleteSet(ex.exerciseId, ex.name, ex.muscleGroup, sIdx, w, r, totalSets, selectedRpe)
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

            Text(
                text = if (setObj.completed) "RPE ${setObj.rpe}  ·  RIR ${setObj.repsInReserve}" else "Effort is recorded after the set",
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = SecondaryText
            )
        }
    }
}
