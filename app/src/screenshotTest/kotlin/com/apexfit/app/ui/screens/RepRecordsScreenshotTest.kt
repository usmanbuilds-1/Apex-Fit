package com.apexfit.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.*
import java.time.LocalDate

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US")
@Composable
fun ExactRepRecordsPreview() {
    ApexFitTheme {
        RepRecordsPanel(RepRecordProjection(listOf(
            RepRecordEvent(RepRecordKey(RecordedLoadBasis.TOTAL, "gym a barbell", 5),
                "s1", LocalDate.parse("2026-11-15"), 1L, 60000L, false),
            RepRecordEvent(RepRecordKey(RecordedLoadBasis.TOTAL, "gym a barbell", 8),
                "s2", LocalDate.parse("2026-11-16"), 2L, 55000L, true)
        )), "kg")
    }
}
