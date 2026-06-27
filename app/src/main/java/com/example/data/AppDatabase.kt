package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        WeeklyReport::class,
        BodyMeasurement::class,
        DetectedPatternEntity::class,
        Exercise::class,
        ExerciseMetadata::class
    ],
    version = 10,
    exportSchema = true
)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_fit_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
