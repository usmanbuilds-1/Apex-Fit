package com.apexfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConvertersTest {

    @Test
    fun toStringList_validJson_returnsList() {
        assertEquals(listOf("Chest", "Triceps"), Converters().toStringList("[\"Chest\",\"Triceps\"]"))
    }

    @Test
    fun toStringList_nullInput_returnsEmpty() {
        assertTrue(Converters().toStringList(null).isEmpty())
    }

    @Test
    fun toStringList_malformedJson_returnsEmpty() {
        assertTrue(Converters().toStringList("not json").isEmpty())
    }
}
