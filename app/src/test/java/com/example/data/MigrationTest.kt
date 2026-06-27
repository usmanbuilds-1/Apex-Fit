package com.example.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate8To10() {
        val dbName = "test_db"
        
        // Create database in version 8
        var db = helper.createDatabase(dbName, 8)
        
        // Insert sample data matching v8 schema
        db.execSQL("""
            INSERT INTO training_sessions (id, date, sessionType, completed, durationMinutes, sessionFeel)
            VALUES ('s1', '2023-10-01', 'Upper A', 1, 60, 4)
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO weight_entries (date, time, weight)
            VALUES ('2023-10-01', '08:00 AM', 75.0)
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO nutrition_entries (date, name, time, calories, protein, carbs, fat)
            VALUES ('2023-10-01', 'Breakfast', '08:00 AM', 500, 30.0, 40.0, 15.0)
        """.trimIndent())
        
        db.execSQL("""
            INSERT INTO workout_plans (id, name, goal, isActive, createdAt)
            VALUES (1, 'Beginner', 'Hypertrophy', 1, 1696118400000)
        """.trimIndent())
        
        // Close DB
        db.close()

        // Run migrations to 10
        db = helper.runMigrationsAndValidate(
            dbName, 
            10, 
            true, 
            AppDatabase.MIGRATION_8_9, 
            AppDatabase.MIGRATION_9_10
        )
        
        // Query to check if data is there
        var cursor = db.query("SELECT * FROM workout_sessions WHERE id = 's1'")
        assertTrue(cursor.moveToFirst())
        cursor.close()
        
        cursor = db.query("SELECT * FROM body_weights")
        assertTrue(cursor.moveToFirst())
        cursor.close()
        
        cursor = db.query("SELECT * FROM nutrition_logs")
        assertTrue(cursor.moveToFirst())
        cursor.close()
        
        cursor = db.query("SELECT * FROM workout_programs WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        cursor.close()
    }
}
