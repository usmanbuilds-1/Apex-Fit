package com.apexfit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WeightEntry::class,
        NutritionEntry::class,
        TrainingSession::class,
        ExerciseSet::class,
        WorkoutPlan::class,
        PlanSession::class,
        PlanExercise::class,
        PersonalRecord::class,
        BodyMeasurement::class,
        DetectedPatternEntity::class,
        Exercise::class,
        ExerciseMetadata::class
    ],
    version = 22,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fitnessDao(): FitnessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration - no schema changes between 6 and 7
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_sessionId` ON `exercise_sets` (`sessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_entries_date` ON `nutrition_entries` (`date`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_training_sessions_date` ON `training_sessions` (`date`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `training_sessions` RENAME TO `workout_sessions`")
                database.execSQL("ALTER TABLE `weight_entries` RENAME TO `body_weights`")
                database.execSQL("ALTER TABLE `nutrition_entries` RENAME TO `nutrition_logs`")
                database.execSQL("ALTER TABLE `workout_plans` RENAME TO `workout_programs`")

                database.execSQL("DROP INDEX IF EXISTS `index_nutrition_entries_date`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_logs_date` ON `nutrition_logs` (`date`)")
                database.execSQL("DROP INDEX IF EXISTS `index_training_sessions_date`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_date` ON `workout_sessions` (`date`)")

                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `training_week_id` INTEGER")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `plan_session_id` INTEGER")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `status` TEXT DEFAULT 'completed'")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `readiness_score_at_start` INTEGER")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `notes` TEXT")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `started_at` INTEGER")
                database.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `completed_at` INTEGER")

                database.execSQL("ALTER TABLE `body_weights` ADD COLUMN `trend_weight_kg` REAL")

                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `description` TEXT")
                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `days_per_week` INTEGER DEFAULT 4")
                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `is_template` INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `source` TEXT DEFAULT 'manual'")
                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `import_raw_text` TEXT")
                database.execSQL("ALTER TABLE `workout_programs` ADD COLUMN `activated_at` INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercises` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `primary_muscle` TEXT NOT NULL, 
                        `secondary_muscles` TEXT, 
                        `equipment_required` TEXT NOT NULL, 
                        `is_bilateral` INTEGER NOT NULL DEFAULT 1, 
                        `is_user_created` INTEGER NOT NULL DEFAULT 0, 
                        `is_deleted` INTEGER NOT NULL DEFAULT 0, 
                        `created_at` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_metadata` (
                        `exercise_id` TEXT NOT NULL, 
                        `fatigue_cost_coefficient` REAL NOT NULL DEFAULT 1.0, 
                        `systemic_multiplier` REAL NOT NULL DEFAULT 1.0, 
                        `default_progression_increment_kg` REAL NOT NULL DEFAULT 2.5, 
                        `min_reps` INTEGER NOT NULL DEFAULT 1, 
                        `max_reps` INTEGER NOT NULL DEFAULT 30, 
                        `default_rest_seconds` INTEGER NOT NULL DEFAULT 120, 
                        `force_type` TEXT NOT NULL DEFAULT 'push', 
                        `recovery_tau_days` REAL NOT NULL DEFAULT 1.2, 
                        `notes` TEXT, 
                        PRIMARY KEY(`exercise_id`), 
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // PART A: Migration for foreign keys and nullability

                // 1. Recreate Exercises to make secondary_muscles NOT NULL
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercises_new` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `primary_muscle` TEXT NOT NULL, 
                        `secondary_muscles` TEXT NOT NULL, 
                        `equipment_required` TEXT NOT NULL, 
                        `is_bilateral` INTEGER NOT NULL DEFAULT 1, 
                        `is_user_created` INTEGER NOT NULL DEFAULT 0, 
                        `is_deleted` INTEGER NOT NULL DEFAULT 0, 
                        `created_at` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)
                database.execSQL("INSERT INTO `exercises_new` SELECT id, name, category, primary_muscle, COALESCE(secondary_muscles, '[]'), equipment_required, is_bilateral, is_user_created, is_deleted, created_at FROM `exercises`")
                database.execSQL("DROP TABLE `exercises`")
                database.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)")

                // 2. Recreate ExerciseMetadata
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_metadata_new` (
                        `exercise_id` TEXT NOT NULL, 
                        `fatigue_cost_coefficient` REAL NOT NULL DEFAULT 1.0, 
                        `systemic_multiplier` REAL NOT NULL DEFAULT 1.0, 
                        `default_progression_increment_kg` REAL NOT NULL DEFAULT 2.5, 
                        `min_reps` INTEGER NOT NULL DEFAULT 1, 
                        `max_reps` INTEGER NOT NULL DEFAULT 30, 
                        `default_rest_seconds` INTEGER NOT NULL DEFAULT 120, 
                        `force_type` TEXT NOT NULL DEFAULT 'push', 
                        `recovery_tau_days` REAL NOT NULL DEFAULT 1.2, 
                        `notes` TEXT, 
                        PRIMARY KEY(`exercise_id`), 
                        FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """)
                database.execSQL("INSERT INTO `exercise_metadata_new` (exercise_id, fatigue_cost_coefficient, systemic_multiplier, default_progression_increment_kg, min_reps, max_reps, default_rest_seconds, force_type, recovery_tau_days, notes) SELECT exercise_id, fatigue_cost_coefficient, systemic_multiplier, default_progression_increment_kg, min_reps, max_reps, default_rest_seconds, force_type, recovery_tau_days, notes FROM `exercise_metadata`")
                database.execSQL("DROP TABLE `exercise_metadata`")
                database.execSQL("ALTER TABLE `exercise_metadata_new` RENAME TO `exercise_metadata`")

                // 3. ExerciseSet
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_sets_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `muscleGroup` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `reps` INTEGER NOT NULL,
                        `rpe` INTEGER NOT NULL,
                        `isWarmup` INTEGER NOT NULL,
                        `restTaken` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `repsInReserve` INTEGER NOT NULL,
                        `effectiveSetValue` REAL NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("INSERT INTO `exercise_sets_new` (id, sessionId, exerciseId, exerciseName, muscleGroup, weight, reps, rpe, isWarmup, restTaken, completed, repsInReserve, effectiveSetValue) SELECT id, sessionId, exerciseId, exerciseName, muscleGroup, weight, reps, rpe, isWarmup, restTaken, completed, repsInReserve, effectiveSetValue FROM `exercise_sets`")
                database.execSQL("DROP TABLE `exercise_sets`")
                database.execSQL("ALTER TABLE `exercise_sets_new` RENAME TO `exercise_sets`")
                // AUDIT FIX (BUG-V4-008): restore indexes destroyed by the DROP above.
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_sessionId` ON `exercise_sets` (`sessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)")

                // 4. PlanSession
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plan_sessions_new` (
                        `id` INTEGER NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `day` TEXT NOT NULL,
                        `focus` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`planId`) REFERENCES `workout_programs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("INSERT INTO `plan_sessions_new` (id, planId, label, day, focus) SELECT id, planId, label, day, focus FROM `plan_sessions`")
                database.execSQL("DROP TABLE `plan_sessions`")
                database.execSQL("ALTER TABLE `plan_sessions_new` RENAME TO `plan_sessions`")

                // 5. PlanExercise
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plan_exercises_new` (
                        `id` INTEGER NOT NULL,
                        `planSessionId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `muscleGroup` TEXT NOT NULL,
                        `sets` INTEGER NOT NULL,
                        `repsMin` INTEGER NOT NULL,
                        `repsMax` INTEGER NOT NULL,
                        `weight` REAL NOT NULL,
                        `restSeconds` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`planSessionId`) REFERENCES `plan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("INSERT INTO `plan_exercises_new` (id, planSessionId, name, muscleGroup, sets, repsMin, repsMax, weight, restSeconds, notes) SELECT id, planSessionId, name, muscleGroup, sets, repsMin, repsMax, weight, restSeconds, notes FROM `plan_exercises`")
                database.execSQL("DROP TABLE `plan_exercises`")
                database.execSQL("ALTER TABLE `plan_exercises_new` RENAME TO `plan_exercises`")

                // 6. PersonalRecord
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `personal_records_new` (
                        `id` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `date` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                database.execSQL("INSERT INTO `personal_records_new` (id, exerciseId, type, value, date) SELECT id, exerciseId, type, value, date FROM `personal_records`")
                database.execSQL("DROP TABLE `personal_records`")
                database.execSQL("ALTER TABLE `personal_records_new` RENAME TO `personal_records`")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("UPDATE exercises SET secondary_muscles = '[]' WHERE secondary_muscles IS NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `weekly_reports`")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema version 14 adds no tables, columns, or indexes.
                // The version bump was introduced to force a clean re-seed
                // of the exercises table via SeedService on app update.
                // Verified against: app/schemas/.../13.json vs 14.json diff.
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_sessions_planId` ON `plan_sessions` (`planId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_planSessionId` ON `plan_exercises` (`planSessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_personal_records_exerciseId` ON `personal_records` (`exerciseId`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE body_weights ADD COLUMN unit TEXT NOT NULL DEFAULT 'kg'"
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE exercise_metadata ADD COLUMN stalledSessions INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE exercise_sets_v18 (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `exerciseId` TEXT NOT NULL,
                        `exerciseName` TEXT NOT NULL,
                        `muscleGroup` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `reps` INTEGER NOT NULL,
                        `rpe` INTEGER NOT NULL,
                        `isWarmup` INTEGER NOT NULL,
                        `restTaken` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `repsInReserve` INTEGER NOT NULL,
                        `effectiveSetValue` REAL NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON DELETE CASCADE,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION
                    )
                """)
                database.execSQL("INSERT INTO exercise_sets_v18 SELECT * FROM exercise_sets")
                database.execSQL("DROP TABLE exercise_sets")
                database.execSQL("ALTER TABLE exercise_sets_v18 RENAME TO exercise_sets")
                // AUDIT FIX (BUG-V4-008): restore indexes destroyed by the DROP above.
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_sessionId` ON `exercise_sets` (`sessionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE body_weights_new (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `unit` TEXT NOT NULL
                    )
                """)
                database.execSQL("INSERT INTO body_weights_new (`id`, `date`, `time`, `weight`, `unit`) SELECT `id`, `date`, `time`, `weight`, `unit` FROM body_weights")
                database.execSQL("DROP TABLE body_weights")
                database.execSQL("ALTER TABLE body_weights_new RENAME TO body_weights")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_plan_sessions_planId ON plan_sessions (planId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_plan_exercises_planSessionId ON plan_exercises (planSessionId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_records_exerciseId ON personal_records (exerciseId)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add weight_unit column to exercise_sets; default "kg"
                database.execSQL(
                    "ALTER TABLE exercise_sets ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'kg'"
                )
                // One-time normalization: rows already in kg need no change.
                // Rows stored in lbs: none exist pre-launch.
                // For post-launch users, a separate migration step would
                // read weight_unit and divide by 2.20462 — not needed here.
            }
        }

        // FIX (§9 item 5 / BUG-V4-018): add weight_unit to plan_exercises so
        // plan weights have an explicit unit; subsequent app launch will canonicalize
        // pre-existing imperial plan weights via DataStoreManager.
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE plan_exercises ADD COLUMN weight_unit TEXT NOT NULL DEFAULT 'kg'"
                )
            }
        }

        fun setTestDatabase(db: AppDatabase?) {
            INSTANCE = db
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_fit_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}