package com.apexfit.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingStateTest {
    private lateinit var preferences: DataStoreManager

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = DataStoreManager.getInstance(context)
        preferences.clearAllData()
    }

    @After
    fun tearDown() = runBlocking {
        preferences.clearAllData()
    }

    @Test
    fun newInstallRequiresOnboarding() = runBlocking {
        assertFalse(preferences.isOnboardedFlow.first())
    }

    @Test
    fun completedOnboardingIsRemembered() = runBlocking {
        preferences.saveOnboardingData("Athlete", "Maintain", 70.0, 70.0)
        assertTrue(preferences.isOnboardedFlow.first())
    }

    @Test
    fun resettingProfileRequiresOnboardingAgain() = runBlocking {
        preferences.saveOnboardingData("Athlete", "Maintain", 70.0, 70.0)
        preferences.clearAllData()
        assertFalse(preferences.isOnboardedFlow.first())
    }

    @Test
    fun fullProfilePersistsCanonicalMeasurementsAndPreferences() = runBlocking {
        val profile = OnboardingProfile(name = "Sam", goal = "Lose Fat", weightKg = 80.0,
            targetWeightKg = 75.0, heightCm = 180.0, units = "lb", experience = "Advanced",
            daysPerWeek = 5, proteinPreference = "Plant based", dietaryRestrictions = "Vegan",
            foodBudget = "Moderate", supplements = "None")
        preferences.saveOnboardingProfile(profile)
        assertEquals(80.0, preferences.currentWeightFlow.first(), 0.001)
        assertEquals(180.0, preferences.heightFlow.first(), 0.001)
        assertEquals(5, preferences.weeklyWorkoutsFlow.first())
        assertEquals("Advanced", preferences.experienceFlow.first())
        assertEquals("Vegan", preferences.nutritionPreferencesFlow.first().restrictions)
        assertTrue(preferences.weightsNormalizedFlow.first())
        assertTrue(preferences.isOnboardedFlow.first())
    }

    @Test
    fun invalidProfileDoesNotCompleteOnboarding() = runBlocking {
        try {
            preferences.saveOnboardingProfile(OnboardingProfile(weightKg = Double.NaN))
            org.junit.Assert.fail("Invalid measurements must be rejected")
        } catch (_: IllegalArgumentException) {
            assertFalse(preferences.isOnboardedFlow.first())
        }
    }
    @Test
    fun referenceSetupPersistsPersonalDetailsAndTwoDayFrequency() = runBlocking {
        preferences.saveTrainingSetup(OnboardingProfile(name = "  Sam  ", heightCm = 180.0, weightKg = 80.0,
            age = 28, sex = "female", units = "lb", goal = "Recomposition", experience = "Intermediate", daysPerWeek = 2))
        assertEquals("Recomposition", preferences.goalFlow.first())
        assertEquals("Intermediate", preferences.experienceFlow.first())
        assertEquals(2, preferences.weeklyWorkoutsFlow.first())
        assertTrue(preferences.isOnboardedFlow.first())
        assertTrue(preferences.profileOnlySetupFlow.first())
        assertTrue(preferences.hasBodyDetailsFlow.first())
        assertEquals("Sam", preferences.usernameFlow.first())
        assertEquals(180.0, preferences.heightFlow.first(), 0.001)
        assertEquals(80.0, preferences.currentWeightFlow.first(), 0.001)
        assertEquals(28, preferences.ageFlow.first())
        assertEquals("female", preferences.sexFlow.first())
        assertTrue(preferences.weightsNormalizedFlow.first())
    }

    @Test
    fun exploreDoesNotMarkBodyDetailsAsComplete() = runBlocking {
        preferences.saveTrainingSetup(null)
        assertTrue(preferences.isOnboardedFlow.first())
        assertFalse(preferences.hasBodyDetailsFlow.first())
    }

    @Test
    fun invalidSetupDoesNotSavePartialProfile() = runBlocking {
        for (invalid in listOf(OnboardingProfile(name = ""), OnboardingProfile(heightCm = Double.NaN),
            OnboardingProfile(weightKg = -1.0), OnboardingProfile(age = 0), OnboardingProfile(sex = ""))) {
            try {
                preferences.saveTrainingSetup(invalid)
                org.junit.Assert.fail("Invalid personal details must be rejected")
            } catch (_: IllegalArgumentException) {
                assertFalse(preferences.isOnboardedFlow.first())
                assertFalse(preferences.hasBodyDetailsFlow.first())
            }
        }
    }

}
