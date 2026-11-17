package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.ui.theme.ApexFitTheme

private val sampleHistory = listOf(
    LastSetWithDate(
        id = 1,
        sessionId = "session-preview",
        exerciseId = "barbell-bench-press",
        exerciseName = "Barbell Bench Press",
        muscleGroup = "Chest",
        weight = 60.0,
        reps = 10,
        rpe = 8,
        isWarmup = false,
        restTaken = 90,
        completed = true,
        date = "Nov 15",
        repsInReserve = 2
    )
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun RirSelectionNightLedger() {
    ApexFitTheme {
        RpeRirSelectorSheet(
            exerciseName = "Barbell Bench Press",
            exerciseNumber = 2,
            exerciseCount = 5,
            setNumber = 2,
            totalSets = 4,
            targetRepsMin = 8,
            targetRepsMax = 12,
            selectedRir = 2,
            weight = 60.0,
            reps = 10,
            exerciseHistory = sampleHistory,
            isShowingHistory = false,
            hasLoggedSet = false,
            hasRestContext = false,
            restSeconds = 0,
            totalRestSeconds = 0,
            lastSetContext = "",
            nextSetContext = "",
            primaryActionLabel = "Log set 2",
            units = "kg",
            onSelectRirOption = {},
            onConfirmRirSelection = {},
            onReturnFromHistory = {},
            onAddRestTime = {},
            onSubtractRestTime = {},
            onConfirmRirAndNextSet = {},
            onDismiss = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun RirAndRestNightLedger() {
    ApexFitTheme {
        RpeRirSelectorSheet(
            exerciseName = "Incline Dumbbell Curl",
            exerciseNumber = 2,
            exerciseCount = 5,
            setNumber = 2,
            totalSets = 3,
            targetRepsMin = 12,
            targetRepsMax = 15,
            selectedRir = 2,
            weight = 14.0,
            reps = 14,
            exerciseHistory = sampleHistory,
            isShowingHistory = false,
            hasLoggedSet = true,
            hasRestContext = true,
            restSeconds = 74,
            totalRestSeconds = 90,
            lastSetContext = "Last set: 14 kg × 14 reps · RPE 8",
            nextSetContext = "Next: Incline Dumbbell Curl (set 3/3)",
            primaryActionLabel = "Start set 3",
            units = "kg",
            onSelectRirOption = {},
            onConfirmRirSelection = {},
            onReturnFromHistory = {},
            onAddRestTime = {},
            onSubtractRestTime = {},
            onConfirmRirAndNextSet = {},
            onDismiss = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun RirHistoryNightLedger() {
    ApexFitTheme {
        RpeRirSelectorSheet(
            exerciseName = "Barbell Bench Press",
            exerciseNumber = 2,
            exerciseCount = 5,
            setNumber = 2,
            totalSets = 4,
            targetRepsMin = 8,
            targetRepsMax = 12,
            selectedRir = 2,
            weight = 60.0,
            reps = 10,
            exerciseHistory = sampleHistory,
            isShowingHistory = true,
            hasLoggedSet = false,
            hasRestContext = false,
            restSeconds = 0,
            totalRestSeconds = 0,
            lastSetContext = "",
            nextSetContext = "",
            primaryActionLabel = "Back to workout",
            units = "kg",
            onSelectRirOption = {},
            onConfirmRirSelection = {},
            onReturnFromHistory = {},
            onAddRestTime = {},
            onSubtractRestTime = {},
            onConfirmRirAndNextSet = {},
            onDismiss = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun RestTimerNightLedger() {
    ApexFitTheme {
        RestTimerOverlay(
            seconds = 74,
            totalSeconds = 90,
            lastSet = "Last set: 60.0 kg × 10 reps · RPE 8",
            nextSet = "Next: Barbell Bench Press (set 3/4)",
            onDismiss = {},
            onAddTime = {},
            onSubtractTime = {}
        )
    }
}
