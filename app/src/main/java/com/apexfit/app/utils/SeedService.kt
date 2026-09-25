package com.apexfit.app.utils

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata
import org.json.JSONArray

object SeedService {

    const val CURRENT_SEED_VERSION = 2

    suspend fun seed(context: Context) {
        val db = com.apexfit.app.di.ServiceLocator.database(context)
        val dao = db.fitnessDao()
        val dataStore = com.apexfit.app.di.ServiceLocator.dataStore(context)

        val exercises = mutableListOf<Exercise>()
        val metadatas = mutableListOf<ExerciseMetadata>()
        val now = System.currentTimeMillis()

        context.assets.open("seed_exercises.json").use { stream ->
            JsonReader(stream.reader()).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    val item = parseExerciseItem(reader, now)
                    if (item != null) {
                        exercises.add(item.first)
                        metadatas.add(item.second)
                    }
                }
                reader.endArray()
            }
        }

        dao.insertSeed(exercises, metadatas)
        dataStore.setExercisesSeeded(true)
        dataStore.setExerciseSeedVersion(CURRENT_SEED_VERSION)
        Log.i("SeedService", "Successfully seeded ${exercises.size} exercises (seed version $CURRENT_SEED_VERSION).")
    }

    private fun parseExerciseItem(
        reader: JsonReader,
        createdAt: Long
    ): Pair<Exercise, ExerciseMetadata>? {
        var id = ""
        var name = ""
        var category = ""
        var primaryMuscle = ""
        var secondaryMuscles = emptyList<String>()
        var equipmentRequired = ""
        var isBilateral = 1
        var isUserCreated = 0
        var isDeleted = 0
        var metadata: ExerciseMetadata? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "name" -> name = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "category" -> category = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "primary_muscle" -> primaryMuscle = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "secondary_muscles" -> secondaryMuscles = parseSecondaryMuscles(reader)
                "equipment_required" -> equipmentRequired = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "is_bilateral" -> isBilateral = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 1 } else reader.nextInt()
                "is_user_created" -> isUserCreated = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 0 } else reader.nextInt()
                "is_deleted" -> isDeleted = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 0 } else reader.nextInt()
                "metadata" -> metadata = parseMetadata(reader, id)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (id.isEmpty()) return null

        val exercise = Exercise(
            id = id,
            name = name,
            category = category,
            primaryMuscle = primaryMuscle,
            secondaryMuscles = secondaryMuscles,
            equipmentRequired = equipmentRequired,
            isBilateral = isBilateral,
            isUserCreated = isUserCreated,
            isDeleted = isDeleted,
            createdAt = createdAt
        )

        val finalMetadata = metadata?.copy(exerciseId = id) ?: ExerciseMetadata(
            exerciseId = id,
            fatigueCostCoefficient = 1.0,
            systemicMultiplier = 1.0,
            defaultProgressionIncrementKg = 2.5,
            minReps = 1,
            maxReps = 30,
            defaultRestSeconds = 120,
            forceType = "push",
            recoveryTauDays = 1.2,
            notes = null
        )

        return Pair(exercise, finalMetadata)
    }

    private fun parseSecondaryMuscles(reader: JsonReader): List<String> {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val list = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        list.add(reader.nextString())
                    }
                }
                reader.endArray()
                list
            }
            JsonToken.STRING -> {
                val list = mutableListOf<String>()
                val json = reader.nextString()
                try {
                    val array = JSONArray(json)
                    for (j in 0 until array.length()) {
                        list.add(array.getString(j))
                    }
                } catch (_: Exception) {}
                list
            }
            else -> {
                reader.skipValue()
                emptyList()
            }
        }
    }

    private fun parseMetadata(reader: JsonReader, exerciseId: String): ExerciseMetadata {
        var fatigueCost = 1.0
        var systemic = 1.0
        var defaultProgression = 2.5
        var minReps = 1
        var maxReps = 30
        var defaultRestSeconds = 120
        var forceType = "push"
        var recoveryTauDays = 1.2
        var notes: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "fatigue_cost_coefficient" -> fatigueCost = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 1.0 } else reader.nextDouble()
                "systemic_multiplier" -> systemic = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 1.0 } else reader.nextDouble()
                "default_progression_increment_kg" -> defaultProgression = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 2.5 } else reader.nextDouble()
                "min_reps" -> minReps = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 1 } else reader.nextInt()
                "max_reps" -> maxReps = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 30 } else reader.nextInt()
                "default_rest_seconds" -> defaultRestSeconds = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 120 } else reader.nextInt()
                "force_type" -> forceType = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "push" } else reader.nextString()
                "recovery_tau_days" -> recoveryTauDays = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); 1.2 } else reader.nextDouble()
                "notes" -> {
                    notes = if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        null
                    } else {
                        reader.nextString()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ExerciseMetadata(
            exerciseId = exerciseId,
            fatigueCostCoefficient = fatigueCost,
            systemicMultiplier = systemic,
            defaultProgressionIncrementKg = defaultProgression,
            minReps = minReps,
            maxReps = maxReps,
            defaultRestSeconds = defaultRestSeconds,
            forceType = forceType,
            recoveryTauDays = recoveryTauDays,
            notes = notes
        )
    }
}
