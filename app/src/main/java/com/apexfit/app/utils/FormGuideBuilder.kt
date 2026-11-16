package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata

data class FormGuideContent(
    val sourceNote: String?,
    val muscleNote: String,
    val instructions: List<String> = emptyList()
)

object FormGuideBuilder {
    fun buildFormGuide(
        exercise: Exercise,
        metadata: ExerciseMetadata?,
        sourceInstructions: List<String> = emptyList()
    ): FormGuideContent {
        val instructions = sourceInstructions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { parseNumberedInstructions(metadata?.notes) }

        val sourceNote = metadata?.notes?.trim()?.takeIf { instructions.isEmpty() && it.isNotEmpty() }
        val secondary = exercise.secondaryMuscles.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = " Secondary: ", postfix = ".").orEmpty()
        return FormGuideContent(
            sourceNote = sourceNote,
            muscleNote = "Primary: ${exercise.primaryMuscle}.$secondary",
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
