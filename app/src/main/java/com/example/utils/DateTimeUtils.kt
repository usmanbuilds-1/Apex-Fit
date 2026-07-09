package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun getCurrentDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

fun getPreviousDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: Date()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, -1)
        sdf.format(cal.time)
    } catch (e: Exception) {
        dateStr
    }
}

fun getDaysBetween(dateStr1: String, dateStr2: String): Long {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d1 = sdf.parse(dateStr1) ?: return 0L
        val d2 = sdf.parse(dateStr2) ?: return 0L
        val diff = d2.time - d1.time
        TimeUnit.MILLISECONDS.toDays(diff)
    } catch (e: Exception) {
        0L
    }
}

fun getDateDaysFromNow(dateStr: String, days: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: Date()
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, days)
        sdf.format(cal.time)
    } catch (e: Exception) {
        dateStr
    }
}

fun getDateDaysAgo(days: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun getWeekKey(dateStr: String): String {
    return try {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.minimalDaysInFirstWeek = 4
        cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
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
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val last = format.parse(lastDate)
        val current = format.parse(today)
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

