package com.apexfit.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.components.ExerciseMediaPanelContent
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.SetVolumeAdjustment
import com.apexfit.app.data.EffectiveSetsData
import com.apexfit.app.utils.ExerciseMedia
import com.apexfit.app.utils.FormGuideContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseDetailInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val exercise = Exercise(
        id = "dumbbell-lateral-raise",
        name = "Dumbbell Lateral Raise",
        category = "Dumbbell",
        primaryMuscle = "Shoulders",
        equipmentRequired = "Dumbbell"
    )

    @Test
    fun systemBackDismissesFormGuide() {
        var dismissed = false
        compose.setContent {
            ApexFitTheme {
                FormGuideSheet(
                    exercise = exercise,
                    content = FormGuideContent(sourceNote = null, muscleNote = "Primary: Shoulders."),
                    onDismiss = { dismissed = true }
                )
            }
        }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun activeWorkoutFormGuideEntryOpensTheCurrentExercise() {
        val plannedExercise = PlanExercise(
            planSessionId = 1,
            exerciseId = exercise.id,
            name = exercise.name,
            muscleGroup = exercise.primaryMuscle,
            sets = 3,
            repsMin = 10,
            repsMax = 15,
            weight = 0.0,
            restSeconds = 90,
            notes = ""
        )
        var opened: PlanExercise? = null
        compose.setContent {
            ApexFitTheme {
                ExerciseListSection(
                    exercises = listOf(plannedExercise),
                    currentIdx = 0,
                    loggedSets = emptyMap<String, List<UiExerciseSet>>(),
                    effectiveSetsMap = emptyMap<String, EffectiveSetsData>(),
                    warmupComp = emptyMap(),
                    lastWeights = emptyMap(),
                    weightContextLines = emptyMap(),
                    volumeAdjustments = emptyMap<String, SetVolumeAdjustment>(),
                    onUndoVolumeAdjustment = { false },
                    preferredUnits = "kg",
                    showRirOverlay = false,
                    onOpenFormGuide = { opened = it },
                    onToggleWarmupItem = {},
                    onLogSetState = { _, _, _, _, _, _, _ -> },
                    onCompleteSet = { _, _, _, _, _, _, _, _ -> },
                    onAddCustomLogSet = {},
                    onNextExercise = {},
                    onSkipExercise = {},
                    onShowNormalFinishFeelDialog = {},
                    onShowFinishEarlyDialog = {},
                    onShowExitDialog = {},
                    onCancelOrCompleteEmptySession = {}
                )
            }
        }

        compose.onNodeWithText("Form guide").performClick()
        compose.runOnIdle { assertEquals(plannedExercise, opened) }
    }

    @Test
    fun canScrollToAndSelectAnAlternative() {
        val alternative = exercise.copy(
            id = "cable-lateral-raise",
            name = "Cable Lateral Raise",
            category = "Cable",
            equipmentRequired = "Cable"
        )
        var selected: Exercise? = null
        compose.setContent {
            ApexFitTheme {
                FormGuideScreenContent(
                    exercise = exercise,
                    content = FormGuideContent(
                        sourceNote = null,
                        muscleNote = "Primary: Shoulders.",
                        instructions = listOf("Raise to shoulder height.", "Lower with control."),
                        alternativeExercises = listOf(alternative)
                    ),
                    preferredUnits = "kg",
                    onBack = {},
                    onSelectAlternative = { selected = it },
                    mediaContent = { Text("No mapped technique video.") }
                )
            }
        }

        compose.onNodeWithText("Alternatives").performScrollTo().assertExists()
        compose.onNodeWithText("Cable Lateral Raise").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(alternative, selected) }
    }

    @Test
    fun videoOfflineStateOffersRetry() {
        val media = ExerciseMedia(
            sourceId = null,
            imageUrls = emptyList(),
            instructions = emptyList(),
            youtubeVideoId = "qpAvhLiy50Y",
            youtubeVideoTitle = "Technique video",
            youtubeMatchType = "exact",
            youtubeMatchScore = 100,
            youtubeChannel = "DEMIC"
        )
        var retryRequested = false
        compose.setContent {
            ApexFitTheme {
                ExerciseMediaPanelContent(
                    media = media,
                    isLoaded = true,
                    loadFailed = false,
                    videoUnavailable = true,
                    onRetryVideo = { retryRequested = true }
                )
            }
        }

        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertTrue(retryRequested) }
    }

    @Test
    fun techniqueVideoLoadsOnlyAfterUserRequestsPlayback() {
        val media = ExerciseMedia(
            sourceId = null,
            imageUrls = emptyList(),
            instructions = emptyList(),
            youtubeVideoId = "qpAvhLiy50Y",
            youtubeVideoTitle = "Technique video",
            youtubeMatchType = "exact",
            youtubeMatchScore = 100,
            youtubeChannel = "DEMIC"
        )
        compose.setContent {
            ApexFitTheme {
                ExerciseMediaPanelContent(media = media, isLoaded = true, loadFailed = false)
            }
        }

        compose.onNodeWithText("Play technique video").assertExists().performClick()
        compose.onNodeWithText("Streamed by YouTube. Apex Fit does not download or rehost the video.")
            .assertExists()
    }
}
