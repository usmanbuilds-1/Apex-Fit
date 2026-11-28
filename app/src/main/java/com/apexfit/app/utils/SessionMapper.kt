package com.apexfit.app.utils

object SessionMapper {
    fun buildRichSessions(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        sets: List<com.apexfit.app.data.ExerciseSet>,
        exerciseSecondaryMuscles: Map<String, List<String>> = emptyMap()   // AUDIT FIX (BUG-V4-013)
    ): List<com.apexfit.app.data.RichTrainingSession> {
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.map { sessionObj ->
            val sessionSets = setsBySession[sessionObj.id] ?: emptyList()
            val exercises = sessionSets
                .groupBy { Triple(it.exerciseId, MuscleAliases.getCanonical(it.muscleGroup),
                    MuscleAttribution.resolve(it.muscleGroup, it.secondaryMuscleSnapshot,
                        exerciseSecondaryMuscles[it.exerciseId])) }
                .map { (key, exSets) ->
                    val exerciseId = key.first
                    com.apexfit.app.utils.ExerciseLog(
                        id = exerciseId,
                        name = exSets.first().exerciseName,
                        muscleGroup = exSets.first().muscleGroup,
                        // AUDIT FIX (BUG-V4-013): attach structured secondary muscles
                        // so the fatigue/readiness engine uses real data not substring guesses.
                        secondaryMuscles = key.third.secondary,
                        // Preserve stable persisted row IDs, occurrence and recorded set metadata.
                        sets = exSets
                    )
                }
            com.apexfit.app.utils.TrainingSession(
                date = sessionObj.date,
                sessionType = sessionObj.sessionType,
                completed = sessionObj.completed,
                sessionFeel = sessionObj.sessionFeel,
                durationMinutes = sessionObj.durationMinutes,
                exercises = exercises,
                startedAt = sessionObj.startedAt,
                completedAt = sessionObj.completedAt
            )
        }
    }
}
