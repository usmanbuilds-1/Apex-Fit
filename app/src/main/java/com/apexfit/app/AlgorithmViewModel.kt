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

    private val weightFlow: Flow<List<com.apexfit.app.utils.WeightEntry>> = dao.getAllWeightEntriesFlow()
        .map { list -> list.map { com.apexfit.app.utils.WeightEntry(it.date, it.weight) } }
        .flowOn(Dispatchers.IO)

    private val nutritionFlow: Flow<List<com.apexfit.app.utils.NutritionEntry>> = dao.getNutritionEntriesSince(getDateDaysAgo(30))
        .map { list ->
            list.map {
                com.apexfit.app.utils.NutritionEntry(
                    date = it.date,
                    calories = it.calories,
                    protein = it.protein.roundToInt(),
                    carbs = it.carbs.roundToInt(),
                    fat = it.fat.roundToInt()
                )
            }
        }
        .flowOn(Dispatchers.IO)

    private val sessionsFlow: Flow<List<com.apexfit.app.data.TrainingSession>> =
        dao.getRecentCompletedSessionsFlow(
            getDateDaysAgo(90)
        ).flowOn(Dispatchers.IO)



    val targetsFlow: Flow<com.apexfit.app.utils.NutritionTargets> = combine(
        dataStore.calorieTargetValueFlow,
        weightFlow,
        dataStore.goalFlow
    ) { calTarget, weights, goal ->
        val latestTrend = weights.maxByOrNull { it.date }?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        com.apexfit.app.utils.AlgorithmEngine.calcMacroTargets(calTarget, latestTrend, goal)
    }.flowOn(Dispatchers.IO)

    private val goalFlow: Flow<String> = dataStore.goalFlow
        .flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 2 — INTERMEDIATE COMPUTED FLOWS
    // ─────────────────────────────────────────────────────────────────


    private val richSessionsFlow: Flow<List<com.apexfit.app.utils.TrainingSession>> =
        com.apexfit.app.di.ServiceLocator.richSessionsFlow

    // ─────────────────────────────────────────────────────────────────
    // SECTION 3 — PUBLIC STATEFLOWS
    // ─────────────────────────────────────────────────────────────────

    val completedSessions: StateFlow<List<UiTrainingSession>> = sessionsFlow
        .map { list -> list.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trendWeight: StateFlow<Double?> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.getCurrentTrendWeight(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightDirection: StateFlow<String> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.apexfit.app.utils.AlgorithmEngine.getWeightDirection(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "insufficient_data")

    val tdeeResult: StateFlow<com.apexfit.app.utils.TDEEResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights, nutrition, sessions ->
        Triple(weights, nutrition, sessions)
    }.combine(
        dataStore.heightFlow, dataStore.ageFlow, dataStore.sexFlow
    ) { (weights, nutrition, sessions), height, age, sex ->
        val fourteenDaysAgo = com.apexfit.app.utils.getDateDaysAgo(14)
        val recentSessions = sessions.filter { it.date >= fourteenDaysAgo && it.completed }.size
        val workoutsFreq = Math.round(recentSessions / 2.0).toInt().coerceIn(1, 7)

        com.apexfit.app.utils.AlgorithmEngine.calcAdaptiveTDEE(
            weightLog = weights,
            nutritionLog = nutrition,
            windowDays = 14,
            heightCm = height,
            ageYears = age,
            biologicalSex = sex,
            weeklyWorkouts = workoutsFreq
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0)
    )


    val streakResult: StateFlow<com.apexfit.app.utils.StreakResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        withContext(Dispatchers.Default) {
            val validTargets = targets ?: com.apexfit.app.utils.NutritionTargets(calories = 2650, protein = 160, carbs = 280, fat = 75, weeklyTrainingSessions = 4)
            com.apexfit.app.utils.AlgorithmEngine.calcStreaks(nutrition, sessions, validTargets)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.StreakResult(com.apexfit.app.utils.StreakInfo(0), com.apexfit.app.utils.StreakInfo(0))
    )


    val complianceScores: StateFlow<UiComplianceResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights: List<com.apexfit.app.utils.WeightEntry>, nutrition: List<com.apexfit.app.utils.NutritionEntry>, sessions: List<com.apexfit.app.utils.TrainingSession> ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.detectPlateau(weights, nutrition, sessions)
        }
        res.toUi()
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class SessionDerivedResults(
        val fatigueResult: com.apexfit.app.utils.FatigueResult,
        val muscleHeatmap: Map<String, HeatmapEntry>,
        val injuryRiskSignals: List<String>,
        val weeklyVolume: Map<String, Int>
    )

    private val sessionDerived: StateFlow<SessionDerivedResults> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                SessionDerivedResults(
                    fatigueResult = com.apexfit.app.utils.AlgorithmEngine.calcFatigueToFitness(sessions),
                    muscleHeatmap = com.apexfit.app.utils.AlgorithmEngine.calcMuscleHeatmap(sessions, days = 7),
                    injuryRiskSignals = com.apexfit.app.utils.AlgorithmEngine.detectInjuryRiskSignals(sessions)
                        .ifEmpty { listOf("Recovery indicators normal. High-intensity load distributed optimally within target thresholds.") },
                    weeklyVolume = buildMuscleVolumes(sessions)
                )
            }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.apexfit.app.data.FatigueRatio(0.0, "No Data"))

    val muscleVolumes: StateFlow<Map<String, Int>> = sessionDerived
        .map { it.weeklyVolume }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    val muscleHeatmap: StateFlow<Map<String, HeatmapEntry>> = sessionDerived
        .map { it.muscleHeatmap }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    private val _exerciseProgression = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    val exerciseProgression: StateFlow<List<Pair<String, Double>>> = _exerciseProgression.asStateFlow()

    private val _progressionStatus = MutableStateFlow("No Data")
    val progressionStatus: StateFlow<String> = _progressionStatus.asStateFlow()

    val allPRs: StateFlow<List<UiPersonalRecord>> = dao.getPersonalRecordsWithNames()
        .map { entries -> entries.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val injuryRiskSignals: StateFlow<List<String>> = sessionDerived
        .map { it.injuryRiskSignals }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private fun buildMuscleVolumes(sessions: List<com.apexfit.app.utils.TrainingSession>): Map<String, Int> {
        val muscleSetsMap = mutableMapOf<String, Int>()
        val allKeys = listOf(
            "chest", "back", "front delts", "side delts", "rear delts",
            "biceps", "triceps", "forearms", "trapezius", "neck",
            "abs", "obliques", "transverse abdominis", "lower back",
            "glutes", "quadriceps", "hamstrings", "calves",
            "hip abductors", "hip adductors", "rotator cuff",
            "serratus anterior", "tibialis anterior"
        )
        allKeys.forEach { muscleSetsMap[it] = 0 }

        sessions.forEach { s ->
            if (isDateInCurrentWeekSinceMonday(s.date)) {
                s.exercises.forEach { e ->
                    val workingSetsCount = e.sets.count { !it.isWarmup && it.completed }
                    val group = e.muscleGroup.lowercase().trim()
                    
                    val targetGroup = when {
                        group.contains("chest") || group.contains("pectoral") -> "chest"
                        group.contains("back") && !group.contains("lower") -> "back"
                        group.contains("front delt") || group.contains("front_delt") || group.contains("anterior delt") -> "front delts"
                        group.contains("rear delt") || group.contains("rear_delt") || group.contains("posterior delt") -> "rear delts"
                        group.contains("side delt") || group.contains("side_delt") || group.contains("lateral delt") || group.contains("lateral") || group.contains("shoulder") || group.contains("delt") -> "side delts"
                        group.contains("bicep") -> "biceps"
                        group.contains("tricep") -> "triceps"
                        group.contains("forearm") -> "forearms"
                        group.contains("trapezius") || group.contains("trap") -> "trapezius"
                        group.contains("neck") -> "neck"
                        
                        group.contains("abs") || group.contains("rectus abdominis") || group.contains("rectus_abdominis") || group.contains("abdom") || group.contains("core") -> "abs"
                        group.contains("oblique") -> "obliques"
                        group.contains("transverse abdominis") || group.contains("transverse_abdominis") -> "transverse abdominis"
                        group.contains("lower back") || group.contains("lumbar") || group.contains("spinal erector") || group.contains("erector") -> "lower back"
                        
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val targets: StateFlow<com.apexfit.app.utils.NutritionTargets?> = targetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)



    val todayExercisesFlow: Flow<List<com.apexfit.app.data.PlanExercise>> = repository.getActivePlan().flatMapLatest { plan ->
        val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
        sessions.flatMapLatest { sessionList ->
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    val sessionReadiness: StateFlow<UiSessionReadiness?> =
        com.apexfit.app.di.ServiceLocator.sessionReadinessFlow

    val detectedPatterns: StateFlow<List<com.apexfit.app.data.DetectedPatternEntity>> = dao.getAllDetectedPatternsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deloadRecommendation: StateFlow<UiDeloadResult> = combine(
        richSessionsFlow, complianceScore
    ) { sessions, score ->
        val res = withContext(Dispatchers.Default) {
            com.apexfit.app.utils.AlgorithmEngine.calcDeloadRecommendation(sessions, score)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.apexfit.app.utils.DeloadResult(recommendation = "No data yet", urgency = "low", signals = 0).toUi()
    )

    // ─────────────────────────────────────────────────────────────────
    // SECTION 4 — COACH CONTEXT STRING
    // ─────────────────────────────────────────────────────────────────


    private fun isDateInCurrentWeekSinceMonday(dateStr: String): Boolean {
        return try {
            val sessionDate = com.apexfit.app.utils.DateTimeUtils.parseDate(dateStr) ?: return false

            val today = java.util.Date()
            val cal = java.util.Calendar.getInstance(java.util.Locale.US)
            cal.time = today
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)

            val currentDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysToSubtract = when (currentDayOfWeek) {
                java.util.Calendar.SUNDAY -> 6
                java.util.Calendar.MONDAY -> 0
                else -> currentDayOfWeek - java.util.Calendar.MONDAY
            }
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysToSubtract)
            val mondayDate = cal.time

            val todayCal = java.util.Calendar.getInstance(java.util.Locale.US)
            todayCal.time = today
            todayCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            todayCal.set(java.util.Calendar.MINUTE, 59)
            todayCal.set(java.util.Calendar.SECOND, 59)
            todayCal.set(java.util.Calendar.MILLISECOND, 999)
            val endOfTodayStr = todayCal.time

            !sessionDate.before(mondayDate) && !sessionDate.after(endOfTodayStr)
        } catch (e: Exception) {
            false
        }
    }

}

private fun <T1, T2, T3, T4, R> Flow<T1>.combine(
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    transform: suspend (T1, T2, T3, T4) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(this, flow2, flow3, flow4, transform)
