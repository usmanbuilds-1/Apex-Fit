package com.apexfit.app.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.TrainingSession
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureUserExportTest {
    @Test fun fullSnapshotPreservesOldRowsNullsFormatAndLiteralSensitiveText() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val text = "=SUM(1,2)\n\"literal\""
            db.fitnessDao().insertTrainingSession(TrainingSession("legacy", "2020-01-01", "Upper", true, 30, 3, notes = text))
            db.fitnessDao().insertTrainingSession(TrainingSession("group", "2026-11-28", "Upper", true, 25, 4,
                trainingFormat = "grouped"))
            val out = ByteArrayOutputStream()
            SecureUserExport.writeSnapshot(db, mapOf("username" to text, "active_session_json" to "SECRET",
                "workout_receipt_json_v1" to "SECRET", "strength_set_load_confirmations_v1" to setOf("a","b"),
                "unrecognized_token" to "SECRET"), out)
            val json = JsonParser.parseString(out.toString(Charsets.UTF_8.name())).asJsonObject
            assertEquals(1, json.get("format_version").asInt)
            val prefs = json.getAsJsonObject("preferences")
            assertEquals(text, prefs.get("username").asString)
            assertEquals(2, prefs.getAsJsonArray("strength_set_load_confirmations_v1").size())
            assertFalse(out.toString(Charsets.UTF_8.name()).contains("SECRET"))
            val tables = json.getAsJsonObject("tables")
            assertEquals(ExportPolicy.tables.toSet(), tables.keySet())
            val workouts = tables.getAsJsonArray("workout_sessions").associate { it.asJsonObject.get("id").asString to it.asJsonObject }
            assertEquals("2020-01-01", workouts.getValue("legacy").get("date").asString)
            assertEquals(text, workouts.getValue("legacy").get("notes").asString)
            assertTrue(workouts.getValue("legacy").get("training_format").isJsonNull)
            assertEquals("grouped", workouts.getValue("group").get("training_format").asString)
            assertTrue(tables.getAsJsonArray("schedule_move_reservations").isEmpty)
        } finally { db.close() }
    }
}
