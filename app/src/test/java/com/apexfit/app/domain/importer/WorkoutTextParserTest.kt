package com.apexfit.app.domain.importer

import org.junit.Assert.*
import org.junit.Test

class WorkoutTextParserTest {
    private val library = listOf(
        entry("barbell-bench-press", "Barbell Bench Press", "Chest", "Barbell"),
        entry("flat-dumbbell-press", "Flat Dumbbell Press", "Chest", "Dumbbell"),
        entry("dumbbell-lateral-raise", "Dumbbell Lateral Raise", "Shoulders", "Dumbbell"),
        entry("cable-lateral-raise", "Cable Lateral Raise", "Shoulders", "Cable"),
        entry("front-raise", "Front Raise", "Shoulders", "Dumbbell"),
        entry("dual-rope-tricep-pushdown", "Dual Rope Tricep Pushdown", "Triceps", "Cable"),
        entry("pullups", "Pullups", "Back", "Bodyweight"),
        entry("barbell-row", "Barbell Row", "Back", "Barbell"),
        entry("dumbbell-row", "Dumbbell Row", "Back", "Dumbbell"),
        entry("seated-cable-row", "Seated Cable Row", "Back", "Cable"),
        entry("face-pull", "Face Pull", "Shoulders", "Cable"),
        entry("hammer-bicep-curl", "Hammer Bicep Curl", "Biceps", "Dumbbell"),
        entry("barbell-back-squat", "Barbell Back Squat", "Quads", "Barbell"),
        entry("romanian-deadlift", "Romanian Deadlift", "Hamstrings", "Barbell"),
        entry("leg-press", "Leg Press", "Quads", "Machine"),
        entry("leg-curl", "Leg Curl", "Hamstrings", "Machine"),
        entry("standing-calf-raise", "Standing Calf Raise", "Calves", "Machine"),
        entry("cable-fly", "Cable Fly", "Chest", "Cable"),
        entry("cable-row", "Cable Row", "Back", "Cable"),
        entry("machine-chest-press", "Machine Chest Press", "Chest", "Machine")
    )
    private val parser = WorkoutTextParser(library) { 1234L }

    @Test
    fun redditPpl_parsesEndToEnd() {
        val result = parser.parse(
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
        )

        assertTrue(result.isSuccess)
        val plan = requireNotNull(result.plan)
        assertEquals("Reddit PPL", plan.name)
        assertEquals(3, plan.days.size)
        assertEquals("Monday", plan.days[0].dayOfWeek)
        assertEquals("Barbell Bench Press", plan.days[0].exercises[0].matchedName)
        assertEquals(3, plan.days[0].exercises[1].sets)
        assertEquals(10, plan.days[0].exercises[1].repsMin)
        assertEquals(12, plan.days[0].exercises[1].repsMax)
        assertEquals(ImportRepTarget.AMRAP, plan.days[1].exercises.first().repTarget)
        assertTrue(plan.days[1].exercises.first().needsReview)
    }

    @Test
    fun markdownTitle_isNotTurnedIntoExercise() {
        val plan = requireNotNull(parser.parse(
            """
            **My 4 Day Workout**
            ### Upper A
            - Bench Press 4×6–8
            - Cable Fly 3 x 12
            ### Lower A
            1. Back Squat 3x5
            2. RDL 3x8
            """.trimIndent()
        ).plan)

        assertEquals("My 4 Day Workout", plan.name)
        assertEquals(2, plan.days.size)
        assertEquals(2, plan.days.first().exercises.size)
    }

    @Test
    fun multilinePrescriptionAndRest_areJoined() {
        val exercise = requireNotNull(parser.parse(
            """
            Monday
            Barbell Bench Press
            3 sets of 8-10 reps
            Rest 2 min
            """.trimIndent()
        ).plan).days.first().exercises.first()

        assertEquals(3, exercise.sets)
        assertEquals(8, exercise.repsMin)
        assertEquals(10, exercise.repsMax)
        assertEquals(120, exercise.restSeconds)
    }

    @Test
    fun trailingRest_isNotParsedAsRepRange() {
        val exercise = requireNotNull(parser.parse(
            """
            Monday
            Barbell Bench Press 3 x 8 - 90 sec rest
            """.trimIndent()
        ).plan).days.first().exercises.first()

        assertEquals(8, exercise.repsMin)
        assertEquals(8, exercise.repsMax)
        assertEquals(90, exercise.restSeconds)
    }

    @Test
    fun rpeRirAndMetricWeight_arePreserved() {
        val exercise = requireNotNull(parser.parse(
            """
            Monday
            RDL 3x8 @ RPE 8 RIR 2 70kg
            """.trimIndent()
        ).plan).days.first().exercises.first()

        assertEquals(8.0, exercise.rpe ?: 0.0, 0.0)
        assertEquals(2, exercise.rir)
        assertEquals(70.0, exercise.weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.KG, exercise.weight?.unit)
    }

    @Test
    fun imperialWeight_isNotSilentlyConverted() {
        val exercise = requireNotNull(parser.parse(
            """
            Monday
            Bench Press 3x8 135lb
            """.trimIndent()
        ).plan).days.first().exercises.first()

        assertEquals(135.0, exercise.weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.LB, exercise.weight?.unit)
    }

    @Test
    fun amrapAndFailure_requireNumericReview() {
        val amrap = requireNotNull(parser.parse("Monday\nPullups 3xAMRAP").plan).days.first().exercises.first()
        val failure = requireNotNull(parser.parse("Monday\nPushups 3 sets to failure").plan).days.first().exercises.first()

        assertEquals(ImportRepTarget.AMRAP, amrap.repTarget)
        assertNull(amrap.repsMin)
        assertTrue(amrap.needsReview)
        assertEquals(ImportRepTarget.FAILURE, failure.repTarget)
        assertNull(failure.repsMin)
        assertTrue(failure.needsReview)
    }

    @Test
    fun noDayHeadings_createWorkoutOneWithWarning() {
        val plan = requireNotNull(parser.parse("Quick routine\nBench Press 3x8\nRDL 3x10").plan)
        assertEquals(1, plan.days.size)
        assertEquals("Workout 1", plan.days.first().label)
        assertTrue(plan.warnings.any { it.code == ImportWarning.NO_DAYS_FOUND })
    }

    @Test
    fun supersets_arePreservedAsNotesWithoutLeakingPastBlankLine() {
        val plan = requireNotNull(parser.parse(
            """
            Monday
            Superset:
            Lateral Raise 3x15
            Face Pull 3x15

            Bench Press 3x8
            """.trimIndent()
        ).plan)
        val exercises = plan.days.first().exercises
        assertNotNull(exercises[0].supersetTag)
        assertEquals(exercises[0].supersetTag, exercises[1].supersetTag)
        assertNull(exercises[2].supersetTag)
    }

    @Test
    fun ambiguousGenericNames_areNotAutoAccepted() {
        val matcher = WorkoutExerciseMatcher(library)
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Row").confidence)
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Curls").confidence)
    }

    @Test
    fun movementGuardrails_preventBadPressRowAndRaiseMatches() {
        val matcher = WorkoutExerciseMatcher(library)
        assertNotEquals("Barbell Bench Press", matcher.match("Leg Press").entry?.name)
        assertEquals("Cable Fly", matcher.match("Cable Fly").entry?.name)
        assertNotEquals("Cable Row", matcher.match("Cable Fly").entry?.name)
        assertEquals("Dumbbell Lateral Raise", matcher.match("Lateral Raise").entry?.name)
        assertNotEquals("Front Raise", matcher.match("Lateral Raise").entry?.name)
        assertTrue(matcher.match("Cable Chest Press").confidence in setOf(MatchConfidence.MEDIUM, MatchConfidence.UNRESOLVED))
    }

    @Test
    fun mediumConfidence_alwaysRequiresReview() {
        val match = WorkoutExerciseMatcher(library).match("Cable Chest Press")
        if (match.entry != null) {
            val parsed = ParsedImportedExercise(
                rawName = "Cable Chest Press",
                rawPrescription = "3x10",
                matchedEntryId = match.entry.id,
                matchedName = match.entry.name,
                matchConfidence = MatchConfidence.MEDIUM,
                matchFlagged = false,
                candidates = match.candidates,
                sets = 3,
                repsMin = 10,
                repsMax = 10,
                repTarget = ImportRepTarget.FIXED,
                repNote = null,
                weight = null,
                restSeconds = null,
                rpe = null,
                rir = null,
                supersetTag = null,
                notes = emptyList(),
                warnings = emptyList(),
                lineIndex = 1
            )
            assertTrue(parsed.needsReview)
        }
    }

    @Test
    fun emptyOrNoiseOnlyInput_failsSafely() {
        assertEquals(ImportWarning.EMPTY_INPUT, parser.parse("   ").fatalError?.code)
        assertEquals(
            ImportWarning.NO_EXERCISES_FOUND,
            parser.parse("https://reddit.com/r/fitness\nEdit: thanks everyone").fatalError?.code
        )
    }

    @Test
    fun inputIsBounded() {
        val huge = buildString {
            appendLine("Huge Workout")
            while (length < WorkoutTextNormalizer.MAX_INPUT_CHARS + 20_000) {
                appendLine("Monday")
                appendLine("Bench Press 3x8")
            }
        }
        val result = parser.parse(huge)
        assertNotNull(result.plan)
        assertTrue(requireNotNull(result.plan).warnings.any { it.code == ImportWarning.INPUT_TOO_LARGE })
        assertTrue(
            requireNotNull(result.plan).provenance.originalText.length <= WorkoutTextNormalizer.MAX_INPUT_CHARS
        )
    }

    @Test
    fun editableDraft_blocksUnreviewedAndMissingScheduleData() {
        val plan = requireNotNull(parser.parse("Push\nBench Press 3x8").plan)
        val draft = plan.toEditablePlan(library.associateBy { it.id })
        assertTrue(draft.validationErrors().any { it.contains("weekday", ignoreCase = true) })
    }

    private fun entry(id: String, name: String, muscle: String, equipment: String) =
        ExerciseLibraryEntry(id, name, muscle, equipment, WorkoutExerciseAliases.forExercise(name))
}
