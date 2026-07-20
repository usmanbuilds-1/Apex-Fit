package com.apexfit.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeUtils {
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatDate(date: Date): String = synchronized(DATE_FORMAT) { DATE_FORMAT.format(date) }
    fun parseDate(dateStr: String): Date? = try {
        synchronized(DATE_FORMAT) { DATE_FORMAT.parse(dateStr) }
    } catch (e: Exception) {
        null
    }
    fun todayDateString(): String = synchronized(DATE_FORMAT) { DATE_FORMAT.format(Date()) }
}

fun getCurrentDate(): String {
    return DateTimeUtils.todayDateString()
}

fun getPreviousDate(dateStr: String): String? {
    return try {
        val date = DateTimeUtils.parseDate(dateStr) ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -1)
        DateTimeUtils.formatDate(cal.time)
    } catch (e: Exception) {
        null
    }
}

fun getDaysBetween(dateStr1: String, dateStr2: String): Long {
    return try {
        val d1 = DateTimeUtils.parseDate(dateStr1) ?: return 0L
        val d2 = DateTimeUtils.parseDate(dateStr2) ?: return 0L
        val diff = d2.time - d1.time
        Math.round(diff.toDouble() / (1000 * 60 * 60 * 24)).coerceAtLeast(0L)
    } catch (e: Exception) {
        0L
    }
}

fun getDateDaysFromNow(dateStr: String, days: Int): String? {
    return try {
        val date = DateTimeUtils.parseDate(dateStr) ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, days)
        DateTimeUtils.formatDate(cal.time)
    } catch (e: Exception) {
        null
    }
}

fun getDateDaysAgo(days: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days)
    return DateTimeUtils.formatDate(cal.time)
}

fun getWeekKey(dateStr: String): String {
    return try {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        cal.time = DateTimeUtils.parseDate(dateStr) ?: return dateStr
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        DateTimeUtils.formatDate(cal.time)
    } catch (e: Exception) { 
        dateStr 
    }
}

/**
 * Calculates the number of days between two dates, specifically designed for progression calculations.
 * Returns 2 on parsing error or null dates, and clamps any negative day differences to 2.
 */
fun getDaysBetweenClamped(lastDate: String, today: String): Int {
    return try {
        val last = DateTimeUtils.parseDate(lastDate)
        val current = DateTimeUtils.parseDate(today)
        if (last != null && current != null) {
            val diffInMillis = current.time - last.time
            val days = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
            if (days < 0) 2 else days
        } else {
            2
        }
    } catch (e: Exception) {
        2
    }
}

fun formatTimeForDisplay(context: android.content.Context, timeStr: String): String {
    if (timeStr.isEmpty()) return ""
    return try {
        val parser = SimpleDateFormat("HH:mm", Locale.US)
        val date = parser.parse(timeStr)
        if (date != null) {
            android.text.format.DateFormat.getTimeFormat(context).format(date)
        } else {
            timeStr
        }
    } catch (e: Exception) {
        timeStr
    }
}

