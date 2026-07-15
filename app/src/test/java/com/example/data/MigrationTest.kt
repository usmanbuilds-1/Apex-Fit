package com.example.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
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

    @Test
    @Throws(IOException::class)
    fun migrate10To12() {
        val dbName = "test_db_migration_12"

        // 1. Create database in version 10
        var db = helper.createDatabase(dbName, 10)

        // Insert parents
        db.execSQL("INSERT INTO workout_sessions (id, date, sessionType, completed, durationMinutes, sessionFeel) VALUES ('s_v10', '2023-10-01', 'Upper', 1, 60, 4)")
        db.execSQL("INSERT INTO exercises (id, name, category, primary_muscle, secondary_muscles, equipment_required, created_at) VALUES ('e_v10', 'Push Up', 'Bodyweight', 'Chest', '[\"Triceps\"]', 'None', 1696118400000)")
        db.execSQL("INSERT INTO exercises (id, name, category, primary_muscle, secondary_muscles, equipment_required, created_at) VALUES ('e_null', 'Null Exercise', 'None', 'None', NULL, 'None', 1696118400000)")
        db.execSQL("INSERT INTO workout_programs (id, name, goal, isActive, createdAt) VALUES (10, 'Plan v10', 'Strength', 1, 1696118400000)")
        db.execSQL("INSERT INTO plan_sessions (id, planId, label, day, focus) VALUES (100, 10, 'Day 1', 'Monday', 'Push')")

        // Insert children
        db.execSQL("INSERT INTO exercise_sets (id, sessionId, exerciseId, exerciseName, muscleGroup, weight, reps, rpe, isWarmup, completed, restTaken, repsInReserve, effectiveSetValue) VALUES (1000, 's_v10', 'e_v10', 'Push Up', 'Chest', 0.0, 10, 8, 0, 1, 60, 2, 10.0)")
        db.execSQL("INSERT INTO plan_exercises (id, planSessionId, name, muscleGroup, sets, repsMin, repsMax, weight, restSeconds, notes) VALUES (10000, 100, 'Push Up', 'Chest', 3, 8, 12, 0.0, 60, 'Keep form')")
        db.execSQL("INSERT INTO personal_records (id, exerciseId, type, value, date) VALUES ('e_v10_max_weight', 'e_v10', 'max_weight', 100.0, '2023-10-01')")

        db.close()

        // 2. Run migrations to 12
        // We skip validation for version 11 because 11.json is missing
        db = helper.runMigrationsAndValidate(
            dbName,
            12,
            true,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )

        db.execSQL("PRAGMA foreign_keys = ON")

        // 3. Verify data is preserved
        var cursor = db.query("SELECT * FROM exercise_sets WHERE id = 1000")
        assertTrue("ExerciseSet data lost", cursor.moveToFirst())
        cursor.close()

        cursor = db.query("SELECT * FROM plan_exercises WHERE id = 10000")
        assertTrue("PlanExercise data lost", cursor.moveToFirst())
        cursor.close()

        cursor = db.query("SELECT * FROM personal_records WHERE id = 'e_v10_max_weight'")
        assertTrue("PersonalRecord data lost", cursor.moveToFirst())
        cursor.close()

        // 4. Verify FK constraints are enforced (try inserting child with non-existent parent)
        try {
            db.execSQL("INSERT INTO exercise_sets (id, sessionId, exerciseId, exerciseName, muscleGroup, weight, reps, rpe, isWarmup, completed) VALUES (1001, 'non_existent', 'e_v10', 'Name', 'Group', 0.0, 10, 8, 0, 1)")
            fail("Should have thrown ForeignKeyConstraintException")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }

        // 5. Verify cascade delete works
        db.execSQL("DELETE FROM workout_sessions WHERE id = 's_v10'")
        cursor = db.query("SELECT * FROM exercise_sets WHERE sessionId = 's_v10'")
        assertTrue("ExerciseSet not cascaded", !cursor.moveToFirst())
        cursor.close()

        db.execSQL("DELETE FROM workout_programs WHERE id = 10")
        cursor = db.query("SELECT * FROM plan_sessions WHERE planId = 10")
        assertTrue("PlanSession not cascaded", !cursor.moveToFirst())
        cursor.close()

        cursor = db.query("SELECT * FROM plan_exercises WHERE planSessionId = 100")
        assertTrue("PlanExercise not cascaded", !cursor.moveToFirst())
        cursor.close()

        // 6. Verify secondaryMuscles (List<String>)
        cursor = db.query("SELECT secondary_muscles FROM exercises WHERE id = 'e_v10'")
        assertTrue(cursor.moveToFirst())
        val json = cursor.getString(0)
        assertTrue("Secondary muscles mapping failed", json.contains("Triceps"))
        cursor.close()

        cursor = db.query("SELECT secondary_muscles FROM exercises WHERE id = 'e_null'")
        assertTrue(cursor.moveToFirst())
        assertEquals("[]", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrate13to14() {
        val db = helper.createDatabase("test_db_migration_14", 13)
        db.execSQL("""INSERT INTO workout_sessions (id, date, sessionType, completed,
            durationMinutes, sessionFeel) VALUES ('test-uuid-1', '2024-01-01', 'Upper A', 1, 60, 4)""")
        db.execSQL("""INSERT INTO body_weights (date, time, weight)
            VALUES ('2024-01-01', '08:00', 80.5)""")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            "test_db_migration_14", 14, true, AppDatabase.MIGRATION_13_14
        )

        val sessionCursor = migratedDb.query("SELECT COUNT(*) FROM workout_sessions")
        sessionCursor.moveToFirst()
        assertEquals(1, sessionCursor.getInt(0))
        sessionCursor.close()

        val weightCursor = migratedDb.query("SELECT COUNT(*) FROM body_weights")
        weightCursor.moveToFirst()
        assertEquals(1, weightCursor.getInt(0))
        weightCursor.close()

        migratedDb.close()
    }
}
