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
import com.apexfit.app.utils.BodyWeightHistory
import com.apexfit.app.utils.AlgorithmEngine
import com.apexfit.app.utils.WorkoutSchedule
import com.apexfit.app.utils.ComplianceEngine
import com.apexfit.app.utils.MacroEngine
import com.apexfit.app.utils.TdeeEngine
import com.apexfit.app.utils.SessionMapper
import com.apexfit.app.utils.StreakResult
import com.apexfit.app.utils.getWindowStartDate
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

    private val todayDateFlow: StateFlow<String> by lazy {
        com.apexfit.app.utils.DateTimeUtils.todayDateFlow()
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = com.apexfit.app.utils.DateTimeUtils.todayDateString()
            )
    }

    private data class BioProfile(
        val heightCm: Double,
        val ageYears: Int,
        val biologicalSex: String,
        val weeklyWorkouts: Int,
        val goalWeightKg: Double?,
        val currentWeightKg: Double
    ) {
        companion object {
            val DEFAULT = BioProfile(
                heightCm = com.apexfit.app.UserDefaults.HEIGHT_CM,
                ageYears = com.apexfit.app.UserDefaults.AGE_YEARS,
                biologicalSex = "male",
                weeklyWorkouts = com.apexfit.app.UserDefaults.WEEKLY_WORKOUTS,
                goalWeightKg = null,
                currentWeightKg = com.apexfit.app.UserDefaults.WEIGHT_KG
            )
        }
    }

    private val bioProfileFlow: StateFlow<BioProfile> by lazy {
        combine(
            dataStore.heightFlow,
            dataStore.ageFlow,
            dataStore.sexFlow,
            getAverageTrainingFrequencyFlow(),
            dataStore.explicitGoalWeightFlow
        ) { height, age, sex, weekly, goalWeight ->
            BioProfile(height, age, sex, weekly, goalWeight, com.apexfit.app.UserDefaults.WEIGHT_KG)
        }
        .combine(getCurrentWeightFlow()) { profile, weight -> profile.copy(currentWeightKg = weight) }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), BioProfile.DEFAULT)
    }

    override val recent90DaySessionsFlow: StateFlow<List<TrainingSession>> by lazy {
        dao.getRecentCompletedSessionsFlow(getWindowStartDate(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
    }

    override val recent90DaySetsFlow: StateFlow<List<ExerciseSet>> by lazy {
        dao.getRecentExerciseSetsFlow(getWindowStartDate(90))
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
            recent90DaySetsFlow,
            dao.getExerciseAttributionFlow()
        ) { sessions, sets, exercises ->
            SessionMapper.buildRichSessions(sessions, sets, exercises.associate { it.id to it.secondaryMuscles })
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    override val weightEntriesFlow: StateFlow<List<WeightEntry>> by lazy {
        todayDateFlow.flatMapLatest { date -> dao.getWeightEntriesSinceFlow(LocalDate.parse(date).minusDays(89).toString()) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    override val nutritionEntriesFlow: StateFlow<List<NutritionEntry>> by lazy {
        dao.getNutritionEntriesSinceFlow(getWindowStartDate(90))
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = appScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    override val sessionReadinessFlow: StateFlow<UiSessionReadiness?> by lazy {
        val todayContextFlow = todayExercisesFlow.mapLatest { exercises ->
            val ids = exercises.map { it.exerciseId }.filter { it.isNotBlank() }.distinct()
            val metadata = if (ids.isEmpty()) emptyMap() else {
                dao.getMetadataForExercises(ids).associateBy { it.exerciseId }
            }
            exercises to metadata
        }

        combine(
            richSessionsFlow,
            nutritionEntriesFlow,
            todayContextFlow,
            sharedTargetsFlow,
            getCurrentWeightFlow()
        ) { sessions, nutritionList, todayContext, targets, weight ->
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
                    val (todayExercises, metadata) = todayContext
                    val scoreResult = com.apexfit.app.utils.ReadinessFinal.buildReadinessInputs(
                        completedSessions = sessions,
                        todayExercises = todayExercises,
                        nutritionLog = mappedNutrition,
                        targets = targets,
                        bodyWeightKg = weight,
                        metadata = metadata
                    )
                    scoreResult.finalScore?.let { scoreResult.toUi() }
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

    // Keep legacy preference-based targets intact; only rotating plans use
    // their actual current-cycle training-day count.
    private val currentCycleFrequency: Flow<Int> by lazy {
        dao.getActivePlanFlow().flatMapLatest { plan ->
            if (plan == null || plan.cycleWeeks == 1) dataStore.weeklyWorkoutsFlow
            else combine(dao.getSessionsForPlanFlow(plan.id), todayDateFlow) { sessions, date ->
                WorkoutSchedule.trainingFrequency(plan, sessions, LocalDate.parse(date))
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override fun getTrainingFrequencyFlow(): Flow<Int> = currentCycleFrequency

    override fun getAverageTrainingFrequencyFlow(): Flow<Int> = dao.getActivePlanFlow().flatMapLatest { plan ->
        if (plan == null || plan.cycleWeeks == 1) dataStore.weeklyWorkoutsFlow
        else dao.getSessionsForPlanFlow(plan.id).map { sessions -> WorkoutSchedule.averageTrainingFrequency(plan, sessions) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override val sharedTargetsFlow: Flow<NutritionTargets> by lazy {
        val weightFlow = dao.getWeightEntriesSinceFlow(getWindowStartDate(90))
            .flowOn(Dispatchers.IO)
        val completedTodayFlow: Flow<Int> = todayDateFlow
            .flatMapLatest { date -> dao.getCompletedSessionCountFlow(date) }
            .distinctUntilChanged()

        val activePlanFlow: Flow<WorkoutPlan?> = dao.getActivePlanFlow()

        val activePlanSessionsFlow: Flow<List<PlanSession>> =
            activePlanFlow.flatMapLatest { plan ->
                if (plan != null) combine(dao.getSessionsForPlanFlow(plan.id), todayDateFlow) { sessions, date ->
                    WorkoutSchedule.weekSessions(plan, sessions, LocalDate.parse(date))
                } else flowOf(emptyList())
            }

        @Suppress("UNCHECKED_CAST")
        combine(
            dataStore.calorieTargetManualFlow,
            dataStore.calorieTargetValueFlow,
            dataStore.goalFlow,
            weightFlow,
            completedTodayFlow,
            activePlanSessionsFlow,
            bioProfileFlow,
            nutritionEntriesFlow,
            todayDateFlow,
            getTrainingFrequencyFlow(),
            dataStore.nutritionDayConfirmationsFlow
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
            @Suppress("UNCHECKED_CAST")
            val nutrition           = values[7] as List<NutritionEntry>
            val todayDate           = values[8] as String
            val currentWeekFrequency = values[9] as Int
            @Suppress("UNCHECKED_CAST")
            val confirmations = values[10] as Map<String, String>

            val latestWeight = BodyWeightHistory.latest(weights.filter { it.date <= todayDate })?.weight
                ?: bio.currentWeightKg
            val calories: Int = if (isManual) {
                manualCals
            } else {
                val tdeeResult = TdeeEngine.calcAdaptiveTDEE(
                    weightLog      = weights,
                    nutritionLog   = nutrition,
                    heightCm       = bio.heightCm,
                    ageYears       = bio.ageYears,
                    biologicalSex  = bio.biologicalSex,
                    weeklyWorkouts = bio.weeklyWorkouts,
                    fallbackWeightKg = latestWeight,
                    nutritionConfirmations = confirmations
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
                weeklyTrainingSessions = currentWeekFrequency,
                goal                   = goal,
                bodyWeightKg           = latestWeight,
                heightCm               = bio.heightCm,
                sex                    = bio.biologicalSex
            )

            val todayDayString = java.time.LocalDate.parse(todayDate).format(DAY_OF_WEEK_FORMAT)
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
                weeklyTrainingSessions = currentWeekFrequency
            )
        }.combine(dataStore.hasBodyDetailsFlow) { targets, ready ->
            if (ready) targets else NutritionTargets(0, 0, 0, 0)
        }.debounce(300L).flowOn(Dispatchers.IO).shareIn(appScope, SharingStarted.WhileSubscribed(5_000), 1)
    }

    override val todayWorkoutChoiceFlow: StateFlow<com.apexfit.app.utils.TodayWorkoutChoice?> by lazy {
        todayDateFlow.flatMapLatest { date ->
            com.apexfit.app.utils.TodayWorkoutResolver.observe(dao, LocalDate.parse(date))
                .map<com.apexfit.app.utils.TodayWorkoutChoice, com.apexfit.app.utils.TodayWorkoutChoice?> { it }
                .onStart { emit(null) }
        }.flowOn(Dispatchers.IO).stateIn(appScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    override val todayExercisesFlow: StateFlow<List<PlanExercise>> by lazy {
        todayWorkoutChoiceFlow.flatMapLatest { choice ->
            choice?.session?.let { dao.getExercisesForSessionFlow(it.id) } ?: flowOf(emptyList())
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
        combine(
            nutritionEntriesFlow,
            richSessionsFlow,
            sharedTargetsFlow,
            dataStore.nutritionDayConfirmationsFlow,
            todayDateFlow
        ) { nutrition, sessions, targets, confirmations, date ->
            ComplianceEngine.calcComplianceScores(nutrition, sessions, targets, nutritionConfirmations = confirmations, today = LocalDate.parse(date)).toUi()
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
        combine(
            nutritionEntriesFlow,
            richSessionsFlow,
            sharedTargetsFlow,
            dataStore.nutritionDayConfirmationsFlow,
            todayDateFlow
        ) { nutrition, sessions, targets, confirmations, date ->
            ComplianceEngine.calcStreaks(nutrition, sessions, targets, nutritionConfirmations = confirmations, today = LocalDate.parse(date))
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
        require(BodyWeightHistory.valid(entry) && entry.unit == "kg") { "Invalid body weight" }
        dataStore.resolveWeightBaseline(dao.getWeightEntries(limit = 1).isNotEmpty())
        dao.insertWeightEntry(entry)
    }

    override suspend fun editWeightEntry(id: Long, weightKg: Double): Boolean {
        require(weightKg.isFinite() && weightKg in com.apexfit.app.utils.AppConstants.MIN_WEIGHT_KG..com.apexfit.app.utils.AppConstants.MAX_WEIGHT_KG)
        dataStore.resolveWeightBaseline(dao.getWeightEntries(limit = 1).isNotEmpty())
        return dao.updateWeightById(id, weightKg) == 1
    }

    override suspend fun removeWeightEntry(id: Long) {
        dataStore.resolveWeightBaseline(dao.getWeightEntries(limit = 1).isNotEmpty())
        dao.deleteWeightEntryById(id)
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
        require(WorkoutSchedule.validationError(plan, sessions) == null) { WorkoutSchedule.validationError(plan, sessions).orEmpty() }
        if (plan.isActive) {
            dao.deactivateAllPlans()
        }
        val anchoredPlan = if (plan.cycleWeeks > 1 && plan.isActive && plan.cycleAnchorMonday == null)
            plan.copy(cycleAnchorMonday = WorkoutSchedule.monday(LocalDate.now()).toString()) else plan
        val persistedPlan = if (anchoredPlan.cycleWeeks > 1) anchoredPlan.copy(
            daysPerWeek = kotlin.math.round(sessions.size.toDouble() / anchoredPlan.cycleWeeks).toInt()
        ) else anchoredPlan
        val planId = dao.upsertWorkoutPlan(persistedPlan)

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

        // Step 4: resolve known exercise IDs in one batch. Imported/manual plans normally
        // already carry catalog IDs, so avoid an N+ name lookup during plan saves.
        val suppliedExerciseIds = exercises
            .map { it.exerciseId }
            .filter { it.isNotBlank() }
            .distinct()
        val knownExercisesById = if (suppliedExerciseIds.isEmpty()) {
            emptyMap()
        } else {
            dao.getExercisesByIds(suppliedExerciseIds).associateBy { it.id }
        }

        val remappedExercises = exercises.map { exercise ->
            val sessionIndex = sessions.indexOfFirst { it.id == exercise.planSessionId }
            require(sessionIndex >= 0) { "Exercise has no matching training day" }

            val knownById = knownExercisesById[exercise.exerciseId]
            val exerciseId = if (knownById != null) {
                knownById.id
            } else {
                // Compatibility fallback for older/manual callers that only supplied a name.
                val existing = dao.getExerciseByName(exercise.name)
                if (existing != null) {
                    existing.id
                } else {
                    val slug = com.apexfit.app.utils.exerciseNameToSlug(exercise.name)
                    val id = if (dao.getExerciseById(slug) == null) slug else java.util.UUID.randomUUID().toString()
                    dao.insertExercises(listOf(Exercise(
                        id = id,
                        name = exercise.name,
                        category = "User Created",
                        primaryMuscle = exercise.muscleGroup,
                        equipmentRequired = "Unknown",
                        isUserCreated = 1
                    )))
                    id
                }
            }
            exercise.copy(
                planSessionId = sessionIdMap[sessionIndex] ?: 0L,
                exerciseId = exerciseId,
                id = 0L
            )
        }

        // Step 5: insert remapped exercises
        dao.insertPlanExercises(remappedExercises)
        com.apexfit.app.utils.ScheduleLedger.capture(db)
    }

    override fun getWeightHistory(): Flow<List<WeightEntry>> {
        return dao.observeAllWeightEntries().flowOn(Dispatchers.IO)
    }

    override fun getNutritionEntries(date: String): Flow<List<NutritionEntry>> {
        return dao.getNutritionForDateFlow(date)
    }

    override fun getTrainingSessions(): Flow<List<TrainingSession>> {
        val cutoffDate = LocalDate.now().minusDays(364).toString()
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
        val cutoffDate = getWindowStartDate(90)
        return dao.getWeightEntriesSince(cutoffDate)
    }

    override fun getAllNutritionEntriesFlow(): Flow<List<NutritionEntry>> {
        val cutoffDate = getWindowStartDate(90)
        return dao.getNutritionEntriesFromFlow(cutoffDate)
    }

    override suspend fun getSetsForSession(sessionId: String): List<ExerciseSet> {
        return dao.getSetsForSession(sessionId)
    }

    override suspend fun getAllCompletedSessions(): List<TrainingSession> {
        return dao.getCompletedSessions()
    }

    override suspend fun getAllExerciseSets(): List<ExerciseSet> {
        val cutoffDate = getWindowStartDate(365)
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
        return combine(dao.observeAllWeightEntries(), dataStore.weightBaselineFlow, todayDateFlow) { entries, fallback, today ->
            BodyWeightHistory.latest(entries.filter { it.date <= today })?.weight
                ?: fallback ?: com.apexfit.app.UserDefaults.WEIGHT_KG
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun scanAndSaveWeeklyPatterns() {
        val cutoffDate = getWindowStartDate(90)

        val dbWeights = dao.getWeightEntriesSince(cutoffDate)
        val engineWeights = BodyWeightHistory.daily(dbWeights)
        
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
        
        val latestWeight = BodyWeightHistory.latest(dbWeights)?.weight ?: getCurrentWeightFlow().first()
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
}
