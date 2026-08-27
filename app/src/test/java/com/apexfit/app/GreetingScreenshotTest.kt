package com.apexfit.app

import com.apexfit.app.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSmokeTest {
    @Test
    fun weightEntry_defaultsToKg() {
        val entry = WeightEntry(date = "2024-01-01", time = "08:00", weight = 80.0)
        assertEquals("kg", entry.unit)
    }
    
    @Test
    fun weightEntry_storesProvidedWeight() {
        val entry = WeightEntry(date = "2024-01-01", time = "08:00", weight = 80.0, unit = "kg")
        assertEquals(80.0, entry.weight, 0.001)
    }
}
