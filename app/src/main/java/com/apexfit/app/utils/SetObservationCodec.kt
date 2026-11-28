package com.apexfit.app.utils

/** Self-reported set facts, bound to actual mass and reps. Null legacy field is unknown. */
data class SetObservation(val kg: Double, val reps: Int, val rir: Int?,
    val basis: RecordedLoadBasis?, val setup: String = "", val freeWeightRepLimit: Boolean = false)

object SetObservationCodec {
    fun valid(o: SetObservation): Boolean =
        o.kg.isFinite() && o.kg >= 0 && o.reps > 0 && (o.rir == null || o.rir in 0..5) &&
            (if (o.basis == null) o.setup.isEmpty() else o.kg > 0 && LoadConfirmations.validSetup(o.setup)) &&
            (!o.freeWeightRepLimit || (o.basis == RecordedLoadBasis.TOTAL && o.rir == 0 && o.reps in 2..5))

    fun encode(o: SetObservation): String {
        require(valid(o))
        return listOf("v1", o.kg.toString(), o.reps.toString(), o.rir?.toString().orEmpty(),
            o.basis?.name.orEmpty(), o.setup.trim(), o.freeWeightRepLimit.toString()).joinToString("|")
    }

    fun match(raw: String?, kg: Double, reps: Int): SetObservation? = raw?.let {
        runCatching {
            val p = it.split('|')
            require(p.size == 7 && p[0] == "v1" && p[6] in setOf("true", "false"))
            SetObservation(p[1].toDouble(), p[2].toInt(), p[3].takeIf(String::isNotEmpty)?.toInt(),
                p[4].takeIf(String::isNotEmpty)?.let(RecordedLoadBasis::valueOf), p[5], p[6].toBoolean())
                .also { o -> require(valid(o) && o.kg == kg && o.reps == reps) }
        }.getOrNull()
    }

    fun confirmation(s: com.apexfit.app.data.ExerciseSet): SetLoadConfirmation? {
        if (s.id <= 0 || !TrainingEvidence.isWorkingSet(s) || s.advancedDetails != null) return null
        val kg = when(s.weightUnit.trim().lowercase(java.util.Locale.ROOT)) {
            "kg" -> s.weight
            "lb", "lbs" -> s.weight / AppConstants.KG_TO_LBS
            else -> return null
        }
        val observation = match(s.setObservation, kg, s.reps) ?: return null
        val basis = observation.basis ?: return null
        return SetLoadConfirmation(s.id, LoadConfirmations.fingerprint(s), basis, observation.setup,
            observation.freeWeightRepLimit && s.rpe == 10)
    }
}
