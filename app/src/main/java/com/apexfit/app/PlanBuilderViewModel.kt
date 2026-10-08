@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.apexfit.app.data.*
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.ui.models.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanBuilderViewModel @Inject constructor(
    application: Application,
    private val db: AppDatabase = AppDatabase.getDatabase(application),
    private val dao: FitnessDao = db.fitnessDao(),
    private val dataStore: DataStoreManager = DataStoreManager.getInstance(application),
    private val repository: FitnessRepository = FitnessRepositoryImpl(db, dao, dataStore)
) : AndroidViewModel(application) {

    private val sessionIdCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

    private fun nextSessionId(): Long = sessionIdCounter.incrementAndGet()

    fun generateNewSessionId(): Long = nextSessionId()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = dao.getAllPlans()
                if (existing.isEmpty()) {
                    seedDefaultWorkoutPlan()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlanBuilderViewModel", "Failed to seed default plan: ${e.message}", e)
            }
        }
    }

    val units = dataStore.unitsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kg")
    val equipmentAvailable = dataStore.equipmentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Barbell,Dumbbell,Cable,Machine")

    val exerciseLibrary: StateFlow<UiState<List<Exercise>>> = dao.getAllExercisesFlow()
        .map { UiState.Success(it) as UiState<List<Exercise>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    // Training State (Plans, Sessions)
    val workoutPlans = repository.getWorkoutPlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    // Fix D-L7: Cached single-subscription flow at ViewModel level
    private val cachedActivePlan: StateFlow<WorkoutPlan?> = repository.getActivePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val cachedActivePlanSessions: StateFlow<List<PlanSession>> = cachedActivePlan.flatMapLatest { plan ->
        if (plan != null) dao.getSessionsForPlanFlow(plan.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activePlan: StateFlow<UiState<WorkoutPlan?>> = cachedActivePlan
        .map { UiState.Success(it) as UiState<WorkoutPlan?> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val activePlanSessions: StateFlow<UiState<List<PlanSession>>> = cachedActivePlanSessions
        .map { UiState.Success(it) as UiState<List<PlanSession>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    // Plan Builder Editing State
    val planBuilderSessions = MutableStateFlow<List<PlanSession>>(emptyList())
    val planBuilderExercises = MutableStateFlow<List<PlanExercise>>(emptyList())
    private val _isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    val isInitialized: Boolean get() = _isInitialized.get()

    fun initializePlanBuilder(plan: WorkoutPlan?, sessions: List<PlanSession>, exercises: List<PlanExercise>) {
        if (!_isInitialized.compareAndSet(false, true)) return
        planBuilderSessions.value = sessions
        planBuilderExercises.value = exercises
    }

    fun resetPlanBuilder() {
        _isInitialized.set(false)
        planBuilderSessions.value = emptyList()
        planBuilderExercises.value = emptyList()
    }

    fun addSession(session: PlanSession) {
        planBuilderSessions.value = planBuilderSessions.value + session
    }

    fun updateSession(session: PlanSession) {
        planBuilderSessions.value = planBuilderSessions.value.map { if (it.id == session.id) session else it }
    }

    fun deleteSession(sessionId: Long) {
        planBuilderSessions.value = planBuilderSessions.value.filter { it.id != sessionId }
        planBuilderExercises.value = planBuilderExercises.value.filter { it.planSessionId != sessionId }
    }

    fun addExercise(exercise: PlanExercise) {
        planBuilderExercises.value = planBuilderExercises.value + exercise
    }

    fun addCustomExercise(planExercise: PlanExercise): kotlinx.coroutines.Job = viewModelScope.launch {
        val slug = com.apexfit.app.utils.exerciseNameToSlug(planExercise.name)
        val existing = dao.getExerciseById(slug)
        if (existing == null) {
            dao.insertExercises(listOf(
                Exercise(
                    id = slug,
                    name = planExercise.name,
                    category = "User Created",
                    primaryMuscle = planExercise.muscleGroup,
                    secondaryMuscles = emptyList(),
                    equipmentRequired = "Unknown",
                    isBilateral = 1,
                    isUserCreated = 1,
                    isDeleted = 0,
                    createdAt = System.currentTimeMillis()
                )
            ))
            dao.insertExerciseMetadata(ExerciseMetadata(exerciseId = slug))
        }
        addExercise(planExercise)
    }

    fun updateExercise(exercise: PlanExercise) {
        planBuilderExercises.value = planBuilderExercises.value.map { if (it.id == exercise.id) exercise else it }
    }

    fun deleteExercise(exerciseId: Long) {
        planBuilderExercises.value = planBuilderExercises.value.filter { it.id != exerciseId }
    }

    fun getSessionById(id: Long): PlanSession? {
        return planBuilderSessions.value.find { it.id == id }
    }

    fun getExerciseById(id: Long): PlanExercise? {
        return planBuilderExercises.value.find { it.id == id }
    }

    fun reorderSession(index: Int, moveUp: Boolean) {
        val list = planBuilderSessions.value.toMutableList()
        val swapIndex = if (moveUp) index - 1 else index + 1
        if (swapIndex in list.indices) {
            val temp = list[index]
            list[index] = list[swapIndex]
            list[swapIndex] = temp
            planBuilderSessions.value = list
        }
    }

    private val _selectedDayOfWeek = MutableStateFlow("Monday")
    val selectedDayOfWeek: StateFlow<String> = _selectedDayOfWeek.asStateFlow()

    val allPlanExercises = dao.getAllPlanExercisesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedDaySession = combine(cachedActivePlanSessions, _selectedDayOfWeek) { sessions, day ->
        sessions.firstOrNull { it.day.equals(day, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedDayExercises = selectedDaySession.flatMapLatest { session ->
        if (session != null) repository.getExercisesForSession(session.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayExercises: StateFlow<UiState<List<PlanExercise>>> = repository.todayExercisesFlow
        .map { UiState.Success(it) as UiState<List<PlanExercise>> }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectDay(day: String) {
        _selectedDayOfWeek.value = day
        _activeSubstIndex.value = -1
        _substitutionList.value = emptyList()
    }

    // Hoisted TrainScreen UI state
    private val _activeSubstIndex = MutableStateFlow<Int?>(-1)
    val activeSubstIndex: StateFlow<Int?> = _activeSubstIndex.asStateFlow()

    private val _substitutionList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val substitutionList: StateFlow<List<Pair<String, String>>> = _substitutionList.asStateFlow()

    fun selectSubstIndex(index: Int?) {
        _activeSubstIndex.value = index
    }

    fun setSubstitutionList(list: List<Pair<String, String>>) {
        _substitutionList.value = list
    }

    fun loadSubstitutionSuggestions(muscleGroup: String, exerciseName: String) {
        viewModelScope.launch {
            _substitutionList.value = getSubstitutionSuggestions(muscleGroup, exerciseName)
        }
    }

    suspend fun getSubstitutionSuggestions(pattern: String, currentEx: String): List<Pair<String, String>> {
        val muscleGroup = pattern
        val exerciseName = currentEx
        val exercises = (exerciseLibrary.value as? UiState.Success)?.data ?: return emptyList()
        val equipment = equipmentAvailable.value.split(",").map { it.trim().lowercase() }
        return exercises
            .filter { ex ->
                ex.primaryMuscle.equals(muscleGroup, ignoreCase = true) &&
                ex.name != exerciseName &&
                (equipment.isEmpty() || equipment.any { e ->
                    ex.equipmentRequired.lowercase().contains(e) ||
                    ex.category.lowercase().contains(e)
                })
            }
            .sortedBy { it.name }
            .take(5)
            .map { it.name to "Alternative targeting ${it.primaryMuscle} (${it.equipmentRequired})." }
    }

    fun updateWorkoutPlan(
        plan: WorkoutPlan,
        sessions: List<PlanSession>,
        exercises: List<PlanExercise>
    ) {
        viewModelScope.launch {
            repository.updateWorkoutPlan(plan, sessions, exercises)
        }
    }

    fun activatePlan(planId: Long): kotlinx.coroutines.Job = viewModelScope.launch {
        // AUDIT FIX (BUG-V4-009): wrap in a transaction so deactivate + activate
        // are atomic — a crash between the two calls cannot leave all plans inactive.
        db.withTransaction {
            dao.deactivateAllPlans()
            dao.activatePlan(planId)
        }
    }

    suspend fun seedDefaultWorkoutPlan() {
        val plan = WorkoutPlan(
            id = 0,
            name = "Scientific Hypertrophy Split",
            goal = "Gain Muscle",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        dao.deleteAllPlans()  // cascades to plan_sessions and plan_exercises via FK
        val generatedPlanId = dao.insertWorkoutPlan(plan)
        
        val sessions = listOf(
            PlanSession(id = 0, planId = generatedPlanId, label = "Upper A", day = "Monday", focus = "Chest, Back, Arms"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Lower A", day = "Tuesday", focus = "Quads, Hamstrings, Calves"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Upper B", day = "Thursday", focus = "Shoulders, Chest, Back"),
            PlanSession(id = 0, planId = generatedPlanId, label = "Lower B", day = "Friday", focus = "Hamstrings, Glutes, Quads")
        )

        val sessionIds = dao.insertPlanSessions(sessions)
        val upperASessionId = sessionIds[0]
        val lowerASessionId = sessionIds[1]
        val upperBSessionId = sessionIds[2]
        val lowerBSessionId = sessionIds[3]

        val exercises = listOf(
            // Monday - Upper A
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Barbell Bench Press", muscleGroup = "Chest", sets = 4, repsMin = 6, repsMax = 10, weight = 80.0, restSeconds = 120, notes = "Focus on the deep stretch at chest level."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Weighted Pull-Up", muscleGroup = "Back", sets = 4, repsMin = 6, repsMax = 10, weight = 5.0, restSeconds = 120, notes = "Control the eccentric descent."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dumbbell Lateral Raise", muscleGroup = "Shoulders", sets = 3, repsMin = 10, repsMax = 15, weight = 12.5, restSeconds = 90, notes = "Slight torso lean forward."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Incline Dumbbell Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 8, repsMax = 12, weight = 14.0, restSeconds = 90, notes = "Biceps fully stretched at bottom."),
            PlanExercise(id = 0, planSessionId = upperASessionId, name = "Dual Rope Tricep Pushdown", muscleGroup = "Triceps", sets = 3, repsMin = 10, repsMax = 15, weight = 25.0, restSeconds = 90, notes = "Flare ropes outward at end of range."),

            // Tuesday - Lower A
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Barbell Back Squat", muscleGroup = "Quads", sets = 4, repsMin = 6, repsMax = 8, weight = 100.0, restSeconds = 180, notes = "Keep knees tracking over toes."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Romanian Deadlift", muscleGroup = "Hamstrings", sets = 4, repsMin = 8, repsMax = 12, weight = 90.0, restSeconds = 120, notes = "Hinge at hips, keep back flat."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Leg Press (High & Wide)", muscleGroup = "Quads", sets = 3, repsMin = 10, repsMax = 12, weight = 160.0, restSeconds = 120, notes = "Aesthetic emphasis on quad sweep."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Seated Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 15, weight = 50.0, restSeconds = 90, notes = "Hard squeeze at full flexion."),
            PlanExercise(id = 0, planSessionId = lowerASessionId, name = "Standing Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 15, weight = 60.0, restSeconds = 60, notes = "2-second pause at full stretch."),

            // Thursday - Upper B
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Standing Overhead Press", muscleGroup = "Shoulders", sets = 4, repsMin = 6, repsMax = 10, weight = 50.0, restSeconds = 120, notes = "Press overhead in a straight line."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Chest-Supported Dumbbell Row", muscleGroup = "Back", sets = 4, repsMin = 8, repsMax = 12, weight = 25.0, restSeconds = 120, notes = "Squeeze shoulder blades together."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Flat Dumbbell Press", muscleGroup = "Chest", sets = 3, repsMin = 8, repsMax = 12, weight = 30.0, restSeconds = 120, notes = "Drive dumbbells toward center on press."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Lat Pulldown (Neutral Grip)", muscleGroup = "Back", sets = 3, repsMin = 8, repsMax = 12, weight = 65.0, restSeconds = 90, notes = "Pull down to upper collarbone."),
            PlanExercise(id = 0, planSessionId = upperBSessionId, name = "Hammer Bicep Curl", muscleGroup = "Biceps", sets = 3, repsMin = 10, repsMax = 15, weight = 15.0, restSeconds = 90, notes = "Focus on brachialis and forearm development."),

            // Friday - Lower B
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Conventional Deadlift", muscleGroup = "Hamstrings", sets = 3, repsMin = 5, repsMax = 8, weight = 120.0, restSeconds = 180, notes = "Pull slack out of the bar before initiating lift."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Bulgarian Split Squat", muscleGroup = "Quads", sets = 3, repsMin = 8, repsMax = 12, weight = 20.0, restSeconds = 120, notes = "Slight forward torso lean for glutes & quads."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Barbell Hip Thrust", muscleGroup = "Glutes", sets = 4, repsMin = 8, repsMax = 12, weight = 100.0, restSeconds = 120, notes = "1-second pause at peak glute contraction."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Lying Leg Curl", muscleGroup = "Hamstrings", sets = 3, repsMin = 10, repsMax = 15, weight = 45.0, restSeconds = 90, notes = "Keep hips pressed firmly into pad."),
            PlanExercise(id = 0, planSessionId = lowerBSessionId, name = "Seated Calf Raise", muscleGroup = "Calves", sets = 4, repsMin = 12, repsMax = 20, weight = 40.0, restSeconds = 60, notes = "Targets soleus muscle.")
        )

        val resolvedExercises = exercises.map { exercise ->
            val exerciseId = dao.getExerciseByName(exercise.name)?.id
                ?: com.apexfit.app.utils.exerciseNameToSlug(exercise.name)
            exercise.copy(exerciseId = exerciseId)
        }

        dao.insertPlanExercises(resolvedExercises)
    }
}
