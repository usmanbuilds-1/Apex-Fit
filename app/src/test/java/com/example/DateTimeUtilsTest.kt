package com.example

import com.example.utils.getDaysBetweenClamped
import com.example.utils.getPreviousDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateTimeUtilsTest {

    @Test
    fun getPreviousDate_invalidInput_returnsNull() {
        assertNull(getPreviousDate("invalid"))
        assertNull(getPreviousDate(""))
    }

    @Test
    fun getPreviousDate_validInput_returnsPreviousDay() {
        assertEquals("2023-10-03", getPreviousDate("2023-10-04"))
    }

    @Test
    fun getDaysBetweenClamped_normalDates_returnsCorrectDiff() {
        assertEquals(3, getDaysBetweenClamped("2023-10-01", "2023-10-04"))
    }

    @Test
    fun getDaysBetweenClamped_negativeDiff_returnsTwo() {
        assertEquals(2, getDaysBetweenClamped("2023-10-04", "2023-10-01"))
    }

    @Test
    fun getDaysBetweenClamped_unparseableDate_returnsTwo() {
        assertEquals(2, getDaysBetweenClamped("invalid-date", "2023-10-04"))
        assertEquals(2, getDaysBetweenClamped("2023-10-01", "invalid-date"))
        assertEquals(2, getDaysBetweenClamped("", ""))
    }
}
