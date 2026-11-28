package com.apexfit.app.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** User-entered direct working-set target, effective from Monday. Null explicitly clears a target. */
data class MuscleTargetRevision(val muscle: String, val effectiveWeek: LocalDate, val target: Int?)

object MuscleTargetHistory {
    // Input/storage bound only, not a safe or optimal training prescription.
    const val MAX_TARGET = 999
    val muscles: Set<String> get() =
        (MuscleGroups.ALL.map(MuscleAliases::getCanonical) + "hip_flexors")
            .filterNot { it == "cardio" || it == "full_body" }.toSet()

    fun week(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun nextWeek(today: LocalDate): LocalDate = week(today).plusWeeks(1)

    fun resolve(revisions: List<MuscleTargetRevision>, muscle: String, date: LocalDate): MuscleTargetRevision? =
        revisions.filter { it.muscle == MuscleAliases.getCanonical(muscle) && it.effectiveWeek <= week(date) }
            .maxByOrNull { it.effectiveWeek }

    /** Only the next week's pending revision is editable. Past targets are never changed. */
    fun schedule(
        revisions: List<MuscleTargetRevision>, muscle: String, target: Int?, today: LocalDate
    ): List<MuscleTargetRevision> {
        val key = MuscleAliases.getCanonical(muscle)
        require(key in muscles)
        require(target == null || target in 0..MAX_TARGET)
        val effective = nextWeek(today)
        return (revisions.filterNot { it.muscle == key && it.effectiveWeek == effective } +
            MuscleTargetRevision(key, effective, target)).sortedWith(compareBy({ it.effectiveWeek }, { it.muscle }))
    }

    fun encode(revisions: List<MuscleTargetRevision>): Set<String> =
        revisions.map { "${it.muscle}|${it.effectiveWeek}|${it.target ?: "unset"}" }.toSet()

    fun decode(values: Set<String>): List<MuscleTargetRevision> =
        values.mapNotNull { value ->
            val p = value.split('|')
            if (p.size != 3 || p[0] !in muscles) return@mapNotNull null
            val date = runCatching { LocalDate.parse(p[1]) }.getOrNull() ?: return@mapNotNull null
            if (date.dayOfWeek != DayOfWeek.MONDAY) return@mapNotNull null
            val target = if (p[2] == "unset") null else
                p[2].toIntOrNull()?.takeIf { it in 0..MAX_TARGET } ?: return@mapNotNull null
            MuscleTargetRevision(p[0], date, target)
        }.groupBy { it.muscle to it.effectiveWeek }
            .values.mapNotNull { it.singleOrNull() }
            .sortedWith(compareBy({ it.effectiveWeek }, { it.muscle }))
}
