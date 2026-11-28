@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
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

    fun observeStrengthExercises(today: java.time.LocalDate): Flow<List<StrengthExerciseOption>> =
        dao.observeStrengthExercises(today.toString()).distinctUntilChanged()

    fun observeStrengthHistory(exerciseId: String, start: java.time.LocalDate, end: java.time.LocalDate):
        Flow<List<StrengthSessionPoint>> = dao.observeStrengthSets(exerciseId, start.toString(), end.toString())
            .map { StrengthAnalytics.calculate(it, exerciseId, start, end) }
            .distinctUntilChanged().flowOn(Dispatchers.Default)

    val setLoadConfirmations = dataStore.setLoadConfirmationsFlow

    suspend fun confirmSetLoad(observation: StrengthObservation, basis: RecordedLoadBasis?, setup: String,
                               freeWeightRepLimit: Boolean = false) {
        val fingerprint = requireNotNull(observation.fingerprint)
        require(observation.setId > 0)
        if (basis != null) require(LoadConfirmations.validSetup(setup) && (observation.loadKg ?: 0.0) > 0)
        dataStore.confirmSetLoad(observation.setId, basis?.let {
            SetLoadConfirmation(observation.setId, fingerprint, it, setup.trim(),
                freeWeightRepLimit && it == RecordedLoadBasis.TOTAL && observation.reps in 2..5)
        })
    }

    fun observeRepRecords(exerciseId: String, today: java.time.LocalDate): Flow<RepRecordProjection> =
        combine(observeStrengthHistory(exerciseId, java.time.LocalDate.of(1, 1, 1), today), setLoadConfirmations) { points, confirmations ->
            RepRecords.calculate(points, confirmations)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun observeEstimatedStrength(exerciseId: String, start: java.time.LocalDate, end: java.time.LocalDate):
        Flow<List<EstimatedStrengthPoint>> = combine(
        observeStrengthHistory(exerciseId, start, end), setLoadConfirmations,
        dao.observeAllWeightEntries()
    ) { points, confirmations, weights ->
        EstimatedStrength.calculate(points, confirmations, weights)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun observeCustomGoals(today: java.time.LocalDate): Flow<List<FitnessGoalProgress>> =
        dataStore.customFitnessGoalsFlow.flatMapLatest { goals ->
            if (goals.isEmpty()) flowOf(emptyList())
            else {
                val start = minOf(today, goals.minOf { it.start })
                combine(dao.observeAnalyticsWorkingSets(start.toString(), today.toString()),
                    dao.observeCalendarSessions(start.toString(), today.toString()), dao.observeAllWeightEntries(),
                    setLoadConfirmations) { rows, sessions, weights, confirmations ->
                    val pointsByExercise = goals.filter { it.spec.exerciseGoal }.map { it.spec.exerciseId }.distinct()
                        .associateWith { id -> StrengthAnalytics.calculate(rows, id, start, today) }
                    goals.map { goal ->
                        val end = listOfNotNull(today, goal.spec.deadline, goal.archivedOn).minOrNull()!!
                        CustomFitnessGoals.progress(goal, today, CustomFitnessGoals.observations(
                            goal.spec, pointsByExercise[goal.spec.exerciseId].orEmpty(), confirmations, sessions, weights,
                            goal.start, end))
                    }
                }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun createCustomGoal(spec: FitnessGoalSpec) {
        require(spec.valid())
        val today = java.time.LocalDate.now()
        val end = today.minusDays(1)
        val start = java.time.LocalDate.of(1, 1, 1)
        val baseline = withContext(Dispatchers.Default) {
            val points = if (spec.exerciseGoal) observeStrengthHistory(spec.exerciseId, start, end).first() else emptyList()
            val confirmations = if (spec.exerciseGoal) setLoadConfirmations.first() else emptyMap()
            val weights = if (spec.kind == FitnessGoalKind.WEIGHT_UP || spec.kind == FitnessGoalKind.WEIGHT_DOWN)
                dao.observeAllWeightEntries().first() else emptyList()
            CustomFitnessGoals.observations(spec, points, confirmations, emptyList(), weights, start, end)
        }
        dataStore.insertCustomFitnessGoal(CustomFitnessGoals.create(spec, today, baseline))
    }

    suspend fun archiveCustomGoal(id: String) = dataStore.archiveCustomFitnessGoal(id)
    suspend fun deleteCustomGoal(id: String) = dataStore.deleteCustomFitnessGoal(id)

    val personalRecords = dao.getPersonalRecordsWithNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeCalendar(month: java.time.YearMonth, today: java.time.LocalDate): Flow<TrainingCalendarMonth> =
        combine(dao.observeCalendarSessions(month.atDay(1).toString(), month.atEndOfMonth().toString()),
            dao.observeCalendarPlans(),
            dao.observeScheduleRevisions(month.atDay(1).toString(), month.atEndOfMonth().toString()),
            dao.observeScheduleDispositions(month.atDay(1).toString(), month.atEndOfMonth().toString()),
            dao.observeScheduleWorkoutAssociations(month.atDay(1).toString(), month.atEndOfMonth().toString())) { sessions, plans, revisions, dispositions, associations ->
            TrainingCalendar.build(month, today, sessions, plans, revisions = revisions, dispositions = dispositions,
                associations = associations)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun setOccurrenceDecision(date: java.time.LocalDate, occurrenceId: String,
                                      decision: OccurrenceDecision?) =
        AppDatabase.getDatabase(getApplication()).withTransaction {
            require(date < java.time.LocalDate.now()) { "Only past scheduled workouts can be reviewed" }
            val revision = dao.getScheduleRevisionForDate(date.toString())
            val day = ScheduleLedger.resolve(date, listOfNotNull(revision))
            require(day.kind == CalendarScheduleKind.TRAINING && day.occurrenceId == occurrenceId) {
                "Schedule occurrence no longer matches"
            }
            if (decision == null) {
                dao.clearScheduleDisposition(occurrenceId)
            } else {
                require(dao.getScheduleWorkoutAssociation(occurrenceId) == null) {
                    "Clear the rescheduled workout association first"
                }
                val linked = dao.getOccurrenceWorkouts(date.toString(), occurrenceId)
                require(linked.none { it.planSessionId == day.slotId &&
                    it.startedAt != null && it.completedAt != null && it.completedAt >= it.startedAt &&
                    ((it.completed && it.status == "completed") || (!it.completed && it.status == "partial")) }) {
                    "A verified workout already exists for this occurrence"
                }
                dao.upsertScheduleDisposition(ScheduleDisposition(occurrenceId, date.toString(),
                    decision.name, System.currentTimeMillis()))
            }
        }

    suspend fun reschedulingCandidates(date: java.time.LocalDate, occurrenceId: String) =
        RescheduledWorkouts.candidates(AppDatabase.getDatabase(getApplication()), date, occurrenceId)

    suspend fun linkRescheduledWorkout(date: java.time.LocalDate, occurrenceId: String, sessionId: String) =
        RescheduledWorkouts.link(AppDatabase.getDatabase(getApplication()), date, occurrenceId, sessionId)

    suspend fun unlinkRescheduledWorkout(date: java.time.LocalDate, occurrenceId: String) =
        RescheduledWorkouts.unlink(AppDatabase.getDatabase(getApplication()), date, occurrenceId)

    val workoutHistory = dao.getSavedTrainingSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeWorkoutReport(sessionId: String, today: java.time.LocalDate): Flow<WorkoutReport?> =
        combine(dao.observeSavedWorkoutFacts(sessionId), dao.observeWorkoutRecordHistory(sessionId, today.toString()),
            setLoadConfirmations) { facts, history, confirmations ->
            facts?.let { WorkoutReports.calculate(it, history, confirmations, today) }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

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
     * Progress shows observed training recency rather than a separate
     * speculative countdown. Legacy numeric recovery fields are not presented.
     */
    private fun calculateMuscleRecovery(
        sessions: List<com.apexfit.app.data.TrainingSession>,
        allSets: List<com.apexfit.app.data.ExerciseSet>
    ): List<MuscleRecoveryStatus> {
        val muscles = com.apexfit.app.utils.MuscleGroups.ALL
        val nowMs = System.currentTimeMillis()
        val today = com.apexfit.app.utils.DateTimeUtils.todayDateString()
        val latestMoments = ProgressDataAggregation.latestSessionMoments(
            sessions.filter { it.completed && it.date <= today &&
                (it.completedAt ?: it.startedAt ?: 0L) <= nowMs },
            allSets.filter { it.reps > 0 },
            muscles, ::isMuscleMatch
        )
        return muscles.mapNotNull { muscle ->
            val lastMoment = latestMoments[muscle] ?: return@mapNotNull null
            MuscleRecoveryStatus(
                muscleGroup = muscle,
                hoursRemaining = 0,
                recoveryFraction = 0.0f,
                lastTrainedDate = lastMoment.date
            )
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
