package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexFitTheme

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WeightLogSaveFailure() {
    ApexFitTheme {
        LogWeightDialog(
            initialInput = "78.4",
            units = "kg",
            onDismiss = {},
            onSave = {},
            saveError = "Failed to save weight. Please try again."
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US", fontScale = 1.3f)
@Composable
fun WeightLogSaveFailureLargeFont() {
    ApexFitTheme {
        LogWeightDialog(
            initialInput = "78.4",
            units = "kg",
            onDismiss = {},
            onSave = {},
            saveError = "Failed to save weight. Please try again."
        )
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun WeightLogSaving() {
    ApexFitTheme {
        LogWeightDialog(
            initialInput = "78.4",
            units = "kg",
            onDismiss = {},
            onSave = {},
            isSaving = true
        )
    }
}
