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
import com.apexfit.app.ui.models.UiBodyMeasurement
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ProgressMeasurementsWithHistory() {
    RenderMeasurements(
        listOf(
            UiBodyMeasurement(bodyPart = "Chest", value = 101.5, date = "2026-11-14"),
            UiBodyMeasurement(bodyPart = "Waist", value = 84.0, date = "2026-11-10"),
            UiBodyMeasurement(bodyPart = "Biceps", value = 36.2, date = "2026-11-08")
        )
    )
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ProgressMeasurementsEmpty() {
    RenderMeasurements(emptyList())
}

@Composable
private fun RenderMeasurements(measurements: List<UiBodyMeasurement>) {
    ApexFitTheme {
        Column(
            Modifier.fillMaxSize().background(ApexColors.Canvas).padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            BodyMeasurementsTrackerPanel(measurements, lengthUnit = "cm", onPartClick = {})
        }
    }
}
