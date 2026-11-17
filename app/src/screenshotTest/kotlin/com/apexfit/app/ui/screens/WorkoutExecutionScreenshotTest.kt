package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.ui.theme.ApexColors

private val activeWorkoutExercises = listOf(
    PlanExercise(
        planSessionId = 1,
        exerciseId = "db-chest-press",
        name = "Dumbbell Chest Press",
        muscleGroup = "Chest",
        sets = 3,
        repsMin = 8,
        repsMax = 12,
        weight = 26.0,
        restSeconds = 120,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "db-lateral-raise",
        name = "Dumbbell Lateral Raise",
        muscleGroup = "Shoulders",
        sets = 3,
        repsMin = 12,
        repsMax = 15,
        weight = 14.0,
        restSeconds = 90,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "seated-cable-row",
        name = "Seated Cable Row",
        muscleGroup = "Back",
        sets = 3,
        repsMin = 8,
        repsMax = 12,
        weight = 59.0,
        restSeconds = 120,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "db-shoulder-press",
        name = "Dumbbell Shoulder Press",
        muscleGroup = "Shoulders",
        sets = 3,
        repsMin = 6,
        repsMax = 10,
        weight = 20.0,
        restSeconds = 120,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "cable-fly",
        name = "Cable Fly",
        muscleGroup = "Chest",
        sets = 3,
        repsMin = 10,
        repsMax = 15,
        weight = 0.0,
        restSeconds = 90,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "face-pull",
        name = "Face Pull",
        muscleGroup = "Shoulders",
        sets = 3,
        repsMin = 15,
        repsMax = 20,
        weight = 22.5,
        restSeconds = 60,
        notes = ""
    ),
    PlanExercise(
        planSessionId = 1,
        exerciseId = "triceps-rope-pushdown",
        name = "Triceps Rope Pushdown",
        muscleGroup = "Arms",
        sets = 3,
        repsMin = 12,
        repsMax = 15,
        weight = 27.5,
        restSeconds = 75,
        notes = ""
    )
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ActiveWorkoutNightLedger() {
    val lateralRaises = listOf(
        UiExerciseSet(id = 1, weight = 14.0, reps = 14, rpe = 8, completed = true,
            exerciseName = "Dumbbell Lateral Raise", exerciseId = "db-lateral-raise", muscleGroup = "Shoulders"),
        UiExerciseSet(id = 2, weight = 14.0, reps = 14, rpe = 0, completed = false,
            exerciseName = "Dumbbell Lateral Raise", exerciseId = "db-lateral-raise", muscleGroup = "Shoulders"),
        UiExerciseSet(id = 3, weight = 14.0, reps = 12, rpe = 0, completed = false,
            exerciseName = "Dumbbell Lateral Raise", exerciseId = "db-lateral-raise", muscleGroup = "Shoulders")
    )

    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            WorkoutExecutionHeader(
                sessionLabel = "Upper Body A",
                elapsedTime = "24:18",
                onBack = {},
                onFinish = {}
            )
            ExerciseListSection(
                exercises = activeWorkoutExercises,
                currentIdx = 1,
                loggedSets = mapOf("db-lateral-raise" to lateralRaises),
                effectiveSetsMap = emptyMap(),
                warmupComp = mapOf("Shoulder circles" to true, "Band pull-aparts" to false),
                lastWeights = mapOf("db-lateral-raise" to 14.0),
                weightContextLines = emptyMap(),
                volumeAdjustments = emptyMap(),
                onUndoVolumeAdjustment = { false },
                preferredUnits = "kg",
                showRirOverlay = false,
                onOpenFormGuide = {},
                onToggleWarmupItem = {},
                onLogSetState = { _, _, _, _, _, _, _ -> },
                onCompleteSet = { _, _, _, _, _, _, _, _ -> },
                onAddCustomLogSet = {},
                onNextExercise = {},
                onSkipExercise = {},
                onShowNormalFinishFeelDialog = {},
                onShowFinishEarlyDialog = {},
                onShowExitDialog = {},
                onCancelOrCompleteEmptySession = {},
                modifier = Modifier.weight(1f)
            )
        }
    }
}
