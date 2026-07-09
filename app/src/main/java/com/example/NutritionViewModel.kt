@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.repository.FitnessRepository
import com.example.data.repository.FitnessRepositoryImpl
import com.example.ui.models.*
import com.example.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.fitnessDao()
    private val dataStore = DataStoreManager(application)
    private val repository: FitnessRepository = FitnessRepositoryImpl(db, dao, dataStore)

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
        viewModelScope.launch {
            val newEntry = com.example.data.NutritionEntry(
                date = date,
                name = name,
                time = getCurrentLocalTimeString(),
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat
            )
            dao.insertNutritionEntry(newEntry)
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
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val dateObj = sdf.parse(_selectedNutritionDate.value) ?: java.util.Date()
                val cal = java.util.Calendar.getInstance()
                cal.time = dateObj
                cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)

                // Clamp to today (no future dates)
                val today = java.util.Calendar.getInstance()
                if (cal.after(today)) {
                    cal.time = today.time
                }

                _selectedNutritionDate.value = sdf.format(cal.time)
            } catch (e: Exception) {
                android.util.Log.e("ApexFit", "Error in changeNutritionDate: ${e.message}", e)
            }
        }
    }



    private fun getTodayDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun getCurrentLocalTimeString(): String {
        return android.text.format.DateFormat.getTimeFormat(getApplication()).format(java.util.Date())
    }
}
