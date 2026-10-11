package com.apexfit.app.data.repository

import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import androidx.room.withTransaction
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import com.apexfit.app.ui.models.UiComplianceResult
import com.apexfit.app.ui.models.UiSessionReadiness
import com.apexfit.app.ui.models.toUi
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.ComplianceEngine
import com.apexfit.app.utils.MacroEngine
import com.apexfit.app.utils.TdeeEngine
import com.apexfit.app.utils.SessionMapper
import com.apexfit.app.utils.StreakResult
import com.apexfit.app.utils.getDateDaysAgo
import kotlin.math.roundToInt

/**
 * FitnessRepositoryImpl provides clean, centralized data access delegating to the local database and datastore.
 */
/**
 * Implementation of [FitnessRepository].
 *
 * Architecture Note (Fix D-L7):
 * Repository flows return cold Flow instances originating from Room DAOs.
 * In ViewModels that collect the same repository flow multiple times or across multiple UI states,
 * repeated calls should be consolidated into a single stateIn-cached flow at the ViewModel level
 * (using viewModelScope and SharingStarted.WhileSubscribed(5_000)) to prevent redundant Room subscriptions.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@Singleton
class FitnessRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: FitnessDao,
    private val dataStore: DataStoreManager
) : FitnessRepository {

    override var appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor(
        db: AppDatabase,
        dao: FitnessDao,
        dataStore: DataStoreManager,
        scope: CoroutineScope
    ) : this(db, dao, dataStore) {
        this.appScope = scope
    }

    private val DAY_OF_WEEK_FORMAT = java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US)

    private data class BioProfile(
        val heightCm: Double,
        val ageYears: Int,
        val biologicalSex: String,
        val weeklyWorkouts: Int,
        val goalWeightKg: Double
    ) {
        companion object {
            val DEFAULT = BioProfile(
                heightCm = com.apexfit.app.UserDefaults.HEIGHT_CM,
                ageYears = com.apexfit.app.UserDefaults.AGE_YEARS,
                biologicalSex = "male",
                weeklyWorkouts = com.apexfit.app.UserDefaults.WEEKLY_WORKOUTS,
                goalWeightKg = com.apexfit.app.UserDefaults.WEIGHT_KG
            )
        }
    }

    private val bioProfileFlow: StateFlow<BioProfile> by lazy {
        combine(
            dataStore.heightFlow,
            dataStore.ageFlow,
            dataStore.sexFlow,
            dataStore.weeklyWorkoutsFlow,
            dataStore.goalWeightFlow
        ) { height, age, sex, weekly, goalWeight ->
            BioProfile(height, age, sex, weekly, goalWeight)
        }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), BioProfile.DEFAULT)
    }

    override val recent90DaySessionsFlow: StateFlow<List<TrainingSession>> by lazy {
        dao.getRecentCompletedSessionsFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    override val recent90DaySetsFlow: StateFlow<List<ExerciseSet>> by lazy {
        dao.getRecentExerciseSetsFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    override val richSessionsFlow: StateFlow<List<RichTrainingSession>> by lazy {
        combine(
            recent90DaySessionsFlow,
            recent90DaySetsFlow
        ) { sessions, sets ->
            SessionMapper.buildRichSessions(sessions, sets)
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    override val weightEntriesFlow: StateFlow<List<WeightEntry>> by lazy {
        dao.getWeightEntriesSinceFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    override val nutritionEntriesFlow: StateFlow<List<NutritionEntry>> by lazy {
        dao.getNutritionEntriesSinceFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    override val sessionReadinessFlow: StateFlow<UiSessionReadiness?> by lazy {
        combine(
            richSessionsFlow,
            dataStore.calorieTargetValueFlow,
            dataStore.currentWeightFlow,
            nutritionEntriesFlow
        ) { sessions, calorieTarget, weight, nutritionList ->
            try {
                if (sessions.isEmpty()) {
                    null
                } else {
                    val mappedNutrition = nutritionList.map {
                        com.apexfit.app.utils.NutritionEntry(
                            date = it.date,
                            calories = it.calories,
                            protein = it.protein.roundToInt(),
                            carbs = it.carbs.roundToInt(),
                            fat = it.fat.roundToInt()
                        )
                    }
                    val scoreResult = com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                        sessions = sessions,
                        nutritionLog = mappedNutrition,
                        bodyWeightKg = weight,
                        calorieTarget = calorieTarget
                    )
                    scoreResult.toUi()
                }
            } catch (e: Exception) {
                android.util.Log.e("FitnessRepositoryImpl", "Readiness scoring failed", e)
                null
            }
        }
        .debounce(300L)
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    override val sharedTargetsFlow: Flow<NutritionTargets> by lazy {
        val weightFlow = dao.getWeightEntriesSinceFlow(getDateDaysAgo(90))
            .flowOn(Dispatchers.IO)
        val completedTodayFlow: Flow<Int> = dao
            .getCompletedSessionCountFlow(com.apexfit.app.utils.DateTimeUtils.todayDateString())
            .distinctUntilChanged()

        val activePlanFlow: Flow<WorkoutPlan?> = dao.getActivePlanFlow()

        val activePlanSessionsFlow: Flow<List<PlanSession>> =
            activePlanFlow.flatMapLatest { plan ->
                if (plan != null) dao.getSessionsForPlanFlow(plan.id)
                else flowOf(emptyList())
            }

        @Suppress("UNCHECKED_CAST")
        combine(
            dataStore.calorieTargetManualFlow,
            dataStore.calorieTargetValueFlow,
            dataStore.goalFlow,
            weightFlow,
            completedTodayFlow,
            activePlanSessionsFlow,
            bioProfileFlow
        ) { values ->
            val isManual            = values[0] as Boolean
            val manualCals          = values[1] as Int
            val goal                = values[2] as String
            @Suppress("UNCHECKED_CAST")
            val weights             = values[3] as List<WeightEntry>
            val completedTodayCount = values[4] as Int
            @Suppress("UNCHECKED_CAST")
            val planSessions        = values[5] as List<PlanSession>
            val bio                 = values[6] as BioProfile

            val latestWeight = weights.maxByOrNull { it.date }?.weight
                ?: com.apexfit.app.UserDefaults.WEIGHT_KG
            val calories: Int = if (isManual) {
                manualCals
            } else {
                val recentWeights = weights
                    .sortedByDescending { it.date }
                    .take(14)
                    .map { com.apexfit.app.utils.WeightEntry(date = it.date, weight = it.weight) }
                val tdeeResult = TdeeEngine.calcAdaptiveTDEE(
                    weightLog      = recentWeights,
                    nutritionLog   = emptyList(),
                    heightCm       = bio.heightCm,
                    ageYears       = bio.ageYears,
                    biologicalSex  = bio.biologicalSex,
                    weeklyWorkouts = bio.weeklyWorkouts
                )
                TdeeEngine.suggestCaloricTarget(
                    tdee            = tdeeResult.tdee ?: com.apexfit.app.UserDefaults.CALORIES,
                    goal            = goal,
                    currentWeightKg = latestWeight,
                    goalWeightKg    = bio.goalWeightKg,
                    heightCm        = bio.heightCm,
                    ageYears        = bio.ageYears,
                    sex             = bio.biologicalSex
                )
            }

            val cycled = MacroEngine.calcCycledTargets(
                weeklyCalorieTarget    = calories,
                weeklyTrainingSessions = bio.weeklyWorkouts,
                goal                   = goal,
                bodyWeightKg           = latestWeight,
                heightCm               = bio.heightCm,
                sex                    = bio.biologicalSex
            )

            val todayDayString = java.time.LocalDate.now().format(DAY_OF_WEEK_FORMAT)
            val todayPlanned = planSessions.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
            val isPlannedTraining = todayPlanned != null &&
                !todayPlanned.label.contains("Rest", ignoreCase = true) &&
                todayPlanned.focus != "Muscle Recovery & Rest"
            val isTodayTraining = completedTodayCount > 0 || isPlannedTraining

            val todayCalories = if (isTodayTraining) cycled.trainingDayCalories else cycled.restDayCalories
            val todayProtein  = if (isTodayTraining) cycled.trainingDayProtein else cycled.restDayProtein
            val todayCarbs    = if (isTodayTraining) cycled.trainingDayCarbs else cycled.restDayCarbs
            val todayFat      = cycled.fat

            NutritionTargets(
                calories               = todayCalories,
                protein                = todayProtein,
                carbs                  = todayCarbs,
                fat                    = todayFat,
                weeklyTrainingSessions = bio.weeklyWorkouts
            )
        }.debounce(300L).flowOn(Dispatchers.IO).shareIn(appScope, SharingStarted.WhileSubscribed(5_000), 1)
    }

    override val todayExercisesFlow: StateFlow<List<PlanExercise>> by lazy {
        getActivePlan().flatMapLatest { plan ->
            val sessions = if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
            sessions.flatMapLatest { sessionList ->
                val todayDayString = java.time.LocalDate.now().format(DAY_OF_WEEK_FORMAT)
                val todaySession = sessionList.firstOrNull { it.day.equals(todayDayString, ignoreCase = true) }
                if (todaySession != null) dao.getExercisesForSessionFlow(todaySession.id) else flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.IO)
        .catch { e ->
            android.util.Log.e("FitnessRepositoryImpl", "todayExercisesFlow error", e)
            emit(emptyList())
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    }

    override val complianceScoresFlow: StateFlow<UiComplianceResult> by lazy {
        val mappedNutritionFlow = nutritionEntriesFlow.map { list ->
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
        combine(
            mappedNutritionFlow,
            richSessionsFlow,
            sharedTargetsFlow
        ) { nutrition, sessions, targets ->
            val res = ComplianceEngine.calcComplianceScores(nutrition, sessions, targets)
            res.toUi()
        }
        .debounce(300L)
        .flowOn(Dispatchers.Default)
        .catch { e ->
            android.util.Log.e("FitnessRepositoryImpl", "complianceScoresFlow error", e)
            emit(com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi())
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.apexfit.app.utils.ComplianceResult(calories = 0, protein = 0, training = 0, overall = 0, weakestDay = null).toUi()
        )
    }

    override val streakResultFlow: StateFlow<StreakResult> by lazy {
        val mappedNutritionFlow = nutritionEntriesFlow.map { list ->
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
        combine(
            mappedNutritionFlow,
            richSessionsFlow,
            sharedTargetsFlow
        ) { nutrition, sessions, targets ->
            ComplianceEngine.calcStreaks(nutrition, sessions, targets)
        }
        .debounce(300L)
        .flowOn(Dispatchers.Default)
        .catch { e ->
            android.util.Log.e("FitnessRepositoryImpl", "streakResultFlow error", e)
            emit(StreakResult(com.apexfit.app.data.StreakInfo(0), com.apexfit.app.data.StreakInfo(0)))
        }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreakResult(com.apexfit.app.data.StreakInfo(0), com.apexfit.app.data.StreakInfo(0))
        )
    }

    override suspend fun logWeightEntry(entry: WeightEntry) {
        dao.insertWeightEntry(entry)
    }

    override suspend fun logNutritionEntry(entry: NutritionEntry) {
        dao.insertNutritionEntry(entry)
    }

    override suspend fun logExerciseSet(set: ExerciseSet) {
        dao.insertExerciseSet(set)
    }

    override suspend fun savePlan(plan: WorkoutPlan): Long {
        return dao.insertWorkoutPlan(plan)
    }

    override fun getWorkoutPlans(): Flow<List<WorkoutPlan>> {
        return dao.getAllPlansFlow()
    }

    override fun getExercisesForSession(sessionId: Long): Flow<List<PlanExercise>> {
        return dao.getExercisesForSessionFlow(sessionId)
    }

    override suspend fun updateWorkoutPlan(plan: WorkoutPlan, sessions: List<PlanSession>, exercises: List<PlanExercise>) = db.withTransaction {
        if (plan.isActive) {
            dao.deactivateAllPlans()
        }
        val planId = dao.upsertWorkoutPlan(plan)

        // Step 1: delete old sessions (cascade deletes their exercises too)
        dao.deleteSessionsForPlan(planId)

        // Step 2: insert new sessions and CAPTURE the new generated IDs
        val newSessionIds: List<Long> = dao.insertPlanSessions(
            sessions.map { it.copy(planId = planId, id = 0L) }
        )

        // Step 3: build a map from old session position to new session ID
        val sessionIdMap: Map<Int, Long> = newSessionIds.mapIndexed { index, newId ->
            index to newId
        }.toMap()

        // Step 4: remap each exercise's planSessionId to the correct new ID and resolve exerciseId FK
        val remappedExercises = exercises.map { exercise ->
            val sessionIndex = sessions.indexOfFirst { it.id == exercise.planSessionId }
                .takeIf { it >= 0 } ?: 0
            val exerciseId = dao.getExerciseByName(exercise.name)?.id
                ?: com.apexfit.app.utils.exerciseNameToSlug(exercise.name)
            exercise.copy(
                planSessionId = sessionIdMap[sessionIndex] ?: 0L,
                exerciseId = exerciseId,
                id = 0L
            )
        }

        // Step 5: insert remapped exercises
        dao.insertPlanExercises(remappedExercises)
    }

    override fun getWeightHistory(): Flow<List<WeightEntry>> {
        val cutoffDate = LocalDate.now().minusDays(90).toString()
        return dao.getWeightEntriesFromFlow(cutoffDate)
    }

    override fun getNutritionEntries(date: String): Flow<List<NutritionEntry>> {
        return dao.getNutritionForDateFlow(date)
    }

    override fun getTrainingSessions(): Flow<List<TrainingSession>> {
        val cutoffDate = LocalDate.now().minusDays(365).toString()
        return dao.getRecentCompletedSessionsFlow(cutoffDate)
    }

    override fun getBodyMeasurements(): Flow<List<BodyMeasurement>> {
        return dao.getBodyMeasurementsFlow()
    }

    override fun getActivePlan(): Flow<WorkoutPlan?> {
        return dao.getActivePlanFlow()
    }

    override fun getPlanSessions(planId: Long): Flow<List<PlanSession>> {
        return dao.getSessionsForPlanFlow(planId)
    }

    override suspend fun getLastSessionWeights(exerciseName: String): List<Pair<Double, Int>> {
        val slug = com.apexfit.app.utils.exerciseNameToSlug(exerciseName)
        val sets = dao.getLastSetsForExercise(slug)
        return sets
            .groupBy { it.sessionId }
            .values
            .firstOrNull()
            ?.map { Pair(it.weight, it.reps) }
            ?: emptyList()
    }

    override suspend fun getLastSetForExercise(exerciseId: String): LastSetWithDate? {
        return dao.getLastSetForExercise(exerciseId)
    }

    override suspend fun getLastSetsForExercise(exerciseId: String): List<com.apexfit.app.data.ExerciseSet> =
        dao.getLastSetsForExercise(exerciseId)

    override suspend fun getAllSetsForExercises(exerciseIds: List<String>): List<ExerciseSet> =
        dao.getAllSetsForExercises(exerciseIds)

    override suspend fun getLastSetsForExercises(exerciseIds: List<String>): List<ExerciseSet> =
        dao.getLastSetsForExercises(exerciseIds)

    override suspend fun getPRsForExercise(exerciseId: String): List<PersonalRecord> {
        return dao.getPRsForExercise(exerciseId)
    }

    override suspend fun insertSessionAtomic(session: TrainingSession, sets: List<ExerciseSet>) {
        dao.insertSessionAtomic(session, sets)
    }

    override suspend fun insertSessionWithPRsAtomic(session: TrainingSession, sets: List<ExerciseSet>, prs: List<PersonalRecord>) {
        dao.insertSessionWithPRsAtomic(session, sets, prs)
    }

    override suspend fun insertPersonalRecord(record: PersonalRecord) {
        dao.insertPersonalRecord(record)
    }

    override suspend fun getAllWeightEntries(): List<WeightEntry> {
        val cutoffDate = LocalDate.now().minusDays(90).toString()
        return dao.getWeightEntriesSince(cutoffDate)
    }

    override fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>> {
        val cutoffDate = LocalDate.now().minusDays(90).toString()
        return dao.getNutritionEntriesFromFlow(cutoffDate)
    }

    override suspend fun getSetsForSession(sessionId: String): List<ExerciseSet> {
        return dao.getSetsForSession(sessionId)
    }

    override suspend fun getAllCompletedSessions(): List<TrainingSession> {
        return dao.getCompletedSessions()
    }

    override suspend fun getAllExerciseSets(): List<ExerciseSet> {
        val cutoffDate = LocalDate.now().minusDays(365).toString()
        return dao.getRecentExerciseSets(cutoffDate)
    }

    override suspend fun getRecentCompletedSessions(cutoffDate: String): List<TrainingSession> {
        return dao.getRecentCompletedSessions(cutoffDate)
    }

    override suspend fun getRecentExerciseSets(cutoffDate: String): List<ExerciseSet> {
        return dao.getRecentExerciseSets(cutoffDate)
    }

    override fun getCalorieTargetFlow(): Flow<Int> {
        return dataStore.calorieTargetValueFlow
    }

    override fun getGoalFlow(): Flow<String> {
        return dataStore.goalFlow
    }

    override fun getCurrentWeightFlow(): Flow<Double> {
        return dataStore.currentWeightFlow
    }

    override suspend fun scanAndSaveWeeklyPatterns() {
        val cutoffDate = LocalDate.now().minusDays(90).toString()

        val dbWeights = dao.getWeightEntriesSince(cutoffDate)
        val engineWeights = dbWeights.groupBy { it.date }.map { (date, list) ->
            com.apexfit.app.utils.WeightEntry(date, list.map { it.weight }.average())
        }.sortedBy { it.date }
        
        val dbNutrition = dao.getNutritionEntriesSince(cutoffDate)
        val engineNutrition = dbNutrition.groupBy { it.date }.map { (date, list) ->
            com.apexfit.app.utils.NutritionEntry(
                date = date,
                calories = list.sumOf { it.calories },
                protein = list.sumOf { it.protein },
                carbs = list.sumOf { it.carbs },
                fat = list.sumOf { it.fat }
            )
        }.sortedBy { it.date }
        
        val dbSessions = dao.getRecentCompletedSessions(cutoffDate)
        val muscleVolumes = dao.getMuscleGroupVolumesSince(cutoffDate)
        val sessions = dbSessions.map { session ->
            com.apexfit.app.utils.TrainingSession(
                date = session.date,
                sessionType = session.sessionType,
                completed = session.completed,
                sessionFeel = session.sessionFeel,
                durationMinutes = session.durationMinutes,
                exercises = emptyList()
            )
        }
        
        val latestWeight = engineWeights.lastOrNull()?.weight ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        val proteinTarget = (latestWeight * com.apexfit.app.UserDefaults.PROTEIN_PER_KG)
            .coerceIn(100.0, 250.0)

        val detected = com.apexfit.app.utils.PatternDetector.scanAllPatterns(
            weightLog = engineWeights,
            nutritionLog = engineNutrition,
            trainingLog = sessions,
            proteinTarget = proteinTarget
        )
        
        val entities = detected.map { p ->
            com.apexfit.app.data.DetectedPatternEntity(
                id = p.id,
                type = p.type,
                title = p.title,
                description = p.description,
                confidence = p.confidence,
                actionable = p.actionable,
                detectedAt = p.detectedAt
            )
        }
        db.withTransaction {
            dao.clearAllDetectedPatterns()
            dao.insertDetectedPatterns(entities)
        }
    }

    override suspend fun insertWeightEntry(entry: com.apexfit.app.data.WeightEntry) = dao.insertWeightEntry(entry)

    override suspend fun deleteWeightEntryById(id: Long) = dao.deleteWeightEntryById(id)

    override fun getAllPlanExercisesFlow(): kotlinx.coroutines.flow.Flow<List<com.apexfit.app.data.PlanExercise>> =
        dao.getAllPlanExercisesFlow()

    override suspend fun getWeightEntriesSince(cutoffDate: String): List<com.apexfit.app.data.WeightEntry> =
        dao.getWeightEntriesSince(cutoffDate)

    override suspend fun getNutritionEntriesSince(cutoffDate: String): List<com.apexfit.app.data.NutritionEntry> =
        dao.getNutritionEntriesSince(cutoffDate)

    override suspend fun getAllWorkoutSessions(): List<com.apexfit.app.data.TrainingSession> =
        dao.getAllTrainingSessions()

    override suspend fun getRecentPersonalRecordsWithNames(limit: Int): List<com.apexfit.app.data.PersonalRecordWithName> =
        dao.getRecentPersonalRecordsWithNames(limit).first()

    override suspend fun clearAllData() = db.clearAllTables()

    override suspend fun activatePlan(planId: Long) = db.withTransaction {
        dao.deactivateAllPlans()
        dao.activatePlan(planId)
    }
}
