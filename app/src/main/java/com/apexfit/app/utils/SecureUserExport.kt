package com.apexfit.app.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.google.gson.stream.JsonWriter
import androidx.room.withTransaction
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.dataStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.OutputStream
import java.time.Instant

/** Explicit allowlists: never export arbitrary preferences, tokens, or internal journals. */
internal object ExportPolicy {
    val tables = listOf("body_weights", "nutrition_logs", "workout_sessions", "exercise_sets",
        "workout_programs", "plan_sessions", "plan_exercises", "personal_records", "body_measurements",
        "detected_patterns", "exercises", "exercise_metadata", "schedule_revisions", "schedule_revision_slots",
        "schedule_dispositions", "schedule_workout_associations", "schedule_move_reservations")
    val preferenceKeys = setOf("username", "goal", "current_weight", "weight_profile_baseline_kg",
        "weight_profile_baseline_resolved_v1", "goal_weight", "units", "calorie_target_manual",
        "calorie_target_value", "equipment_available", "height", "age", "sex", "weekly_workouts",
        "training_experience", "protein_preference", "dietary_restrictions", "food_budget", "supplements",
        "profile_only_setup", "onboarded", "strength_set_load_confirmations_v1", "custom_fitness_goals_v1",
        "muscle_target_history_v1", "nutrition_day_confirmations_v1")
}

internal object SecureUserExport {
    private const val DIRECTORY = "user-export-staging"

    /** Clean up artifacts from an interrupted previous process; never scan arbitrary cache paths. */
    fun cleanupStale(context: Context, now: Long = System.currentTimeMillis()) {
        val dir = File(context.cacheDir, DIRECTORY)
        dir.listFiles()?.filter { it.isFile && it.name.startsWith("snapshot-") &&
            it.name.endsWith(".json") && now - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach { it.delete() }
        // Remove the exact old fixed-name export created by prior versions.
        context.getExternalFilesDir(null)?.let { File(it, "apexfit_export.csv").delete() }
    }

    /** Must run under LocalDataGate. Files saved to user-selected providers are outside app control. */
    fun clearStaging(context: Context) {
        val dir = File(context.cacheDir, DIRECTORY)
        if (dir.exists()) check(dir.deleteRecursively()) { "Could not delete export staging files" }
        context.getExternalFilesDir(null)?.let { root ->
            val legacy = File(root, "apexfit_export.csv")
            if (legacy.exists()) check(legacy.delete()) { "Could not delete legacy export" }
        }
    }

    suspend fun writeSnapshot(db: AppDatabase, preferences: Map<String, Any>, output: OutputStream) {
        JsonWriter(output.bufferedWriter(Charsets.UTF_8)).use { writer ->
            writer.serializeNulls = true
            writer.beginObject()
            writer.name("format").value("apex-fit-data-export")
            writer.name("format_version").value(1L)
            writer.name("generated_at_utc").value(Instant.now().toString())
            writer.name("snapshot_scope").value("Saved Room tables in one transaction; allowlisted preferences read separately. Not a restore backup.")
            writer.name("preferences").beginObject()
            for ((key, value) in preferences.toSortedMap()) {
                if (key !in ExportPolicy.preferenceKeys) continue
                writer.name(key)
                writePreference(writer, value)
            }
            writer.endObject()
            writer.name("tables").beginObject()
            db.withTransaction {
                val sqlite = db.openHelper.writableDatabase
                for (table in ExportPolicy.tables) {
                    currentCoroutineContext().ensureActive()
                    writer.name(table).beginArray()
                    // Table names are fixed source literals above, not user-controlled input.
                    sqlite.query("SELECT * FROM \"$table\"").use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            writer.beginObject()
                            for (column in 0 until cursor.columnCount) {
                                writer.name(cursor.getColumnName(column))
                                when (cursor.getType(column)) {
                                    Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                                    Cursor.FIELD_TYPE_INTEGER -> writer.value(cursor.getLong(column))
                                    Cursor.FIELD_TYPE_FLOAT -> {
                                        val value = cursor.getDouble(column)
                                        if (value.isFinite()) writer.value(value) else writer.value(value.toString())
                                    }
                                    Cursor.FIELD_TYPE_STRING -> writer.value(cursor.getString(column))
                                    else -> error("Unsupported export column type")
                                }
                            }
                            writer.endObject()
                        }
                    }
                    writer.endArray()
                }
            }
            writer.endObject()
            writer.endObject()
        }
    }

    private fun writePreference(writer: JsonWriter, value: Any) {
        when (value) {
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Int -> writer.value(value.toLong())
            is Long -> writer.value(value)
            is Double -> if (value.isFinite()) writer.value(value) else writer.value(value.toString())
            is Float -> if (value.isFinite()) writer.value(value.toDouble()) else writer.value(value.toString())
            is Set<*> -> {
                require(value.all { it is String })
                writer.beginArray()
                value.map { it as String }.sorted().forEach { writer.value(it) }
                writer.endArray()
            }
            else -> error("Unsupported export preference type")
        }
    }

    suspend fun save(context: Context, destination: Uri, expectedGeneration: Long = LocalResetCoordinator.currentGeneration()) = LocalDataGate.exclusive {
        check(LocalResetCoordinator.permits(context, expectedGeneration)) { "Local data changed; choose a new export" }
        saveLocked(context, destination)
    }

    private suspend fun saveLocked(context: Context, destination: Uri) {
        check(!LocalResetCoordinator.pending(context)) { "Local deletion pending" }
        require(destination.scheme == "content") { "Expected a document provider URI" }
        cleanupStale(context)
        val directory = File(context.cacheDir, DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create export staging directory" }
        val temporary = File.createTempFile("snapshot-", ".json", directory)
        try {
            val preferences = context.dataStore.data.first().asMap().mapKeys { it.key.name }
                .filterKeys { it in ExportPolicy.preferenceKeys }
            writeSnapshot(AppDatabase.getDatabase(context), preferences, temporary.outputStream())
            currentCoroutineContext().ensureActive()
            val stream = context.contentResolver.openOutputStream(destination, "wt")
                ?: error("Document provider refused export")
            stream.use { output ->
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.flush()
            }
        } finally {
            temporary.delete()
        }
    }
}
