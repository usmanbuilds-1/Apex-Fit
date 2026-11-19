package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderMetadataWithValues() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            PlanBuilderMetadataFields(
                planName = "APEX Upper / Lower",
                planGoal = "Build muscle",
                planNameError = null,
                onPlanNameChange = {},
                onPlanGoalChange = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderNameValidationError() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp)) {
            PlanBuilderMetadataFields(
                planName = "",
                planGoal = "Build muscle",
                planNameError = "Enter a plan name",
                onPlanNameChange = {},
                onPlanGoalChange = {}
            )
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun PlanBuilderSaveAction() {
    ApexFitTheme {
        Column(Modifier.fillMaxSize().background(ApexColors.Canvas)) {
            PlanBuilderSaveBar(isSaving = false, onSave = {})
        }
    }
}
