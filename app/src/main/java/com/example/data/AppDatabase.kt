package com.example.data

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
    version = 13,
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
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
