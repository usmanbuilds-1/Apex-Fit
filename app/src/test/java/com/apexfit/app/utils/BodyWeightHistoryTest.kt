package com.apexfit.app.utils

import com.apexfit.app.data.WeightEntry
import com.apexfit.app.ui.models.UiWeightEntry
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BodyWeightHistoryTest {
    private fun weight(id: Long, date: String, time: String, kg: Double) =
        WeightEntry(id = id, date = date, time = time, weight = kg)

    @Test
    fun sameTimestampUsesStableIdAndBackfillDoesNotWin() {
        val rows = listOf(
            weight(9, "2026-11-23", "23:59:59.999", 55.0),
            weight(2, "2026-11-24", "10:00", 61.0),
            weight(3, "2026-11-24", "10:00", 62.0),
            weight(4, "2026-11-24", "09:59", 60.0)
        )
        assertEquals(3L, BodyWeightHistory.latest(rows)?.id)
        assertEquals(3L, BodyWeightHistory.latest(rows.reversed())?.id)
        val precise = weight(5, "2026-11-24", "10:00:00.123", 63.0)
        assertEquals(5L, BodyWeightHistory.latest(rows + precise)?.id)
    }

    @Test
    fun multipleMeasurementsYieldOneDailyAverageAndOneTrendPoint() {
        val rows = listOf(weight(1, "2026-11-23", "07:00", 60.0),
            weight(2, "2026-11-24", "07:00", 61.0),
            weight(3, "2026-11-24", "08:00", 63.0))
        assertEquals(2, BodyWeightHistory.daily(rows).size)
        assertEquals(62.0, BodyWeightHistory.daily(rows).last().weight, 1e-9)
        assertEquals(63.0, BodyWeightHistory.latest(rows)!!.weight, 1e-9)
        val ui = rows.map { UiWeightEntry(id = it.id, weight = it.weight, date = it.date, time = it.time) }
        assertEquals(62.0, BodyWeightHistory.dailyUi(ui).last().weight, 1e-9)
        val trend = AlgorithmEngine.calcTrendWeight(rows)
        assertEquals(2, trend.size)
        assertEquals(62.0, trend.last().raw, 1e-9)
        assertEquals(60.27, trend.last().trend, 1e-9)
    }

    @Test
    fun invalidRowsAreNotUsedInDerivedMetrics() {
        val rows = listOf(weight(1, "2026-11-24", "07:00", 61.0),
            weight(2, "2026-11-24", "09:00", Double.NaN),
            weight(3, "2026-11-24", "10:00", -10.0),
            weight(4, "not-a-date", "12:00", 80.0))
        assertEquals(1L, BodyWeightHistory.latest(rows)?.id)
        assertEquals(61.0, BodyWeightHistory.daily(rows).single().weight, 1e-9)
        assertTrue(BodyWeightHistory.daily(emptyList()).isEmpty())
    }

    @Test
    fun randomizedOrderingEditsAndDeletesRespectInvariants() {
        val rng = Random(20261124)
        repeat(1000) {
            val rows = (1..rng.nextInt(2, 60)).map { id ->
                weight(id.toLong(), "2026-11-" + rng.nextInt(1, 25).toString().padStart(2, '0'),
                    listOf("07:00", "07:00", "07:00:00.001", "20:30")[rng.nextInt(4)],
                    rng.nextInt(2000, 30001) / 100.0)
            }
            val latest = rows.maxWithOrNull(compareBy<WeightEntry> { it.date }.thenBy { it.time }.thenBy { it.id })!!
            assertEquals(latest.id, BodyWeightHistory.latest(rows.shuffled(rng))?.id)
            val daily = BodyWeightHistory.daily(rows)
            assertEquals(rows.map { it.date }.distinct().size, daily.size)
            daily.forEach { day ->
                assertEquals(rows.filter { it.date == day.date }.map { it.weight }.average(), day.weight, 1e-9)
            }
            val edited = rows.map { if (it.id == latest.id) it.copy(weight = 65.0) else it }
            assertEquals(rows.size, edited.size)
            assertEquals(65.0, BodyWeightHistory.latest(edited)!!.weight, 1e-9)
            val deleted = edited.filterNot { it.id == latest.id }
            assertEquals(rows.size - 1, deleted.size)
            assertNotEquals(latest.id, BodyWeightHistory.latest(deleted)?.id)
        }
    }
}
