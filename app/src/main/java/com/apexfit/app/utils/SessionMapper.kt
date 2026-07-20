package com.apexfit.app.utils

object SessionMapper {
    fun buildRichSessions(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        sets: List<com.apexfit.app.data.ExerciseSet>
    ): List<com.apexfit.app.data.RichTrainingSession> {
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.map { sessionObj ->
            val sessionSets = setsBySession[sessionObj.id] ?: emptyList()
            val exercises = sessionSets
                .groupBy { it.exerciseId }
                .map { (exerciseId, exSets) ->
                    com.apexfit.app.utils.ExerciseLog(
                        id = exerciseId,
                        name = exSets.first().exerciseName,
                        muscleGroup = exSets.first().muscleGroup,
                        sets = exSets.map { s ->
                            com.apexfit.app.utils.ExerciseSet(
                                weight = s.weight,
                                reps = s.reps,
                                rpe = s.rpe,
                                isWarmup = s.isWarmup,
                                completed = s.completed
                            )
                        }
                    )
                }
            com.apexfit.app.utils.TrainingSession(
                date = sessionObj.date,
                sessionType = sessionObj.sessionType,
                completed = sessionObj.completed,
                sessionFeel = sessionObj.sessionFeel,
                durationMinutes = sessionObj.durationMinutes,
                exercises = exercises
            )
        }
    }
}
