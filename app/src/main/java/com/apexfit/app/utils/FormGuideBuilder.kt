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
    private data class Cues(val setup: String, val execution: String, val mistake: String)

    // Movement-specific cues for exercises used in the onboarding plans.
    private val starterCues = mapOf(
        "flat-dumbbell-press" to Cues(
            "Lie on a flat bench with your feet planted. Hold the dumbbells above your chest.",
            "Lower the weights with control toward the sides of your chest, then press up until your arms are extended comfortably.",
            "Avoid bouncing at the bottom or letting the weights drift out of control."
        ),
        "chest-supported-dumbbell-row" to Cues(
            "Set an incline bench and rest your chest against it, feet stable on the floor. Let the dumbbells hang below your shoulders.",
            "Pull your elbows toward your hips, then lower the weights slowly until your arms extend comfortably.",
            "Avoid lifting your chest off the bench or jerking the weights upward."
        ),
        "dumbbell-lateral-raise" to Cues(
            "Stand tall with a light dumbbell at each side and a slight bend in your elbows.",
            "Raise your arms out and slightly forward to around shoulder height; lower under control.",
            "Avoid swinging your torso or forcing your arms above a comfortable range."
        ),
        "hammer-bicep-curl" to Cues(
            "Stand with dumbbells at your sides, palms facing inward and elbows close to your body.",
            "Bend your elbows to raise the weights, then lower slowly with your palms still facing inward.",
            "Avoid using your back to swing the weights."
        ),
        "leg-press-high-wide" to Cues(
            "Sit with your back and hips supported. Place your feet comfortably on the platform and release the safety handles.",
            "Lower the platform only as far as you can keep your hips supported, then push through your feet to extend your knees without locking them forcefully.",
            "Avoid allowing your lower back or hips to lift from the pad."
        ),
        "romanian-deadlift" to Cues(
            "Stand holding the weight close to your thighs with feet about hip-width apart and knees softly bent.",
            "Push your hips back while keeping the weight close to your legs. Lower until you feel a hamstring stretch you can control, then stand by driving your hips forward.",
            "Avoid rounding your back or lowering farther than you can control."
        ),
        "seated-leg-curl" to Cues(
            "Adjust the machine so its pivot lines up with your knee and the pad rests above your heels. Secure your thighs.",
            "Bend your knees to pull the pad down and back; return slowly through a comfortable range.",
            "Avoid lifting your hips or letting the stack drop between reps."
        ),
        "standing-calf-raise" to Cues(
            "Stand on the balls of your feet with support for balance. Keep your knees comfortably straight.",
            "Rise onto your toes, pause briefly, then lower your heels slowly through a comfortable range.",
            "Avoid bouncing or cutting the movement short."
        ),
        "standing-overhead-press" to Cues(
            "Stand with feet stable and hold the weight around shoulder height. Brace your trunk.",
            "Press overhead along a comfortable path, moving your head back slightly as needed. Lower under control.",
            "Avoid excessive lower-back arching or forcing a painful range."
        ),
        "lat-pulldown-neutral-grip" to Cues(
            "Secure your thighs under the pads and hold the neutral-grip handles with palms facing each other.",
            "Pull the handles toward your upper chest while driving your elbows down, then return slowly until your arms extend comfortably.",
            "Avoid leaning far back or pulling the handles behind your neck."
        ),
        "dual-rope-tricep-pushdown" to Cues(
            "Set the ropes above head height. Stand facing the cable with elbows near your sides.",
            "Extend your elbows to press the ropes down, then return slowly while keeping your upper arms steady.",
            "Avoid leaning your body into the weight or letting your elbows swing forward."
        )
    )

    fun buildFormGuide(exercise: Exercise, metadata: ExerciseMetadata?): FormGuideContent {
        val cues = starterCues[exercise.id]
        val muscles = buildString {
            append("Primary target: ${exercise.primaryMuscle}.")
            if (exercise.secondaryMuscles.isNotEmpty()) {
                append(" Also involved: ${exercise.secondaryMuscles.joinToString(", ")}.")
            }
        }
        return if (cues != null) {
            FormGuideContent(cues.setup, cues.execution, cues.mistake, muscles)
        } else {
            FormGuideContent(
                setup = "A movement-specific setup guide is not available for this exercise yet.",
                execution = "Use a light load and a controlled, comfortable range. Consult a qualified coach if you are unsure how to perform it.",
                commonMistake = "Avoid using momentum or continuing through pain.",
                muscleNote = muscles
            )
        }
    }
}
