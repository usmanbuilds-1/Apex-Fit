package com.apexfit.app.utils

import com.apexfit.app.data.AnalyticsWorkingSet
import com.apexfit.app.data.ExerciseLog
import com.apexfit.app.data.RichTrainingSession

/**
 * Observed saved work only. Prefer occurrence-time secondary snapshots; legacy
 * rows may use current-library inference. Never merge different attributions.
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
                    Triple(it.set.exerciseId, MuscleAliases.getCanonical(it.set.muscleGroup),
                        MuscleAttribution.resolve(it.set.muscleGroup, it.set.secondaryMuscleSnapshot,
                            it.analyticsSecondaryMuscles.takeIf { _ -> it.analyticsCatalogPresent }))
                }.values.map { exerciseRows ->
                    val first = exerciseRows.first()
                    ExerciseLog(
                        id = first.set.exerciseId,
                        name = first.set.exerciseName,
                        muscleGroup = first.set.muscleGroup,
                        sets = exerciseRows.map { it.set },
                        secondaryMuscles = MuscleAttribution.resolve(first.set.muscleGroup,
                            first.set.secondaryMuscleSnapshot, first.analyticsSecondaryMuscles.takeIf { first.analyticsCatalogPresent }).secondary
                    )
                }
            )
        }
}
