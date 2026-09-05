package com.apexfit.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val TEST_DB = "migration_test_db"
    private val migrationTestHelper get() = helper

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
    @Throws(IOException::class)
    fun migrate12To13() {
        val db = migrationTestHelper.createDatabase(TEST_DB, 12)
        db.execSQL("INSERT INTO workout_sessions (id, date, sessionType, completed, durationMinutes, sessionFeel) VALUES ('s1', '2024-01-01', 'Upper A', 1, 45, 4)")
        db.close()
        val migrated = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 13, true, AppDatabase.MIGRATION_12_13
        )
        val cursor = migrated.query("SELECT COUNT(*) FROM workout_sessions")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
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

    @Test
    @Throws(IOException::class)
    fun migrate14To15() {
        val db = migrationTestHelper.createDatabase(TEST_DB, 14)
        db.execSQL("INSERT INTO body_weights (date, time, weight, trend_weight_kg) VALUES ('2024-01-01', '08:00', 80.0, 80.0)")
        db.close()
        val migrated = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 15, true, AppDatabase.MIGRATION_14_15
        )
        val cursor = migrated.query("SELECT weight FROM body_weights LIMIT 1")
        cursor.moveToFirst()
        assertEquals(80.0, cursor.getDouble(0), 0.001)
        cursor.close()
    }

    @Test
    fun migrate15To16() {
        val dbName = "test_db_migration_16"
        var db = helper.createDatabase(dbName, 15)
        db.execSQL("INSERT INTO body_weights (date, time, weight) VALUES ('2024-01-01', '08:00', 80.0)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            dbName, 16, true, AppDatabase.MIGRATION_15_16
        )

        val cursor = migratedDb.query("SELECT unit FROM body_weights LIMIT 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("kg", cursor.getString(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate16To17() {
        val dbName = "test_db_migration_17"
        var db = helper.createDatabase(dbName, 16)
        db.execSQL("INSERT INTO exercises (id, name, category, primary_muscle, secondary_muscles, equipment_required, created_at) VALUES ('e1', 'Bench Press', 'Chest', 'Chest', '[]', 'Barbell', 1000)")
        db.execSQL("INSERT INTO exercise_metadata (exercise_id) VALUES ('e1')")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            dbName, 17, true, AppDatabase.MIGRATION_16_17
        )

        val cursor = migratedDb.query("SELECT stalledSessions FROM exercise_metadata LIMIT 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate17To18() {
        val dbName = "test_db_migration_18"
        var db = helper.createDatabase(dbName, 17)
        db.execSQL("INSERT INTO workout_sessions (id, date, sessionType, completed, durationMinutes, sessionFeel) VALUES ('s1', '2024-01-01', 'Upper', 1, 60, 4)")
        db.execSQL("INSERT INTO exercises (id, name, category, primary_muscle, secondary_muscles, equipment_required, created_at) VALUES ('e1', 'Bench Press', 'Chest', 'Chest', '[]', 'Barbell', 1000)")
        db.execSQL("""
            INSERT INTO exercise_sets (id, sessionId, exerciseId, exerciseName, muscleGroup, weight, reps, rpe, isWarmup, restTaken, completed, repsInReserve, effectiveSetValue)
            VALUES (1, 's1', 'e1', 'Bench Press', 'Chest', 100.0, 10, 8, 0, 90, 1, 2, 1.0)
        """.trimIndent())
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            dbName, 18, true, AppDatabase.MIGRATION_17_18
        )

        val cursor = migratedDb.query("SELECT COUNT(*) FROM exercise_sets")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate18To19() {
        val dbName = "test_db_migration_19"
        var db = helper.createDatabase(dbName, 18)
        db.execSQL("INSERT INTO body_weights (date, time, weight, unit) VALUES ('2024-01-01', '08:00', 80.0, 'kg')")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            dbName, 19, true, AppDatabase.MIGRATION_18_19
        )

        val cursor = migratedDb.query("SELECT weight FROM body_weights LIMIT 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(80.0, cursor.getDouble(0), 0.001)
        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrateAll8To20() {
        val dbName = "test_migrate_8_to_20"
        var db = helper.createDatabase(dbName, 8)
        db.execSQL("INSERT INTO weight_entries (date, time, weight) VALUES ('2023-01-01', '08:00', 75.5)")
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            20,
            true,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
            AppDatabase.MIGRATION_17_18,
            AppDatabase.MIGRATION_18_19,
            AppDatabase.MIGRATION_19_20
        )

        val cursor = migratedDb.query("SELECT weight FROM body_weights LIMIT 1")
        assertTrue("Canary row should survive migrations from 8 to 20", cursor.moveToFirst())
        assertEquals(75.5, cursor.getDouble(0), 0.001)
        cursor.close()
        migratedDb.close()
    }

    // ─── AUDIT FIX (MY-001): Native-mode tests for rebuild migrations.
    // The class-level @SQLiteMode(LEGACY) annotation masks index divergence.
    // These three tests run in native SQLite mode and will catch it.
    // They use a helper that validates the migrated schema and asserts indexes.

    @Test
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    @Throws(IOException::class)
    fun migration10To11_recreatesExerciseSetIndexes() {
        helper.createDatabase(TEST_DB, 10).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true,
            AppDatabase.MIGRATION_10_11)
        val cursor = db.query("PRAGMA index_list(`exercise_sets`)")
        val indexNames = buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        cursor.close()
        db.close()
        assert(indexNames.any { it.contains("sessionId") }) {
            "index_exercise_sets_sessionId missing after MIGRATION_10_11"
        }
        assert(indexNames.any { it.contains("exerciseId") }) {
            "index_exercise_sets_exerciseId missing after MIGRATION_10_11"
        }
    }

    @Test
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    @Throws(IOException::class)
    fun migration17To18_recreatesExerciseSetIndexes() {
        helper.createDatabase(TEST_DB, 17).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true,
            AppDatabase.MIGRATION_17_18)
        val cursor = db.query("PRAGMA index_list(`exercise_sets`)")
        val indexNames = buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        cursor.close()
        db.close()
        assert(indexNames.any { it.contains("sessionId") }) {
            "index_exercise_sets_sessionId missing after MIGRATION_17_18"
        }
        assert(indexNames.any { it.contains("exerciseId") }) {
            "index_exercise_sets_exerciseId missing after MIGRATION_17_18"
        }
    }

    @Test
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    @Throws(IOException::class)
    fun migration20To21_addsWeightUnitColumn() {
        helper.createDatabase(TEST_DB, 20).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true,
            AppDatabase.MIGRATION_20_21)
        val cursor = db.query("PRAGMA table_info(`exercise_sets`)")
        val columns = buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        cursor.close()
        db.close()
        assert(columns.contains("weight_unit")) {
            "weight_unit column missing after MIGRATION_20_21"
        }
    }
}
