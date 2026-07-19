package com.example.utils

object SessionMapper {
    fun buildRichSessions(
        sessions: List<com.example.data.TrainingSession>,
        sets: List<com.example.data.ExerciseSet>
    ): List<com.example.data.RichTrainingSession> {
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.map { sessionObj ->
            val sessionSets = setsBySession[sessionObj.id] ?: emptyList()
            val exercises = sessionSets
                .groupBy { it.exerciseId }
                .map { (exerciseId, exSets) ->
                    com.example.utils.ExerciseLog(
                        id = exerciseId,
                        name = exSets.first().exerciseName,
                        muscleGroup = exSets.first().muscleGroup,
                        sets = exSets.map { s ->
                            com.example.utils.ExerciseSet(
                                weight = s.weight,
                                reps = s.reps,
                                rpe = s.rpe,
                                isWarmup = s.isWarmup,
                                completed = s.completed
                            )
                        }
                    )
                }
            com.example.utils.TrainingSession(
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
