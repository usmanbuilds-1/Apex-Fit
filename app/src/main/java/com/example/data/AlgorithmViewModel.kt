@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.utils.*
import com.example.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlgorithmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)

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
        // Helms et al. guidance: 1.8g protein per kg total bodyweight for muscle maintenance
        val proteinTarget = (latestWeight * 1.8).toInt().coerceIn(100, 250)
        // Fat range: 25% of absolute daily calorie target
        val fatTarget = (calorieTarget * 0.25 / 9.0).toInt().coerceIn(45, 120)
        // Carbohydrates: Remainder of daily energetic allocations
        val carbsTarget = ((calorieTarget - (proteinTarget * 4) - (fatTarget * 9)) / 4).toInt().coerceIn(100, 500)
        
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

    private val trendPointsFlow: Flow<List<com.example.utils.TrendPoint>> = weightFlow
        .map { entries ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcTrendWeight(entries)
            }
        }

    private val richSessionsFlow: Flow<List<com.example.utils.TrainingSession>> = combine(
        sessionsFlow, setsFlow
    ) { sessions, sets ->
        withContext(Dispatchers.Default) {
            buildRichSessions(sessions, sets)
        }
    }

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Stable")

    val tdeeResult: StateFlow<com.example.utils.TDEEResult> = combine(
        weightFlow,
        nutritionFlow,
        dataStore.heightFlow,
        dataStore.ageFlow,
        dataStore.sexFlow,
        richSessionsFlow
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val weights = args[0] as List<com.example.utils.WeightEntry>
        @Suppress("UNCHECKED_CAST")
        val nutrition = args[1] as List<com.example.utils.NutritionEntry>
        val height = args[2] as Double
        val age = args[3] as Int
        val sex = args[4] as String
        @Suppress("UNCHECKED_CAST")
        val sessions = args[5] as List<com.example.utils.TrainingSession>

        withContext(Dispatchers.Default) {
            val fourteenDaysAgo = com.example.utils.AlgorithmEngine.getDateDaysAgo(14)
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
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.TDEEResult(tdee = 2440, confidence = "low (Mifflin-St Jeor)", avgCalories = 0, weightChangeKg = 0.0)
    )

    val adaptiveTdee: StateFlow<com.example.utils.TDEEResult?> = tdeeResult

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

    val weeklyReportText = MutableStateFlow<String>("")

    val complianceScores: StateFlow<com.example.utils.ComplianceResult> = combine(
        nutritionFlow, richSessionsFlow, targetsFlow
    ) { nutrition, sessions, targets ->
        withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.calcComplianceScores(nutrition, sessions, targets)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null)
    )

    val complianceScore: StateFlow<Int> = complianceScores
        .map { it.overall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 88)

    val plateauResult: StateFlow<UiPlateauResult> = combine(
        weightFlow, nutritionFlow
    ) { weights: List<com.example.utils.WeightEntry>, nutrition: List<com.example.utils.NutritionEntry> ->
        val res = withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.detectPlateau(weights, nutrition)
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

    val fatigueResult: StateFlow<com.example.utils.FatigueResult> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcFatigueToFitness(sessions)
            }
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
            )
        )

    val fatigueRatio: StateFlow<com.example.data.FatigueRatio> = fatigueResult
        .map { result ->
            com.example.data.FatigueRatio(
                ratio = result.ratio ?: 1.0,
                riskStatus = result.statusLabel
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.data.FatigueRatio(1.1, "Optimal"))

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
            mapOf(
                "chest" to 12, "back" to 14, "front delts" to 6, "side delts" to 8, "rear delts" to 5,
                "biceps" to 8, "triceps" to 8, "forearms" to 4, "trapezius" to 6, "neck" to 1,
                "abs" to 6, "obliques" to 4, "transverse abdominis" to 3, "lower back" to 5,
                "glutes" to 6, "quadriceps" to 10, "hamstrings" to 8, "calves" to 5,
                "hip abductors" to 4, "hip adductors" to 4, "rotator cuff" to 3,
                "serratus anterior" to 3, "tibialis anterior" to 2
            )
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

    private val _exerciseProgression = MutableStateFlow<List<Pair<String, Double>>>(
        listOf(
            "Session 1" to 80.0,
            "Session 2" to 82.5,
            "Session 3" to 82.5,
            "Session 4" to 85.0,
            "Session 5" to 87.5,
            "Session 6" to 90.0
        )
    )
    val exerciseProgression: StateFlow<List<Pair<String, Double>>> = _exerciseProgression.asStateFlow()

    private val _progressionStatus = MutableStateFlow("PROGRESSING")
    val progressionStatus: StateFlow<String> = _progressionStatus.asStateFlow()

    val allPRs: StateFlow<List<UiPersonalRecord>> = dao.getAllPRsFlow()
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
            listOf(
                "High RPE clustering across consecutive sessions",
                "Acute to chronic volume ratio above 1.5"
            )
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
            mapOf(
                "chest" to 11.5,
                "back" to 15.0,
                "side delts" to 8.2,
                "quads" to 14.5,
                "hamstrings" to 6.2
            )
        )

    val effectiveSets: StateFlow<Map<String, Double>> = setsFlow
        .map { sets ->
            withContext(Dispatchers.Default) {
                val algSets = sets
                    .filter { !it.isWarmup }
                    .map { com.example.utils.ExerciseSet(it.weight, it.reps, it.rpe, it.isWarmup, it.completed) }
                com.example.utils.AlgorithmEngine.calcEffectiveSets(
                    listOf(
                        com.example.utils.TrainingSession(
                            date = "",
                            sessionType = "",
                            completed = true,
                            exercises = listOf(
                                com.example.utils.ExerciseLog("", "", "", algSets)
                            )
                        )
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weeklyVolume: StateFlow<Map<String, List<Pair<String, Double>>>> = richSessionsFlow
        .map { sessions ->
            withContext(Dispatchers.Default) {
                com.example.utils.AlgorithmEngine.calcWeeklyVolumePerMuscle(sessions)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val weeklyReport: StateFlow<String?> = dao.getAllWeeklyReportsFlow()
        .map { reports -> reports.firstOrNull()?.geminiResponse }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val targets: StateFlow<com.example.utils.NutritionTargets?> = targetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionReadiness: StateFlow<com.example.utils.SessionReadiness?> = combine(
        nutritionFlow,
        richSessionsFlow,
        weightFlow,
        targetsFlow
    ) { nutrition, sessions, weights, targets ->
        withContext(Dispatchers.Default) {
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val detectedPatterns: StateFlow<List<com.example.data.DetectedPatternEntity>> = dao.getAllDetectedPatternsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deloadRecommendation: StateFlow<com.example.utils.DeloadResult> = combine(
        richSessionsFlow, complianceScore
    ) { sessions, score ->
        withContext(Dispatchers.Default) {
            com.example.utils.AlgorithmEngine.calcDeloadRecommendation(sessions, score)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.example.utils.DeloadResult(recommendation = "No data yet", urgency = "low", signals = 0)
    )

    // ─────────────────────────────────────────────────────────────────
    // SECTION 4 — COACH CONTEXT STRING
    // ─────────────────────────────────────────────────────────────────

    fun buildCoachContext(): String {
        val tw = trendWeight.value
        val dir = weightDirection.value
        val fatigue = fatigueResult.value
        val compliance = complianceScores.value
        val plateau = plateauResult.value
        val deload = deloadRecommendation.value
        val readiness = sessionReadiness.value
        val tdee = tdeeResult.value
        val injuries = injuryRiskSignals.value
        val patterns = detectedPatterns.value

        return buildString {
            appendLine("Current user data:")
            appendLine("Trend weight ${tw?.let { "%.1f".format(it) } ?: "unknown"} kg moving $dir.")
            appendLine("Fatigue status ${fatigue.statusLabel} with ratio ${fatigue.ratio?.let { "%.2f".format(it) } ?: "N/A"}.")
            appendLine("Compliance this week: calories ${compliance.calories}%, protein ${compliance.protein}%, training ${compliance.training}%, overall ${compliance.overall}%.")
            appendLine("Plateau status: ${if (plateau.isPlateau) "yes" else "no"}.")
            appendLine("Weakest nutrition day: ${compliance.weakestDay ?: "unknown"}.")
            appendLine("Deload recommendation: ${deload.recommendation} (urgency: ${deload.urgency}, signals: ${deload.signals}).")
            appendLine("Injury signals: ${if (injuries.isEmpty()) "none" else injuries.joinToString(", ")}.")
            appendLine("Session readiness today: ${readiness?.score ?: "N/A"} — ${readiness?.label ?: "insufficient data"}.")
            appendLine("TDEE estimate: ${tdee.tdee ?: "calculating"} kcal (confidence: ${tdee.confidence}).")
            appendLine("Detected patterns: ${if (patterns.isEmpty()) "none yet" else patterns.take(3).joinToString(", ") { it.title }}.")
        }.trim()
    }

    // ─────────────────────────────────────────────────────────────────
    // SECTION 5 — PRIVATE HELPERS & COMPATIBILITY METHODS
    // ─────────────────────────────────────────────────────────────────

    fun scanAndSaveWeeklyPatterns() {
        viewModelScope.launch {
            val dbWeights = dao.getAllWeightEntries()
            val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
                com.example.utils.WeightEntry(date, list.map { it.weight }.average())
            }.sortedBy { it.date }
            
            val dbNutrition = dao.getAllNutritionEntriesFlow().first()
            val engineNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
                com.example.utils.NutritionEntry(
                    date = date,
                    calories = list.sumOf { it.calories },
                    protein = list.sumOf { it.protein }.toInt(),
                    carbs = list.sumOf { it.carbs }.toInt(),
                    fat = list.sumOf { it.fat }.toInt()
                )
            }.sortedBy { it.date }
            
            val sessions = loadFullTrainingSessions()
            
            val detected = com.example.utils.PatternDetector.scanAllPatterns(
                weightLog = engineWeights,
                nutritionLog = engineNutrition,
                trainingLog = sessions,
                sleepLog = emptyList()
            )
            
            val entities = detected.map { p ->
                com.example.data.DetectedPatternEntity(
                    id = p.id,
                    type = p.type,
                    title = p.title,
                    description = p.description,
                    confidence = p.confidence,
                    actionable = p.actionable,
                    detectedAt = p.detectedAt
                )
            }
            dao.clearAllDetectedPatterns()
            dao.insertDetectedPatterns(entities)
        }
    }

    private suspend fun loadFullTrainingSessions(): List<com.example.utils.TrainingSession> {
        val dbSessions = dao.getAllCompletedSessions()
        return dbSessions.map { session ->
            val dbSets = dao.getSetsForSession(session.id)
            val exerciseLogs = dbSets.groupBy { it.exerciseId }.map { (exId, sets) ->
                val firstSet = sets.firstOrNull()
                val name = firstSet?.exerciseName ?: "Exercise"
                val muscle = firstSet?.muscleGroup ?: "General"
                com.example.utils.ExerciseLog(
                    id = exId,
                    name = name,
                    muscleGroup = muscle,
                    sets = sets.map { s ->
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
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = exerciseLogs
            )
        }
    }

    private fun buildRichSessions(
        sessions: List<com.example.data.TrainingSession>,
        sets: List<com.example.data.ExerciseSet>
    ): List<com.example.utils.TrainingSession> {
        val setsBySession = sets.groupBy { it.sessionId }

        return sessions.map { session ->
            val sessionSets = setsBySession[session.id] ?: emptyList()
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
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = exercises
            )
        }
    }

    private fun isDateInCurrentWeekSinceMonday(dateStr: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val sessionDate = sdf.parse(dateStr) ?: return false

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

    fun calculateTdeeAndMetabolism() {}
    fun calculateTrendWeight() {}
    fun calculateFatigueRatio() {}
    fun calculateCompliance() {}
    fun calculateSessionReadinessAuto() {}
    fun calculateSessionReadiness(todaySessionType: String) {}
    fun detectPlateaus() {}
    fun updateVolumeLoadProgression() {}
    fun checkPersonalRecords() {}
    fun identifyWeakPoints() {}
    fun scoreHypertrophyQuality() {}
    fun scanInjuryRisks() {}
    fun planDeload() {}
    fun generateWeeklyReport() {}
}
