package com.apexfit.app.utils

import com.apexfit.app.data.WeightEntry
import com.apexfit.app.ui.models.UiWeightEntry
import java.time.LocalDate

/** Chronology is by the recorded local measurement date/time, then stable row ID. */
object BodyWeightHistory {
    val chronological = compareBy<WeightEntry> { it.date }.thenBy { it.time }.thenBy { it.id }
    val uiChronological = compareBy<UiWeightEntry> { it.date }.thenBy { it.time }.thenBy { it.id }

    fun valid(entry: WeightEntry): Boolean =
        entry.weight.isFinite() && entry.weight in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG &&
            runCatching { LocalDate.parse(entry.date) }.isSuccess

    fun latest(entries: List<WeightEntry>): WeightEntry? =
        entries.asSequence().filter(::valid).maxWithOrNull(chronological)

    /** One equally weighted daily point; never confuse it with the latest reading. */
    fun daily(entries: List<WeightEntry>): List<WeightEntry> =
        entries.filter(::valid).groupBy { it.date }.map { (date, values) ->
            WeightEntry(date = date, weight = values.map { it.weight }.average())
        }.sortedBy { it.date }

    fun dailyUi(entries: List<UiWeightEntry>): List<UiWeightEntry> =
        entries.filter { it.weight.isFinite() && it.weight in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG &&
            runCatching { LocalDate.parse(it.date) }.isSuccess }
            .groupBy { it.date }.map { (date, values) ->
                UiWeightEntry(date = date, weight = values.map { it.weight }.average())
            }.sortedBy { it.date }
}
