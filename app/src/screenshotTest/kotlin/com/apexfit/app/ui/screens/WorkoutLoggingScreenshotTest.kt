package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.ui.theme.ApexColors
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
            setNumber = 2,
            totalSets = 4,
            targetRepsMin = 8,
            targetRepsMax = 12,
            selectedRir = 2,
            weight = 60.0,
            reps = 10,
            exerciseHistory = sampleHistory,
            isShowingHistory = false,
            units = "kg",
            onSelectRirOption = {},
            onConfirmRirSelection = {},
            onReturnFromHistory = {},
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
            setNumber = 2,
            totalSets = 4,
            targetRepsMin = 8,
            targetRepsMax = 12,
            selectedRir = 2,
            weight = 60.0,
            reps = 10,
            exerciseHistory = sampleHistory,
            isShowingHistory = true,
            units = "kg",
            onSelectRirOption = {},
            onConfirmRirSelection = {},
            onReturnFromHistory = {},
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
