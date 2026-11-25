package com.apexfit.app.domain.importer

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FragmentedWorkoutPasteTest {
    private val names = listOf("Leg Press", "Hack Squat", "Incline dumbbell press",
        "Chest-supported incline dumbbell row", "Incline dumbbell curl", "Dead bug",
        "B-stance dumbbell Romanian deadlift", "Single-arm dumbbell row towards hip",
        "Seated dumbbell lateral raise", "Chest-supported rear delt fly",
        "Seated overhead dumbbell triceps extension")
    private val library = names.mapIndexed { index, name ->
        ExerciseLibraryEntry("exercise-$index", name, "Other", "Other")
    }
    private val parser = WorkoutTextParser(library) { 123L }
    private fun fragmented(values: List<String>) = values.joinToString("\n\n") { "|   |\n| - |\n\n$it" }

    private val workoutA = listOf("Leg press / Hack squat", "3", "8–10",
        "Incline dumbbell press", "3", "8–10", "Chest-supported incline dumbbell row", "3", "10–12",
        "Incline dumbbell curl", "3", "10–12", "Dead bug", "2", "6–10 per side")
    private val workoutB = listOf("B-stance dumbbell Romanian deadlift", "3", "8–10 per leg",
        "Single-arm dumbbell row towards hip", "3", "10–12",
        "Seated dumbbell lateral raise", "3", "12–15",
        "Chest-supported rear delt fly", "2", "12–15",
        "Seated overhead dumbbell triceps extension", "3", "10–12")
    private val schedule = listOf("Day", "Week 1", "Week 2",
        "Monday", "Workout A", "Workout B", "Tuesday", "Rest", "Rest",
        "Wednesday", "Workout B", "Workout A", "Thursday", "Rest", "Rest",
        "Friday", "Workout A", "Workout B", "Saturday", "Rest", "Rest", "Sunday", "Rest", "Rest")
    internal fun source(): String =
        fragmented(listOf("Exercise", "Sets", "Reps") + workoutA) +
        "\n\n## **Workout B**\n\n" +
        fragmented(listOf("Exercise", "Sets", "Reps") + workoutB) +
        "\n\n## **Weekly schedule**\n\n" + fragmented(schedule)

    @Test
    fun exactUserPasteRecoversTenExercisesAndBothWeeksWithoutGuessingAlternatives() {
        val text = source()
        val plan = requireNotNull(parser.parse(text).plan)
        assertEquals(text, plan.provenance.originalText)
        assertEquals(listOf("Workout A", "Workout B"), plan.days.map { it.label })
        assertEquals(listOf(5, 5), plan.days.map { it.exercises.size })
        val a = plan.days[0].exercises
        val b = plan.days[1].exercises
        assertEquals(listOf(3, 3, 3, 3, 2), a.map { it.sets })
        assertEquals(listOf(8, 8, 10, 10, 6), a.map { it.repsMin })
        assertEquals(listOf(10, 10, 12, 12, 10), a.map { it.repsMax })
        assertEquals(listOf(3, 3, 3, 2, 3), b.map { it.sets })
        assertEquals(listOf(8, 10, 12, 12, 10), b.map { it.repsMin })
        assertEquals(listOf(10, 12, 15, 15, 12), b.map { it.repsMax })
        assertNull(a.first().matchedEntryId)
        assertTrue(a.first().needsReview)
        assertTrue(a.first().candidates.map { it.entry.name }.containsAll(listOf("Leg Press", "Hack Squat")))
        assertTrue(a.last().notes.any { "per side" in it })
        assertTrue(b.first().notes.any { "per leg" in it })
        assertEquals(14, plan.schedule.size)
        assertEquals(8, plan.schedule.count { it.isRest })
        assertEquals(listOf("Workout A", "Workout B", "Workout A"),
            plan.schedule.filter { it.week == "Week 1" && !it.isRest }.map { it.workout })
        assertEquals(listOf("Workout B", "Workout A", "Workout B"),
            plan.schedule.filter { it.week == "Week 2" && !it.isRest }.map { it.workout })
        val draft = plan.toEditablePlan(library.associateBy { it.id })
        assertEquals(2, draft.cycleWeeks)
        assertTrue(draft.usesRotation)
        assertFalse(draft.validationErrors().any { "weekday" in it || "schedule" in it })
        val rotation = requireNotNull(draft.scheduledDays())
        assertEquals(listOf(1, 1, 1, 2, 2, 2), rotation.map { it.first })
        assertEquals(listOf("Workout A", "Workout B", "Workout A", "Workout B", "Workout A", "Workout B"), rotation.map { it.second.label })
        assertEquals(30, rotation.sumOf { it.second.exercises.size })
        assertEquals(listOf("Monday", "Wednesday", "Friday", "Monday", "Wednesday", "Friday"), rotation.map { it.second.dayOfWeek })
        val week1 = requireNotNull(draft.selectFixedWeek("Week 1"))
        val week2 = requireNotNull(draft.selectFixedWeek("Week 2"))
        assertEquals(listOf("Monday", "Wednesday", "Friday"), week1.days.map { it.dayOfWeek })
        assertEquals(listOf("Workout B", "Workout A", "Workout B"), week2.days.map { it.label })
        assertEquals(15, week1.days.sumOf { it.exercises.size })
        assertEquals(15, week1.days.flatMap { it.exercises }.map { it.key }.distinct().size)
        assertEquals(text, week1.originalText)
    }

    @Test
    fun missingCellResynchronizesRatherThanShiftingAllSubsequentRows() {
        val text = fragmented(listOf("Exercise", "Sets", "Reps", "Leg Press", "3",
            "Incline dumbbell press", "4", "8–10", "Dead bug", "2", "6–10 per side"))
        val plan = requireNotNull(parser.parse(text).plan)
        assertEquals(listOf("Incline dumbbell press", "Dead bug"), plan.days.single().exercises.map { it.rawName })
        assertEquals(listOf(4, 2), plan.days.single().exercises.map { it.sets })
        assertTrue(plan.warnings.any { "Unaligned table cells" in it.message })
        assertEquals(text, plan.provenance.originalText)
    }

    @Test
    fun randomizedClipboardWhitespaceAndColumnOrderRetainExplicitValues() {
        val rng = Random(20261124)
        repeat(150) {
            val headers = listOf("Exercise", "Sets", "Reps").shuffled(rng)
            val row = mapOf("Exercise" to "Dead bug", "Sets" to "2", "Reps" to "6–10 per side")
            val values = headers + headers.map { row.getValue(it) }
            val source = values.joinToString("\n".repeat(rng.nextInt(1, 5))) {
                "| " + " ".repeat(rng.nextInt(0, 5)) + "|\n| " + "-".repeat(rng.nextInt(1, 5)) +
                    " |" + "\n".repeat(rng.nextInt(1, 5)) + it
            }
            val ex = requireNotNull(parser.parse(source).plan).days.single().exercises.single()
            assertEquals(source, "Dead bug", ex.rawName)
            assertEquals(source, 2, ex.sets)
            assertEquals(source, 6, ex.repsMin)
            assertEquals(source, 10, ex.repsMax)
        }
    }
}
