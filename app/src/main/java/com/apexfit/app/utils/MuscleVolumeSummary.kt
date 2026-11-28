package com.apexfit.app.utils

import com.apexfit.app.data.RichTrainingSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class MuscleVolumePeriod {
    WEEK, MONTH;

    fun window(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday..monday.plusDays(6)
        }
        MONTH -> today.withDayOfMonth(1)..today.with(TemporalAdjusters.lastDayOfMonth())
    }
}

data class MuscleVolumeExercise(
    val name: String,
    val directSets: Int,
    val secondarySets: Int,
    val key: String = name
)

data class MuscleVolumeDay(val date: LocalDate, val directSets: Int, val secondarySets: Int) {
    // Population-level volume proxy, not a measurement of muscle stimulus.
    val fractionalSets: Double get() = directSets + 0.5 * secondarySets
}

data class MuscleVolumeRow(
    val muscle: String,
    val exercises: List<MuscleVolumeExercise>,
    val daily: List<MuscleVolumeDay> = emptyList()
) {
    val directSets: Int = exercises.sumOf { it.directSets }
    val secondarySets: Int = exercises.sumOf { it.secondarySets }
    val fractionalSets: Double get() = directSets + 0.5 * secondarySets
    val directTrainingDays: Int get() = daily.count { it.directSets > 0 }
    val exposureDays: Int get() = daily.count { it.directSets > 0 || it.secondarySets > 0 }
}

object MuscleVolumeSummary {
    private data class Counts(val key: String, val name: String, var directSets: Int = 0, var secondarySets: Int = 0)
    private data class DayCounts(var direct: Int = 0, var secondary: Int = 0)

    fun calculate(
        sessions: List<RichTrainingSession>,
        today: LocalDate,
        period: MuscleVolumePeriod
    ): List<MuscleVolumeRow> {
        return calculateWindow(sessions, period.window(today).start, today)
    }

    /** Explicit inclusive dates, canonical muscle and persisted working-set identity. */
    fun calculateWindow(
        sessions: List<RichTrainingSession>,
        start: LocalDate,
        end: LocalDate
    ): List<MuscleVolumeRow> {
        if (end < start) return emptyList()
        val totals = mutableMapOf<String, MutableMap<String, Counts>>()
        val daily = mutableMapOf<String, MutableMap<LocalDate, DayCounts>>()
        val seenIds = mutableSetOf<Long>()
        TrainingEvidence.sessions(sessions, start, end).forEach { session ->
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            if (date == null || date !in start..end) return@forEach
            for (exercise in session.exercises) {
                val count = exercise.sets.count { TrainingEvidence.isWorkingSet(it) && (it.id == 0L || seenIds.add(it.id)) }
                if (count == 0) continue

                val primary = MuscleAliases.getCanonical(exercise.muscleGroup)
                val secondary = exercise.secondaryMuscles.map(MuscleAliases::getCanonical).toSet() - primary
                val key = exercise.id.ifBlank { exercise.name }
                if (primary.isNotBlank()) {
                    totals.getOrPut(primary) { mutableMapOf() }
                        .getOrPut(key) { Counts(key, exercise.name) }.directSets += count
                    daily.getOrPut(primary) { mutableMapOf() }.getOrPut(date) { DayCounts() }.direct += count
                }
                for (muscle in secondary.filter(String::isNotBlank)) {
                    totals.getOrPut(muscle) { mutableMapOf() }
                        .getOrPut(key) { Counts(key, exercise.name) }.secondarySets += count
                    daily.getOrPut(muscle) { mutableMapOf() }.getOrPut(date) { DayCounts() }.secondary += count
                }
            }
        }
        return totals.map { (muscle, byExercise) ->
            MuscleVolumeRow(muscle, byExercise.values
                .map { MuscleVolumeExercise(it.name, it.directSets, it.secondarySets, it.key) }
                .sortedWith(compareByDescending<MuscleVolumeExercise> { it.directSets }.thenBy { it.name }),
                daily[muscle].orEmpty().entries.sortedBy { it.key }.map { (date, counts) ->
                    MuscleVolumeDay(date, counts.direct, counts.secondary)
                })
        }.sortedWith(compareByDescending<MuscleVolumeRow> { it.directSets }.thenBy { it.muscle })
    }
}
