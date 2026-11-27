package com.apexfit.app.ui.models

import com.apexfit.app.utils.MuscleReadinessDetail
import com.apexfit.app.utils.ReadinessScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadinessUiMappingTest {
    @Test
    fun readinessFactorsUseQualitativeEstimatesAndExplainHistoryCoverage() {
        val ui = ReadinessScore(
            overallPercent = 82,
            label = "Good",
            colorHex = "#7192D3",
            recommendation = "Consider sleep and soreness.",
            muscleDetails = listOf(MuscleReadinessDetail("Chest", 75, 4, "full")),
            systemicReadiness = 76,
            nutritionScore = 63,
            sleepScore = null,
            acrModifier = 1.0,
            dataConfidence = "full"
        ).toUi()

        assertEquals("Higher estimate", ui.factors[0].value)
        assertEquals("Not measured by this estimate", ui.factors[1].value)
        assertEquals("neutral", ui.factors[1].impact)
        assertEquals("Recent muscle history is available", ui.confidence)
        assertFalse(ui.factors.any { it.value.endsWith("%") })
    }

    @Test
    fun limitedReadinessHistoryDoesNotClaimCalibratedConfidence() {
        val ui = ReadinessScore(
            overallPercent = 100,
            label = "Building Baseline",
            colorHex = "#7192D3",
            recommendation = "More recent training history is needed.",
            muscleDetails = emptyList(),
            systemicReadiness = 100,
            nutritionScore = 100,
            sleepScore = null,
            acrModifier = 1.0,
            dataConfidence = "limited - building baseline",
            finalScore = null
        ).toUi()

        assertEquals("Some muscles need more training history", ui.confidence)
        assertEquals("Needs more training history", ui.factors.first().value)
    }
}
