@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.apexfit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexfit.app.data.*
import com.apexfit.app.domain.repository.FitnessRepository
import com.apexfit.app.data.repository.FitnessRepositoryImpl
import com.apexfit.app.ui.models.*
import com.apexfit.app.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = com.apexfit.app.di.ServiceLocator.database(application)
    private val dao = db.fitnessDao()
    private val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(application)
    private val repository = com.apexfit.app.di.ServiceLocator.repository(application)

    private val _isLoggingNutrition = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _selectedNutritionDate = MutableStateFlow(getTodayDateString())
    val selectedNutritionDate: StateFlow<String> = _selectedNutritionDate.asStateFlow()

    val loggedMeals: StateFlow<UiState<List<UiNutritionEntry>>> = _selectedNutritionDate.flatMapLatest { date ->
        repository.getNutritionEntries(date).map { list -> list.map { it.toUi() } }
    }
    .map { UiState.Success(it) as UiState<List<UiNutritionEntry>> }
    .catch { emit(UiState.Error(it.localizedMessage ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    // Log meal and delete meal
    fun logNutrition(
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        date: String = getTodayDateString(),
        name: String = "Logged Meal"
    ) {
        if (!_isLoggingNutrition.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val newEntry = com.apexfit.app.data.NutritionEntry(
                    date = date,
                    name = name,
                    time = getCurrentLocalTimeString(),
                    calories = calories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat
                )
                dao.insertNutritionEntry(newEntry)
            } finally {
                _isLoggingNutrition.set(false)
            }
        }
    }

    fun deleteNutritionEntryById(id: Long) {
        viewModelScope.launch {
            dao.deleteNutritionEntryById(id)
        }
    }

    fun changeNutritionDate(offsetDays: Int) {
        viewModelScope.launch {
            try {
                val dateObj = com.apexfit.app.utils.DateTimeUtils.parseDate(_selectedNutritionDate.value) ?: java.util.Date()
                val cal = java.util.Calendar.getInstance()
                cal.time = dateObj
                cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)

                // Clamp to today (no future dates)
                val today = java.util.Calendar.getInstance()
                if (cal.after(today)) {
                    cal.time = today.time
                }

                _selectedNutritionDate.value = com.apexfit.app.utils.DateTimeUtils.formatDate(cal.time)
            } catch (e: Exception) {
                android.util.Log.e("NutritionViewModel", "Date change failed", e)
            }
        }
    }



    private fun getTodayDateString(): String {
        return com.apexfit.app.utils.DateTimeUtils.todayDateString()
    }

    private fun getCurrentLocalTimeString(): String {
        return android.text.format.DateFormat.getTimeFormat(getApplication()).format(java.util.Date())
    }

    public override fun onCleared() {
        super.onCleared()
    }
}