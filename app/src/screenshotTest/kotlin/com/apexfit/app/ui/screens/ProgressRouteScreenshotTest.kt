package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PersonalRecordWithName
import com.apexfit.app.data.TrainingSession
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PersonalRecordsEmpty() {
    ApexFitTheme {
        PersonalRecordsContent(records = emptyList(), units = "kg", onBack = {})
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PersonalRecordsWithHistory() {
    ApexFitTheme {
        PersonalRecordsContent(
            records = listOf(
                PersonalRecordWithName(
                    record = PersonalRecord(
                        id = "dumbbell_chest_press_max_weight",
                        exerciseId = "dumbbell_chest_press",
                        type = "max_weight",
                        value = 16.0,
                        date = "2026-11-15"
                    ),
                    displayName = "Dumbbell Chest Press"
                ),
                PersonalRecordWithName(
                    record = PersonalRecord(
                        id = "barbell_squat_estimated_1rm",
                        exerciseId = "barbell_squat",
                        type = "estimated_1rm",
                        value = 72.5,
                        date = "2026-11-12"
                    ),
                    displayName = "Barbell Squat"
                )
            ),
            units = "kg",
            onBack = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutHistoryEmpty() {
    ApexFitTheme {
        WorkoutHistoryContent(sessions = emptyList(), onBack = {}, onOpenSession = {})
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutHistoryWithSessions() {
    ApexFitTheme {
        WorkoutHistoryContent(
            sessions = listOf(
                historySession(
                    id = "upper-a",
                    date = "2026-11-15",
                    name = "Upper Body A",
                    minutes = 52,
                    status = "completed"
                ),
                historySession(
                    id = "lower-b",
                    date = "2026-11-13",
                    name = "Lower Body B",
                    minutes = 31,
                    status = "partial"
                )
            ),
            onBack = {},
            onOpenSession = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutHistoryDetailWithSets() {
    ApexFitTheme {
        WorkoutHistoryDetail(
            session = historySession(
                id = "upper-a",
                date = "2026-11-15",
                name = "Upper Body A",
                minutes = 52,
                status = "completed"
            ),
            sets = listOf(
                ExerciseSet(
                    id = 1,
                    sessionId = "upper-a",
                    exerciseId = "dumbbell_chest_press",
                    exerciseName = "Dumbbell Chest Press",
                    muscleGroup = "Chest",
                    weight = 16.0,
                    reps = 8,
                    rpe = 8,
                    isWarmup = false,
                    completed = true
                ),
                ExerciseSet(
                    id = 2,
                    sessionId = "upper-a",
                    exerciseId = "dumbbell_chest_press",
                    exerciseName = "Dumbbell Chest Press",
                    muscleGroup = "Chest",
                    weight = 16.0,
                    reps = 8,
                    rpe = 8,
                    isWarmup = false,
                    completed = true
                )
            ),
            units = "kg",
            onBack = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutHistoryDetailLoading() {
    ApexFitTheme {
        WorkoutHistoryDetail(
            session = historySession("upper-a", "2026-11-15", "Upper Body A", 52, "completed"),
            sets = null,
            units = "kg",
            onBack = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WorkoutHistoryDetailWithoutSets() {
    ApexFitTheme {
        WorkoutHistoryDetail(
            session = historySession("upper-a", "2026-11-15", "Upper Body A", 52, "completed"),
            sets = emptyList(),
            units = "kg",
            onBack = {}
        )
    }
}

private fun historySession(
    id: String,
    date: String,
    name: String,
    minutes: Int,
    status: String
) = TrainingSession(
    id = id,
    date = date,
    sessionType = name,
    completed = status == "completed",
    durationMinutes = minutes,
    sessionFeel = 4,
    status = status
)
