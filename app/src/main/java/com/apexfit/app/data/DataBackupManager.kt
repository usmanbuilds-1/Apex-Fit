package com.apexfit.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class DataBackupManager(
    private val context: Context,
    private val database: AppDatabase
) {

    suspend fun exportUserDataToJson(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = database.fitnessDao().getAllSessionsDirect()
            val sets = database.fitnessDao().getAllSetsDirect()

            val rootJson = JSONObject().apply {
                put("app_version", 26)
                put("export_timestamp", System.currentTimeMillis())

                val sessionArray = JSONArray()
                sessions.forEach { s ->
                    val sObj = JSONObject().apply {
                        put("session_id", s.sessionId)
                        put("title", s.title)
                        put("start_time", s.startTimeEpoch)
                        put("end_time", s.endTimeEpoch ?: 0L)
                        put("duration_seconds", s.durationSeconds)
                        put("total_volume_kg", s.totalVolumeKg)
                        put("notes", s.notes ?: "")
                    }
                    sessionArray.put(sObj)
                }
                put("sessions", sessionArray)

                val setsArray = JSONArray()
                sets.forEach { st ->
                    val stObj = JSONObject().apply {
                        put("set_id", st.setId)
                        put("parent_session_id", st.parentSessionId)
                        put("exercise_id", st.exerciseId)
                        put("set_order", st.setOrder)
                        put("weight_kg", st.weightKg)
                        put("reps", st.repsCompleted)
                        put("rpe", st.rpe ?: -1.0)
                        put("is_warmup", st.isWarmup)
                    }
                    setsArray.put(stObj)
                }
                put("sets", setsArray)
            }

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(rootJson.toString(2))
                }
            } ?: throw IllegalStateException("Unable to open output stream.")
        }
    }

    suspend fun importUserDataFromJson(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stringBuilder = java.lang.StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            } ?: throw IllegalStateException("Unable to open input stream.")

            val rootJson = JSONObject(stringBuilder.toString())
            val sessionArray = rootJson.getJSONArray("sessions")
            val setsArray = rootJson.getJSONArray("sets")

            val sessions = mutableListOf<WorkoutSessionEntity>()
            for (i in 0 until sessionArray.length()) {
                val obj = sessionArray.getJSONObject(i)
                sessions.add(
                    WorkoutSessionEntity(
                        sessionId = obj.getLong("session_id"),
                        routineId = null,
                        title = obj.getString("title"),
                        startTimeEpoch = obj.getLong("start_time"),
                        endTimeEpoch = obj.getLong("end_time"),
                        durationSeconds = obj.getLong("duration_seconds"),
                        totalVolumeKg = obj.getDouble("total_volume_kg"),
                        notes = obj.optString("notes", null)
                    )
                )
            }

            val sets = mutableListOf<WorkoutSetEntity>()
            for (i in 0 until setsArray.length()) {
                val obj = setsArray.getJSONObject(i)
                val rpeVal = obj.getDouble("rpe")
                sets.add(
                    WorkoutSetEntity(
                        setId = obj.getLong("set_id"),
                        parentSessionId = obj.getLong("parent_session_id"),
                        exerciseId = obj.getLong("exercise_id"),
                        setOrder = obj.getInt("set_order"),
                        weightKg = obj.getDouble("weight_kg"),
                        repsCompleted = obj.getInt("reps"),
                        rpe = if (rpeVal > 0) rpeVal else null,
                        isWarmup = obj.getBoolean("is_warmup"),
                        isCompleted = true
                    )
                )
            }

            database.fitnessDao().restoreWorkoutData(sessions, sets)
            sessions.size
        }
    }
}