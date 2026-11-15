package com.apexfit.app.domain.importer

import org.junit.Assert.*
import org.junit.Test

/**
 * Hardening suite adapted from the uploaded importer package.
 *
 * These tests intentionally target the production Apex Fit importer rather than
 * copying the ZIP's reference Room/UI layer into the app.
 */
class WorkoutImporterRigorousTest {

    private val library = listOf(
        e("barbell-bench-press", "Barbell Bench Press", "Chest", "Barbell"),
        e("incline-barbell-bench-press", "Incline Barbell Bench Press", "Chest", "Barbell"),
        e("flat-dumbbell-press", "Flat Dumbbell Press", "Chest", "Dumbbell"),
        e("incline-dumbbell-press", "Incline Dumbbell Press", "Chest", "Dumbbell"),
        e("standing-overhead-press", "Standing Overhead Press", "Shoulders", "Barbell"),
        e("dumbbell-lateral-raise", "Dumbbell Lateral Raise", "Shoulders", "Dumbbell"),
        e("cable-lateral-raise", "Cable Lateral Raise", "Shoulders", "Cable"),
        e("front-raise", "Front Raise", "Shoulders", "Dumbbell"),
        e("face-pull", "Face Pull", "Shoulders", "Cable"),
        e("pullups", "Pullups", "Back", "Bodyweight"),
        e("barbell-row", "Barbell Row", "Back", "Barbell"),
        e("dumbbell-row", "Dumbbell Row", "Back", "Dumbbell"),
        e("seated-cable-row", "Seated Cable Row", "Back", "Cable"),
        e("cable-row", "Cable Row", "Back", "Cable"),
        e("cable-fly", "Cable Fly", "Chest", "Cable"),
        e("hammer-bicep-curl", "Hammer Bicep Curl", "Biceps", "Dumbbell"),
        e("barbell-back-squat", "Barbell Back Squat", "Quads", "Barbell"),
        e("romanian-deadlift", "Romanian Deadlift", "Hamstrings", "Barbell"),
        e("conventional-deadlift", "Conventional Deadlift", "Hamstrings", "Barbell"),
        e("leg-press", "Leg Press", "Quads", "Machine"),
        e("leg-extension", "Leg Extension", "Quads", "Machine"),
        e("leg-curl", "Leg Curl", "Hamstrings", "Machine"),
        e("standing-calf-raise", "Standing Calf Raise", "Calves", "Machine"),
        e("dual-rope-tricep-pushdown", "Dual Rope Tricep Pushdown", "Triceps", "Cable")
    )

    private val parser = WorkoutTextParser(library) { 42L }

    @Test
    fun normalizer_preservesOriginalAndNormalizesUnicodeMarkdown() {
        val source = """
            ### **Push Day**
            • Bench Press 3×8–12
            > RDL 3×8
        """.trimIndent()

        val result = WorkoutTextNormalizer.normalize(source)

        assertEquals(source, result.originalText)
        assertTrue(result.normalizedText.contains("Push Day"))
        assertTrue(result.normalizedText.contains("Bench Press 3x8-12"))
        assertTrue(result.normalizedText.contains("RDL 3x8"))
        assertFalse(result.normalizedText.contains("**"))
        assertFalse(result.normalizedText.contains("###"))
    }

    @Test
    fun normalizer_collapsesWhitespaceButPreservesMeaningfulLineBreaks() {
        val result = WorkoutTextNormalizer.normalize("  Monday   Push\n\n\n Bench   Press   3 x 8 ")
        assertEquals("Monday Push\n\nBench Press 3 x 8", result.normalizedText)
    }

    @Test
    fun normalizer_boundsOversizedInput() {
        val input = buildString {
            while (length < WorkoutTextNormalizer.MAX_INPUT_CHARS + 10_000) {
                appendLine("Bench Press 3x8")
            }
        }
        val result = WorkoutTextNormalizer.normalize(input)
        assertTrue(result.truncated)
        assertTrue(result.normalizedText.length <= WorkoutTextNormalizer.MAX_INPUT_CHARS)
    }

    @Test
    fun dayDetector_handlesWeekdaysAbbreviationsAndFocus() {
        assertEquals("Monday", WorkoutDayDetector.detect("Monday - Push")?.dayOfWeek)
        assertEquals("Tuesday", WorkoutDayDetector.detect("Tue Pull")?.dayOfWeek)
        assertEquals("Push", WorkoutDayDetector.detect("Monday - Push")?.focus)
        assertEquals("Wednesday", WorkoutDayDetector.detect("Wed: Back")?.dayOfWeek)
    }

    @Test
    fun dayDetector_handlesNumberedAndSplitHeadings() {
        assertEquals("Day 1", WorkoutDayDetector.detect("DAY 1 CHEST + BACK")?.label)
        assertEquals("Chest + Back", WorkoutDayDetector.detect("DAY 1 CHEST + BACK")?.focus)
        assertNotNull(WorkoutDayDetector.detect("Upper A:"))
        assertNotNull(WorkoutDayDetector.detect("Lower B"))
        assertNotNull(WorkoutDayDetector.detect("Full Body"))
    }

    @Test
    fun dayDetector_doesNotTreatExercisePrescriptionAsHeading() {
        assertNull(WorkoutDayDetector.detect("Monday Press 3x8"))
        assertNull(WorkoutDayDetector.detect("Day 1 Squat 3x5"))
    }

    @Test
    fun lineParser_handlesInlineAndMultilineShapes() {
        assertTrue(WorkoutExerciseLineParser.parse("Bench Press 3x8") is ImportLineShape.FullExercise)
        assertTrue(WorkoutExerciseLineParser.parse("Bench Press: 3 sets of 8-12") is ImportLineShape.FullExercise)
        assertTrue(WorkoutExerciseLineParser.parse("Incline Dumbbell Press") is ImportLineShape.NameOnly)
        assertTrue(WorkoutExerciseLineParser.parse("3 x 8-10") is ImportLineShape.PrescriptionOnly)
        assertTrue(WorkoutExerciseLineParser.parse("Rest 2 min") is ImportLineShape.RestOnly)
    }

    @Test
    fun lineParser_handlesSupersetMarkersAndA1A2Prefixes() {
        assertTrue(WorkoutExerciseLineParser.parse("Superset:") is ImportLineShape.GroupMarker)
        val a1 = WorkoutExerciseLineParser.parse("A1 Bench Press 3x8") as ImportLineShape.FullExercise
        val a2 = WorkoutExerciseLineParser.parse("A2 Barbell Row 3x8") as ImportLineShape.FullExercise
        assertEquals("A", a1.groupTag)
        assertEquals("A", a2.groupTag)
    }

    @Test
    fun prescriptionParser_parsesCoreSetRepFormats() {
        assertPrescription("3x8", 3, 8, 8, ImportRepTarget.FIXED)
        assertPrescription("3 x 8", 3, 8, 8, ImportRepTarget.FIXED)
        assertPrescription("3x8-12", 3, 8, 12, ImportRepTarget.RANGE)
        assertPrescription("3 sets of 8-12 reps", 3, 8, 12, ImportRepTarget.RANGE)
        assertPrescription("3 sets x 8 reps", 3, 8, 8, ImportRepTarget.FIXED)
    }

    @Test
    fun prescriptionParser_parsesAmrapAndFailureWithoutInventingReps() {
        val amrap = WorkoutPrescriptionParser.parse("3xAMRAP")
        assertEquals(3, amrap.sets)
        assertEquals(ImportRepTarget.AMRAP, amrap.repTarget)
        assertNull(amrap.repsMin)
        assertNull(amrap.repsMax)

        val failure = WorkoutPrescriptionParser.parse("3 sets to failure")
        assertEquals(3, failure.sets)
        assertEquals(ImportRepTarget.FAILURE, failure.repTarget)
        assertNull(failure.repsMin)
    }

    @Test
    fun prescriptionParser_keepsRestSeparateFromRepRange() {
        val parsed = WorkoutPrescriptionParser.parse("3 x 8 - 90 sec rest")
        assertEquals(3, parsed.sets)
        assertEquals(8, parsed.repsMin)
        assertEquals(8, parsed.repsMax)
        assertEquals(90, parsed.restSeconds)
    }

    @Test
    fun prescriptionParser_parsesRestVariants() {
        assertEquals(60, WorkoutPrescriptionParser.parse("3x8 Rest 60 sec").restSeconds)
        assertEquals(60, WorkoutPrescriptionParser.parse("3x8 60s rest").restSeconds)
        assertEquals(120, WorkoutPrescriptionParser.parse("3x8 Rest: 2 min").restSeconds)
        assertEquals(120, WorkoutPrescriptionParser.parse("3x8 2 minutes rest").restSeconds)
        val ranged = WorkoutPrescriptionParser.parse("3x8 Rest 2-3 min")
        assertEquals(120, ranged.restSeconds)
        assertNotNull(ranged.restNote)
    }

    @Test
    fun prescriptionParser_parsesRpeRirAndWeights() {
        val parsed = WorkoutPrescriptionParser.parse("3x8 @ RPE 8.5 RIR 2 70kg Rest 90 sec")
        assertEquals(8.5, parsed.rpe ?: 0.0, 0.0)
        assertEquals(2, parsed.rir)
        assertEquals(70.0, parsed.weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.KG, parsed.weight?.unit)
        assertEquals(90, parsed.restSeconds)

        val lb = WorkoutPrescriptionParser.parse("3x8 135 lbs")
        assertEquals(135.0, lb.weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.LB, lb.weight?.unit)
    }

    @Test
    fun prescriptionParser_rejectsOutOfRangeIntensity() {
        assertNull(WorkoutPrescriptionParser.parse("3x8 RPE 11").rpe)
        assertNull(WorkoutPrescriptionParser.parse("3x8 RIR 20").rir)
    }

    @Test
    fun matcher_resolvesKnownAliases() {
        val matcher = WorkoutExerciseMatcher(library)
        assertEquals("Standing Overhead Press", matcher.match("OHP").entry?.name)
        assertEquals("Romanian Deadlift", matcher.match("RDL").entry?.name)
        assertEquals("Flat Dumbbell Press", matcher.match("DB Bench").entry?.name)
        assertEquals("Dumbbell Lateral Raise", matcher.match("Lat Raises").entry?.name)
        assertEquals("Barbell Back Squat", matcher.match("Back Squat").entry?.name)
        assertEquals("Dual Rope Tricep Pushdown", matcher.match("Tricep Pushdown").entry?.name)
    }

    @Test
    fun matcher_preventsWrongMovementAndDirectionMatches() {
        val matcher = WorkoutExerciseMatcher(library)
        assertNotEquals("Barbell Bench Press", matcher.match("Leg Press").entry?.name)
        assertNotEquals("Cable Row", matcher.match("Cable Fly").entry?.name)
        assertNotEquals("Front Raise", matcher.match("Lateral Raise").entry?.name)
        assertNotEquals("Leg Curl", matcher.match("Hammer Curl").entry?.name)
    }

    @Test
    fun matcher_doesNotSilentlyResolveGenericOrUnknownNames() {
        val matcher = WorkoutExerciseMatcher(library)
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Row").confidence)
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Curls").confidence)
        assertEquals(MatchConfidence.UNRESOLVED, matcher.match("Mystery Cable Thing").confidence)
    }

    @Test
    fun fullExample_pplParsesWithCorrectDaysAndOrder() {
        val plan = requireNotNull(parser.parse(
            """
            PPL Routine

            Monday - Push
            Bench Press 3x8
            Incline DB Press 3 x 10-12
            Lateral Raises 4x15
            Tricep Pushdown 3x12

            Tuesday Pull
            Pullups 3xAMRAP
            Barbell Row 4 x 8
            Face Pull 3x15
            Hammer curls 3 x 10-12

            Wednesday Legs
            Back Squat 3x5
            Romanian Deadlift 3x8
            Leg Press 3x10
            Calf Raises 4x15
            """.trimIndent()
        ).plan)

        assertEquals("PPL Routine", plan.name)
        assertEquals(3, plan.days.size)
        assertEquals(listOf("Barbell Bench Press", "Incline Dumbbell Press", "Dumbbell Lateral Raise", "Dual Rope Tricep Pushdown"),
            plan.days[0].exercises.map { it.matchedName })
        assertEquals(ImportRepTarget.AMRAP, plan.days[1].exercises.first().repTarget)
        assertEquals(10, plan.days[1].exercises.last().repsMin)
        assertEquals(12, plan.days[1].exercises.last().repsMax)
    }

    @Test
    fun fullExample_upperLowerParsesSetsOfAndIntensity() {
        val plan = requireNotNull(parser.parse(
            """
            Upper A:
            Bench press - 4 sets of 6-8
            OHP - 3x8
            Lat Raises - 3x10
            Hammer Curl - 2x12 @ RPE 8

            Lower A:
            Back squat 3 x 5
            RDL 3 x 8
            Leg curl 3 x 12
            Calves 4 x 12-15
            """.trimIndent()
        ).plan)

        assertEquals(2, plan.days.size)
        val upper = plan.days[0].exercises
        assertEquals(4, upper[0].sets)
        assertEquals(6, upper[0].repsMin)
        assertEquals(8, upper[0].repsMax)
        assertEquals("Standing Overhead Press", upper[1].matchedName)
        assertEquals(8.0, upper[3].rpe ?: 0.0, 0.0)
    }

    @Test
    fun fullExample_multilineAndSupersetPreserved() {
        val plan = requireNotNull(parser.parse(
            """
            DAY 1 CHEST + BACK

            Incline Dumbbell Press
            3 sets x 8-10
            Rest 2 mins

            Pullups
            3 sets to failure

            Superset:
            Lateral Raise 3x15
            Face Pull 3x15
            """.trimIndent()
        ).plan)

        assertEquals(1, plan.days.size)
        assertEquals("Chest + Back", plan.days.single().focus)
        val exercises = plan.days.single().exercises
        assertEquals(120, exercises[0].restSeconds)
        assertEquals(ImportRepTarget.FAILURE, exercises[1].repTarget)
        assertNotNull(exercises[2].supersetTag)
        assertEquals(exercises[2].supersetTag, exercises[3].supersetTag)
    }

    @Test
    fun fullExample_weightsArePreservedInSourceUnits() {
        val plan = requireNotNull(parser.parse(
            """
            Monday
            Back Squat 5x5 @ 100kg
            Bench Press 5x5 70 kg
            Barbell Row 5x5 135lb
            """.trimIndent()
        ).plan)
        val ex = plan.days.single().exercises
        assertEquals(100.0, ex[0].weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.KG, ex[0].weight?.unit)
        assertEquals(135.0, ex[2].weight?.value ?: 0.0, 0.0)
        assertEquals(ImportWeightUnit.LB, ex[2].weight?.unit)
    }

    @Test
    fun markdownRedditPlanParsesWithoutTurningMarkupIntoExercises() {
        val plan = requireNotNull(parser.parse(
            """
            **Reddit Upper Lower**
            ### Monday - Upper
            - Bench Press 3×8
            - Barbell Row 3×8
            ### Friday - Lower
            1. Back Squat 3×5
            2. RDL 3×8
            """.trimIndent()
        ).plan)

        assertEquals("Reddit Upper Lower", plan.name)
        assertEquals(2, plan.days.size)
        assertEquals(2, plan.days[0].exercises.size)
        assertEquals(2, plan.days[1].exercises.size)
    }

    @Test
    fun noDayHeadingCreatesWorkoutOne() {
        val result = parser.parse("Quick Routine\nBench Press 3x8\nRDL 3x10")
        val plan = requireNotNull(result.plan)
        assertEquals(1, plan.days.size)
        assertEquals("Workout 1", plan.days.single().label)
        assertTrue(plan.warnings.any { it.code == ImportWarning.NO_DAYS_FOUND })
    }

    @Test
    fun commentaryAndUrlsDoNotBecomeExercises() {
        val plan = requireNotNull(parser.parse(
            """
            Reddit PPL
            Here is what worked for me
            https://reddit.com/r/fitness
            Monday Push
            Bench Press 3x8
            Control the eccentric
            """.trimIndent()
        ).plan)

        assertEquals(1, plan.days.size)
        assertEquals(1, plan.days.single().exercises.size)
        assertTrue(plan.days.single().exercises.single().notes.any { it.contains("Control the eccentric") })
    }

    @Test
    fun unknownExerciseStaysUnresolved() {
        val exercise = requireNotNull(parser.parse("Monday\nMystery Cable Thing 3x12").plan)
            .days.single().exercises.single()
        assertNull(exercise.matchedEntryId)
        assertEquals(MatchConfidence.UNRESOLVED, exercise.matchConfidence)
        assertTrue(exercise.needsReview)
    }

    @Test
    fun blankAndNoiseOnlyInputsFailSafely() {
        assertEquals(ImportWarning.EMPTY_INPUT, parser.parse("   ").fatalError?.code)
        assertEquals(
            ImportWarning.NO_EXERCISES_FOUND,
            parser.parse("https://reddit.com/r/fitness\nEdit: thanks everyone").fatalError?.code
        )
    }

    @Test
    fun provenanceIsCapturedAndBounded() {
        val source = "Monday\nBench Press 3x8"
        val plan = requireNotNull(parser.parse(source).plan)
        assertEquals(source, plan.provenance.originalText)
        assertEquals(42L, plan.provenance.importedAtEpochMs)
        assertTrue(plan.provenance.normalizedText.contains("Bench Press 3x8"))
    }

    @Test
    fun duplicateExercisesArePreservedRatherThanCollapsed() {
        val plan = requireNotNull(parser.parse(
            "Monday\nBench Press 3x8\nBench Press 2x10"
        ).plan)
        assertEquals(2, plan.days.single().exercises.size)
        assertEquals(3, plan.days.single().exercises[0].sets)
        assertEquals(2, plan.days.single().exercises[1].sets)
    }

    @Test
    fun malformedPrescriptionDoesNotInventValues() {
        val exercise = requireNotNull(parser.parse("Monday\nBench Press someday maybe 3 bananas").plan)
            .days.single().exercises.single()
        assertNull(exercise.sets)
        assertNull(exercise.repsMin)
        assertTrue(exercise.needsReview)
    }

    @Test
    fun caseAndWhitespaceDifferencesDoNotChangeExerciseResolution() {
        val a = requireNotNull(parser.parse("Monday\nBench Press 3x8").plan)
            .days.single().exercises.single()
        val b = requireNotNull(parser.parse("MONDAY\n   bench   press    3 x 8").plan)
            .days.single().exercises.single()
        assertEquals(a.matchedEntryId, b.matchedEntryId)
        assertEquals(a.sets, b.sets)
        assertEquals(a.repsMin, b.repsMin)
    }

    @Test
    fun explicitPlanNameWinsOverInferredTitle() {
        val plan = requireNotNull(parser.parse(
            "Routine: Real Name\nReddit PPL\nMonday\nBench Press 3x8"
        ).plan)
        assertEquals("Real Name", plan.name)
    }

    @Test
    fun parserDoesNotNeedNetworkOrHtmlExecutionPrimitives() {
        val result = parser.parse(
            "Monday\n<script>alert('x')</script>\nBench Press 3x8\nhttps://evil.example"
        )
        val plan = requireNotNull(result.plan)
        assertEquals(1, plan.days.single().exercises.size)
        assertEquals("Barbell Bench Press", plan.days.single().exercises.single().matchedName)
    }

    private fun assertPrescription(
        text: String,
        sets: Int,
        min: Int,
        max: Int,
        target: ImportRepTarget
    ) {
        val parsed = WorkoutPrescriptionParser.parse(text)
        assertEquals(sets, parsed.sets)
        assertEquals(min, parsed.repsMin)
        assertEquals(max, parsed.repsMax)
        assertEquals(target, parsed.repTarget)
    }

    private fun e(id: String, name: String, muscle: String, equipment: String) =
        ExerciseLibraryEntry(
            id = id,
            name = name,
            primaryMuscle = muscle,
            equipment = equipment,
            aliases = WorkoutExerciseAliases.forExercise(name)
        )
}
