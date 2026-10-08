package com.apexfit.app.utils

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    fun formatDate(date: Date): String =
        Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun parseDate(dateStr: String): Date? = try {
        val localDate = LocalDate.parse(dateStr)
        Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    } catch (e: Exception) {
        null
    }

    fun todayDateString(): String = LocalDate.now().toString()

    fun formatDateForDisplay(localDate: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(localDate)
}

fun getTodayDateString(): String {
    return DateTimeUtils.todayDateString()
}

fun getCurrentDate(): String {
    return DateTimeUtils.todayDateString()
}

fun getPreviousDate(dateStr: String): String? {
    return try {
        LocalDate.parse(dateStr).minusDays(1).toString()
    } catch (e: Exception) {
        null
    }
}

fun getDaysBetween(dateStr1: String, dateStr2: String): Long =
    ChronoUnit.DAYS.between(LocalDate.parse(dateStr1), LocalDate.parse(dateStr2))
// Signed: negative if dateStr2 is before dateStr1

fun getDaysBetweenAbs(dateStr1: String, dateStr2: String): Long =
    kotlin.math.abs(getDaysBetween(dateStr1, dateStr2))

fun getDateDaysFromNow(dateStr: String, days: Int): String? {
    return try {
        LocalDate.parse(dateStr).plusDays(days.toLong()).toString()
    } catch (e: Exception) {
        null
    }
}

fun getDateDaysAgo(days: Int): String {
    return LocalDate.now().minusDays(days.toLong()).toString()
}

fun getWeekKey(dateStr: String): String {
    return try {
        LocalDate.parse(dateStr).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
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
        val days = getDaysBetween(lastDate, today).toInt()
        if (days < 0) 2 else days
    } catch (e: Exception) {
        2
    }
}

fun formatTimeForDisplay(context: android.content.Context, timeStr: String): String {
    if (timeStr.isEmpty()) return ""
    return try {
        val localTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        val date = Date.from(localTime.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant())
        android.text.format.DateFormat.getTimeFormat(context).format(date)
    } catch (e: Exception) {
        timeStr
    }
}

