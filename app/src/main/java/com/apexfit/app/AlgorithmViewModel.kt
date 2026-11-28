@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.*
import com.apexfit.app.data.HeatmapEntry
import com.apexfit.app.utils.*
import com.apexfit.app.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.apexfit.app.domain.repository.FitnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlgorithmViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository,
    private val dataStore: DataStoreManager,
    private val dao: FitnessDao
) : AndroidViewModel(application) {

    // ─────────────────────────────────────────────────────────────────
    // SECTION 1 — RAW ROOM FLOWS
    // ─────────────────────────────────────────────────────────────────

    private val weightFlow = repository.weightEntriesFlow

    private val nutritionFlow = repository.nutritionEntriesFlow

    val targetsFlow: Flow<com.apexfit.app.utils.NutritionTargets> =
        repository.sharedTargetsFlow

    private val goalFlow: Flow<String> = dataStore.goalFlow
        .flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 2 — INTERMEDIATE COMPUTED FLOWS
    // ─────────────────────────────────────────────────────────────────

    private val richSessionsFlow = repository.richSessionsFlow

    // ─────────────────────────────────────────────────────────────────
    // SECTION 3 — PUBLIC STATEFLOWS
    // ─────────────────────────────────────────────────────────────────

    val completedSessions: StateFlow<List<UiTrainingSession>> = repository.richSessionsFlow
        .map { list ->
            list.map { rs ->
                UiTrainingSession(
                    id = "",
                    name = rs.sessionType,
                    date = rs.date,
                    duration = rs.durationMinutes,
                    feelRating = rs.sessionFeel,
                    isCompleted = rs.completed
                )
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trendWeight: StateFlow<Double?> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.getCurrentTrendWeight(entries)
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightDirection: StateFlow<String> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.getWeightDirection(entries)
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit("insufficient_data") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "insufficient_data")

    val tdeeResult: StateFlow<com.apexfit.app.utils.TDEEResult> = combine(
        weightFlow, nutritionFlow, dataStore.nutritionDayConfirmationsFlow
    ) { weights, nutrition, confirmations ->
        Triple(weights, nutrition, confirmations)
    }.debounce(300L)
    .combine(
        combine(
            dataStore.heightFlow,
            dataStore.ageFlow,
            dataStore.sexFlow,
            repository.getAverageTrainingFrequencyFlow(),
            repository.getCurrentWeightFlow()
        ) { height, age, sex, workouts, weight ->
            AlgorithmBioProfile(height, age, sex, workouts, weight)
        }
    ) { (weights, nutrition, confirmations), bio ->
        withContext(Dispatchers.Default) {
            com.apexfit.app.utils.TdeeEngine.calcAdaptiveTDEE(
                weightLog = weights,
                nutritionLog = nutrition,
                windowDays = 14,
                heightCm = bio.height,
                ageYears = bio.age,
                biologicalSex = bio.sex,
                weeklyWorkouts = bio.weeklyWorkouts,
                fallbackWeightKg = bio.weight,
                nutritionConfirmations = confirmations
            )
        }
    }.catch { e ->
        android.util.Log.e("AlgorithmVM", "flow error", e)
        emit(com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0))
    }.combine(dataStore.hasBodyDetailsFlow) { result, ready ->
        if (ready) result else com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "Body details needed", avgCalories = 0, weightChangeKg = 0.0)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0)
    )


    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> =
        repository.streakResultFlow

    val complianceScores: StateFlow<UiComplianceResult> =
        repository.complianceScoresFlow

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow, dataStore.goalFlow
    ) { weights: List<com.apexfit.app.utils.WeightEntry>,
        nutrition: List<com.apexfit.app.utils.NutritionEntry>,
        sessions: List<com.apexfit.app.utils.TrainingSession>,
        goal: String ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.PlateauEngine.detectPlateau(
                weightLog = weights,
                nutritionLog = nutrition,
                trainingLog = sessions,
                goal = goal
            )
        }
        res.toUi()
    }.catch { e ->
        android.util.Log.e("AlgorithmVM", "flow error", e)
        emit(com.apexfit.app.data.PlateauResult(isPlateaued = false).toUi())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.data.PlateauResult(isPlateaued = false).toUi()
    )

    val plateauAlert: StateFlow<com.apexfit.app.data.PlateauResult?> = plateauResult
        .map { result ->
            if (result.isPlateau) {
                com.apexfit.app.data.PlateauResult(
                    isPlateaued = true,
                    interventionRecommendation = result.recommendation
                )
            } else {
                null
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class SessionDerivedResults(
        val fatigueResult: com.apexfit.app.utils.FatigueResult,
        val muscleHeatmap: Map<String, HeatmapEntry>,
        val trainingPatternSignals: List<String>,
        val weeklyVolume: Map<String, Int>
    )

    private val sessionDerived: StateFlow<SessionDerivedResults> = combine(
        richSessionsFlow, com.apexfit.app.utils.DateTimeUtils.todayDateFlow()
    ) { sessions, date -> sessions to java.time.LocalDate.parse(date) }
        .debounce(300L)
        .map { (sessions, today) ->
            withContext(Dispatchers.Default) {
                val fatigue = com.apexfit.app.utils.FatigueEngine.calcFatigueToFitnessRatio(sessions)
                val rows = com.apexfit.app.utils.MuscleVolumeSummary.calculate(
                    sessions, today, com.apexfit.app.utils.MuscleVolumePeriod.WEEK
                )
                SessionDerivedResults(
                    fatigueResult = fatigue,
                    muscleHeatmap = com.apexfit.app.utils.FatigueEngine.directHeatmap(rows),
                    trainingPatternSignals = com.apexfit.app.utils.AlgorithmEngine.detectTrainingPatternSignals(sessions)
                        .ifEmpty { listOf("No unusual training patterns in recent sessions.") },
                    weeklyVolume = com.apexfit.app.utils.MuscleVolumeSummary.directCounts(rows)
                )
            }
        }
        .catch { e ->
            android.util.Log.e("AlgorithmVM", "flow error", e)
            emit(
                SessionDerivedResults(
                    fatigueResult = com.apexfit.app.utils.FatigueResult(
                        ratio = null,
                        status = "unknown",
                        statusLabel = "No Data",
                        recommendation = "Log workouts to activate fatigue tracking",
                        acuteLoad = 0.0,
                        chronicLoad = 0.0
                    ),
                    muscleHeatmap = emptyMap(),
                    trainingPatternSignals = listOf("Log workouts to see recent training patterns."),
                    weeklyVolume = emptyMap()
                )
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionDerivedResults(
                fatigueResult = com.apexfit.app.utils.FatigueResult(
                    ratio = null,
                    status = "unknown",
                    statusLabel = "No Data",
                    recommendation = "Log workouts to activate fatigue tracking",
                    acuteLoad = 0.0,
                    chronicLoad = 0.0
                ),
                muscleHeatmap = emptyMap(),
                trainingPatternSignals = listOf("Log workouts to see recent training patterns."),
                weeklyVolume = emptyMap()
            )
        )

    val fatigueResult: StateFlow<UiFatigueResult> = sessionDerived
        .map { it.fatigueResult.toUi() }
        .catch { e ->
            android.util.Log.e("AlgorithmVM", "flow error", e)
            emit(
                com.apexfit.app.utils.FatigueResult(
                    ratio = null,
                    status = "unknown",
                    statusLabel = "No Data",
                    recommendation = "Log workouts to activate fatigue tracking",
                    acuteLoad = 0.0,
                    chronicLoad = 0.0
                ).toUi()
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.apexfit.app.utils.FatigueResult(
                ratio = null,
                status = "unknown",
                statusLabel = "No Data",
                recommendation = "Log workouts to activate fatigue tracking",
                acuteLoad = 0.0,
                chronicLoad = 0.0
            ).toUi()
        )

    val fatigueRatio: StateFlow<com.apexfit.app.data.FatigueRatio> = fatigueResult
        .map { result ->
            com.apexfit.app.data.FatigueRatio(
                ratio = result.ratio ?: 0.0,
                riskStatus = result.statusLabel
            )
        }
        .catch { e ->
            android.util.Log.e("AlgorithmVM", "flow error", e)
            emit(com.apexfit.app.data.FatigueRatio(0.0, "No Data"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.data.FatigueRatio(0.0, "No Data"))

    val muscleVolumes: StateFlow<Map<String, Int>> = sessionDerived
        .map { it.weeklyVolume }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyMap()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    val muscleHeatmap: StateFlow<Map<String, HeatmapEntry>> = sessionDerived
        .map { it.muscleHeatmap }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyMap()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    val allPRs: StateFlow<List<UiPersonalRecord>> = dao.getPersonalRecordsWithNames()
        .map { entries -> entries.map { it.toUi() } }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trainingPatternSignals: StateFlow<List<String>> = sessionDerived
        .map { it.trainingPatternSignals }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val hypertrophyQualityScores: StateFlow<Map<String, Double>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.calcEffectiveSets(sessions)
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyMap()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )


    val weeklyVolume: StateFlow<Map<String, List<Pair<String, Double>>>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.calcWeeklyVolumePerMuscle(sessions)
            }
        }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val targets: StateFlow<com.apexfit.app.utils.NutritionTargets?> = targetsFlow
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)



    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> =
        repository.todayExercisesFlow

    val sessionReadiness: StateFlow<UiSessionReadiness?> =
        repository.sessionReadinessFlow

    val detectedPatterns: StateFlow<List<com.apexfit.app.data.DetectedPatternEntity>> = dao.getAllDetectedPatternsFlow()
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deloadRecommendation: StateFlow<UiDeloadResult> = combine(
        richSessionsFlow, complianceScore, sessionDerived
    ) { sessions, score, derived ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.PlateauEngine.calcDeloadRecommendation(
                trainingLog = sessions,
                complianceScore = score,
                precomputedFatigue = derived.fatigueResult
            )
        }
        res.toUi()
    }.catch { e ->
        android.util.Log.e("AlgorithmVM", "flow error", e)
        emit(com.apexfit.app.utils.DeloadResult(recommendation = "No data yet", urgency = "none", signals = 0).toUi())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.DeloadResult(recommendation = "No data yet", urgency = "none", signals = 0).toUi()
    )

    // ─────────────────────────────────────────────────────────────────
    // SECTION 4 — COACH CONTEXT STRING
    // ─────────────────────────────────────────────────────────────────


    private fun isDateInCurrentWeekSinceMonday(dateStr: String): Boolean {
        return try {
            val sessionDate = java.time.LocalDate.parse(dateStr)
            val today = java.time.LocalDate.now()
            val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            !sessionDate.isBefore(monday) && !sessionDate.isAfter(today)
        } catch (e: Exception) {
            false
        }
    }

}

private data class AlgorithmBioProfile(
    val height: Double,
    val age: Int,
    val sex: String,
    val weeklyWorkouts: Int,
    val weight: Double
)
