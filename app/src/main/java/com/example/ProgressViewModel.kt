@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.ui.models.*
import com.example.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(dao, dataStore)

    // Base Flows for processing
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

    private val richSessionsFlow: Flow<List<com.example.utils.TrainingSession>> = combine(
        sessionsFlow, setsFlow
    ) { sessions, sets ->
        withContext(Dispatchers.Default) {
            val setsBySession = sets.groupBy { it.sessionId }
            sessions.map { s ->
                val sSets = setsBySession[s.id.toString()] ?: emptyList()
                com.example.utils.TrainingSession(
                    date = s.date,
                    sessionType = s.sessionType,
                    completed = s.completed,
                    sessionFeel = s.sessionFeel,
                    durationMinutes = s.durationMinutes,
                    exercises = sSets.groupBy { it.exerciseId }.map { (exId, exSets) ->
                        com.example.utils.ExerciseLog(
                            id = exId,
                            name = exSets.first().exerciseName,
                            muscleGroup = exSets.first().muscleGroup,
                            sets = exSets.map { exSet ->
                                com.example.utils.ExerciseSet(
                                    weight = exSet.weight,
                                    reps = exSet.reps,
                                    rpe = exSet.rpe,
                                    isWarmup = exSet.isWarmup,
                                    completed = exSet.completed
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    // Weight and measurements history
    val allBodyMeasurements: StateFlow<List<UiBodyMeasurement>> = dao.getAllBodyMeasurementsFlow()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightHistory: StateFlow<List<UiWeightEntry>> = repository.getWeightHistory()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1. Trend Weight
    val trendWeight: StateFlow<Double?> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.getCurrentTrendWeight(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 2. Monthly muscle volumes for radar chart
    val monthlyMuscleVolumes = dao.getAllTrainingSessionsFlow().map { sessions ->
        val allSets = dao.getAllExerciseSets()
        val currentMonthPrefix = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        val thisMonthSessions = sessions.filter { it.date.startsWith(currentMonthPrefix) }
        val thisMonthSessionIds = thisMonthSessions.map { it.id }.toSet()
        val finishedSets = allSets.filter { it.completed && !it.isWarmup && it.sessionId in thisMonthSessionIds }
        
        val axes = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs")
        axes.associateWith { axis ->
            finishedSets.count { set ->
                if (axis == "Legs") {
                    set.muscleGroup.lowercase() in listOf("quads", "quadriceps", "hamstrings", "calves", "glutes")
                } else {
                    set.muscleGroup.lowercase().trim() == axis.lowercase().trim()
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 3. Muscle recovery status calculation
    val muscleRecoveryStatuses = dao.getAllTrainingSessionsFlow().map { sessions ->
        calculateMuscleRecovery(sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Fatigue Analysis
    val fatigueResult: StateFlow<UiFatigueResult> = richSessionsFlow
        .map { sessions ->
            val res = withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcFatigueToFitness(sessions)
            }
            res.toUi()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.example.utils.FatigueResult(
                ratio = null,
                status = "unknown",
                statusLabel = "No Data",
                recommendation = "Log workouts to activate fatigue tracking",
                acuteLoad = 0.0,
                chronicLoad = 0.0
            ).toUi()
        )

    val fatigueRatio: StateFlow<com.example.data.FatigueRatio> = fatigueResult
        .map { result ->
            com.example.data.FatigueRatio(
                ratio = result.ratio ?: 1.0,
                riskStatus = result.statusLabel
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.FatigueRatio(1.1, "Optimal"))

    // 5. Plateau Analysis
    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow
    ) { weights, nutrition ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.detectPlateau(weights, nutrition)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.data.PlateauResult(isPlateaued = false).toUi()
    )

    // Log & delete measurements
    fun logBodyMeasurement(bodyPart: String, value: Double, unit: String, date: String = getTodayDateString()) {
        viewModelScope.launch {
            dao.insertBodyMeasurement(
                BodyMeasurement(
                    bodyPart = bodyPart,
                    value = value,
                    unit = unit,
                    date = date
                )
            )
        }
    }

    fun deleteBodyMeasurement(id: Long) {
        viewModelScope.launch {
            dao.deleteBodyMeasurement(id)
        }
    }

    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun isMuscleMatch(group: String, muscle: String): Boolean {
        val g = group.lowercase().trim()
        val m = muscle.lowercase().trim()
        if (g == m) return true
        return when (m) {
            "shoulders" -> g in listOf("shoulders", "front delts", "side delts", "rear delts", "side_delt", "rear_delt")
            "back" -> g in listOf("back", "upper back", "lats", "trapezius")
            "quads" -> g in listOf("quads", "quadriceps")
            "abs" -> g in listOf("abs", "core", "obliques")
            "lower back" -> g in listOf("lower back", "lower_back")
            else -> false
        }
    }

    private suspend fun calculateMuscleRecovery(
        sessions: List<com.example.data.TrainingSession>
    ): List<MuscleRecoveryStatus> = withContext(Dispatchers.IO) {
        val allSets = dao.getAllExerciseSets()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        
        val muscles = listOf("Chest", "Shoulders", "Triceps", "Back", "Biceps", "Quads", "Hamstrings", "Glutes", "Calves", "Abs", "Lower Back")
        
        muscles.map { muscle ->
            val matchingSets = allSets.filter { set ->
                set.completed && !set.isWarmup && isMuscleMatch(set.muscleGroup, muscle)
            }
            
            if (matchingSets.isEmpty()) {
                MuscleRecoveryStatus(
                    muscleGroup = muscle,
                    recoveryPercentage = 100,
                    lastExercise = "No recent exercises",
                    lastTrainingDate = null,
                    requiredHours = 48
                )
            } else {
                val setsWithSessionsAndDates = matchingSets.mapNotNull { set ->
                    val session = sessions.find { it.id == set.sessionId } ?: return@mapNotNull null
                    set to session
                }.sortedByDescending { it.second.date }
                
                if (setsWithSessionsAndDates.isEmpty()) {
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        recoveryPercentage = 100,
                        lastExercise = "No recent exercises",
                        lastTrainingDate = null,
                        requiredHours = 48
                    )
                } else {
                    val (lastSet, lastSession) = setsWithSessionsAndDates.first()
                    val lastDateStr = lastSession.date
                    val lastDateObj = try { sdf.parse(lastDateStr) } catch(e: Exception) { null } ?: java.util.Date()
                    val todayDateObj = java.util.Date()
                    
                    val diffMs = todayDateObj.time - lastDateObj.time
                    val diffHoursRaw = diffMs / (1000 * 60 * 60)
                    val elapsedHours = maxOf(0, diffHoursRaw.toInt())
                    
                    val todayStr = sdf.format(todayDateObj)
                    val trainedToday = (lastDateStr == todayStr)
                    
                    val todaysSets = setsWithSessionsAndDates.filter { it.second.date == lastDateStr }
                    val totalSets = todaysSets.size
                    val highestRpe = todaysSets.maxOfOrNull { it.first.rpe } ?: 0
                    
                    val isHighIntensity = totalSets > 5 || highestRpe >= 8
                    val requiredHours = if (isHighIntensity) 72 else 48
                    
                    var pct = if (elapsedHours >= requiredHours) {
                        100
                    } else {
                        ((elapsedHours.toFloat() / requiredHours.toFloat()) * 100f).toInt()
                    }
                    pct = maxOf(0, minOf(100, pct))
                    
                    if (trainedToday && highestRpe >= 8) {
                        pct = 0
                    }
                    
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        recoveryPercentage = pct,
                        lastExercise = lastSet.exerciseName,
                        lastTrainingDate = lastDateStr,
                        requiredHours = requiredHours
                    )
                }
            }
        }
    }
}
