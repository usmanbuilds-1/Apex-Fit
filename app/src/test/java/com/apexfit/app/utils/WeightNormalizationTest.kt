package com.apexfit.app.utils

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeightNormalizationTest {

    @Test
    fun lbsToKg_176lbs_isAbout80kg() {
        val kg = 176.0.fromDisplayWeightToKg("lbs")
        assertTrue("Expected ~79.8 kg, got $kg", kg in 79.5..80.1)
    }

    @Test
    fun kgToDisplay_kgMode_returnsOriginal() {
        assertEquals(80.0, 80.0.toDisplayWeight("kg"), 0.01)
    }

    @Test
    fun kgToDisplay_lbsMode_converts() {
        val lbs = 80.0.toDisplayWeight("lbs")
        assertTrue("Expected ~176.4, got $lbs", lbs in 176.0..176.9)
    }

    @Test
    fun proteinTarget_180lbsUser_isNotClamped() {
        val storedKg = 176.0 / 2.20462
        val protein = (storedKg * 1.8).toInt().coerceIn(100, 250)
        assertTrue("Expected ~143-148g, got ${protein}g", protein in 140..155)
    }

    @Test
    fun proteinTarget_80kgUser_is144g() {
        val protein = (80.0 * 1.8).toInt().coerceIn(100, 250)
        assertEquals(144, protein)
    }
}
