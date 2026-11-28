package com.apexfit.app.utils

import androidx.annotation.Keep
import com.apexfit.app.data.ActiveExercise
import com.apexfit.app.data.LastSetWithDate
import java.time.LocalDate
import java.util.Locale

/** Optional recommendation. Never applied until the user confirms the current setup. */
@Keep
data class ConfirmedProgressionProposal(
    val basis: RecordedLoadBasis, val setup: String, val priorKg: Double,
    val suggestedKg: Double, val sourceSessionId: String, val sourceDate: String,
    val sourceSetIds: List<Long>, val targetSets: Int
) {
    companion object {
        fun valid(p: ConfirmedProgressionProposal?): Boolean = runCatching {
            requireNotNull(p)
            p.basis.name.isNotBlank() && LoadConfirmations.validSetup(p.setup) &&
                p.priorKg.isFinite() && p.priorKg > 0 && p.suggestedKg.isFinite() &&
                p.suggestedKg >= p.priorKg && p.sourceSessionId.isNotBlank() &&
                LocalDate.parse(p.sourceDate) != null && p.targetSets > 0 &&
                p.sourceSetIds.size == p.targetSets && p.sourceSetIds.all { it > 0 } &&
                p.sourceSetIds.distinct().size == p.sourceSetIds.size
        }.getOrDefault(false)
    }
}

object ProgressionEvidence {
    fun kg(row: LastSetWithDate): Double? = when (row.weightUnit.trim().lowercase(Locale.ROOT)) {
        "kg" -> row.weight
        "lb", "lbs" -> row.weight / AppConstants.KG_TO_LBS
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }

    fun proposal(history: List<LastSetWithDate>, planExerciseId: Long, targetSets: Int,
                 repsMax: Int, incrementKg: Double, today: LocalDate): ConfirmedProgressionProposal? {
        if (planExerciseId <= 0 || targetSets <= 0 || repsMax <= 0 ||
            !incrementKg.isFinite() || incrementKg <= 0 || history.isEmpty()) return null
        val first = history.first()
        val date = runCatching { LocalDate.parse(first.date) }.getOrNull() ?: return null
        if (date > today || history.any { it.sessionId != first.sessionId || it.date != first.date ||
                it.exerciseId != first.exerciseId || it.advancedDetails != null ||
                it.sourcePlanExerciseId != planExerciseId || it.occurrenceIndex == null } ||
            history.map { it.occurrenceIndex }.distinct().size != 1) return null
        val prescribed = (1..targetSets).map { number ->
            history.singleOrNull { it.setNumber == number } ?: return null
        }
        val observations = prescribed.map { row ->
            if (!row.completed || row.isWarmup || row.id <= 0 || row.reps <= 0) return null
            val load = kg(row)?.takeIf { it > 0 } ?: return null
            val o = SetObservationCodec.match(row.setObservation, load, row.reps) ?: return null
            if (o.basis == null || o.rir == null || EffortSelection.rpe(o.rir) != row.rpe) return null
            o
        }
        val reference = observations.first()
        if (observations.any { it.basis != reference.basis ||
                LoadConfirmations.normalizedSetup(it.setup) != LoadConfirmations.normalizedSetup(reference.setup) ||
                kotlin.math.abs(it.kg - reference.kg) > 0.000001 }) return null
        val increase = prescribed.all { it.reps >= repsMax && it.rpe in 1..8 }
        val candidate = reference.kg + if (increase) incrementKg else 0.0
        return ConfirmedProgressionProposal(requireNotNull(reference.basis), reference.setup, reference.kg,
            candidate, first.sessionId, first.date, prescribed.map { it.id }, targetSets)
            .takeIf(ConfirmedProgressionProposal::valid)
    }

    fun apply(exercise: ActiveExercise, proposal: ConfirmedProgressionProposal): ActiveExercise? {
        if (exercise.progressionProposal != proposal || !ConfirmedProgressionProposal.valid(proposal) ||
            exercise.sets.size != proposal.targetSets || exercise.sets.any {
                it.completed || it.isWarmup || it.restTakenSeconds > 0 || it.advancedDetails != null || it.setObservation != null
            }) return null
        return exercise.copy(sets = exercise.sets.map { set ->
            set.copy(weight = proposal.suggestedKg.toEditableWeight(set.weightUnit))
        }.toMutableList(), progressionProposal = null,
            loadContext = "Confirmed same setup: ${proposal.basis.label} · ${proposal.setup}. Adjust load if needed.")
    }
}
