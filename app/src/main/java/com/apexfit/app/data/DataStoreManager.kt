package com.apexfit.app.data

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apex_fit_preferences")

class DataStoreManager(context: Context) {
    private val context = context.applicationContext

    companion object {
        @Volatile private var instance: DataStoreManager? = null
        fun getInstance(context: Context): DataStoreManager =
            instance ?: synchronized(this) {
                instance ?: DataStoreManager(context.applicationContext).also { instance = it }
            }
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
        val ACTIVE_SESSION_JSON_KEY = stringPreferencesKey("active_session_json")
        val WEIGHTS_NORMALIZED_KEY = booleanPreferencesKey("weights_normalized_v1")
        // AUDIT FIX (BUG-V4-015): flag for exercise_sets/PR canonicalization
        val SET_WEIGHTS_CANONICALIZED_KEY = booleanPreferencesKey("set_weights_canonicalized_v2")
    }

    val weightsNormalizedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WEIGHTS_NORMALIZED_KEY] ?: false
    }

    suspend fun markWeightsNormalized() {
        context.dataStore.edit { preferences ->
            preferences[WEIGHTS_NORMALIZED_KEY] = true
        }
    }

    suspend fun normalizeDataStoreWeights() {
        context.dataStore.edit { preferences ->
            val units = preferences[UNITS_KEY] ?: "kg"
            if (units.lowercase() in listOf("lb", "lbs")) {
                val currentW = preferences[CURRENT_WEIGHT_KEY]
                val goalW = preferences[GOAL_WEIGHT_KEY]
                if (currentW != null) preferences[CURRENT_WEIGHT_KEY] = currentW / 2.20462
                if (goalW != null) preferences[GOAL_WEIGHT_KEY] = goalW / 2.20462
            }
            preferences[WEIGHTS_NORMALIZED_KEY] = true
        }
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
        preferences[CURRENT_WEIGHT_KEY] ?: com.apexfit.app.UserDefaults.WEIGHT_KG
    }

    val goalWeightFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[GOAL_WEIGHT_KEY] ?: com.apexfit.app.UserDefaults.WEIGHT_KG
    }

    val unitsFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UNITS_KEY] ?: "kg"
    }

    val heightFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[HEIGHT_KEY] ?: com.apexfit.app.UserDefaults.HEIGHT_CM
    }

    val ageFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AGE_KEY] ?: com.apexfit.app.UserDefaults.AGE_YEARS
    }

    val sexFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SEX_KEY] ?: "male"
    }

    val calorieTargetManualFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CALORIE_TARGET_MANUAL_KEY] ?: false
    }

    val calorieTargetValueFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CALORIE_TARGET_VALUE_KEY] ?: com.apexfit.app.UserDefaults.CALORIES
    }

    val equipmentFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EQUIPMENT_KEY] ?: com.apexfit.app.UserDefaults.DEFAULT_EQUIPMENT
    }

    val activeSessionJsonFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_SESSION_JSON_KEY]
    }

    suspend fun saveActiveSessionJson(json: String?) {
        context.dataStore.edit { preferences ->
            if (json == null) {
                preferences.remove(ACTIVE_SESSION_JSON_KEY)
            } else {
                preferences[ACTIVE_SESSION_JSON_KEY] = json
            }
        }
    }

    suspend fun saveOnboardingData(
        username: String,
        goal: String,
        currentWeight: Double,
        goalWeight: Double,
        height: Double = com.apexfit.app.UserDefaults.HEIGHT_CM,
        age: Int = com.apexfit.app.UserDefaults.AGE_YEARS,
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

    /**
     * AUDIT FIX (BUG-V4-015): one-time canonicalization of exercise_set weights
     * and PR values for users who had lb data written by a pre-v21 build.
     * Runs once per install; kg users are untouched.
     *
     * @param dao FitnessDao to execute the DAO update queries
     * @param isImperial true if the user's preferred unit is lb/lbs
     */
    suspend fun canonicalizeExerciseSetWeightsIfNeeded(
        dao: com.apexfit.app.data.FitnessDao,
        isImperial: Boolean
    ) {
        val alreadyDone = context.dataStore.data
            .map { prefs -> prefs[SET_WEIGHTS_CANONICALIZED_KEY] ?: false }
            .first()
        if (alreadyDone) return

        if (isImperial) {
            dao.convertAllExerciseSetWeightsToKg()
            dao.convertAllPersonalRecordValuesToKg()
        }

        context.dataStore.edit { prefs ->
            prefs[SET_WEIGHTS_CANONICALIZED_KEY] = true
        }
    }
}
