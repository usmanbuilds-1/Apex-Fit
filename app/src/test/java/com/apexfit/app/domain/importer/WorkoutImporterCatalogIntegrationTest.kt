package com.apexfit.app.domain.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutImporterCatalogIntegrationTest {

    private fun productionLibrary(): List<ExerciseLibraryEntry> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = JSONArray(
            context.assets.open("seed_exercises.json").bufferedReader().use { it.readText() }
        )
        return buildList(json.length()) {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                val name = item.getString("name")
                add(
                    ExerciseLibraryEntry(
                        id = item.getString("id"),
                        name = name,
                        primaryMuscle = item.getString("primary_muscle"),
                        equipment = item.getString("equipment_required"),
                        aliases = WorkoutExerciseAliases.forExercise(name)
                    )
                )
            }
        }
    }

    @Test
    fun productionCatalogContainsExpected901UniqueExercises() {
        val library = productionLibrary()
        assertEquals(901, library.size)
        assertEquals(901, library.map { it.id.lowercase() }.distinct().size)
        assertEquals(901, library.map { it.name.lowercase() }.distinct().size)
    }

    @Test
    fun commonRedditAliasesResolveAgainstTheRealCatalog() {
        val matcher = WorkoutExerciseMatcher(productionLibrary())

        assertEquals("barbell-bench-press", matcher.match("Bench Press").entry?.id)
        assertEquals("dumbbell-lateral-raise", matcher.match("Lateral Raises").entry?.id)
        assertEquals("barbell-back-squat", matcher.match("Back Squat").entry?.id)
        assertEquals("romanian-deadlift", matcher.match("RDL").entry?.id)
        assertEquals("hammer-curls", matcher.match("Hammer Curl").entry?.id)
        assertEquals("pullups", matcher.match("Pullups").entry?.id)
        assertEquals("triceps-pushdown", matcher.match("Triceps Pushdown").entry?.id)
        assertEquals("leg-curl", matcher.match("Hamstring Curl").entry?.id)
        assertEquals("leg-extensions", matcher.match("Leg Extensions").entry?.id)
    }

    @Test
    fun realCatalogGuardrailsRejectDangerousCrossMovementMatches() {
        val matcher = WorkoutExerciseMatcher(productionLibrary())

        assertNotEquals("barbell-bench-press", matcher.match("Leg Press").entry?.id)
        assertNotEquals("cable-row", matcher.match("Cable Fly").entry?.id)
        assertNotEquals("front-raise", matcher.match("Lateral Raise").entry?.id)
        assertNotEquals("leg-curl", matcher.match("Hammer Curl").entry?.id)
        assertNotEquals("leg-press", matcher.match("Cable Chest Press").entry?.id)
    }

    @Test
    fun genericNamesStayReviewableWithTheFullCatalog() {
        val matcher = WorkoutExerciseMatcher(productionLibrary())

        assertTrue(matcher.match("Row").confidence in setOf(MatchConfidence.MEDIUM, MatchConfidence.UNRESOLVED))
        assertTrue(matcher.match("Curls").confidence in setOf(MatchConfidence.MEDIUM, MatchConfidence.UNRESOLVED))
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Mystery Cable Thing").confidence)
    }

    @Test
    fun acceptancePlanParsesAgainstAll901ExercisesWithoutCreatingFakeNames() {
        val library = productionLibrary()
        val parser = WorkoutTextParser(library) { 99L }
        val plan = requireNotNull(
            parser.parse(
                """
                Reddit PPL

                Monday Push
                Bench Press 3x8
                Incline DB Press 3x10-12
                Lateral Raise 4x15
                Tricep Pushdown 3x12

                Wednesday Pull
                Pullups 3xAMRAP
                Barbell Row 4x8
                Face Pull 3x15
                Hammer Curl 3x10

                Friday Legs
                Back Squat 3x5
                Romanian Deadlift 3x8
                Leg Press 3x10
                Leg Curl 3x12
                Calf Raise 4x15
                """.trimIndent()
            ).plan
        )

        assertEquals(3, plan.days.size)
        assertEquals(13, plan.days.sumOf { it.exercises.size })

        val resolvedIds = plan.days.flatMap { it.exercises }.mapNotNull { it.matchedEntryId }
        assertTrue("Most normal Reddit exercises should resolve against the production catalog", resolvedIds.size >= 11)
        assertTrue(resolvedIds.all { id -> library.any { it.id == id } })

        val bench = plan.days[0].exercises[0]
        assertEquals("barbell-bench-press", bench.matchedEntryId)
        assertEquals(3, bench.sets)
        assertEquals(8, bench.repsMin)

        val pullups = plan.days[1].exercises[0]
        assertEquals(ImportRepTarget.AMRAP, pullups.repTarget)
        assertNull(pullups.repsMin)
        assertTrue(pullups.needsReview)

        val rdl = plan.days[2].exercises[1]
        assertEquals("romanian-deadlift", rdl.matchedEntryId)
    }

    @Test
    fun everyAutoSelectedMatchReferencesARealProductionExercise() {
        val library = productionLibrary()
        val parser = WorkoutTextParser(library)
        val source = """
            Monday
            Bench Press 3x8
            Incline Dumbbell Press 3x10
            Cable Fly 3x12
            OHP 3x8
            Lateral Raise 3x15
            Pullups 3xAMRAP
            Barbell Row 3x8
            Face Pull 3x15
            Hammer Curl 3x12
            Back Squat 3x5
            RDL 3x8
            Leg Press 3x10
            Leg Extension 3x12
            Hamstring Curl 3x12
            Calf Raise 4x15
        """.trimIndent()

        val plan = requireNotNull(parser.parse(source).plan)
        val knownIds = library.mapTo(hashSetOf()) { it.id }
        plan.days.flatMap { it.exercises }.forEach { exercise ->
            exercise.matchedEntryId?.let { id ->
                assertTrue("Importer returned an ID absent from seed_exercises.json: $id", id in knownIds)
            }
        }
    }
    @Test
    fun fragmentedUserWorkoutResolvesOnlyRealCatalogIdsAndRetainsAllRows() {
        val library = productionLibrary()
        val known = library.map { it.id }.toSet()
        val source = FragmentedWorkoutPasteTest().source()
        val plan = requireNotNull(WorkoutTextParser(library).parse(source).plan)
        assertEquals(listOf("Workout A", "Workout B"), plan.days.map { it.label })
        assertEquals(10, plan.days.sumOf { it.exercises.size })
        assertEquals(14, plan.schedule.size)
        val exercises = plan.days.flatMap { it.exercises }
        assertTrue(exercises.all { it.sets != null && it.repsMin != null && it.repsMax != null })
        assertNull(exercises.first().matchedEntryId)
        assertTrue(exercises.first().needsReview)
        assertTrue(exercises.all { it.matchedEntryId == null || it.matchedEntryId in known })
        assertTrue(exercises.flatMap { it.candidates }.all { it.entry.id in known })
        assertEquals(source, plan.provenance.originalText)
    }
}
