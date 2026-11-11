package com.apexfit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.models.UiExerciseSet
import com.apexfit.app.ui.screens.*
import com.apexfit.app.ui.theme.*

class UiReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = intent.getStringExtra("view")
        setContent {
            ApexFitTheme {
                when (view) {
                    "rir" -> RpeRirSelectorSheet(2, 20.0, 10, emptyList(), false, "kg", {}, {}, {}, {})
                    "rest" -> RestTimerOverlay(85, 120, {}, {}, {}, "Last: 8 reps", "Next: 10 reps")
                    "complete" -> SessionCompleteSheet(
                        listOf(UiExerciseSet(weight = 20.0, reps = 10, rpe = 8)),
                        listOf("Dumbbell press: 20 kg"), 8.0, 45, 600.0, "kg", {}
                    )
                    else -> Column(Modifier.fillMaxSize().background(ApexColors.Canvas)
                        .verticalScroll(rememberScrollState()).padding(16.dp)) {
                        SetLoggingRow(
                            ex = PlanExercise(planSessionId = 1, exerciseId = "press", name = "Dumbbell press",
                                muscleGroup = "Chest", sets = 3, repsMin = 8, repsMax = 12,
                                weight = 20.0, restSeconds = 120, notes = ""),
                            sIdx = 0, setObj = UiExerciseSet(weight = 20.0, reps = 10, rpe = 8, completed = false),
                            totalSets = 3, preferredUnits = "kg", unitSuffix = "kg", isImperial = false,
                            lastWeights = emptyMap(), weightContextLines = emptyMap(), showRirOverlay = false,
                            onLogSetState = { _, _, _, _, _, _, _ -> },
                            onCompleteSet = { _, _, _, _, _, _, _, _ -> }
                        )
                    }
                }
            }
        }
    }
}
