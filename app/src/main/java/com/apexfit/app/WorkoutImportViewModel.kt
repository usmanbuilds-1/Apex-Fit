package com.apexfit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.ExerciseMetadata
import com.apexfit.app.data.FitnessDao
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.data.WorkoutPlan
import com.apexfit.app.domain.importer.EditableImportedDay
import com.apexfit.app.domain.importer.EditableImportedExercise
import com.apexfit.app.domain.importer.EditableImportedPlan
import com.apexfit.app.domain.importer.ExerciseLibraryEntry
import com.apexfit.app.domain.importer.ImportRepTarget
import com.apexfit.app.domain.importer.ImportWeightUnit
import com.apexfit.app.domain.importer.MatchConfidence
import com.apexfit.app.domain.importer.WorkoutExerciseAliases
import com.apexfit.app.domain.importer.WorkoutTextNormalizer
import com.apexfit.app.domain.importer.WorkoutTextParser
import com.apexfit.app.domain.importer.toEditablePlan
import com.apexfit.app.domain.repository.FitnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class WorkoutImportStage { PASTE, REVIEW }

data class WorkoutImportUiState(
    val stage: WorkoutImportStage = WorkoutImportStage.PASTE,
    val inputText: String = "",
    val isParsing: Boolean = false,
    val isSaving: Boolean = false,
    val draft: EditableImportedPlan? = null,
    val goal: String = "",
    val parseWarnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val validationErrors: List<String> = emptyList(),
    val saveCompleted: Boolean = false
)

@HiltViewModel
class WorkoutImportViewModel @Inject constructor(
    private val repository: FitnessRepository,
    private val dao: FitnessDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutImportUiState())
    val uiState: StateFlow<WorkoutImportUiState> = _uiState.asStateFlow()

    private val _exerciseLibrary = MutableStateFlow<List<ExerciseLibraryEntry>>(emptyList())
    val exerciseLibrary: StateFlow<List<ExerciseLibraryEntry>> = _exerciseLibrary.asStateFlow()

    @Volatile
    private var libraryLoaded = false

    fun updateInput(text: String) {
        val tooLarge = text.length > WorkoutTextNormalizer.MAX_INPUT_CHARS
        val bounded = text.take(WorkoutTextNormalizer.MAX_INPUT_CHARS)
        _uiState.value = _uiState.value.copy(
            inputText = bounded,
            errorMessage = if (tooLarge) {
                "Paste is limited to " + WorkoutTextNormalizer.MAX_INPUT_CHARS + " characters for safe local parsing."
            } else {
                null
            }
        )
    }

    fun clear() {
        _uiState.value = WorkoutImportUiState()
    }

    fun backToPaste() {
        _uiState.value = _uiState.value.copy(
            stage = WorkoutImportStage.PASTE,
            errorMessage = null,
            validationErrors = emptyList(),
            saveCompleted = false
        )
    }

    fun parse() {
        val snapshot = _uiState.value.inputText
        if (_uiState.value.isParsing || snapshot.isBlank()) {
            if (snapshot.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Paste a workout before parsing.")
            }
            return
        }

        _uiState.value = _uiState.value.copy(
            isParsing = true,
            errorMessage = null,
            validationErrors = emptyList(),
            saveCompleted = false
        )

        viewModelScope.launch {
            try {
                val library = loadLibrary()
                val result = withContext(Dispatchers.Default) {
                    WorkoutTextParser(library).parse(snapshot)
                }
                val plan = result.plan
                if (plan == null) {
                    _uiState.value = _uiState.value.copy(
                        isParsing = false,
                        errorMessage = result.fatalError?.message ?: "Apex Fit couldn't find a workout in that text."
                    )
                    return@launch
                }
                val libraryById = library.associateBy { it.id }
                val currentGoal = repository.getGoalFlow()
                    .firstOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "General Fitness" }
                _uiState.value = _uiState.value.copy(
                    stage = WorkoutImportStage.REVIEW,
                    isParsing = false,
                    draft = plan.toEditablePlan(libraryById),
                    goal = currentGoal,
                    parseWarnings = plan.warnings.map { it.message }.distinct(),
                    errorMessage = null,
                    validationErrors = emptyList()
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isParsing = false,
                    errorMessage = "Apex Fit couldn't parse that workout. Your pasted text is still here."
                )
            }
        }
    }

    fun updatePlanName(value: String) = updateDraft { it.copy(name = value.take(80)) }

    fun updatePlanGoal(value: String) {
        _uiState.value = _uiState.value.copy(
            goal = value.take(80),
            errorMessage = null,
            validationErrors = emptyList()
        )
    }

    fun updateMakeActive(value: Boolean) = updateDraft { it.copy(makeActive = value) }

    fun updateDayLabel(dayKey: String, value: String) = updateDay(dayKey) { it.copy(label = value.take(48)) }

    fun updateDayWeekday(dayKey: String, value: String?) = updateDay(dayKey) { it.copy(dayOfWeek = value) }

    fun moveDay(dayKey: String, delta: Int) {
        updateDraft { draft ->
            val list = draft.days.toMutableList()
            val from = list.indexOfFirst { it.key == dayKey }
            val to = from + delta
            if (from !in list.indices || to !in list.indices) return@updateDraft draft
            val moved = list.removeAt(from)
            list.add(to, moved)
            draft.copy(days = list)
        }
    }

    fun deleteDay(dayKey: String) = updateDraft { draft ->
        draft.copy(days = draft.days.filterNot { it.key == dayKey })
    }

    fun addDay() = updateDraft { draft ->
        val next = draft.days.size + 1
        draft.copy(days = draft.days + EditableImportedDay(
            label = "Workout $next",
            dayOfWeek = null,
            focus = null,
            exercises = emptyList()
        ))
    }

    fun updateExerciseText(dayKey: String, exerciseKey: String, field: ExerciseField, value: String) {
        updateExercise(dayKey, exerciseKey) { exercise ->
            when (field) {
                ExerciseField.SETS -> exercise.copy(setsText = value.filter(Char::isDigit).take(2))
                ExerciseField.REPS_MIN -> exercise.copy(repsMinText = value.filter(Char::isDigit).take(3))
                ExerciseField.REPS_MAX -> exercise.copy(repsMaxText = value.filter(Char::isDigit).take(3))
                ExerciseField.WEIGHT -> exercise.copy(weightText = sanitizeDecimal(value))
                ExerciseField.REST -> exercise.copy(restSecondsText = value.filter(Char::isDigit).take(4))
                ExerciseField.NOTES -> exercise.copy(notes = value.take(500))
            }
        }
    }

    fun updateWeightUnit(dayKey: String, exerciseKey: String, unit: ImportWeightUnit) {
        updateExercise(dayKey, exerciseKey) { it.copy(weightUnit = unit) }
    }

    fun confirmSuggestedMatch(dayKey: String, exerciseKey: String) {
        updateExercise(dayKey, exerciseKey) { exercise ->
            if (exercise.selectedExerciseId == null) exercise else exercise.copy(matchConfirmed = true)
        }
    }

    fun selectExercise(dayKey: String, exerciseKey: String, exerciseId: String) {
        val option = _exerciseLibrary.value.firstOrNull { it.id == exerciseId } ?: return
        updateExercise(dayKey, exerciseKey) { exercise ->
            exercise.copy(
                selectedExerciseId = option.id,
                selectedExerciseName = option.name,
                muscleGroup = option.primaryMuscle,
                matchConfidence = MatchConfidence.EXACT,
                matchConfirmed = true
            )
        }
    }

    fun moveExercise(dayKey: String, exerciseKey: String, delta: Int) {
        updateDay(dayKey) { day ->
            val list = day.exercises.toMutableList()
            val from = list.indexOfFirst { it.key == exerciseKey }
            val to = from + delta
            if (from !in list.indices || to !in list.indices) return@updateDay day
            val moved = list.removeAt(from)
            list.add(to, moved)
            day.copy(exercises = list)
        }
    }

    fun deleteExercise(dayKey: String, exerciseKey: String) {
        updateDay(dayKey) { day -> day.copy(exercises = day.exercises.filterNot { it.key == exerciseKey }) }
    }

    fun addExercise(dayKey: String, exerciseId: String) {
        val option = _exerciseLibrary.value.firstOrNull { it.id == exerciseId } ?: return
        updateDay(dayKey) { day ->
            day.copy(exercises = day.exercises + EditableImportedExercise(
                sourceName = option.name,
                selectedExerciseId = option.id,
                selectedExerciseName = option.name,
                muscleGroup = option.primaryMuscle,
                matchConfidence = MatchConfidence.EXACT,
                matchConfirmed = true,
                candidates = emptyList(),
                setsText = "3",
                repsMinText = "8",
                repsMaxText = "12",
                weightText = "",
                weightUnit = ImportWeightUnit.KG,
                restSecondsText = "",
                repTarget = ImportRepTarget.RANGE,
                notes = "",
                sourcePrescription = null
            ))
        }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _uiState.value.draft ?: return
        if (_uiState.value.isSaving || _uiState.value.saveCompleted) return

        val planGoal = _uiState.value.goal.trim()
        val errors = buildList {
            addAll(draft.validationErrors())
            if (planGoal.isBlank()) add("Choose a goal for this plan.")
        }.distinct()
        if (errors.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(validationErrors = errors, errorMessage = errors.first())
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, validationErrors = emptyList())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val selectedIds = draft.days.flatMap { it.exercises }
                    .mapNotNull { it.selectedExerciseId }
                    .distinct()
                val metadataById = dao.getMetadataForExercises(selectedIds).associateBy(ExerciseMetadata::exerciseId)
                val plan = WorkoutPlan(
                    id = 0,
                    name = draft.name.trim(),
                    goal = planGoal,
                    isActive = draft.makeActive,
                    daysPerWeek = draft.days.size,
                    isTemplate = 0,
                    source = "text_import",
                    importRawText = draft.originalText.take(WorkoutTextNormalizer.MAX_INPUT_CHARS)
                )

                val temporarySessionIds = draft.days.indices.associateWith { index -> -(index + 1L) }
                val sessions = draft.days.mapIndexed { index, day ->
                    val muscles = day.exercises.mapNotNull { it.muscleGroup }.distinct()
                    PlanSession(
                        id = temporarySessionIds.getValue(index),
                        planId = 0,
                        label = day.label.trim().ifBlank { day.dayOfWeek ?: "Workout ${index + 1}" },
                        day = requireNotNull(day.dayOfWeek),
                        focus = if (muscles.isEmpty()) "General" else muscles.joinToString(", ")
                    )
                }

                val exercises = draft.days.flatMapIndexed { dayIndex, day ->
                    val temporarySessionId = temporarySessionIds.getValue(dayIndex)
                    day.exercises.map { exercise ->
                        val exerciseId = requireNotNull(exercise.selectedExerciseId)
                        val name = requireNotNull(exercise.selectedExerciseName)
                        val muscle = exercise.muscleGroup ?: "Other"
                        val importedWeight = exercise.weight
                        val canonicalWeightKg = when {
                            importedWeight == null -> 0.0
                            exercise.weightUnit == ImportWeightUnit.LB -> importedWeight * 0.45359237
                            else -> importedWeight
                        }
                        val notes = buildList {
                            if (exercise.repTarget == ImportRepTarget.AMRAP) add("Imported target: AMRAP")
                            if (exercise.repTarget == ImportRepTarget.FAILURE) add("Imported target: to failure")
                            if (importedWeight != null && exercise.weightUnit == ImportWeightUnit.LB) {
                                add("Imported load: " + formatImportedWeight(importedWeight) + " lb")
                            }
                            if (exercise.notes.isNotBlank()) add(exercise.notes.trim())
                        }.distinct().joinToString(" · ")
                        PlanExercise(
                            id = 0,
                            planSessionId = temporarySessionId,
                            exerciseId = exerciseId,
                            name = name,
                            muscleGroup = muscle,
                            sets = requireNotNull(exercise.sets),
                            repsMin = requireNotNull(exercise.repsMin),
                            repsMax = requireNotNull(exercise.repsMax),
                            weight = canonicalWeightKg,
                            weightUnit = "kg",
                            restSeconds = exercise.restSeconds
                                ?: metadataById[exerciseId]?.defaultRestSeconds
                                ?: 120,
                            notes = notes
                        )
                    }
                }

                repository.updateWorkoutPlan(plan, sessions, exercises)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveCompleted = true)
                    onSaved()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Couldn't save the imported plan. Nothing partial was kept."
                )
            }
        }
    }

    private suspend fun loadLibrary(): List<ExerciseLibraryEntry> {
        if (libraryLoaded && _exerciseLibrary.value.isNotEmpty()) return _exerciseLibrary.value
        return withContext(Dispatchers.IO) {
            val entries = dao.getAllExercises().map { exercise ->
                ExerciseLibraryEntry(
                    id = exercise.id,
                    name = exercise.name,
                    primaryMuscle = exercise.primaryMuscle,
                    equipment = exercise.equipmentRequired,
                    aliases = WorkoutExerciseAliases.forExercise(exercise.name)
                )
            }
            _exerciseLibrary.value = entries
            libraryLoaded = true
            entries
        }
    }

    private fun updateDraft(transform: (EditableImportedPlan) -> EditableImportedPlan) {
        val current = _uiState.value
        val draft = current.draft ?: return
        val updated = transform(draft)
        _uiState.value = current.copy(
            draft = updated,
            validationErrors = emptyList(),
            errorMessage = null
        )
    }

    private fun updateDay(dayKey: String, transform: (EditableImportedDay) -> EditableImportedDay) {
        updateDraft { draft ->
            draft.copy(days = draft.days.map { if (it.key == dayKey) transform(it) else it })
        }
    }

    private fun updateExercise(
        dayKey: String,
        exerciseKey: String,
        transform: (EditableImportedExercise) -> EditableImportedExercise
    ) {
        updateDay(dayKey) { day ->
            day.copy(exercises = day.exercises.map { if (it.key == exerciseKey) transform(it) else it })
        }
    }

    private fun formatImportedWeight(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun sanitizeDecimal(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered.take(7) else {
            filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
        }.take(8)
    }
}

enum class ExerciseField { SETS, REPS_MIN, REPS_MAX, WEIGHT, REST, NOTES }