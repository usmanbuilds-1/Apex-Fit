@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao
) : AndroidViewModel(application) {

    val allBodyMeasurements: StateFlow<UiState<List<UiBodyMeasurement>>> = dao.getBodyMeasurementsFlow()
        .map { entries -> UiState.Success(entries.map { it.toUi() }) as UiState<List<UiBodyMeasurement>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val personalRecords = dao.getPersonalRecordsWithNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workoutHistory = dao.getSavedTrainingSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setsForSession(sessionId: String): Flow<List<com.apexfit.app.data.ExerciseSet>> = dao.getSetsForSessionFlow(sessionId)

    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")

    fun measurementsForBodyPart(bodyPart: String): Flow<List<UiBodyMeasurement>> {
        return dao.getBodyMeasurementsForPartFlow(bodyPart)
            .map { entries -> entries.map { it.toUi() } }
    }

    // 2. Monthly muscle volumes for radar chart
    val monthlyMuscleVolumes: StateFlow<UiState<Map<String, Int>>> = combine(
        repository.recent90DaySessionsFlow,
        repository.recent90DaySetsFlow
    ) { sessions, allSets ->
        val result = ProgressDataAggregation.monthlyWorkingSets(
            sessions, allSets, java.time.YearMonth.now().toString()
        )
        UiState.Success(result) as UiState<Map<String, Int>>
    }
    .flowOn(Dispatchers.Default)
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    // 3. Muscle recovery status calculation
    val muscleRecoveryStatuses: StateFlow<UiState<List<MuscleRecoveryStatus>>> = combine(
        repository.recent90DaySessionsFlow,
        repository.recent90DaySetsFlow
    ) { sessions, allSets ->
        UiState.Success(calculateMuscleRecovery(sessions, allSets)) as UiState<List<MuscleRecoveryStatus>>
    }
    .flowOn(Dispatchers.Default)
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

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
     * recovery(t) = 1 - e^(-t/tau), where tau is an exponential time constant.
     * hoursRemaining = max(0, 3*tau - elapsedHours), approximately the time to
     * 95% recovery under this heuristic model.
     * Values are heuristic recovery time constants and must not be described as
     * biological half-lives (a true half-life would be tau * ln(2)).
     */
    private fun calculateMuscleRecovery(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        allSets: List<com.apexfit.app.data.ExerciseSet>
    ): List<MuscleRecoveryStatus> {
        // Heuristic recovery time constants (hours) by muscle group.
        // These are calibration values for the app's exponential model, not measured biological half-lives.
        fun tauForMuscle(muscle: String): Double = when {
            muscle.contains("core") || muscle.contains("abs") || muscle.contains("oblique") -> 16.0
            muscle.contains("calf") || muscle.contains("calves") || muscle.contains("forearm") -> 18.0
            muscle.contains("shoulder") || muscle.contains("delt") ||
            muscle.contains("bicep") || muscle.contains("tricep") -> 24.0
            muscle.contains("chest") -> 30.0
            muscle.contains("lower back") || muscle.contains("lower_back") || muscle.contains("lumbar") -> 60.0
            muscle.contains("trap") || muscle.contains("upper back") || muscle.contains("upper_back") -> 36.0
            muscle.contains("back") || muscle.contains("lat") -> 40.0
            muscle.contains("quad") || muscle.contains("hamstring") ||
            muscle.contains("glute") || muscle.contains("leg") -> 48.0
            else -> 36.0  // conservative default for unknown muscles
        }

        val muscles = com.apexfit.app.utils.MuscleGroups.ALL
        val nowMs = System.currentTimeMillis()
        // Use the actual completion timestamp when available. Falling back to the
        // date's local midnight is only for legacy sessions that predate timestamps.
        val latestMoments = ProgressDataAggregation.latestSessionMoments(
            sessions, allSets, muscles, ::isMuscleMatch
        )

        return muscles.map { muscle ->
            val lastMoment = latestMoments[muscle]
            if (lastMoment == null) {
                MuscleRecoveryStatus(
                    muscleGroup = muscle,
                    hoursRemaining = 0,
                    recoveryFraction = 1.0f,
                    lastTrainedDate = ""
                )
            } else {
                val elapsedHours = maxOf(
                    0.0,
                    (nowMs - lastMoment.timestampMs).toDouble() / (1000.0 * 60 * 60)
                )
                val tau = tauForMuscle(muscle.lowercase())
                val remainingFatigue = Math.exp(-elapsedHours / tau).toFloat().coerceIn(0f, 1f)
                val hoursRemaining = maxOf(0, kotlin.math.ceil(3.0 * tau - elapsedHours).toInt())
                MuscleRecoveryStatus(
                    muscleGroup = muscle,
                    hoursRemaining = hoursRemaining,
                    recoveryFraction = 1.0f - remainingFatigue,
                    lastTrainedDate = lastMoment.date
                )
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}

// Pure aggregations are shared by the reactive Progress flow and regression tests.
// These functions do not touch Room or the UI thread.
internal object ProgressDataAggregation {
    data class RecoveryMoment(val date: String, val timestampMs: Long)

    fun latestSessionMoments(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        sets: List<com.apexfit.app.data.ExerciseSet>,
        muscles: List<String>,
        muscleMatches: (String, String) -> Boolean
    ): Map<String, RecoveryMoment> {
        val sessionsById = sessions.associateBy { it.id }
        val musclesByGroup = HashMap<String, List<String>>()
        val latest = HashMap<String, RecoveryMoment>()

        for (set in sets) {
            if (!set.completed || set.isWarmup) continue
            val session = sessionsById[set.sessionId] ?: continue
            val timestamp = session.completedAt
                ?: session.startedAt
                ?: com.apexfit.app.utils.DateTimeUtils.parseDate(session.date)?.time
                ?: continue
            val matchingMuscles = musclesByGroup.getOrPut(set.muscleGroup) {
                muscles.filter { muscleMatches(set.muscleGroup, it) }
            }
            for (muscle in matchingMuscles) {
                val previous = latest[muscle]
                if (previous == null || timestamp > previous.timestampMs) {
                    latest[muscle] = RecoveryMoment(session.date, timestamp)
                }
            }
        }
        return latest
    }

    fun monthlyWorkingSets(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        sets: List<com.apexfit.app.data.ExerciseSet>,
        yearMonth: String
    ): Map<String, Int> {
        val sessionIds = sessions.asSequence()
            .filter { it.date.startsWith(yearMonth) }
            .map { it.id }
            .toHashSet()
        val counts = HashMap<String, Int>()
        for (set in sets) {
            if (!set.completed || set.isWarmup || set.sessionId !in sessionIds) continue
            val muscle = set.muscleGroup.lowercase().trim()
            counts[muscle] = (counts[muscle] ?: 0) + 1
        }
        return MuscleGroups.ALL.associateWith { counts[it.lowercase().trim()] ?: 0 }
    }

    fun latestSessionDates(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        sets: List<com.apexfit.app.data.ExerciseSet>,
        muscles: List<String>,
        muscleMatches: (String, String) -> Boolean
    ): Map<String, String> {
        val datesBySession = sessions.associate { it.id to it.date }
        val musclesByGroup = HashMap<String, List<String>>()
        val latestDates = HashMap<String, String>()
        for (set in sets) {
            if (!set.completed || set.isWarmup) continue
            val date = datesBySession[set.sessionId] ?: continue
            val matchingMuscles = musclesByGroup.getOrPut(set.muscleGroup) {
                muscles.filter { muscleMatches(set.muscleGroup, it) }
            }
            for (muscle in matchingMuscles) {
                val previousDate = latestDates[muscle]
                if (previousDate == null || date > previousDate) {
                    latestDates[muscle] = date
                }
            }
        }
        return latestDates
    }
}
