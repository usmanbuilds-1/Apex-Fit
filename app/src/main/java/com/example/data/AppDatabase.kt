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
        DetectedPatternEntity::class
    ],
    version = 8,
    exportSchema = false
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_fit_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
