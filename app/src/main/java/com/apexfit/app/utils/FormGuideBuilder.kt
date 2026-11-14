package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata

data class FormGuideContent(
    val setup: String,
    val execution: String,
    val commonMistake: String,
    val muscleNote: String,
    val instructions: List<String> = emptyList()
)

object FormGuideBuilder {
    fun buildFormGuide(
        exercise: Exercise,
        metadata: ExerciseMetadata?,
        sourceInstructions: List<String> = emptyList()
    ): FormGuideContent {
        val forceType = metadata?.forceType ?: "movement"
        val instructions = sourceInstructions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { parseNumberedInstructions(metadata?.notes) }

        val setup =
            "For this ${exercise.category.lowercase()} exercise utilizing a $forceType pattern, ensure a stable base and proper bracing before initiating the movement."

        val execution =
            "Drive the $forceType motion deliberately, focusing on contracting the ${exercise.primaryMuscle.lowercase()} throughout the entire range of motion."

        val mistakeBase =
            "Avoid using excessive momentum or compromising your posture. Control the eccentric (yielding) phase."
        val commonMistake = if (instructions.isEmpty() && !metadata?.notes.isNullOrBlank()) {
            "$mistakeBase Note: ${metadata?.notes}"
        } else {
            mistakeBase
        }

        val secondary = if (exercise.secondaryMuscles.isNotEmpty()) {
            "Secondary engagers include: ${exercise.secondaryMuscles.joinToString(", ")}."
        } else {
            "This is a highly isolated movement."
        }

        return FormGuideContent(
            setup = setup,
            execution = execution,
            commonMistake = commonMistake,
            muscleNote = "Primary target: ${exercise.primaryMuscle}. $secondary",
            instructions = instructions
        )
    }

    internal fun parseNumberedInstructions(notes: String?): List<String> {
        val text = notes?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()

        val marker = Regex("""(?m)^\s*\d+[.)]\s+""")
        if (marker.findAll(text).count() < 2) return emptyList()

        return text
            .split(marker)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
