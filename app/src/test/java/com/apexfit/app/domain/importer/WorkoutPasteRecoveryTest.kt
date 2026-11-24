package com.apexfit.app.domain.importer

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WorkoutPasteRecoveryTest {
    private val library = listOf(
        entry("bench", "Barbell Bench Press", "Chest", "Barbell"),
        entry("row", "Barbell Row", "Back", "Barbell"),
        entry("rdl", "Romanian Deadlift", "Hamstrings", "Barbell"),
        entry("squat", "Barbell Back Squat", "Quads", "Barbell"),
        entry("plank", "Plank", "Abs", "Bodyweight")
    )
    private val parser = WorkoutTextParser(library) { 123L }

    @Test
    fun markdownTableSupportsReorderedColumnsAndInterleavedDays() {
        val source = """
            | Reps | Day | Exercise | Rest (sec) | Sets | Weight (kg) | Notes |
            | --- | --- | --- | --- | --- | --- | --- |
            | 8-12 | Monday | Bench Press | 90 | 3 | 20 | slow eccentric |
            | 5 | Friday | Back Squat | 120 | 4 | 30 | |
            | 10 | Monday | Barbell Row | 60 | 2 | 15 | pause |
        """.trimIndent()
        val plan = requireNotNull(parser.parse(source).plan)
        assertEquals(source, plan.provenance.originalText)
        assertEquals(listOf("Monday", "Friday"), plan.days.map { it.dayOfWeek })
        assertEquals(listOf("bench", "row"), plan.days.first().exercises.map { it.matchedEntryId })
        val bench = plan.days.first().exercises.first()
        assertEquals(3, bench.sets)
        assertEquals(8, bench.repsMin)
        assertEquals(12, bench.repsMax)
        assertEquals(90, bench.restSeconds)
        assertEquals(20.0, bench.weight?.value ?: -1.0, 0.0)
        assertTrue(bench.notes.any { "slow eccentric" in it })
    }

    @Test
    fun spreadsheetAndCsvHandleExplicitHeadersWithoutGuessingUnlabeledNumbers() {
        val tsv = "Day\tExercise\tSets\tReps\tWeight (lb)\nTue\tBench Press\t3\t8\t50"
        val csv = "Exercise,Reps,Sets,Notes\nBench Press,8-10,3,\"pause, then press\""
        for (source in listOf(tsv, csv)) {
            val exercise = requireNotNull(parser.parse(source).plan).days.single().exercises.single()
            assertEquals("bench", exercise.matchedEntryId)
            assertEquals(3, exercise.sets)
            assertEquals(8, exercise.repsMin)
        }
        val tsvExercise = requireNotNull(parser.parse(tsv).plan).days.single().exercises.single()
        assertEquals(ImportWeightUnit.LB, tsvExercise.weight?.unit)
        val csvExercise = requireNotNull(parser.parse(csv).plan).days.single().exercises.single()
        assertTrue(csvExercise.notes.any { "pause, then press" in it })
        val ambiguous = requireNotNull(parser.parse("Bench Press 3 8").plan).days.single().exercises.single()
        assertNull(ambiguous.sets)
        assertNull(ambiguous.repsMin)
        assertTrue(ambiguous.needsReview)
    }

    @Test
    fun inlineHeadingAndExercisesAreRecovered() {
        val source = "Monday - Upper: Bench Press 3x8; Barbell Row 4x10, RDL 2x12"
        val day = requireNotNull(parser.parse(source).plan).days.single()
        assertEquals("Monday", day.dayOfWeek)
        assertEquals(listOf("bench", "row", "rdl"), day.exercises.map { it.matchedEntryId })
        assertEquals(listOf(3, 4, 2), day.exercises.map { it.sets })
    }

    @Test
    fun multilineFieldsAndReversedOrderAreRecovered() {
        val source = """
            Upper A
            Bench Press
            Rest: 90 sec
            Reps: 8-12
            Sets: 3
            Weight: 25 kg
            RIR 2
            4x10 Barbell Row
        """.trimIndent()
        val exercises = requireNotNull(parser.parse(source).plan).days.single().exercises
        assertEquals(2, exercises.size)
        val bench = exercises.first()
        assertEquals(3, bench.sets)
        assertEquals(8, bench.repsMin)
        assertEquals(12, bench.repsMax)
        assertEquals(90, bench.restSeconds)
        assertEquals(25.0, bench.weight?.value ?: -1.0, 0.0)
        assertEquals(2, bench.rir)
        assertEquals("row", exercises.last().matchedEntryId)
        assertEquals(4, exercises.last().sets)
    }

    @Test
    fun compatibilityDigitsAndMarkdownLinksDoNotLoseExercises() {
        val source = "Monday\n- [Bench Press](https://example.com/bench) ３×８–１２\nRDL"
        val plan = requireNotNull(parser.parse(source).plan)
        assertEquals(source, plan.provenance.originalText)
        assertEquals(listOf("bench", "rdl"), plan.days.single().exercises.map { it.matchedEntryId })
        assertEquals(3, plan.days.single().exercises.first().sets)
        assertEquals(12, plan.days.single().exercises.first().repsMax)
    }

    @Test
    fun allCapsAliasAtStartIsNotDiscardedAsTitle() {
        val exercise = requireNotNull(parser.parse("RDL\n3x8").plan).days.single().exercises.single()
        assertEquals("rdl", exercise.matchedEntryId)
        assertEquals(3, exercise.sets)
    }

    @Test
    fun timeAndPerSetSequencesMustNotBecomeInventedFixedRepTargets() {
        val timed = requireNotNull(parser.parse("Plank 3x30 sec").plan).days.single().exercises.single()
        assertEquals(3, timed.sets)
        assertNull(timed.repsMin)
        assertTrue(timed.needsReview)
        assertTrue(timed.notes.any { "timed target" in it })
        val sequence = requireNotNull(parser.parse("Bench Press 3x12,10,8").plan).days.single().exercises.single()
        assertEquals(3, sequence.sets)
        assertNull(sequence.repsMin)
        assertTrue(sequence.notes.any { "12,10,8" in it })
        val rest = WorkoutPrescriptionParser.parse("3x8 - 90 sec rest")
        assertEquals(8, rest.repsMin)
        assertEquals(90, rest.restSeconds)
    }

    @Test
    fun invalidNumbersAndConflictingTargetsDoNotBecomeValidSilently() {
        assertNull(WorkoutPrescriptionParser.parse("0x8").sets)
        assertNull(WorkoutPrescriptionParser.parse("3x12-8").repsMin)
        assertNull(WorkoutPrescriptionParser.parse("3x8 -5kg").weight)
        assertNull(WorkoutPrescriptionParser.parse("3x8 rest 90 min").restSeconds)
        val exercise = requireNotNull(parser.parse("Bench Press\n3x8\n4x12").plan).days.single().exercises.single()
        assertEquals(3, exercise.sets)
        assertTrue(exercise.notes.any { "4x12" in it })
    }

    @Test
    fun seededFormattingVariationsPreservePrescription() {
        val random = Random(20261124)
        repeat(300) {
            val sets = random.nextInt(1, 9)
            val min = random.nextInt(1, 25)
            val max = min + random.nextInt(1, 10)
            val space = listOf("", " ", "   ")[random.nextInt(3)]
            val times = listOf("x", "×", "✕", "✖")[random.nextInt(4)]
            val dash = listOf("-", "–", "—", "−")[random.nextInt(4)]
            val source = "Monday\n" + listOf("", "- ", "• ", "1. ")[random.nextInt(4)] +
                "Bench Press " + sets + space + times + space + min + dash + max
            val ex = requireNotNull(parser.parse(source).plan).days.single().exercises.single()
            assertEquals(source, sets, ex.sets)
            assertEquals(source, min, ex.repsMin)
            assertEquals(source, max, ex.repsMax)
            assertEquals(source, "bench", ex.matchedEntryId)
        }
    }

    @Test(timeout = 20000)
    fun seededMalformedInputsNeverThrowOrProduceOutOfRangeNumericTargets() {
        val random = Random(924)
        val fragments = listOf("Bench Press", "Row", "RDL", "Monday", "Day 3", "3x8", "0x0", "99x999",
            "12-2", "https://example.com", "90 sec", "RPE 900", "sets:", "reps:", "||", "###", "💪", "\t", "\n", ",", ";", " ")
        repeat(500) {
            val source = buildString {
                repeat(random.nextInt(1, 32)) { append(fragments.random(random)); append(fragments.random(random)) }
            }
            val result = parser.parse(source)
            assertEquals(result, parser.parse(source))
            result.plan?.days.orEmpty().flatMap { it.exercises }.forEach { exercise ->
                exercise.sets?.let { assertTrue(it in 1..99) }
                exercise.repsMin?.let { assertTrue(it in 1..999) }
                exercise.repsMax?.let { assertTrue(it in 1..999) }
                exercise.restSeconds?.let { assertTrue(it in 0..3600) }
                exercise.weight?.let { assertTrue(it.value.isFinite() && it.value >= 0) }
            }
        }
    }

    @Test(timeout = 10000)
    fun longMalformedLineIsBoundedAndOriginalPasteRetained() {
        val source = "Bench Press 3x8\n" + "(".repeat(50000)
        val plan = requireNotNull(parser.parse(source).plan)
        assertTrue(plan.warnings.any { it.code == ImportWarning.INPUT_TOO_LARGE })
        assertEquals(source, plan.provenance.originalText)
    }

    private fun entry(id: String, name: String, muscle: String, equipment: String) =
        ExerciseLibraryEntry(id, name, muscle, equipment, WorkoutExerciseAliases.forExercise(name))
}
