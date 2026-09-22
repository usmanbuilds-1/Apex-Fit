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

class AlgorithmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.apexfit.app.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(application)
    private val repository = com.apexfit.app.di.ServiceLocator.repository(application)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 1 — RAW ROOM FLOWS
    // ─────────────────────────────────────────────────────────────────

    private val weightFlow = com.apexfit.app.di.ServiceLocator.weightEntriesFlow

    private val nutritionFlow = com.apexfit.app.di.ServiceLocator.nutritionEntriesFlow

    val targetsFlow: Flow<com.apexfit.app.utils.NutritionTargets> =
        com.apexfit.app.di.ServiceLocator.sharedTargetsFlow

    private val goalFlow: Flow<String> = dataStore.goalFlow
        .flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 2 — INTERMEDIATE COMPUTED FLOWS
    // ─────────────────────────────────────────────────────────────────

    private val richSessionsFlow = com.apexfit.app.di.ServiceLocator.richSessionsFlow

    // ─────────────────────────────────────────────────────────────────
    // SECTION 3 — PUBLIC STATEFLOWS
    // ─────────────────────────────────────────────────────────────────

    val completedSessions: StateFlow<List<UiTrainingSession>> = com.apexfit.app.di.ServiceLocator.richSessionsFlow
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
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights, nutrition, sessions ->
        Triple(weights, nutrition, sessions)
    }.debounce(300L)
    .combine(
        combine(
            dataStore.heightFlow,
            dataStore.ageFlow,
            dataStore.sexFlow,
            dataStore.weeklyWorkoutsFlow
        ) { height, age, sex, workouts ->
            AlgorithmBioProfile(height, age, sex, workouts)
        }
    ) { (weights, nutrition, sessions), bio ->
        withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.calcAdaptiveTDEE(
                weightLog = weights,
                nutritionLog = nutrition,
                windowDays = 14,
                heightCm = bio.height,
                ageYears = bio.age,
                biologicalSex = bio.sex,
                weeklyWorkouts = bio.weeklyWorkouts
            )
        }
    }.catch { e ->
        android.util.Log.e("AlgorithmVM", "flow error", e)
        emit(com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0))
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0)
    )


    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> =
        com.apexfit.app.di.ServiceLocator.streakResultFlow

    val complianceScores: StateFlow<UiComplianceResult> =
        com.apexfit.app.di.ServiceLocator.complianceScoresFlow

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights: List<com.apexfit.app.utils.WeightEntry>, nutrition: List<com.apexfit.app.utils.NutritionEntry>, sessions: List<com.apexfit.app.utils.TrainingSession> ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.detectPlateau(weights, nutrition, sessions)
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
        val injuryRiskSignals: List<String>,
        val weeklyVolume: Map<String, Int>
    )

    private val sessionDerived: StateFlow<SessionDerivedResults> = richSessionsFlow
        .debounce(300L)
        .map { sessions ->
            withContext(Dispatchers.Default) {
                val fatigue = com.apexfit.app.utils.AlgorithmEngine.calcFatigueToFitness(sessions)
                SessionDerivedResults(
                    fatigueResult = fatigue,
                    muscleHeatmap = com.apexfit.app.utils.AlgorithmEngine.calcMuscleHeatmap(sessions, days = 7),
                    injuryRiskSignals = com.apexfit.app.utils.AlgorithmEngine.detectInjuryRiskSignals(sessions, precomputedFatigue = fatigue)
                        .ifEmpty { listOf("Recovery indicators within normal range.") },
                    weeklyVolume = buildMuscleVolumes(sessions)
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
                    injuryRiskSignals = listOf("Log workouts to activate tracking."),
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
                injuryRiskSignals = listOf("Log workouts to activate tracking."),
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

    val injuryRiskSignals: StateFlow<List<String>> = sessionDerived
        .map { it.injuryRiskSignals }
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private fun buildMuscleVolumes(sessions: List<com.apexfit.app.utils.TrainingSession>): Map<String, Int> {
        val muscleSetsMap = mutableMapOf<String, Int>()
        val allKeys = listOf(
            "chest", "back", "shoulders",
            "biceps", "triceps", "forearms", "trapezius", "neck",
            "abs", "obliques", "transverse abdominis", "lower back",
            "glutes", "quadriceps", "hamstrings", "calves",
            "hip abductors", "hip adductors", "rotator cuff",
            "serratus anterior", "tibialis anterior"
        )
        allKeys.forEach { muscleSetsMap[it] = 0 }

        // 1. Build a precomputed Map<String, String> from exerciseId -> muscleGroup at the start
        val exerciseIdToMuscleGroup = mutableMapOf<String, String>()
        sessions.forEach { s ->
            s.exercises.forEach { e ->
                if (!exerciseIdToMuscleGroup.containsKey(e.id)) {
                    val group = e.muscleGroup.lowercase().trim()
                    val targetGroup = when {
                        group.contains("chest") || group.contains("pectoral") -> "chest"
                        group.contains("back") && !group.contains("lower") -> "back"
                        group.contains("front delt") || group.contains("front_delt") || group.contains("anterior delt") -> "shoulders"
                        group.contains("rear delt") || group.contains("rear_delt") || group.contains("posterior delt") -> "shoulders"
                        group.contains("side delt") || group.contains("side_delt") || group.contains("lateral delt") || group.contains("lateral") || group.contains("shoulder") || group.contains("delt") -> "shoulders"
                        group.contains("bicep") -> "biceps"
                        group.contains("tricep") -> "triceps"
                        group.contains("forearm") -> "forearms"
                        group.contains("trapezius") || group.contains("trap") -> "trapezius"
                        group.contains("neck") -> "neck"
                        
                        group.contains("abs") || group.contains("rectus abdominis") || group.contains("rectus_abdominis") || group.contains("abdom") || group.contains("core") -> "abs"
                        group.contains("oblique") -> "obliques"
                        group.contains("transverse abdominis") || group.contains("transverse_abdominis") -> "transverse abdominis"
                        group.contains("lower back") || group.contains("lower_back") || group.contains("lumbar") || group.contains("spinal erector") || group.contains("erector") -> "lower back"
                        
                        group.contains("glute") -> "glutes"
                        group.contains("quad") || group.contains("quadriceps") -> "quadriceps"
                        group.contains("hamstring") -> "hamstrings"
                        group.contains("calf") || group.contains("calves") -> "calves"
                        group.contains("hip abductor") || group.contains("abductor") -> "hip abductors"
                        group.contains("hip adductor") || group.contains("adductor") -> "hip adductors"
                        
                        group.contains("rotator cuff") || group.contains("rotator") -> "rotator cuff"
                        group.contains("serratus anterior") || group.contains("serratus") -> "serratus anterior"
                        group.contains("tibialis anterior") || group.contains("tibialis") -> "tibialis anterior"
                        
                        else -> group
                    }
                    exerciseIdToMuscleGroup[e.id] = targetGroup
                }
            }
        }

        // 2. Efficient date grouping/filtering using java.time.LocalDate without Calendar allocation
        val sessionsByYearMonth = sessions
            .filter { it.completed }
            .groupBy { session ->
                val ld = java.time.LocalDate.parse(session.date)
                ld.year * 100 + ld.monthValue  // int key, no Calendar allocation
            }

        val today = java.time.LocalDate.now()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        sessions.forEach { s ->
            val sessionDate = try { java.time.LocalDate.parse(s.date) } catch (e: Exception) { null }
            if (sessionDate != null && !sessionDate.isBefore(monday) && !sessionDate.isAfter(today)) {
                s.exercises.forEach { e ->
                    val workingSetsCount = e.sets.count { !it.isWarmup && it.completed }
                    val targetGroup = exerciseIdToMuscleGroup[e.id] ?: e.muscleGroup.lowercase().trim()
                    if (muscleSetsMap.containsKey(targetGroup)) {
                        muscleSetsMap[targetGroup] = (muscleSetsMap[targetGroup] ?: 0) + workingSetsCount
                    }
                }
            }
        }
        return muscleSetsMap
    }

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
        com.apexfit.app.di.ServiceLocator.todayExercisesFlow

    val sessionReadiness: StateFlow<UiSessionReadiness?> =
        com.apexfit.app.di.ServiceLocator.sessionReadinessFlow

    val detectedPatterns: StateFlow<List<com.apexfit.app.data.DetectedPatternEntity>> = dao.getAllDetectedPatternsFlow()
        .catch { e -> android.util.Log.e("AlgorithmVM", "flow error", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deloadRecommendation: StateFlow<UiDeloadResult> = combine(
        richSessionsFlow, complianceScore, sessionDerived
    ) { sessions, score, derived ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.calcDeloadRecommendation(
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
    val weeklyWorkouts: Int
)
