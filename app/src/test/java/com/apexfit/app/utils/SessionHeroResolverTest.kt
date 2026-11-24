package com.apexfit.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionHeroResolverTest {
    @Test
    fun emptySessionIsRecovery() {
        assertEquals(SessionHeroCategory.RECOVERY, SessionHeroResolver.resolve(emptyList()))
    }

    @Test
    fun chestSessionResolvesUpperPush() {
        val exercises = listOf(
            SessionHeroExercise("Chest", listOf("Triceps", "Shoulders"), 4),
            SessionHeroExercise("Triceps", emptyList(), 3)
        )
        assertEquals(SessionHeroCategory.UPPER_PUSH, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun backSessionResolvesUpperPull() {
        val exercises = listOf(
            SessionHeroExercise("Back", listOf("Biceps"), 4),
            SessionHeroExercise("Biceps", emptyList(), 3)
        )
        assertEquals(SessionHeroCategory.UPPER_PULL, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun armsAndShouldersSessionResolvesAccessory() {
        val exercises = listOf(
            SessionHeroExercise("Biceps", emptyList(), 3),
            SessionHeroExercise("Triceps", emptyList(), 3),
            SessionHeroExercise("Shoulders", emptyList(), 4)
        )
        assertEquals(SessionHeroCategory.UPPER_ACCESSORY, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun quadDominantSessionResolvesLowerQuad() {
        val exercises = listOf(
            SessionHeroExercise("Quads", listOf("Glutes"), 5),
            SessionHeroExercise("Quads", emptyList(), 3)
        )
        assertEquals(SessionHeroCategory.LOWER_QUAD, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun posteriorSessionResolvesLowerPosterior() {
        val exercises = listOf(
            SessionHeroExercise("Hamstrings", listOf("Glutes"), 4),
            SessionHeroExercise("Glutes", emptyList(), 4)
        )
        assertEquals(SessionHeroCategory.LOWER_POSTERIOR, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun balancedUpperLowerSessionResolvesFullBody() {
        val exercises = listOf(
            SessionHeroExercise("Chest", listOf("Triceps"), 3),
            SessionHeroExercise("Back", listOf("Biceps"), 3),
            SessionHeroExercise("Quads", listOf("Glutes"), 3),
            SessionHeroExercise("Hamstrings", emptyList(), 3)
        )
        assertEquals(SessionHeroCategory.FULL_BODY, SessionHeroResolver.resolve(exercises))
    }

    @Test
    fun coreOnlySessionDoesNotForceWrongHero() {
        assertEquals(
            SessionHeroCategory.GENERIC_STRENGTH,
            SessionHeroResolver.resolve(listOf(SessionHeroExercise("Core", emptyList(), 4)))
        )
    }
}
