package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.RichTrainingSession

/**
 * Observed saved work only. Secondary attribution is from the current library;
 * older records are not represented as immutable historical attribution.
 */
object AnalyticsHistory {
    fun sessions(rows: List<AnalyticsWorkingSet>): List<RichTrainingSession> =
        rows.groupBy { it.set.sessionId }.values.map { sessionRows ->
            val session = sessionRows.first()
            RichTrainingSession(
                date = session.analyticsDate,
                sessionType = session.analyticsSessionType,
                completed = session.analyticsSessionCompleted,
                savedPartial = session.analyticsStatus == "partial",
                startedAt = session.analyticsStartedAt,
                completedAt = session.analyticsCompletedAt,
                exercises = sessionRows.groupBy {
                    it.set.exerciseId to MuscleAliases.getCanonical(it.set.muscleGroup)
                }.values.map { exerciseRows ->
                    val first = exerciseRows.first()
                    ExerciseLog(
                        id = first.set.exerciseId,
                        name = first.set.exerciseName,
                        muscleGroup = first.set.muscleGroup,
                        sets = exerciseRows.map { it.set },
                        secondaryMuscles = first.analyticsSecondaryMuscles
                    )
                }
            )
        }
}
