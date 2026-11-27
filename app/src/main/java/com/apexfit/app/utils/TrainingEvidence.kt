package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.RichTrainingSession
import java.time.LocalDate

/** Shared eligibility for logged working-set analytics, not physiological stimulus. */
object TrainingEvidence {
    fun isWorkingSet(set: ExerciseSet): Boolean =
        set.completed && !set.isWarmup && set.reps > 0 && set.weight.isFinite() && set.weight >= 0.0

    fun sessions(
        sessions: List<RichTrainingSession>,
        start: LocalDate,
        end: LocalDate
    ): List<RichTrainingSession> = sessions.filter { session ->
        val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
        session.completed && date != null && date in start..end &&
            (session.completedAt ?: session.startedAt ?: 0L) <= System.currentTimeMillis()
    }
}
