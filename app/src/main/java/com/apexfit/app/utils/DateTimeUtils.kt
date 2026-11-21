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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

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

    fun dateStringForEpochMillis(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    /**
     * Emits the device-local calendar date and re-checks periodically so long-lived
     * app processes roll over cleanly at midnight and after timezone/date changes.
     */
    fun todayDateFlow(pollIntervalMillis: Long = 60_000L): Flow<String> =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(todayDateString())
                delay(pollIntervalMillis.coerceAtLeast(1_000L))
            }
        }.distinctUntilChanged()

    fun formatDateForDisplay(isoDate: String): String =
        runCatching { formatDateForDisplay(LocalDate.parse(isoDate)) }.getOrDefault(isoDate)

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

/**
 * Inclusive start date for a rolling window containing [days] calendar dates,
 * including today. Example: a 7-day window starts 6 days ago.
 *
 * Prefer this helper for "last N days" product logic. getDateDaysAgo(N) means
 * exactly N days before today and therefore spans N + 1 dates when used with >=.
 */
fun getWindowStartDate(days: Int): String {
    val safeDays = days.coerceAtLeast(1)
    return LocalDate.now().minusDays((safeDays - 1).toLong()).toString()
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

