package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.LastSetWithDate
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.components.ExerciseMediaPanelContent
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.FormGuideBuilder

private val lateralRaiseDetail = Exercise(
    id = "dumbbell-lateral-raise",
    name = "Dumbbell Lateral Raise",
    category = "Dumbbell",
    primaryMuscle = "Shoulders",
    equipmentRequired = "Dumbbell"
)

private val lateralRaisePrescription = PlanExercise(
    planSessionId = 1,
    exerciseId = lateralRaiseDetail.id,
    name = lateralRaiseDetail.name,
    muscleGroup = "Shoulders",
    sets = 3,
    repsMin = 12,
    repsMax = 15,
    weight = 14.0,
    restSeconds = 90,
    notes = "Lead with the elbows. Keep the movement controlled."
)

private val lateralRaiseHistory = listOf(
    LastSetWithDate(21, "session-21", lateralRaiseDetail.id, lateralRaiseDetail.name, "Shoulders",
        14.0, 14, 8, false, 90, true, "2026-11-18"),
    LastSetWithDate(22, "session-21", lateralRaiseDetail.id, lateralRaiseDetail.name, "Shoulders",
        14.0, 13, 8, false, 90, true, "2026-11-18"),
    LastSetWithDate(23, "session-21", lateralRaiseDetail.id, lateralRaiseDetail.name, "Shoulders",
        14.0, 12, 9, false, 90, true, "2026-11-18"),
    LastSetWithDate(18, "session-18", lateralRaiseDetail.id, lateralRaiseDetail.name, "Shoulders",
        12.0, 15, 8, false, 90, true, "2026-11-15")
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 1100, locale = "en-US")
@Composable
fun ExerciseDetailWithProgramAndHistory() {
    val content = FormGuideBuilder.buildFormGuide(
        exercise = lateralRaiseDetail,
        metadata = null,
        sourceInstructions = listOf(
            "Stand tall with a slight bend in the elbows.",
            "Raise under control, leading with the elbows.",
            "Stop around shoulder height without shrugging.",
            "Lower slowly to the starting position."
        ),
        prescription = lateralRaisePrescription,
        recentHistory = lateralRaiseHistory,
        personalRecords = listOf(
            PersonalRecord("${lateralRaiseDetail.id}_max_weight", lateralRaiseDetail.id,
                "max_weight", 16.0, "2026-11-19")
        )
    )
    ApexFitTheme {
        FormGuideScreenContent(
            exercise = lateralRaiseDetail,
            content = content,
            preferredUnits = "kg",
            onBack = {},
            mediaContent = { ExerciseMediaPanelContent(media = null, isLoaded = true, loadFailed = false) }
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun ExerciseDetailWithoutPlanOrTrainingHistory() {
    val content = FormGuideBuilder.buildFormGuide(
        exercise = lateralRaiseDetail,
        metadata = null,
        sourceInstructions = emptyList()
    )
    ApexFitTheme {
        FormGuideScreenContent(
            exercise = lateralRaiseDetail,
            content = content,
            preferredUnits = "kg",
            onBack = {},
            mediaContent = { ExerciseMediaPanelContent(media = null, isLoaded = true, loadFailed = false) }
        )
    }
}
