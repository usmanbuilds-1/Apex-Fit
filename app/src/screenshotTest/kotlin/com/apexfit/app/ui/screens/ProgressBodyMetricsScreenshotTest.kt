package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.models.UiWeightEntry
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ProgressWeightLogFlat() {
    ApexFitTheme {
        Column(
            Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WeightLogSection("", null, "kg", onInputWeightChange = {}, onSave = {})
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ProgressWeightLogValidationError() {
    ApexFitTheme {
        Column(
            Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WeightLogSection("12", "Must be 20.0–300.0 kg", "kg", onInputWeightChange = {}, onSave = {})
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ProgressWeightHistoryFlat() {
    ApexFitTheme {
        Column(
            Modifier.fillMaxSize().background(ApexColors.Canvas).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WeightHistorySection(
                entries = listOf(
                    UiWeightEntry(id = 1, weight = 78.4, date = "2026-11-16", time = "08:30"),
                    UiWeightEntry(id = 2, weight = 78.7, date = "2026-11-09", time = "08:12")
                ),
                units = "kg",
                onDelete = {}
            )
        }
    }
}
