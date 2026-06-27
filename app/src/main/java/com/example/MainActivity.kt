package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.DataStoreManager
import com.example.data.Exercise
import com.example.data.ExerciseMetadata
import com.example.ui.screens.ApexFitApp
import com.example.ui.theme.ApexFitTheme
import com.example.utils.CoachingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule local background coaching checks and trigger worker safely in background
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                CoachingScheduler.schedule6AmDailyCoachingTask(applicationContext)
            } catch (t: Throwable) {
                android.util.Log.e("MainActivity", "Failed to schedule daily coaching task safely", t)
            }
        }
        
        // Seed exercise database on first launch
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataStore = DataStoreManager(applicationContext)
                val isSeeded = dataStore.isExercisesSeededFlow.first()
                if (!isSeeded) {
                    seedDatabase(applicationContext)
                    dataStore.setExercisesSeeded(true)
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainActivity", "Failed to seed exercise database", t)
            }
        }
        
        // Enforce Edge to Edge insets
        enableEdgeToEdge()
        
        setContent {
            ApexFitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ApexFitApp()
                }
            }
        }
    }

    private suspend fun seedDatabase(context: Context) {
        val jsonString = context.assets.open("seed_exercises.json").bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val exercises = mutableListOf<Exercise>()
        val metadatas = mutableListOf<ExerciseMetadata>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val exerciseId = obj.getString("id")
            exercises.add(
                Exercise(
                    id = exerciseId,
                    name = obj.getString("name"),
                    category = obj.getString("category"),
                    primaryMuscle = obj.getString("primary_muscle"),
                    secondaryMuscles = obj.getString("secondary_muscles"),
                    equipmentRequired = obj.getString("equipment_required"),
                    isBilateral = obj.optInt("is_bilateral", 1),
                    isUserCreated = obj.optInt("is_user_created", 0),
                    isDeleted = obj.optInt("is_deleted", 0),
                    createdAt = System.currentTimeMillis()
                )
            )
            
            val metaObj = obj.getJSONObject("metadata")
            metadatas.add(
                ExerciseMetadata(
                    exerciseId = exerciseId,
                    fatigueCostCoefficient = metaObj.optDouble("fatigue_cost_coefficient", 1.0),
                    systemicMultiplier = metaObj.optDouble("systemic_multiplier", 1.0),
                    defaultProgressionIncrementKg = metaObj.optDouble("default_progression_increment_kg", 2.5),
                    minReps = metaObj.optInt("min_reps", 1),
                    maxReps = metaObj.optInt("max_reps", 30),
                    defaultRestSeconds = metaObj.optInt("default_rest_seconds", 120),
                    forceType = metaObj.optString("force_type", "push"),
                    recoveryTauDays = metaObj.optDouble("recovery_tau_days", 1.2),
                    notes = metaObj.optString("notes", null)
                )
            )
        }
        
        val dao = AppDatabase.getDatabase(context).fitnessDao()
        dao.insertExercises(exercises)
        dao.insertExerciseMetadataList(metadatas)
        android.util.Log.i("MainActivity", "Successfully seeded ${exercises.size} exercises into Room database.")
    }
}
