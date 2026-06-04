@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.data.WeightEntry
import com.example.ui.models.*
import com.example.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(dao, dataStore)

    // Raw database/preference flows
    private val weightFlow: Flow<List<com.example.utils.WeightEntry>> = dao.getAllWeightEntriesFlow()
        .map { list -> list.map { com.example.utils.WeightEntry(it.date, it.weight) } }
        .flowOn(Dispatchers.IO)

    private val nutritionFlow: Flow<List<com.example.utils.NutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { list ->
            list.map {
                com.example.utils.NutritionEntry(
                    date = it.date,
                    calories = it.calories,
                    protein = it.protein.toInt(),
                    carbs = it.carbs.toInt(),
                    fat = it.fat.toInt()
                )
            }
        }
        .flowOn(Dispatchers.IO)

    private val sessionsFlow: Flow<List<com.example.data.TrainingSession>> =
        dao.getAllCompletedSessionsFlow().flowOn(Dispatchers.IO)

    private val setsFlow: Flow<List<com.example.data.ExerciseSet>> =
        dao.getAllExerciseSetsFlow().flowOn(Dispatchers.IO)

    val targetsFlow: Flow<com.example.utils.NutritionTargets> = combine(
        dataStore.calorieTargetValueFlow,
        weightFlow,
        dataStore.goalFlow
    ) { calorieTarget, weights, goal ->
        val latestWeight = weights.lastOrNull()?.weight ?: 80.0
        val proteinTarget = (latestWeight * 1.8).toInt().coerceIn(100, 250)
        val fatTarget = (calorieTarget * 0.25 / 9.0).toInt().coerceIn(45, 120)
        val carbsTarget = ((calorieTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4).toInt().coerceIn(100, 500)
        
        com.example.utils.NutritionTargets(
            calories = calorieTarget,
            protein = proteinTarget,
            carbs = carbsTarget,
            fat = fatTarget,
            weeklyTrainingSessions = 4
        )
    }.flowOn(Dispatchers.IO)

    private val richSessionsFlow: Flow<List<com.example.utils.TrainingSession>> = combine(
        sessionsFlow, setsFlow
    ) { sessions, sets ->
        withContext(Dispatchers.Default) {
            buildRichSessions(sessions, sets)
        }
    }

    private fun buildRichSessions(
        sessions: List<com.example.data.TrainingSession>,
        sets: List<com.example.data.ExerciseSet>
    ): List<com.example.utils.TrainingSession> {
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.map { sessionObj ->
            val sessionSets = setsBySession[sessionObj.id.toString()] ?: emptyList()
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

    // Weight logging State
    val weightHistory: StateFlow<List<UiWeightEntry>> = repository.getWeightHistory()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNutritionHistory: StateFlow<List<UiNutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Readiness
    val sessionReadiness: StateFlow<UiSessionReadiness?> = combine(
        nutritionFlow,
        richSessionsFlow,
        weightFlow,
        targetsFlow
    ) { nutrition, sessions, weights, targets ->
        val res = withContext(Dispatchers.Default) {
            if (sessions.isEmpty()) null
            else {
                com.example.utils.SessionReadinessEngine.calcSessionReadiness(
                    nutritionLog = nutrition,
                    trainingLog = sessions,
                    weightLog = weights,
                    sleepLog = emptyList(),
                    targets = targets,
                    todaySessionType = "Science Hypertrophy"
                )
            }
        }
        res?.toUi()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Compliance
    val complianceScores: StateFlow<UiComplianceResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 88)

    // Home Analytics (Streaks)
    val streakResult: StateFlow<com.example.utils.StreakResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        withContext(Dispatchers.Default) {
            val validTargets = targets ?: com.example.utils.NutritionTargets(calories = 2650, protein = 160, carbs = 280, fat = 75, weeklyTrainingSessions = 4)
            com.example.utils.AlgorithmEngine.calcStreaks(nutrition, sessions, validTargets)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.utils.StreakResult(com.example.utils.StreakInfo(0), com.example.utils.StreakInfo(0))
    )

    fun logWeight(weight: Double, date: String = getTodayDateString()) {
        viewModelScope.launch {
            dao.insertWeightEntry(WeightEntry(date = date, time = getCurrentLocalTimeString(), weight = weight))
            dataStore.saveWeight(weight, dataStore.goalWeightFlow.first())
        }
    }

    fun deleteWeight(date: String) {
        viewModelScope.launch {
            dao.deleteWeightEntry(date)
        }
    }

    fun deleteWeightById(id: Long) {
        viewModelScope.launch {
            dao.deleteWeightEntryById(id)
        }
    }

    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun getCurrentLocalTimeString(): String {
        return java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
    }
}
