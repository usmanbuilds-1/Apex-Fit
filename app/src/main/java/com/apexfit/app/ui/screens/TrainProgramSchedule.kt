package com.apexfit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.InterFamily
import com.apexfit.app.ui.theme.JetBrainsMonoFamily
import com.apexfit.app.ui.theme.SyneFamily
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.AppConstants

private val weekDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
internal fun TrainProgramSchedule(
    sessionsState: UiState<List<PlanSession>>,
    allExercises: List<PlanExercise>,
    selectedDay: String,
    selectedSession: PlanSession?,
    selectedExercises: List<PlanExercise>,
    preferredUnits: String,
    isStarting: Boolean,
    onSelectDay: (String) -> Unit,
    onStartSession: (PlanSession) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFormGuide: (String) -> Unit,
    onSubstituteExercise: (PlanExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    when (sessionsState) {
        UiState.Loading -> Column(
            modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = ApexColors.Primary, strokeWidth = 2.dp)
            Text("Loading weekly schedule", color = ApexColors.TextSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
        is UiState.Error -> Column(modifier.fillMaxSize().padding(top = 24.dp)) {
            Text("Weekly schedule unavailable", color = ApexColors.TextPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(sessionsState.message, color = ApexColors.TextSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
            TextButton(onClick = onOpenPlans, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open plans", color = ApexColors.Primary)
            }
        }
        is UiState.Success -> {
            val sessions = sessionsState.data
            if (sessions.isEmpty()) {
                Column(modifier.fillMaxSize().padding(top = 24.dp)) {
                    Text("No weekly schedule", color = ApexColors.TextPrimary,
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Choose or create a plan to see your training week here.",
                        color = ApexColors.TextSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = onOpenPlans, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Browse plans", color = ApexColors.Primary)
                    }
                }
            } else {
                val unit = if (preferredUnits.lowercase() in listOf("lb", "lbs")) "lb" else "kg"
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item(key = "weekly-schedule-heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weekly schedule", color = ApexColors.TextPrimary,
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onOpenLibrary, modifier = Modifier.heightIn(min = 44.dp)) {
                                Text("Exercise library", color = ApexColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                    items(weekDays, key = { it }) { day ->
                        val session = sessions.firstOrNull { it.day.equals(day, ignoreCase = true) }
                        val isSelected = selectedDay.equals(day, ignoreCase = true)
                        val exercises = if (session == null) emptyList() else if (session.id == selectedSession?.id) {
                            selectedExercises
                        } else {
                            allExercises.filter { it.planSessionId == session.id }.sortedBy { it.id }
                        }
                        val estimatedMinutes = exercises.sumOf { AlgorithmEngine.estimateSetDurationMinutes(it, unit) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .semantics { selected = isSelected }
                                    .clickable(role = Role.Button) { onSelectDay(day) }
                                    .heightIn(min = 64.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day.take(3), color = if (isSelected) ApexColors.Primary else ApexColors.TextSecondary,
                                    fontSize = 14.sp, modifier = Modifier.width(50.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session?.label ?: "Rest", color = ApexColors.TextPrimary,
                                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (session != null) {
                                        val focus = session.focus.replace(Regex("\\s*,\\s*"), " · ")
                                        if (focus.isNotBlank()) {
                                            Text(focus, color = ApexColors.TextSecondary, fontSize = 13.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 3.dp))
                                        }
                                        if (exercises.isNotEmpty()) {
                                            val detail = buildString {
                                                append("${exercises.size} exercises")
                                                if (estimatedMinutes > 0) append(" · ~$estimatedMinutes min")
                                            }
                                            Text(detail, color = ApexColors.TextSecondary, fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 3.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                if (isSelected && session != null) {
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = "Collapse $day",
                                        tint = ApexColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (isSelected && session != null) {
                                val detailExercises = selectedExercises.ifEmpty { exercises }
                                detailExercises.forEachIndexed { index, exercise ->
                                    var expanded by rememberSaveable(exercise.id) { androidx.compose.runtime.mutableStateOf(false) }
                                    val displayWeight = if (unit == "lb") exercise.weight * AppConstants.KG_TO_LBS else exercise.weight
                                    Column(modifier = Modifier.fillMaxWidth().padding(start = 50.dp)) {
                                        HorizontalDivider(color = ApexColors.BorderSubtle)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                                                .heightIn(min = 66.dp).padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(exercise.name, color = ApexColors.TextPrimary, fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                val load = if (exercise.weight > 0.0) {
                                                    val rounded = kotlin.math.round(displayWeight * 10.0) / 10.0
                                                    "$rounded $unit"
                                                } else "Load not set"
                                                Text("${exercise.sets} × ${exercise.repsMin}–${exercise.repsMax} · $load",
                                                    color = ApexColors.TextSecondary, fontSize = 12.sp,
                                                    modifier = Modifier.padding(top = 3.dp))
                                            }
                                            Text("${exercise.restSeconds} sec", color = ApexColors.TextSecondary,
                                                fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                                        }
                                        if (expanded) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                                Text("Rest target · ${exercise.restSeconds} sec", color = ApexColors.TextSecondary,
                                                    fontSize = 12.sp)
                                                if (exercise.notes.isNotBlank()) {
                                                    Text(exercise.notes, color = ApexColors.TextSecondary,
                                                        fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    TextButton(onClick = { onOpenFormGuide(exercise.name) },
                                                        modifier = Modifier.heightIn(min = 48.dp)) {
                                                        Text("Form guide", color = ApexColors.TextSecondary)
                                                    }
                                                    TextButton(onClick = { onSubstituteExercise(exercise) },
                                                        modifier = Modifier.heightIn(min = 48.dp)) {
                                                        Text("Substitute", color = ApexColors.TextSecondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { onStartSession(session) },
                                    enabled = !isStarting,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 50.dp)
                                ) {
                                    Text(if (isStarting) "Starting…" else "Start workout",
                                        color = ApexColors.Primary, fontSize = 14.sp)
                                }
                            }
                            HorizontalDivider(color = ApexColors.BorderSubtle)
                        }
                    }
                }
            }
        }
    }
}
