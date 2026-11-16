package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.Exercise
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.FormGuideBuilder

private val lateralRaise = Exercise(
    id = "dumbbell-lateral-raise", name = "Dumbbell Lateral Raise", category = "Dumbbell",
    primaryMuscle = "Shoulders", equipmentRequired = "Dumbbell"
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerSearch() {
    ApexFitTheme { ExercisePicker(UiState.Success(listOf(lateralRaise)), "Exercise library", onBack = {}, onSelect = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerMuscles() {
    ApexFitTheme {
        ExercisePicker(UiState.Success(listOf(lateralRaise)), "Substitute exercise",
            initialMuscle = "Shoulders", onBack = {}, onSelect = {})
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseGuideWithoutVerifiedImages() {
    val content = FormGuideBuilder.buildFormGuide(lateralRaise, null,
        listOf("Dumbbells beside hips. Keep wrists neutral.", "Raise until arms are near shoulder height."))
    ApexFitTheme { FormGuideSheet(lateralRaise, content, onDismiss = {}) }
}
