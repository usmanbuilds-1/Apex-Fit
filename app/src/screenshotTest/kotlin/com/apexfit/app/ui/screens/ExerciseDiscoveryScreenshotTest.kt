package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.Exercise
import com.apexfit.app.ui.components.ExerciseMediaPanelContent
import com.apexfit.app.utils.ExerciseMedia
import com.apexfit.app.ui.models.UiState
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.FormGuideBuilder

@Composable
private fun RenderMediaState(content: @Composable () -> Unit) {
    ApexFitTheme {
        Box(
            Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)
        ) {
            content()
        }
    }
}

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
fun ExercisePickerMusclesBack() {
    ApexFitTheme {
        ExercisePicker(
            UiState.Success(listOf(lateralRaise)),
            "Substitute exercise",
            initialMuscle = "Back",
            initialShowBack = true,
            onBack = {},
            onSelect = {}
        )
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

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerLoading() {
    ApexFitTheme { ExercisePicker(UiState.Loading, "Exercise library", onBack = {}, onSelect = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerError() {
    ApexFitTheme { ExercisePicker(UiState.Error("Unavailable"), "Exercise library", onBack = {}, onSelect = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerEmpty() {
    ApexFitTheme { ExercisePicker(UiState.Success(emptyList()), "Exercise library", onBack = {}, onSelect = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExercisePickerNoMatches() {
    ApexFitTheme {
        ExercisePicker(
            UiState.Success(listOf(lateralRaise)),
            "Exercise library",
            initialQuery = "barbell",
            onBack = {},
            onSelect = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseMediaLoading() {
    RenderMediaState { ExerciseMediaPanelContent(null, isLoaded = false, loadFailed = false) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseMediaWithoutVideo() {
    RenderMediaState { ExerciseMediaPanelContent(null, isLoaded = true, loadFailed = false) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseVideoOffline() {
    RenderMediaState {
        ExerciseMediaPanelContent(
            ExerciseMedia(
                sourceId = null,
                imageUrls = emptyList(),
                instructions = emptyList(),
                youtubeVideoId = "qpAvhLiy50Y",
                youtubeVideoTitle = "DEMIC technique video",
                youtubeMatchType = "fuzzy",
                youtubeMatchScore = 78,
                youtubeChannel = "DEMIC"
            ),
            isLoaded = true,
            loadFailed = false,
            videoUnavailable = true
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseGuideWithoutWrittenInstructions() {
    val content = FormGuideBuilder.buildFormGuide(lateralRaise, null, emptyList())
    ApexFitTheme { FormGuideSheet(lateralRaise, content, onDismiss = {}) }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseMediaAvailable() {
    RenderMediaState {
        ExerciseMediaPanelContent(
            ExerciseMedia(
                sourceId = null,
                imageUrls = emptyList(),
                instructions = emptyList(),
                youtubeVideoId = "qpAvhLiy50Y",
                youtubeVideoTitle = "Dumbbell lateral raise technique",
                youtubeMatchType = "exact",
                youtubeMatchScore = 100,
                youtubeChannel = "DEMIC"
            ),
            isLoaded = true,
            loadFailed = false
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExerciseRelatedMovementVideoShowsCaution() {
    RenderMediaState {
        ExerciseMediaPanelContent(
            ExerciseMedia(
                sourceId = null,
                imageUrls = emptyList(),
                instructions = emptyList(),
                youtubeVideoId = "qpAvhLiy50Y",
                youtubeVideoTitle = "A related shoulder exercise",
                youtubeMatchType = "fuzzy",
                youtubeMatchScore = 78,
                youtubeChannel = "DEMIC"
            ),
            isLoaded = true,
            loadFailed = false
        )
    }
}

