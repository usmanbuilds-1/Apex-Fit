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
fun SettingsProfileRowsNightLedger() {
    ApexFitTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.Canvas)
                .padding(horizontal = 20.dp)
        ) {
            SettingsSectionTitle("Profile")
            SettingsDisclosureRow("Name", "Ayesha", expanded = false, onClick = {}) {}
            SettingsDisclosureRow("Training goal", "Build muscle", expanded = false, onClick = {}) {}
            SettingsDisclosureRow("Weekly sessions", "4 per week", expanded = false, onClick = {}) {}
            SettingsDisclosureRow("Nutrition target", "2,450 kcal", expanded = false, onClick = {}) {}
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun SettingsExpandedEditorNightLedger() {
    ApexFitTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ApexColors.Canvas)
                .padding(horizontal = 20.dp)
        ) {
            SettingsSectionTitle("Profile")
            SettingsDisclosureRow("Training goal", "Build muscle", expanded = true, onClick = {}) {
                SettingsGoalOptions(selectedGoal = "Gain Muscle", onGoalSelected = {})
            }
            SettingsDisclosureRow("Weekly sessions", "4 per week", expanded = false, onClick = {}) {}
        }
    }
}

