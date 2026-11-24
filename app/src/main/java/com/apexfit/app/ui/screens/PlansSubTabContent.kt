package com.apexfit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.InterFamily
import com.apexfit.app.ui.theme.SyneFamily
import java.time.LocalDate

private val planWeekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
internal fun PlansSubTabContent(
    plans: List<WorkoutPlan>,
    activePlan: WorkoutPlan?,
    activePlanError: String?,
    activeSessions: UiState<List<PlanSession>>,
    onCreatePlan: () -> Unit,
    onImportPlan: () -> Unit,
    onEditPlan: (Long) -> Unit,
    onActivatePlan: (WorkoutPlan) -> Unit,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier
) {
    val otherPlans = plans.filterNot { it.id == activePlan?.id }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "active-plan-heading") {
                Text(
                    "Active plan",
                    color = ApexColors.TextPrimary,
                    fontFamily = InterFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
            }

            if (activePlanError != null) {
                item(key = "active-plan-error") {
                    Text("Active plan unavailable", color = ApexColors.TextPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(activePlanError, color = ApexColors.TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
            } else if (activePlan == null) {
                item(key = "active-plan-empty") {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text("No active plan", color = ApexColors.TextPrimary,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Choose a plan to see your upcoming training here.",
                            color = ApexColors.TextSecondary, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                item(key = "active-plan-summary") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(activePlan.name, color = ApexColors.TextPrimary,
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val frequency = frequencyLabel(activePlan, activeSessions)
                        val summary = listOfNotNull(frequency, activePlan.goal.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                        if (summary.isNotBlank()) {
                            Text(summary, color = ApexColors.TextSecondary, fontSize = 14.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Next", color = ApexColors.TextSecondary, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(nextSessionLabel(activePlan, activeSessions, today), color = ApexColors.TextPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = { onEditPlan(activePlan.id) },
                            modifier = Modifier.align(Alignment.Start).heightIn(min = 44.dp)
                        ) {
                            Text("Edit plan", color = ApexColors.TextSecondary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = ApexColors.BorderSubtle)
                    }
                }
            }

            item(key = "other-plans-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Other plans", color = ApexColors.TextPrimary,
                        fontFamily = InterFamily, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onImportPlan, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("Import from text", color = ApexColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            if (otherPlans.isEmpty()) {
                item(key = "other-plans-empty") {
                    Text(
                        if (activePlan == null && plans.isEmpty()) "No plans yet" else "No other plans",
                        color = ApexColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(otherPlans, key = { it.id }) { plan ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .semantics { contentDescription = "Edit ${plan.name}" }
                                    .clickable(role = Role.Button) { onEditPlan(plan.id) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(plan.name, color = ApexColors.TextPrimary,
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(frequencyLabel(plan, UiState.Success(emptyList())) ?: "Schedule not set",
                                    color = ApexColors.TextSecondary, fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 3.dp))
                            }
                            TextButton(onClick = { onActivatePlan(plan) },
                                modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Activate", color = ApexColors.Primary, fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(color = ApexColors.BorderSubtle)
                    }
                }
            }
        }

        Button(
            onClick = onCreatePlan,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 56.dp)
                .testTag("build_custom_plan_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary,
                contentColor = ApexColors.OnGlow),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Create plan", fontFamily = SyneFamily, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun PlansLoadingState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = ApexColors.Primary, strokeWidth = 2.dp)
        Text("Loading plans", color = ApexColors.TextSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp))
    }
}

private fun frequencyLabel(plan: WorkoutPlan, sessions: UiState<List<PlanSession>>): String? {
    val days = plan.daysPerWeek?.takeIf { it > 0 }
        ?: ((sessions as? UiState.Success)?.data?.map { it.day.lowercase() }?.distinct()?.size?.takeIf { it > 0 })
    if (plan.cycleWeeks > 1) return "${plan.cycleWeeks}-week rotation"
    return days?.let { "$it days / week" }
}

private fun nextSessionLabel(plan: WorkoutPlan, state: UiState<List<PlanSession>>, todayDate: LocalDate): String = when (state) {
    UiState.Loading -> "Schedule loading"
    is UiState.Error -> "Schedule unavailable"
    is UiState.Success -> {
        val sessions = state.data
        if (sessions.isEmpty()) {
            "No sessions scheduled"
        } else {
            val next = com.apexfit.app.utils.WorkoutSchedule.nextSession(plan, sessions, todayDate)
            next?.let { (date, session) -> "${session.label} · ${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}" } ?: "No upcoming session"
        }
    }
}
