package com.apexfit.app.utils

import java.time.LocalDate
import java.util.Locale
import java.security.MessageDigest
import kotlin.math.roundToLong
import com.apexfit.app.data.ExerciseSet

enum class RecordedLoadBasis(val label: String) {
    TOTAL("Total external load"), PER_HAND("Load per hand"), MACHINE_STACK("Machine stack setting")
}

data class SetLoadConfirmation(val setId: Long, val fingerprint: String, val basis: RecordedLoadBasis, val setup: String)

object LoadConfirmations {
    fun fingerprint(s: ExerciseSet): String {
        val fields = listOf(s.id.toString(), s.sessionId, s.exerciseId, s.weight.toBits().toString(),
            s.reps.toString(), s.weightUnit, s.setCompletedAt.toString(), s.completed.toString(), s.isWarmup.toString())
        val payload = fields.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    fun validSetup(setup: String): Boolean = setup.trim().length in 1..48 &&
        setup.none { it == '|' || it.isISOControl() }

    fun encode(c: SetLoadConfirmation): String = "${c.setId}|${c.fingerprint}|${c.basis.name}|${c.setup.trim()}"
    fun decode(raw: String): SetLoadConfirmation? {
        val p = raw.split('|', limit = 4)
        if (p.size != 4 || !p[1].matches(Regex("[0-9a-f]{64}")) || !validSetup(p[3])) return null
        val id = p[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val basis = runCatching { RecordedLoadBasis.valueOf(p[2]) }.getOrNull() ?: return null
        return SetLoadConfirmation(id, p[1], basis, p[3].trim())
    }

    fun decodeAll(raw: Set<String>): Map<Long, SetLoadConfirmation> = raw.mapNotNull(::decode)
        .groupBy { it.setId }.mapNotNull { (id, values) -> values.singleOrNull()?.let { id to it } }.toMap()

    fun match(observation: StrengthObservation, confirmations: Map<Long, SetLoadConfirmation>): SetLoadConfirmation? =
        confirmations[observation.setId]?.takeIf { it.fingerprint == observation.fingerprint }

    fun normalizedSetup(setup: String): String = setup.trim().lowercase(Locale.ROOT)
}

data class RepRecordKey(val basis: RecordedLoadBasis, val setup: String, val reps: Int)
data class RepRecordEvent(
    val key: RepRecordKey, val sessionId: String, val date: LocalDate,
    val setId: Long, val loadGrams: Long, val chronologyLimited: Boolean
) {
    val loadKg: Double get() = loadGrams / 1000.0
}

data class RepRecordProjection(val events: List<RepRecordEvent>) {
    val best: List<RepRecordEvent> get() = events.groupBy { it.key }.values.map { it.last() }
        .sortedWith(compareBy({ it.key.basis.name }, { it.key.setup }, { it.key.reps }))
}

object RepRecords {
    /** Replay only current saved facts. Legacy/unconfirmed setups are never promoted. */
    fun calculate(points: List<StrengthSessionPoint>, confirmations: Map<Long, SetLoadConfirmation>): RepRecordProjection {
        data class Candidate(val key: RepRecordKey, val sessionId: String, val date: LocalDate,
                             val setId: Long, val grams: Long, val time: Long?)
        val candidates = points.flatMap { point ->
            point.observations.mapNotNull { observation ->
                val c = LoadConfirmations.match(observation, confirmations) ?: return@mapNotNull null
                val kg = observation.loadKg?.takeIf { it.isFinite() && it > 0.0 && it <= Long.MAX_VALUE / 1000.0 }
                    ?: return@mapNotNull null
                if (observation.setId <= 0 || observation.reps <= 0) return@mapNotNull null
                val grams = (kg * 1000.0).roundToLong().takeIf { it > 0 } ?: return@mapNotNull null
                Candidate(RepRecordKey(c.basis, LoadConfirmations.normalizedSetup(c.setup), observation.reps),
                    point.sessionId, point.date, observation.setId, grams, observation.completedAt?.takeIf { it > 0 })
            }
        }
        val events = mutableListOf<RepRecordEvent>()
        for ((key, series) in candidates.groupBy { it.key }) {
            var best = 0L
            for ((date, day) in series.groupBy { it.date }.toSortedMap()) {
                // A legacy same-day set order is unknowable. Only the best on that day is provable.
                val limited = day.any { it.time == null } || day.map { it.time }.distinct().size < day.size
                val ordered = if (limited) listOf(day.maxWithOrNull(compareBy<Candidate> { it.grams }
                    .thenByDescending { it.setId })!!) else day.sortedBy { it.time }
                for (candidate in ordered) {
                    if (candidate.grams <= best) continue
                    best = candidate.grams
                    events.add(RepRecordEvent(key, candidate.sessionId, date, candidate.setId, best, limited))
                }
            }
        }
        return RepRecordProjection(events)
    }
}
