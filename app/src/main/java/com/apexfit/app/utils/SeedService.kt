package com.apexfit.app.utils

import android.content.Context
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata
import org.json.JSONArray

object SeedService {
    suspend fun seed(context: Context) {
        val db = com.apexfit.app.di.ServiceLocator.database(context)
        val dao = db.fitnessDao()
        val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(context)

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
                    secondaryMuscles = run {
                        val list = mutableListOf<String>()
                        val json = obj.optString("secondary_muscles", "[]")
                        val array = JSONArray(json)
                        for (j in 0 until array.length()) {
                            list.add(array.getString(j))
                        }
                        list
                    },
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
                    notes = metaObj.optString("notes", null as String?)
                )
            )
        }
        dao.insertExercises(exercises)
        dao.insertExerciseMetadataList(metadatas)
        dataStore.setExercisesSeeded(true)
        android.util.Log.i("SeedService", "Successfully re-seeded ${exercises.size} exercises.")
    }
}
