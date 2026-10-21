package com.apexfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.EffectiveSetsData
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.AppConstants
import kotlin.math.roundToInt

@Composable
fun ExerciseListSection(
    exercises: List<PlanExercise>,
    currentIdx: Int,
    loggedSets: Map<String, List<UiExerciseSet>>,
    effectiveSetsMap: Map<String, EffectiveSetsData>,
    warmupComp: Map<String, Boolean>,
    lastWeights: Map<String, Double>,
    weightContextLines: Map<String, String>,
    preferredUnits: String,
    showRirOverlay: Boolean,
    onToggleWarmupItem: (String) -> Unit,
    onLogSetState: (exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int) -> Unit,
    onOpenRirSelector: (exerciseId: String, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int) -> Unit,
    onAddCustomLogSet: (String) -> Unit,
    onNextExercise: () -> Unit,
    onSkipExercise: (PlanExercise) -> Unit,
    onShowNormalFinishFeelDialog: () -> Unit,
    onShowFinishEarlyDialog: () -> Unit,
    onShowExitDialog: () -> Unit,
    onCancelOrCompleteEmptySession: () -> Unit
) {
    val isImperial = remember(preferredUnits) { preferredUnits.lowercase() in listOf("lb", "lbs") }
    val unitSuffix = if (isImperial) "lb" else "kg"
    val warmupList = remember(warmupComp) { warmupComp.toList() }

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
                    fontSize = 10.sp,
                    color = AmberAccent,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        items(warmupList, key = { it.first }) { (item, comp) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { selected = comp }
                    .clickable(role = Role.Checkbox) { onToggleWarmupItem(item) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = comp,
                    onCheckedChange = { onToggleWarmupItem(item) },
                    colors = CheckboxDefaults.colors(checkedColor = AmberAccent, uncheckedColor = MutedText)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = if (comp) MutedText else PrimaryText,
                    textDecoration = if (comp) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        }

        // Current Exercise Layout
        if (currentIdx < exercises.size) {
            val ex = exercises[currentIdx]
            val setsList = loggedSets[ex.exerciseId] ?: emptyList()

            item {
                val displayWeight = if (isImperial) (ex.weight * AppConstants.KG_TO_LBS).let { Math.round(it * 10.0) / 10.0 } else ex.weight
                var showPlateCalc by rememberSaveable { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "EXERCISE ${currentIdx + 1} OF ${exercises.size}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MutedText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkRaised)
                                    .clickable(role = Role.Button) { showPlateCalc = !showPlateCalc }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.train_plate_calc), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkRaised)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(ex.muscleGroup.uppercase(), fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = AmberAccent)
                            }
                        }
                    }

                    if (showPlateCalc) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PlateCalculatorCard(initialWeight = displayWeight, units = unitSuffix)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ex.name,
                        fontFamily = SyneFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryText
                    )
                    val contextLine = weightContextLines[ex.exerciseId] ?: ""
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

            val currEffSetsData = effectiveSetsMap[ex.exerciseId]
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
                        lastSetReps = lastSetReps,
                        units = preferredUnits
                    )
                }
            }

            // Row of sets logger inputs
            items(setsList.size, key = { setsList[it].id }) { sIdx ->
                val setObj = setsList[sIdx]
                SetLoggingRow(
                    ex = ex,
                    sIdx = sIdx,
                    setObj = setObj,
                    totalSets = setsList.size,
                    preferredUnits = preferredUnits,
                    unitSuffix = unitSuffix,
                    isImperial = isImperial,
                    lastWeights = lastWeights,
                    weightContextLines = weightContextLines,
                    showRirOverlay = showRirOverlay,
                    onLogSetState = onLogSetState,
                    onOpenRirSelector = onOpenRirSelector
                )
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
                                    onAddCustomLogSet(ex.exerciseId)
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
                            Text(
                                stringResource(R.string.train_add_extra_set),
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                color = if (canAddSet) PrimaryText else MutedText
                            )
                        }

                        Button(
                            onClick = {
                                if (currentIdx == exercises.size - 1) {
                                    onShowNormalFinishFeelDialog()
                                } else {
                                    onNextExercise()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (currentIdx == exercises.size - 1) "FINISH WORKOUT" else "NEXT EXERCISE →",
                                fontFamily = SyneFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ApexColors.OnGlow
                            )
                        }
                    }

                    if (setsList.size >= 20) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sets: 1–20",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = RedAccent,
                            modifier = Modifier.testTag("sets_error_msg")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (currentIdx == exercises.size - 1) {
                                onShowNormalFinishFeelDialog()
                            } else {
                                onSkipExercise(ex)
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("skip_exercise_button")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
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
                        onClick = onCancelOrCompleteEmptySession,
                        colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            stringResource(R.string.train_complete_recovery_session),
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ApexColors.OnGlow
                        )
                    }
                } else {
                    Button(
                        onClick = onShowFinishEarlyDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = ApexColors.VictorySoft),
                        border = BorderStroke(1.dp, ApexColors.Victory),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("finish_early_button")
                    ) {
                        Text(
                            stringResource(R.string.train_finish_workout_early_save_prog),
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ApexColors.Victory
                        )
                    }
                }
                Button(
                    onClick = onShowExitDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = ApexColors.AlertSoft),
                    border = BorderStroke(1.dp, ApexColors.Alert),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        stringResource(R.string.train_cancel_current_workout_run),
                        fontFamily = SyneFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ApexColors.Alert
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RealTimeEffectiveSetsCard(
    exName: String,
    effData: EffectiveSetsData,
    currentSetNum: Int,
    totalSetsNum: Int,
    lastSetWeight: Double,
    lastSetReps: Int,
    units: String
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
                    text = "Effective Sets: ${String.format(java.util.Locale.US, "%.2f", effData.currentEffectiveSets)} / ${String.format(java.util.Locale.US, "%.2f", effData.targetEffectiveSets)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "(${effData.progress.roundToInt()}% complete)",
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
                                text = "Last Set: ${if (lastSetWeight > 0) "${lastSetWeight.roundToInt()} $units " else ""}${if (lastSetReps > 0 && lastSetWeight <= 0) "$lastSetReps reps " else if (lastSetReps > 0) "× $lastSetReps reps " else ""}@ RPE ${effData.lastSetRPE}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quality: ${String.format(java.util.Locale.US, "%.2f", effData.lastSetEffectiveness)} effective sets (${effectivenessPct} effective — ${effectivenessZone.uppercase()})",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtle.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Status: " + if (effData.progress >= 100) "Goal complete!" else if (effData.progress > 45) "On track" else "Building momentum",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = GreenAccent
                )
                Text(
                    text = "Next: Set ${currentSetNum}/${totalSetsNum}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
        }
    }
}
