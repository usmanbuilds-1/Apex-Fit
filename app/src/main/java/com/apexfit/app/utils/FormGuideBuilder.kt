package com.apexfit.app.utils

import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata

data class FormGuideContent(
    val setup: String,
    val execution: String,
    val commonMistake: String,
    val muscleNote: String
)

object FormGuideBuilder {
    fun buildFormGuide(exercise: Exercise, metadata: ExerciseMetadata?): FormGuideContent {
        val forceType = metadata?.forceType ?: "movement"
        
        val setup = "For this ${exercise.category.lowercase()} exercise utilizing a $forceType pattern, ensure a stable base and proper bracing before initiating the movement."
        
        val execution = "Drive the $forceType motion deliberately, focusing on contracting the ${exercise.primaryMuscle.lowercase()} throughout the entire range of motion."
        
        val mistakeBase = "Avoid using excessive momentum or compromising your posture. Control the eccentric (yielding) phase."
        val commonMistake = if (!metadata?.notes.isNullOrBlank()) {
            "$mistakeBase Note: ${metadata?.notes}"
        } else {
            mistakeBase
        }
        
        val secondary = if (exercise.secondaryMuscles.isNotEmpty()) {
            "Secondary engagers include: ${exercise.secondaryMuscles.joinToString(", ")}."
        } else {
            "This is a highly isolated movement."
        }
        
        val muscleNote = "Primary target: ${exercise.primaryMuscle}. $secondary"
        
        return FormGuideContent(
            setup = setup,
            execution = execution,
            commonMistake = commonMistake,
            muscleNote = muscleNote
        )
    }
}
