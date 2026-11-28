package com.apexfit.app.utils

import com.apexfit.app.data.NutritionEntry
import java.security.MessageDigest
import java.time.LocalDate

/** Confirmation applies to the exact saved meal rows, not to a date alone. */
object NutritionDayEvidence {
    fun snapshots(entries: List<NutritionEntry>): Map<String, String> =
        entries.groupBy { it.date }.mapNotNull { (date, rows) ->
            if (runCatching { LocalDate.parse(date) }.isFailure ||
                rows.any { it.calories < 0 || !it.protein.isFinite() || !it.carbs.isFinite() || !it.fat.isFinite() ||
                    it.protein < 0 || it.carbs < 0 || it.fat < 0 } ||
                rows.sumOf { it.calories.toLong() } <= 0L) return@mapNotNull null

            val records = rows.map { entry ->
                listOf(entry.id.toString(), entry.date, entry.name, entry.time, entry.calories.toString(),
                    entry.protein.toString(), entry.carbs.toString(), entry.fat.toString())
                    .joinToString("") { "${it.length}:$it" }
            }.sorted().joinToString("") { "${it.length}:$it" }
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(records.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            date to fingerprint
        }.toMap()
}
