package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSession
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID

/** Decode known versions only. The caller must retain the original payload on any failure. */
internal object ActiveWorkoutCodec {
    data class Decoded(val session: ActiveSession, val migrated: Boolean)

    fun decode(raw: String, gson: Gson = Gson(), newId: () -> String = { UUID.randomUUID().toString() }): Decoded {
        val json = JsonParser.parseString(raw).asJsonObject
        val version = json.get("schemaVersion")?.asInt ?: 1
        require(version in 1..ActiveSession.CURRENT_VERSION) { "Unsupported saved workout version" }
        require(json.get("planSessionId")?.asString?.isNotBlank() == true)
        require(json.get("sessionType")?.asString?.isNotBlank() == true)
        require((json.get("startTime")?.asLong ?: 0L) > 0L)
        val exercises = json.getAsJsonArray("exercises")
        require(exercises != null)
        for (entry in exercises) {
            val exercise = entry.asJsonObject
            require(exercise.get("exerciseId")?.asString?.isNotBlank() == true)
            require(exercise.get("exerciseName")?.asString?.isNotBlank() == true)
            require(exercise.get("muscleGroup")?.isJsonPrimitive == true)
            val sets = exercise.getAsJsonArray("sets")
            require(sets != null)
            for (item in sets) {
                val set = item.asJsonObject
                require(set.get("weight")?.asDouble?.isFinite() == true)
                require((set.get("reps")?.asInt ?: -1) >= 0)
                require((set.get("setNumber")?.asInt ?: 0) > 0)
                require(set.get("weightUnit")?.asString?.lowercase() in setOf("kg", "lb", "lbs"))
            }
        }
        val migrated = version != ActiveSession.CURRENT_VERSION
        if (migrated) {
            json.addProperty("sessionId", newId())
            json.addProperty("schemaVersion", ActiveSession.CURRENT_VERSION)
        }
        val id = json.get("sessionId")?.asString.orEmpty()
        require(id.isNotBlank() && UUID.fromString(id).toString() == id) { "Invalid workout identity" }
        return Decoded(gson.fromJson(json, ActiveSession::class.java), migrated)
    }
}
