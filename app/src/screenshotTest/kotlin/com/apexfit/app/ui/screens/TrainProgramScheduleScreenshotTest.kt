package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.R
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.ui.theme.SyneFamily

private val sampleProgramSessions = listOf(
    PlanSession(id = 1, planId = 1, label = "Upper A", day = "Monday", focus = "Chest, Back, Shoulders"),
    PlanSession(id = 2, planId = 1, label = "Lower A", day = "Tuesday", focus = "Quads, Hamstrings, Calves"),
    PlanSession(id = 3, planId = 1, label = "Upper B", day = "Thursday", focus = "Back, Chest, Arms"),
    PlanSession(id = 4, planId = 1, label = "Lower B", day = "Friday", focus = "Glutes, Quads, Calves")
)

private val sampleProgramExercises = listOf(
    PlanExercise(id = 1, planSessionId = 1, exerciseId = "db-lateral-raise", name = "Dumbbell Lateral Raise",
        muscleGroup = "Shoulders", sets = 3, repsMin = 12, repsMax = 15, weight = 14.0,
        restSeconds = 90, notes = ""),
    PlanExercise(id = 2, planSessionId = 1, exerciseId = "db-chest-press", name = "Dumbbell Chest Press",
        muscleGroup = "Chest", sets = 3, repsMin = 8, repsMax = 12, weight = 26.0,
        restSeconds = 120, notes = ""),
    PlanExercise(id = 3, planSessionId = 1, exerciseId = "seated-cable-row", name = "Seated Cable Row",
        muscleGroup = "Back", sets = 3, repsMin = 10, repsMax = 12, weight = 59.0,
        restSeconds = 120, notes = ""),
    PlanExercise(id = 4, planSessionId = 1, exerciseId = "db-shoulder-press", name = "Dumbbell Shoulder Press",
        muscleGroup = "Shoulders", sets = 3, repsMin = 10, repsMax = 12, weight = 20.0,
        restSeconds = 90, notes = ""),
    PlanExercise(id = 5, planSessionId = 1, exerciseId = "incline-db-curl", name = "Incline Dumbbell Curl",
        muscleGroup = "Biceps", sets = 3, repsMin = 12, repsMax = 15, weight = 14.0,
        restSeconds = 75, notes = ""),
    PlanExercise(id = 6, planSessionId = 1, exerciseId = "triceps-rope-pushdown", name = "Triceps Rope Pushdown",
        muscleGroup = "Triceps", sets = 3, repsMin = 12, repsMax = 15, weight = 27.5,
        restSeconds = 75, notes = ""),
    PlanExercise(id = 7, planSessionId = 1, exerciseId = "face-pull", name = "Face Pull",
        muscleGroup = "Shoulders", sets = 3, repsMin = 15, repsMax = 20, weight = 22.5,
        restSeconds = 60, notes = "")
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 1032, locale = "en-US")
@Composable
fun TrainProgramScheduleNightLedger() {
    val selectedSession = sampleProgramSessions.first()
    ApexFitTheme {
        Column(modifier = Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Train",
                    color = ApexColors.TextPrimary,
                    fontFamily = SyneFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(painter = painterResource(R.drawable.ic_apex_logo), contentDescription = null,
                    tint = ApexColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("APEX FIT", color = ApexColors.TextPrimary, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            TrainSubTabControl(
                labels = listOf("Program", "Workout", "Plans"),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TrainProgramSchedule(
                sessionsState = UiState.Success(sampleProgramSessions),
                allExercises = sampleProgramExercises,
                selectedDay = "Monday",
                selectedSession = selectedSession,
                selectedExercises = sampleProgramExercises,
                preferredUnits = "kg",
                isStarting = false,
                onSelectDay = {},
                onStartSession = {},
                onOpenPlans = {},
                onOpenLibrary = {},
                onOpenFormGuide = {},
                onSubstituteExercise = {},
                modifier = Modifier.weight(1f)
            )
            }
            BottomNavBar(activeTab = 1, onTabSelected = {})
        }
    }
}
