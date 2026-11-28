package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.StrengthSessionPoint
import com.apexfit.app.utils.StrengthObservation
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun StrengthHistoryActualPairs() {
    ApexFitTheme {
        Column {
            StrengthHistorySummary(listOf(
                StrengthSessionPoint("a", LocalDate.parse("2026-11-01"), false,
                    listOf(StrengthObservation(1, 50.0, 8))),
                StrengthSessionPoint("b", LocalDate.parse("2026-11-16"), false,
                    listOf(StrengthObservation(2, 60.0, 5), StrengthObservation(3, 50.0, 10)))
            ), "kg")
        }
    }
}
