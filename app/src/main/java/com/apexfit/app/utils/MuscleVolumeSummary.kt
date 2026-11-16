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
    val secondarySets: Int
)

data class MuscleVolumeRow(val muscle: String, val exercises: List<MuscleVolumeExercise>) {
    val directSets: Int = exercises.sumOf { it.directSets }
    val secondarySets: Int = exercises.sumOf { it.secondarySets }
}

object MuscleVolumeSummary {
    private data class Counts(val name: String, var directSets: Int = 0, var secondarySets: Int = 0)

    fun calculate(
        sessions: List<RichTrainingSession>,
        today: LocalDate,
        period: MuscleVolumePeriod
    ): List<MuscleVolumeRow> {
        val window = period.window(today)
        val totals = mutableMapOf<String, MutableMap<String, Counts>>()
        sessions.asSequence().filter { it.completed }.forEach { session ->
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            if (date == null || date !in window || date.isAfter(today)) return@forEach
            for (exercise in session.exercises) {
                val count = exercise.sets.count { it.completed && !it.isWarmup && it.reps > 0 }
                if (count == 0) continue

                val primary = MuscleAliases.getCanonical(exercise.muscleGroup)
                val secondary = exercise.secondaryMuscles.map(MuscleAliases::getCanonical).toSet() - primary
                val key = exercise.id.ifBlank { exercise.name }
                if (primary.isNotBlank()) {
                    totals.getOrPut(primary) { mutableMapOf() }
                        .getOrPut(key) { Counts(exercise.name) }.directSets += count
                }
                for (muscle in secondary.filter(String::isNotBlank)) {
                    totals.getOrPut(muscle) { mutableMapOf() }
                        .getOrPut(key) { Counts(exercise.name) }.secondarySets += count
                }
            }
        }
        return totals.map { (muscle, byExercise) ->
            MuscleVolumeRow(muscle, byExercise.values
                .map { MuscleVolumeExercise(it.name, it.directSets, it.secondarySets) }
                .sortedWith(compareByDescending<MuscleVolumeExercise> { it.directSets }.thenBy { it.name }))
        }.sortedWith(compareByDescending<MuscleVolumeRow> { it.directSets }.thenBy { it.muscle })
    }
}
