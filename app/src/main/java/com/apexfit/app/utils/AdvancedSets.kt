package com.apexfit.app.utils

import com.apexfit.app.data.ExerciseSet

enum class AdvancedSetKind { DROP, REST_PAUSE, WORKING }
enum class SetOutcome { FAILURE, NOT_FAILURE, UNKNOWN }
data class SetContinuation(val kg: Double, val reps: Int, val restSeconds: Int, val outcome: SetOutcome)
data class AdvancedSetDetails(val kind: AdvancedSetKind, val outcome: SetOutcome, val continuations: List<SetContinuation>)

/** One persisted parent bout. Continuations are not independent strength or volume-count observations. */
object AdvancedSets {
    fun valid(parentKg: Double, details: AdvancedSetDetails): Boolean {
        if (!parentKg.isFinite() || parentKg < 0 || details.continuations.size > 10) return false
        if (details.kind == AdvancedSetKind.WORKING) return details.continuations.isEmpty()
        if (details.continuations.isEmpty()) return false
        var prior = parentKg
        for (segment in details.continuations) {
            if (!segment.kg.isFinite() || segment.kg < 0 || segment.reps !in 1..1000 ||
                segment.restSeconds !in 0..3600) return false
            if (details.kind == AdvancedSetKind.DROP && segment.kg >= prior) return false
            if (details.kind == AdvancedSetKind.REST_PAUSE &&
                (kotlin.math.abs(segment.kg - parentKg) > 0.000001 || segment.restSeconds == 0)) return false
            prior = segment.kg
        }
        return true
    }

    // Delimited versioned representation; no user text, locale-dependent formatting or Gson null defaults.
    fun encode(parentKg: Double, details: AdvancedSetDetails): String {
        require(valid(parentKg, details))
        return listOf("v1", details.kind.name, details.outcome.name,
            details.continuations.joinToString(";") { "${it.kg},${it.reps},${it.restSeconds},${it.outcome.name}" }).joinToString("|")
    }

    fun decode(parentKg: Double, raw: String?): AdvancedSetDetails? = raw?.let {
        runCatching {
            val p = it.split('|')
            require(p.size == 4 && p[0] == "v1" && it.length <= 4096)
            val segments = if (p[3].isEmpty()) emptyList() else p[3].split(';').map { row ->
                val fields = row.split(',')
                require(fields.size == 4)
                SetContinuation(fields[0].toDouble(), fields[1].toInt(), fields[2].toInt(), SetOutcome.valueOf(fields[3]))
            }
            AdvancedSetDetails(AdvancedSetKind.valueOf(p[1]), SetOutcome.valueOf(p[2]), segments)
                .also { details -> require(valid(parentKg, details)) }
        }.getOrNull()
    }

    fun details(set: ExerciseSet): AdvancedSetDetails? = decode(set.weight, set.advancedDetails)
    fun totalReps(set: ExerciseSet): Long = set.reps.toLong() + (details(set)?.continuations?.sumOf { it.reps.toLong() } ?: 0L)
    fun isExtended(set: ExerciseSet): Boolean = set.advancedDetails != null &&
        details(set)?.kind != AdvancedSetKind.WORKING
}
