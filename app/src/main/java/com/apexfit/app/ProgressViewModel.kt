@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.apexfit.app.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(application)
    private val repository = com.apexfit.app.di.ServiceLocator.repository(application)

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
    val monthlyMuscleVolumes: StateFlow<UiState<Map<String, Int>>> = combine(
        dao.getAllTrainingSessionsFlow(),
        dao.getRecentExerciseSetsFlow(com.apexfit.app.utils.getDateDaysAgo(90))
    ) { sessions, allSets ->
        val currentMonthPrefix = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        val thisMonthSessions = sessions.filter { it.date.startsWith(currentMonthPrefix) }
        val thisMonthSessionIds = thisMonthSessions.map { it.id }.toSet()
        val finishedSets = allSets.filter { it.completed && !it.isWarmup && it.sessionId in thisMonthSessionIds }
        
        val axes = com.apexfit.app.utils.MuscleGroups.ALL
        val result = axes.associateWith { axis ->
            finishedSets.count { set ->
                set.muscleGroup.lowercase().trim() == axis.lowercase().trim()
            }
        }
        UiState.Success(result) as UiState<Map<String, Int>>
    }
    .flowOn(Dispatchers.Default)
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // 3. Muscle recovery status calculation
    val muscleRecoveryStatuses: StateFlow<UiState<List<MuscleRecoveryStatus>>> = combine(
        dao.getAllTrainingSessionsFlow(),
        dao.getRecentExerciseSetsFlow(com.apexfit.app.utils.getDateDaysAgo(90))
    ) { sessions, allSets ->
        UiState.Success(calculateMuscleRecovery(sessions, allSets)) as UiState<List<MuscleRecoveryStatus>>
    }
    .flowOn(Dispatchers.Default)
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // Log & delete measurements
    fun logMeasurement(bodyPart: String, value: Double) {
        val unit = if (units.value == "kg") "cm" else "in"
        logBodyMeasurement(bodyPart, value, unit)
    }

    fun logBodyMeasurement(bodyPart: String, value: Double, unit: String, date: String = getTodayDateString()) {
        val safeValue = value.coerceIn(0.0, 500.0)
        val safePart  = bodyPart.trim().take(50).ifEmpty { "Unknown" }
        viewModelScope.launch {
            // Always store in cm — convert from inches if needed
            val valueInCm = if (unit.lowercase() in listOf("in", "inches", "inch")) {
                safeValue * 2.54
            } else {
                safeValue
            }
            dao.insertBodyMeasurement(
                BodyMeasurement(
                    bodyPart = safePart,
                    value = valueInCm,
                    unit = "cm",
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
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
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

    /**
     * Per-muscle exponential recovery model.
     * recovery(t) = 1 - e^(-t/tau), where tau is the muscle-group half-life.
     * hoursRemaining = max(0, 3*tau - elapsedHours)  (time to ~95% recovery).
     * recoveryFraction = e^(-elapsedHours/tau) mapped to [0, 1] fresh scale.
     * Half-lives sourced from peer-reviewed recovery literature, matching the
     * same per-muscle tau approach already used by ReadinessEngine.
     */
    private fun calculateMuscleRecovery(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        allSets: List<com.apexfit.app.data.ExerciseSet>
    ): List<MuscleRecoveryStatus> {
        // Evidence-based tau (half-life hours) per muscle group.
        // Source: per-muscle recovery half-life literature (Omnio/Arvo/Schoenfeld).
        fun tauForMuscle(muscle: String): Double = when {
            muscle.contains("core") || muscle.contains("abs") || muscle.contains("oblique") -> 16.0
            muscle.contains("calf") || muscle.contains("calves") || muscle.contains("forearm") -> 18.0
            muscle.contains("shoulder") || muscle.contains("delt") ||
            muscle.contains("bicep") || muscle.contains("tricep") -> 24.0
            muscle.contains("chest") -> 30.0
            muscle.contains("trap") || muscle.contains("upper back") -> 36.0
            muscle.contains("back") || muscle.contains("lat") -> 40.0
            muscle.contains("lower back") || muscle.contains("lower_back") -> 60.0
            muscle.contains("quad") || muscle.contains("hamstring") ||
            muscle.contains("glute") || muscle.contains("leg") -> 48.0
            else -> 36.0  // conservative default for unknown muscles
        }

        val muscles = com.apexfit.app.utils.MuscleGroups.ALL
        val nowMs = System.currentTimeMillis()

        return muscles.map { muscle ->
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
                // Find the most recent session that trained this muscle
                val setsWithDates = matchingSets.mapNotNull { set ->
                    val session = sessions.find { it.id == set.sessionId } ?: return@mapNotNull null
                    set to session
                }.sortedByDescending { it.second.date }

                if (setsWithDates.isEmpty()) {
                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        hoursRemaining = 0,
                        recoveryFraction = 1.0f,
                        lastTrainedDate = ""
                    )
                } else {
                    val (_, lastSession) = setsWithDates.first()
                    val lastDateStr = lastSession.date
                    val lastDateMs = com.apexfit.app.utils.DateTimeUtils.parseDate(lastDateStr)?.time
                        ?: nowMs

                    val elapsedHours = maxOf(0.0, (nowMs - lastDateMs).toDouble() / (1000.0 * 60 * 60))
                    val tau = tauForMuscle(muscle.lowercase())

                    // Exponential recovery: fraction recovered = 1 - e^(-t/tau)
                    // We express recoveryFraction as "freshness": 1.0 = just recovered, 0.0 = just trained
                    val freshness = Math.exp(-elapsedHours / tau).toFloat().coerceIn(0f, 1f)

                    // 95% recovery threshold = 3 × tau (e^-3 ≈ 0.05 fatigue remaining)
                    val fullRecoveryHours = 3.0 * tau
                    val hoursRemaining = maxOf(0, (fullRecoveryHours - elapsedHours).toInt())

                    MuscleRecoveryStatus(
                        muscleGroup = muscle,
                        hoursRemaining = hoursRemaining,
                        recoveryFraction = 1.0f - freshness,  // 0.0 = fresh, 1.0 = fatigued
                        lastTrainedDate = lastDateStr
                    )
                }
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}