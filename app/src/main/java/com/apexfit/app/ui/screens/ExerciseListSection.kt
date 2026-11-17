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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.style.TextOverflow
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
internal fun WorkoutExecutionHeader(
    sessionLabel: String,
    elapsedTime: String,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(ApexColors.Canvas)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit workout",
                    tint = ApexColors.TextPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(sessionLabel, color = ApexColors.TextPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(elapsedTime, color = ApexColors.TextSecondary, fontSize = 13.sp)
            }
            Box(modifier = Modifier.widthIn(min = 68.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Finish", color = ApexColors.TextSecondary, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
    }
}

@Composable
fun ExerciseListSection(
    exercises: List<PlanExercise>,
    currentIdx: Int,
    loggedSets: Map<String, List<UiExerciseSet>>,
    effectiveSetsMap: Map<String, EffectiveSetsData>,
    warmupComp: Map<String, Boolean>,
    lastWeights: Map<String, Double>,
    weightContextLines: Map<String, String>,
    volumeAdjustments: Map<String, com.apexfit.app.utils.SetVolumeAdjustment>,
    onUndoVolumeAdjustment: (String) -> Boolean,
    preferredUnits: String,
    showRirOverlay: Boolean,
    onOpenFormGuide: (String) -> Unit,
    onToggleWarmupItem: (String) -> Unit,
    onLogSetState: (exerciseId: String, setIndex: Int, weight: Double, reps: Int, rpe: Int, completed: Boolean, restTakenSeconds: Int) -> Unit,
    onCompleteSet: (exerciseId: String, exerciseName: String, muscleGroup: String, setIndex: Int, weight: Double, reps: Int, totalSets: Int, rpe: Int) -> Unit,
    onAddCustomLogSet: (String) -> Unit,
    onNextExercise: () -> Unit,
    onSkipExercise: (PlanExercise) -> Unit,
    onShowNormalFinishFeelDialog: () -> Unit,
    onShowFinishEarlyDialog: () -> Unit,
    onShowExitDialog: () -> Unit,
    onCancelOrCompleteEmptySession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImperial = remember(preferredUnits) { preferredUnits.lowercase() in listOf("lb", "lbs") }
    val unitSuffix = if (isImperial) "lb" else "kg"
    val warmupList = remember(warmupComp) { warmupComp.toList() }
    var showMore by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Current Exercise Layout
        if (currentIdx < exercises.size) {
            val ex = exercises[currentIdx]
            val setsList = loggedSets[ex.exerciseId] ?: emptyList()
            val activeSetIndex = setsList.indexOfFirst { !it.completed && !it.isWarmup }
            var showPlateCalc by rememberSaveable(ex.exerciseId) { mutableStateOf(false) }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Exercise ${currentIdx + 1} of ${exercises.size}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 14.sp,
                            color = ApexColors.TextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onOpenFormGuide(ex.name) },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null,
                                    tint = ApexColors.TextSecondary, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Form guide", color = ApexColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ex.name,
                        fontFamily = SyneFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryText
                    )
                    Text(ex.muscleGroup, color = ApexColors.TextSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp))
                    val adjustment = volumeAdjustments[ex.exerciseId]
                    if (adjustment != null) {
                        var showVolumeReason by rememberSaveable(ex.exerciseId) { mutableStateOf(false) }
                        Text("${adjustment.baseSets} → ${adjustment.adjustedSets} sets",
                            color = ApexColors.Primary, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showVolumeReason = true }) { Text("Why?") }
                            TextButton(
                                enabled = loggedSets[ex.exerciseId].orEmpty().let { sets ->
                                    sets.size == adjustment.adjustedSets && sets.none { it.completed || it.isWarmup }
                                },
                                onClick = { onUndoVolumeAdjustment(ex.exerciseId) }
                            ) { Text("Undo") }
                        }
                        if (showVolumeReason) {
                            AlertDialog(
                                onDismissRequest = { showVolumeReason = false },
                                title = { Text("Today's set target") },
                                text = { Text(adjustment.reason) },
                                confirmButton = {
                                    TextButton(onClick = { showVolumeReason = false }) { Text("Done") }
                                }
                            )
                        }
                    }

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

            if (activeSetIndex >= 0) {
                item(key = "active-set-${ex.exerciseId}-${setsList[activeSetIndex].id}") {
                    SetLoggingRow(
                        ex = ex,
                        sIdx = activeSetIndex,
                        setObj = setsList[activeSetIndex],
                        totalSets = setsList.size,
                        preferredUnits = preferredUnits,
                        unitSuffix = unitSuffix,
                        isImperial = isImperial,
                        lastWeights = lastWeights,
                        weightContextLines = weightContextLines,
                        showRirOverlay = showRirOverlay,
                        onLogSetState = onLogSetState,
                        onCompleteSet = onCompleteSet,
                        nightLedgerPresentation = true,
                        previousSet = setsList.take(activeSetIndex).lastOrNull { it.completed && !it.isWarmup },
                        displaySetNumber = setsList.take(activeSetIndex).count { !it.isWarmup } + 1,
                        displayTotalSets = setsList.count { !it.isWarmup }
                    )
                }
            } else {
                item(key = "all-sets-logged-${ex.exerciseId}") {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("All planned sets logged", color = ApexColors.TextPrimary,
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${setsList.count { it.completed && !it.isWarmup }} of ${setsList.count { !it.isWarmup }} sets completed",
                            color = ApexColors.TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp))
                        Button(
                            onClick = {
                                if (currentIdx == exercises.lastIndex) onShowNormalFinishFeelDialog() else onNextExercise()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)
                        ) {
                            Text(if (currentIdx == exercises.lastIndex) "Finish workout" else "Next exercise",
                                color = ApexColors.OnGlow, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                TrainingRail(
                    exercises = exercises,
                    currentIdx = currentIdx,
                    loggedSets = loggedSets,
                    preferredUnits = preferredUnits
                )
            }

            item {
                TextButton(onClick = { showMore = !showMore }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showMore) "Less  ▴" else "More  ▾", color = ApexColors.TextSecondary, fontSize = 14.sp)
                }
                if (showMore) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showPlateCalc = !showPlateCalc },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (showPlateCalc) "Hide plate calculator" else stringResource(R.string.train_plate_calc),
                                color = ApexColors.TextSecondary)
                        }
                        if (showPlateCalc) {
                            val displayWeight = if (isImperial) (ex.weight * AppConstants.KG_TO_LBS).let { Math.round(it * 10.0) / 10.0 } else ex.weight
                            PlateCalculatorCard(initialWeight = displayWeight, units = unitSuffix)
                        }
                        if (warmupList.isNotEmpty()) {
                            Text("Warm up", color = ApexColors.TextSecondary, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp))
                            warmupList.forEach { (warmup, completed) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().semantics { selected = completed }
                                        .clickable(role = Role.Checkbox) { onToggleWarmupItem(warmup) }
                                        .heightIn(min = 48.dp)
                                ) {
                                    Checkbox(checked = completed, onCheckedChange = { onToggleWarmupItem(warmup) },
                                        colors = CheckboxDefaults.colors(checkedColor = ApexColors.Primary,
                                            uncheckedColor = ApexColors.TextTertiary))
                                    Text(warmup, color = if (completed) ApexColors.TextSecondary else ApexColors.TextPrimary,
                                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
                                }
                            }
                        }
                        setsList.forEachIndexed { setIndex, set ->
                            if (set.completed || set.isWarmup) {
                                SetLoggingRow(
                                    ex = ex, sIdx = setIndex, setObj = set, totalSets = setsList.size,
                                    preferredUnits = preferredUnits, unitSuffix = unitSuffix, isImperial = isImperial,
                                    lastWeights = lastWeights, weightContextLines = weightContextLines,
                                    showRirOverlay = showRirOverlay, onLogSetState = onLogSetState,
                                    onCompleteSet = onCompleteSet
                                )
                            }
                        }
                        effectiveSetsMap[ex.exerciseId]?.let { effData ->
                            val last = setsList.lastOrNull { it.completed }
                            RealTimeEffectiveSetsCard(ex.name, effData,
                                (activeSetIndex + 1).coerceAtLeast(1), setsList.size,
                                last?.weight ?: 0.0, last?.reps ?: 0, preferredUnits)
                        }
                        val canAddSet = setsList.size < 20
                        OutlinedButton(
                            onClick = { onAddCustomLogSet(ex.exerciseId) }, enabled = canAddSet,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.TextPrimary),
                            border = BorderStroke(1.dp, ApexColors.BorderSubtle)
                        ) { Text(stringResource(R.string.train_add_extra_set)) }
                        if (!canAddSet) Text("Sets: 1–20", color = RedAccent,
                            modifier = Modifier.testTag("sets_error_msg"))
                        OutlinedButton(
                            onClick = { onSkipExercise(ex) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("skip_exercise_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ApexColors.TextSecondary),
                            border = BorderStroke(1.dp, ApexColors.BorderSubtle)
                        ) { Text("Skip this exercise") }
                        TextButton(onClick = onShowFinishEarlyDialog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.train_finish_workout_early_save_prog), color = ApexColors.TextSecondary)
                        }
                        TextButton(onClick = onShowExitDialog, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.train_cancel_current_workout_run), color = ApexColors.TextSecondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
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
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.train_complete_recovery_session),
                            fontFamily = SyneFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ApexColors.OnGlow
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TrainingRail(
    exercises: List<PlanExercise>,
    currentIdx: Int,
    loggedSets: Map<String, List<UiExerciseSet>>,
    preferredUnits: String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isImperial = preferredUnits.lowercase() in listOf("lb", "lbs")
    val upcomingExercises = exercises.drop(currentIdx)
    val visibleCount = if (expanded) upcomingExercises.size else minOf(4, upcomingExercises.size)
    val unit = if (isImperial) "lb" else "kg"

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Workout sequence", color = ApexColors.TextSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        upcomingExercises.take(visibleCount).forEachIndexed { offset, exercise ->
            val index = currentIdx + offset
            val sets = loggedSets[exercise.exerciseId].orEmpty()
            val complete = sets.isNotEmpty() && sets.all { it.completed }
            val isCurrent = index == currentIdx
            val marker = when {
                isCurrent -> "•"
                complete -> "✓"
                else -> "○"
            }
            val markerColor = when {
                isCurrent -> ApexColors.Primary
                complete -> ApexColors.Victory
                else -> ApexColors.TextTertiary
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(marker, color = markerColor, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(16.dp).padding(top = 1.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, color = if (isCurrent) ApexColors.TextPrimary else ApexColors.TextSecondary,
                        fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                    if (isCurrent && sets.isNotEmpty()) {
                        sets.forEachIndexed { setIndex, set ->
                            val setMarker = when {
                                set.completed -> "✓"
                                setIndex == sets.indexOfFirst { !it.completed && !it.isWarmup } -> "•"
                                else -> "○"
                            }
                            val detail = when {
                                set.completed -> "Set ${setIndex + 1} · ${set.weight} $unit × ${set.reps}"
                                setIndex == sets.indexOfFirst { !it.completed && !it.isWarmup } -> "Set ${setIndex + 1} (current)"
                                else -> "Set ${setIndex + 1} · Target ${exercise.repsMin}–${exercise.repsMax}"
                            }
                            Text("$setMarker  $detail", color = if (set.completed || setIndex == sets.indexOfFirst { !it.completed && !it.isWarmup })
                                ApexColors.TextSecondary else ApexColors.TextTertiary, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp))
                        }
                    } else {
                        val loadKg = exercise.weight
                        val load = if (loadKg <= 0.0) "Load not set" else {
                            val displayed = if (isImperial) loadKg * AppConstants.KG_TO_LBS else loadKg
                            "${(displayed * 10.0).roundToInt() / 10.0} $unit"
                        }
                        Text("${exercise.sets} sets · $load", color = ApexColors.TextTertiary,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (upcomingExercises.size > visibleCount) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("More  ▾", color = ApexColors.TextSecondary, fontSize = 13.sp)
            }
        } else if (expanded && upcomingExercises.size > 4) {
            TextButton(onClick = { expanded = false }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Less  ▴", color = ApexColors.TextSecondary, fontSize = 13.sp)
            }
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
