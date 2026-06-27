package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_fit_preferences")

class DataStoreManager(private val context: Context) {

    companion object {
        val ONBOARDED_KEY = booleanPreferencesKey("onboarded")
        val USER_NAME_KEY = stringPreferencesKey("username")
        val GOAL_KEY = stringPreferencesKey("goal")
        val CURRENT_WEIGHT_KEY = doublePreferencesKey("current_weight")
        val GOAL_WEIGHT_KEY = doublePreferencesKey("goal_weight")
        val UNITS_KEY = stringPreferencesKey("units") // "kg" or "lbs"
        val CALORIE_TARGET_MANUAL_KEY = booleanPreferencesKey("calorie_target_manual")
        val CALORIE_TARGET_VALUE_KEY = intPreferencesKey("calorie_target_value")
        val EQUIPMENT_KEY = stringPreferencesKey("equipment_available") // comma-separated
        val HEIGHT_KEY = doublePreferencesKey("height")
        val AGE_KEY = intPreferencesKey("age")
        val SEX_KEY = stringPreferencesKey("sex")
        val EXERCISES_SEEDED_KEY = booleanPreferencesKey("exercises_seeded")
    }

    val isOnboardedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDED_KEY] ?: false
    }

    val isExercisesSeededFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EXERCISES_SEEDED_KEY] ?: false
    }

    suspend fun setExercisesSeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EXERCISES_SEEDED_KEY] = seeded
        }
    }

    val usernameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY] ?: ""
    }

    val goalFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GOAL_KEY] ?: "Gain Muscle"
    }

    val currentWeightFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_WEIGHT_KEY] ?: com.example.UserDefaults.WEIGHT_KG
    }

    val goalWeightFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[GOAL_WEIGHT_KEY] ?: com.example.UserDefaults.WEIGHT_KG
    }

    val unitsFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UNITS_KEY] ?: "kg"
    }

    val heightFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[HEIGHT_KEY] ?: com.example.UserDefaults.HEIGHT_CM
    }

    val ageFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AGE_KEY] ?: com.example.UserDefaults.AGE_YEARS
    }

    val sexFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SEX_KEY] ?: "male"
    }

    val calorieTargetManualFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CALORIE_TARGET_MANUAL_KEY] ?: false
    }

    val calorieTargetValueFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CALORIE_TARGET_VALUE_KEY] ?: com.example.UserDefaults.CALORIES
    }

    val equipmentFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EQUIPMENT_KEY] ?: "Barbell,Dumbbell,Cable,Machine"
    }

    suspend fun saveOnboardingData(
        username: String,
        goal: String,
        currentWeight: Double,
        goalWeight: Double,
        height: Double = com.example.UserDefaults.HEIGHT_CM,
        age: Int = com.example.UserDefaults.AGE_YEARS,
        sex: String = "male"
    ) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = username
            preferences[GOAL_KEY] = goal
            preferences[CURRENT_WEIGHT_KEY] = currentWeight
            preferences[GOAL_WEIGHT_KEY] = goalWeight
             preferences[ONBOARDED_KEY] = true
            preferences[HEIGHT_KEY] = height
            preferences[AGE_KEY] = age
            preferences[SEX_KEY] = sex
        }
    }

    suspend fun saveBiologicalParameters(height: Double, age: Int, sex: String) {
        context.dataStore.edit { preferences ->
            preferences[HEIGHT_KEY] = height
            preferences[AGE_KEY] = age
            preferences[SEX_KEY] = sex
        }
    }

    suspend fun saveUsername(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = name
        }
    }

    suspend fun saveGoal(goal: String) {
        context.dataStore.edit { preferences ->
            preferences[GOAL_KEY] = goal
        }
    }

    suspend fun saveWeight(current: Double, goal: Double) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_WEIGHT_KEY] = current
            preferences[GOAL_WEIGHT_KEY] = goal
        }
    }

    suspend fun saveUnits(units: String) {
        context.dataStore.edit { preferences ->
            preferences[UNITS_KEY] = units
        }
    }

    suspend fun saveCalorieTargets(manual: Boolean, value: Int) {
        context.dataStore.edit { preferences ->
            preferences[CALORIE_TARGET_MANUAL_KEY] = manual
            preferences[CALORIE_TARGET_VALUE_KEY] = value
        }
    }

    suspend fun saveEquipment(equipment: String) {
        context.dataStore.edit { preferences ->
            preferences[EQUIPMENT_KEY] = equipment
        }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
