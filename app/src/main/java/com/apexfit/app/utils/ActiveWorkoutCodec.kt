package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSession
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.UUID

/** Decode known versions only. The caller must retain the original payload on any failure. */
internal object ActiveWorkoutCodec {
    data class Decoded(val session: ActiveSession, val migrated: Boolean, val normalized: Boolean = false, val legacyIdentity: Boolean = false)

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
            val proposal = exercise.get("progressionProposal")
            if (proposal != null && !proposal.isJsonNull) {
                require(ConfirmedProgressionProposal.valid(
                    com.google.gson.Gson().fromJson(proposal, ConfirmedProgressionProposal::class.java)))
            }
            val snapshot = exercise.get("secondaryMuscleSnapshot")
            if (snapshot != null && !snapshot.isJsonNull) {
                require(snapshot.isJsonPrimitive && snapshot.asJsonPrimitive.isString &&
                    MuscleAttribution.decode(snapshot.asString) != null) { "Invalid muscle attribution snapshot" }
            }
            val sets = exercise.getAsJsonArray("sets")
            require(sets != null)
            for (item in sets) {
                val set = item.asJsonObject
                require(set.get("weight")?.asDouble?.isFinite() == true)
                require((set.get("reps")?.asInt ?: -1) >= 0)
                require((set.get("setNumber")?.asInt ?: 0) > 0)
                require(set.get("weightUnit")?.asString?.lowercase() in setOf("kg", "lb", "lbs"))
                val evidence = set.get("setObservation")
                if (evidence != null && !evidence.isJsonNull) {
                    val kg = set.get("weight").asDouble.fromDisplayWeightToKg(set.get("weightUnit").asString)
                    require(SetObservationCodec.match(evidence.asString, kg, set.get("reps").asInt) != null &&
                        set.get("completed")?.asBoolean == true && set.get("isWarmup")?.asBoolean != true)
                }
                val details = set.get("advancedDetails")
                if (details != null && !details.isJsonNull) {
                    val kg = set.get("weight").asDouble.fromDisplayWeightToKg(set.get("weightUnit").asString)
                    require(AdvancedSets.decode(kg, details.asString) != null &&
                        set.get("completed")?.asBoolean == true && set.get("isWarmup")?.asBoolean != true)
                }
            }
        }
        val migrated = version != ActiveSession.CURRENT_VERSION
        if (migrated) {
            if (version < 9) json.remove("permanentGroups")
            if (version < 8) {
                json.remove("reservedOccurrenceId")
                json.remove("reservationRecordedAt")
            }
            if (version < 7) {
                json.remove("startedLocalDate")
                json.remove("scheduleOccurrenceId")
            }
            if (version == 1) json.addProperty("sessionId", newId())
            json.addProperty("schemaVersion", ActiveSession.CURRENT_VERSION)
        }
        val id = json.get("sessionId")?.asString.orEmpty()
        require(id.isNotBlank() && UUID.fromString(id).toString() == id) { "Invalid workout identity" }
        val decoded = gson.fromJson(json, ActiveSession::class.java)
        require(decoded.startedLocalDate == null ||
            runCatching { java.time.LocalDate.parse(decoded.startedLocalDate).toString() == decoded.startedLocalDate }
                .getOrDefault(false)) { "Invalid recorded start date" }
        require(decoded.scheduleOccurrenceId == null || ScheduleLedger.validOccurrence(
            decoded.scheduleOccurrenceId, decoded.startedLocalDate, decoded.planSessionId.toLongOrNull()
        )) { "Invalid scheduled occurrence" }
        require(ReservedWorkoutExecution.validDraft(decoded)) { "Invalid reserved workout identity" }
        val keys = WorkoutOccurrenceKeys.allocate(decoded.exercises.map { it.exerciseId })
        val current = decoded.exercises.map { it.executionKey }
        val normalized = current != keys
        val session = if (normalized) decoded.copy(exercises = decoded.exercises.mapIndexed { index, exercise ->
            exercise.copy(occurrenceKey = keys[index].takeIf { it != exercise.exerciseId })
        }.toMutableList()) else decoded
        require(CircuitExecution.valid(session)) { "Invalid saved circuit or permanent group" }
        return Decoded(session, migrated, normalized, version == 1)
    }
}
