package com.example

import com.example.utils.getDaysBetweenClamped
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeUtilsTest {

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
