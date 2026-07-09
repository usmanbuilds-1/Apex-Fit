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
    private val repository: FitnessRepository = FitnessRepositoryImpl(db, dao, dataStore)

    val allBodyMeasurements: StateFlow<UiState<List<UiBodyMeasurement>>> = dao.getAllBodyMeasurementsFlow()
        .map { entries -> UiState.Success(entries.map { it.toUi() }) as UiState<List<UiBodyMeasurement>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kg")

    fun measurementsForBodyPart(bodyPart: String): Flow<List<UiBodyMeasurement>> {
        return dao.getBodyMeasurementsForPartFlow(bodyPart)
            .map { entries -> entries.map { it.toUi() } }
    }

    // 2. Monthly muscle volumes for radar chart
    val monthlyMuscleVolumes: StateFlow<UiState<Map<String, Int>>> = dao.getAllTrainingSessionsFlow().map { sessions ->
        val allSets = dao.getAllExerciseSets()
        val currentMonthPrefix = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        val thisMonthSessions = sessions.filter { it.date.startsWith(currentMonthPrefix) }
        val thisMonthSessionIds = thisMonthSessions.map { it.id }.toSet()
        val finishedSets = allSets.filter { it.completed && !it.isWarmup && it.sessionId in thisMonthSessionIds }
        
        val axes = com.example.utils.MuscleGroups.ALL
        val result = axes.associateWith { axis ->
            finishedSets.count { set ->
                set.muscleGroup.lowercase().trim() == axis.lowercase().trim()
            }
        }
        UiState.Success(result) as UiState<Map<String, Int>>
    }
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // 3. Muscle recovery status calculation
    val muscleRecoveryStatuses: StateFlow<UiState<List<MuscleRecoveryStatus>>> = dao.getAllTrainingSessionsFlow().map { sessions ->
        UiState.Success(calculateMuscleRecovery(sessions)) as UiState<List<MuscleRecoveryStatus>>
    }
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // Log & delete measurements
    fun logMeasurement(bodyPart: String, value: Double) {
        val unit = "cm" // Or kg? The UI implies it's length.
        logBodyMeasurement(bodyPart, value, unit)
    }

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
            "core" -> g in listOf("abs", "core", "obliques")
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
        
        val muscles = com.example.utils.MuscleGroups.ALL
        
        muscles.map { muscle ->
            val matchingSets = allSets.filter { set ->
                set.completed && !set.isWarmup && isMuscleMatch(set.muscleGroup, muscle)
            }
            
            if (matchingSets.isEmpty()) {
                MuscleRecoveryStatus(
                    muscleGroup = muscle,
                    hoursRemaining = 0,
                    recoveryFraction = 1.0f,
                    lastTrainedDate = ""
                )
            } else {
                val setsWithSessionsAndDates = matchingSets.mapNotNull { set ->
                    val session = sessions.find { it.id == set.sessionId } ?: return@mapNotNull null
                    set to session
                }.sortedByDescending { it.second.date }
                
                if (setsWithSessionsAndDates.isEmpty()) {
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        hoursRemaining = 0,
                        recoveryFraction = 1.0f,
                        lastTrainedDate = ""
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
                        hoursRemaining = maxOf(0, requiredHours - elapsedHours),
                        recoveryFraction = pct / 100f,
                        lastTrainedDate = lastDateStr
                    )
                }
            }
        }
    }
}
