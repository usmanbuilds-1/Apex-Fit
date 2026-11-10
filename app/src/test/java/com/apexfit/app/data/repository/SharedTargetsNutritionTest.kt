package com.apexfit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.apexfit.app.data.*
import com.apexfit.app.utils.MacroEngine
import com.apexfit.app.utils.TdeeEngine
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedTargetsNutritionTest {
    @Test
    fun sharedTargetUsesLoggedFoodAndWeightHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = DataStoreManager.getInstance(context)
            store.saveSettingsProfile("Test", "Maintain", "kg", "Gym", 175.0, 30, "male", 70.0, 70.0, false, 2000)
            store.saveWeeklyWorkouts(3)
            val weights = (0..9).map { WeightEntry(LocalDate.now().minusDays(it.toLong()).toString(), 70.0) }
            val meals = (0..9).map { NutritionEntry(LocalDate.now().minusDays(it.toLong()).toString(), 3000, 150, 300, 100) }
            weights.forEach { db.fitnessDao().insertWeightEntry(it) }
            meals.forEach { db.fitnessDao().insertNutritionEntry(it) }
            val tdee = TdeeEngine.calcAdaptiveTDEE(weights, meals, heightCm = 175.0, ageYears = 30, biologicalSex = "male", weeklyWorkouts = 3, fallbackWeightKg = 70.0)
            val base = TdeeEngine.suggestCaloricTarget(tdee.tdee!!, "Maintain", 70.0, 70.0, 175.0, 30, "male")
            val expected = MacroEngine.calcCycledTargets(base, 3, "Maintain", 70.0, 175.0, "male").restDayCalories
            val repository = FitnessRepositoryImpl(db, db.fitnessDao(), store)
            val actual = withTimeout(15_000) { repository.sharedTargetsFlow.first { it.calories == expected } }
            assertEquals(expected, actual.calories)
        } finally {
            db.close()
        }
    }
}
