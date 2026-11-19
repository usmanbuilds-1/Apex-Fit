package com.apexfit.app.utils

import androidx.annotation.Keep
import com.apexfit.app.data.ActiveSet
import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate

@Keep
data class SetVolumeAdjustment(val baseSets: Int, val adjustedSets: Int, val reason: String) {
    fun revert(sets: List<ActiveSet>): List<ActiveSet>? {
        if (sets.size != adjustedSets) return null
        if (baseSets < adjustedSets) {
            // Keep any completed original work. Undo is safe while the added
            // set itself has not started; once it has, removing it would erase work.
            if (sets.drop(baseSets).any { it.completed || it.isWarmup || it.restTakenSeconds > 0 }) return null
            return sets.take(baseSets)
        }
        if (baseSets == adjustedSets) return sets
        val template = sets.lastOrNull() ?: return null
        return sets + (adjustedSets + 1..baseSets).map { number ->
            template.copy(
                setNumber = number,
                rpe = 0,
                isWarmup = false,
                restTakenSeconds = 0,
                completedAt = 0L,
                completed = false,
                repsInReserve = 2
            )
        }
    }
}

object AdaptiveSetVolume {
    fun adjustment(
        goal: String,
        exerciseId: String,
        baseSets: Int,
        repsMin: Int,
        sessions: List<RichTrainingSession>,
        today: LocalDate,
        readiness: Int?,
        loadChanged: Boolean
    ): SetVolumeAdjustment? {
        if (goal != "Gain Muscle" || baseSets !in 1..5 || loadChanged || readiness == null) return null
        val exposures = sessions.asSequence()
            .filter(RichTrainingSession::completed)
            .mapNotNull { session ->
                val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
                if (date == null || date.isAfter(today) || date.isBefore(today.minusDays(42))) {
                    return@mapNotNull null
                }
                val sets = session.exercises.filter { it.id == exerciseId }.flatMap { it.sets }
                    .filter { it.completed && !it.isWarmup && it.reps > 0 && it.rpe in 1..10 }
                if (sets.size < baseSets) null else Triple(date, session.completedAt ?: 0, sets.take(baseSets))
            }
            .sortedWith(compareByDescending<Triple<LocalDate, Long, List<com.apexfit.app.data.ExerciseSet>>> { it.first }
                .thenByDescending { it.second })
            .distinctBy { it.first }.take(3).map { it.third }.toList()

        if (exposures.size < 3) return null
        val highEffortExposures = exposures.take(2).count { sets -> sets.count { it.rpe >= 9 } * 2 >= baseSets }
        if (readiness < 55 && highEffortExposures == 2 && baseSets > 1) {
            return SetVolumeAdjustment(baseSets, baseSets - 1,
                "Two recent workouts on different days included repeated high-effort sets, and logged recovery signals suggest reducing work today.")
        }
        if (readiness >= 70 && baseSets < 5 &&
            exposures.all { sets -> sets.all { it.reps >= repsMin && it.rpe <= 8 } }
        ) {
            return SetVolumeAdjustment(baseSets, baseSets + 1,
                "Three workouts on different days met the rep minimum with effort at RPE 8 or lower. The proposed load stays the same.")
        }
        return null
    }
}
