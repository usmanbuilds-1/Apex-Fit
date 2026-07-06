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
    private val repository: FitnessRepository = FitnessRepositoryImpl(db, dao, dataStore)

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
        val latestWeight = weights.lastOrNull()?.weight ?: com.example.UserDefaults.WEIGHT_KG
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

    val richSessionsFlow: Flow<List<com.example.utils.TrainingSession>> = combine(
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

    val currentWeight: Flow<Double> = dataStore.currentWeightFlow
    val calorieTargetFlow: Flow<Int> = dataStore.calorieTargetValueFlow

    val loggedCalories: StateFlow<Int> = allNutritionHistory.map { meals ->
        meals.sumOf { it.calories }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedProtein: StateFlow<Int> = allNutritionHistory.map { meals ->
        meals.sumOf { it.protein }.toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedCarbs: StateFlow<Int> = allNutritionHistory.map { meals ->
        meals.sumOf { it.carbs }.toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loggedFat: StateFlow<Int> = allNutritionHistory.map { meals ->
        meals.sumOf { it.fat }.toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val macroTargets: StateFlow<com.example.utils.NutritionTargets> = combine(
        currentWeight,
        calorieTargetFlow
    ) { weight, calorieTarget ->
        val proteinTarget = (weight * 1.8).toInt().coerceIn(100, 250)
        val fatTarget = (calorieTarget * 0.25 / 9.0).toInt().coerceIn(45, 120)
        val carbsTarget = ((calorieTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4).toInt().coerceIn(100, 500)
        com.example.utils.NutritionTargets(
            calories = calorieTarget,
            protein = proteinTarget,
            carbs = carbsTarget,
            fat = fatTarget,
            weeklyTrainingSessions = 4
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.utils.NutritionTargets(calories = com.example.UserDefaults.CALORIES, protein = com.example.UserDefaults.PROTEIN_G, fat = 70, carbs = 300))

    val todayExercisesFlow: Flow<List<com.example.data.PlanExercise>> = repository.getActivePlan().flatMapLatest { plan ->
        val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
        sessions.flatMapLatest { sessionList ->
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // Readiness
    val sessionReadiness: StateFlow<UiSessionReadiness?> = combine(
        nutritionFlow,
        richSessionsFlow,
        targetsFlow,
        todayExercisesFlow
    ) { nutrition, sessions, targets, todayExercises ->
        val res = withContext(Dispatchers.Default) {
            if (sessions.isEmpty()) null
            else {
                val scoreResult = com.example.utils.ReadinessFinal.buildReadinessInputs(
                    completedSessions = sessions,
                    todayExercises = todayExercises,
                    nutritionLog = nutrition,
                    targets = targets
                )
                
                val predictionText = "Systemic CNS readiness is ${scoreResult.systemicReadiness}%. " +
                        "Acute-to-chronic ratio modifier is ${String.format("%.2f", scoreResult.acrModifier)}."

                val factorList = mutableListOf<com.example.ui.models.UiReadinessFactor>()
                factorList.add(com.example.ui.models.UiReadinessFactor(
                    name = "Systemic CNS",
                    impact = if (scoreResult.systemicReadiness >= 70) "positive" else if (scoreResult.systemicReadiness >= 50) "neutral" else "negative",
                    value = "${scoreResult.systemicReadiness}%"
                ))
                factorList.add(com.example.ui.models.UiReadinessFactor(
                    name = "Nutrition",
                    impact = if (scoreResult.nutritionScore >= 70) "positive" else if (scoreResult.nutritionScore >= 50) "neutral" else "negative",
                    value = "${scoreResult.nutritionScore}%"
                ))
                scoreResult.muscleDetails.forEach { md ->
                    factorList.add(com.example.ui.models.UiReadinessFactor(
                        name = "${md.muscleGroup.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }} Recovery",
                        impact = if (md.readinessPercent >= 70) "positive" else if (md.readinessPercent >= 50) "neutral" else "negative",
                        value = "${md.readinessPercent}% (${md.confidence})"
                    ))
                }

                com.example.ui.models.UiSessionReadiness(
                    score = scoreResult.overallPercent,
                    label = scoreResult.label,
                    colorHex = scoreResult.colorHex,
                    prediction = predictionText,
                    recommendation = scoreResult.recommendation,
                    factors = factorList
                )
            }
        }
        res
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
