package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.EstimatedStrengthPoint
import com.apexfit.app.utils.HistoricalStrengthWeight
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun EstimatedStrengthWithHistoricalWeight() {
    ApexFitTheme {
        Column {
            EstimatedStrengthPanel(listOf(
                EstimatedStrengthPoint(LocalDate.parse("2026-11-12"), "s1", 1, "gym a", 50.0, 5,
                    58.333333, null),
                EstimatedStrengthPoint(LocalDate.parse("2026-11-16"), "s2", 2, "gym a", 60.0, 5,
                    70.0, HistoricalStrengthWeight(LocalDate.parse("2026-11-15"), 70.0, 1))
            ), "kg")
        }
    }
}
