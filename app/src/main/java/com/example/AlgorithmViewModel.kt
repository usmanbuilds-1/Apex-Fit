@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.HeatmapEntry
import com.example.utils.*
import com.example.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class AlgorithmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.example.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.example.di.ServiceLocator.dataStore(application)
    private val repository = com.example.di.ServiceLocator.repository(application)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 1 — RAW ROOM FLOWS
    // ─────────────────────────────────────────────────────────────────

    private val weightFlow: Flow<List<com.example.utils.WeightEntry>> = dao.getAllWeightEntriesFlow()
        .map { list -> list.map { com.example.utils.WeightEntry(it.date, it.weight) } }
        .flowOn(Dispatchers.IO)

    private val nutritionFlow: Flow<List<com.example.utils.NutritionEntry>> = dao.getAllNutritionEntriesFlow()
        .map { list ->
            list.map {
                com.example.utils.NutritionEntry(
                    date = it.date,
                    calories = it.calories,
                    protein = it.protein.roundToInt(),
                    carbs = it.carbs.roundToInt(),
                    fat = it.fat.roundToInt()
                )
            }
        }
        .flowOn(Dispatchers.IO)

    private val sessionsFlow: Flow<List<com.example.data.TrainingSession>> =
        dao.getRecentCompletedSessionsFlow(
            getDateDaysAgo(90)
        ).flowOn(Dispatchers.IO)



    val targetsFlow: Flow<com.example.utils.NutritionTargets> = combine(
        dataStore.calorieTargetValueFlow,
        weightFlow,
        dataStore.goalFlow
    ) { calorieTarget, weights, goal ->
        val latestWeight = weights.maxByOrNull { it.date }?.weight ?: com.example.UserDefaults.WEIGHT_KG
        // Helms et al. guidance: 1.8g protein per kg total bodyweight for muscle maintenance
        val proteinTarget = (latestWeight * com.example.UserDefaults.PROTEIN_PER_KG).roundToInt().coerceIn(100, 250)
        // Fat range: 25% of absolute daily calorie target
        val fatTarget = (calorieTarget * 0.25 / com.example.utils.AppConstants.CALORIES_PER_GRAM_FAT).roundToInt().coerceIn(45, 120)
        // Carbohydrates: Remainder of daily energetic allocations
        val carbsTarget = ((calorieTarget - (proteinTarget * com.example.utils.AppConstants.CALORIES_PER_GRAM_PROTEIN.toInt()) - (fatTarget * com.example.utils.AppConstants.CALORIES_PER_GRAM_FAT.toInt())) / com.example.utils.AppConstants.CALORIES_PER_GRAM_CARB).roundToInt().coerceIn(100, 500)
        
        com.example.utils.NutritionTargets(
            calories = calorieTarget,
            protein = proteinTarget,
            carbs = carbsTarget,
            fat = fatTarget,
            weeklyTrainingSessions = 4
        )
    }.flowOn(Dispatchers.IO)

    private val goalFlow: Flow<String> = dataStore.goalFlow
        .flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────────
    // SECTION 2 — INTERMEDIATE COMPUTED FLOWS
    // ─────────────────────────────────────────────────────────────────


    private val richSessionsFlow: Flow<List<com.example.utils.TrainingSession>> =
        com.example.di.ServiceLocator.richSessionsFlow

    // ─────────────────────────────────────────────────────────────────
    // SECTION 3 — PUBLIC STATEFLOWS
    // ─────────────────────────────────────────────────────────────────

    val completedSessions: StateFlow<List<UiTrainingSession>> = sessionsFlow
        .map { list -> list.map { it.toUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trendWeight: StateFlow<Double?> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.getCurrentTrendWeight(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightDirection: StateFlow<String> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.getWeightDirection(entries)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "insufficient_data")

    val tdeeResult: StateFlow<com.example.utils.TDEEResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights, nutrition, sessions ->
        Triple(weights, nutrition, sessions)
    }.combine(
        dataStore.heightFlow, dataStore.ageFlow, dataStore.sexFlow
    ) { (weights, nutrition, sessions), height, age, sex ->
        val fourteenDaysAgo = com.example.utils.getDateDaysAgo(14)
        val recentSessions = sessions.filter { it.date >= fourteenDaysAgo && it.completed }.size
        val workoutsFreq = Math.round(recentSessions / 2.0).toInt().coerceIn(1, 7)

        com.example.utils.AlgorithmEngine.calcAdaptiveTDEE(
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
        com.example.utils.TDEEResult(tdee = 0, confidence = "No Data", avgCalories = 0, weightChangeKg = 0.0)
    )


    val streakResult: StateFlow<com.example.utils.StreakResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        withContext(Dispatchers.Default) {
            val validTargets = targets ?: com.example.utils.NutritionTargets(calories = 2650, protein = 160, carbs = 280, fat = 75, weeklyTrainingSessions = 4)
            com.example.utils.AlgorithmEngine.calcStreaks(nutrition, sessions, validTargets)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.StreakResult(com.example.utils.StreakInfo(0), com.example.utils.StreakInfo(0))
    )


    val complianceScores: StateFlow<UiComplianceResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow, richSessionsFlow
    ) { weights: List<com.example.utils.WeightEntry>, nutrition: List<com.example.utils.NutritionEntry>, sessions: List<com.example.utils.TrainingSession> ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.detectPlateau(weights, nutrition, sessions)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.data.PlateauResult(isPlateaued = false).toUi()
    )

    val plateauAlert: StateFlow<com.example.data.PlateauResult?> = plateauResult
        .map { result ->
            if (result.isPlateau) {
                com.example.data.PlateauResult(
                    isPlateaued = true,
                    interventionRecommendation = result.recommendation
                )
            } else {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fatigueResult: StateFlow<UiFatigueResult> = richSessionsFlow
        .map { sessions ->
            val res = withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcFatigueToFitness(sessions)
            }
            res.toUi()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
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
                ratio = result.ratio ?: 0.0,
                riskStatus = result.statusLabel
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.data.FatigueRatio(0.0, "No Data"))

    val muscleVolumes: StateFlow<Map<String, Int>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
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
                muscleSetsMap
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    val muscleHeatmap: StateFlow<Map<String, HeatmapEntry>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcMuscleHeatmap(sessions, days = 7)
            }
        }
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

    val injuryRiskSignals: StateFlow<List<String>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                val risks = com.example.utils.AlgorithmEngine.detectInjuryRiskSignals(sessions)
                risks.ifEmpty {
                    listOf("Recovery indicators normal. High-intensity load distributed optimally within target thresholds.")
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val hypertrophyQualityScores: StateFlow<Map<String, Double>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcEffectiveSets(sessions)
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
                com.example.utils.AlgorithmEngine.calcWeeklyVolumePerMuscle(sessions)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val targets: StateFlow<com.example.utils.NutritionTargets?> = targetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)



    val todayExercisesFlow: Flow<List<com.example.data.PlanExercise>> = repository.getActivePlan().flatMapLatest { plan ->
        val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
        sessions.flatMapLatest { sessionList ->
            val todayDayString = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
            val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    val sessionReadiness: StateFlow<UiSessionReadiness?> = combine(
        nutritionFlow,
        richSessionsFlow,
        targetsFlow,
        todayExercisesFlow
    ) { nutrition, sessions, targets, todayExercises ->
        val res = withContext(Dispatchers.Default) {
            if (sessions.isEmpty() || targets == null) null
            else {
                val scoreResult = com.example.utils.ReadinessFinal.buildReadinessInputs(
                    completedSessions = sessions,
                    todayExercises = todayExercises,
                    nutritionLog = nutrition,
                    targets = targets
                )
                
                val predictionText = "Systemic CNS readiness is ${scoreResult.systemicReadiness}%. " +
                        "Acute-to-chronic ratio modifier is ${String.format(java.util.Locale.US, "%.2f", scoreResult.acrModifier)}."

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val detectedPatterns: StateFlow<List<com.example.data.DetectedPatternEntity>> = dao.getAllDetectedPatternsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deloadRecommendation: StateFlow<UiDeloadResult> = combine(
        richSessionsFlow, complianceScore
    ) { sessions, score ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.calcDeloadRecommendation(sessions, score)
        }
        res.toUi()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.DeloadResult(recommendation = "No data yet", urgency = "low", signals = 0).toUi()
    )

    // ─────────────────────────────────────────────────────────────────
    // SECTION 4 — COACH CONTEXT STRING
    // ─────────────────────────────────────────────────────────────────


    private fun isDateInCurrentWeekSinceMonday(dateStr: String): Boolean {
        return try {
            val sessionDate = com.example.utils.DateTimeUtils.parseDate(dateStr) ?: return false

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
