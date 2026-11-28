package com.apexfit.app.utils

import com.apexfit.app.data.*
import java.time.LocalDate

data class ReportLoadGroup(val exerciseId: String, val setup: String, val basis: RecordedLoadBasis,
                           val sets: Int, val kgReps: Double)
data class ReportExercise(val key: String, val sets: List<ExerciseSet>, val workingSets: Int, val reps: Long)
data class WorkoutReport(val session: com.apexfit.app.data.TrainingSession, val exercises: List<ReportExercise>,
    val workingSets: Int, val workingReps: Long, val warmups: Int, val incomplete: Int, val invalid: Int,
    val durationMinutes: Int?, val loadGroups: List<ReportLoadGroup>, val unconfirmedWorkingSets: Int,
    val records: List<RepRecordEvent>, val legacyGrouping: Boolean)

object WorkoutReports {
    fun calculate(facts: SavedWorkoutFacts, history: List<AnalyticsWorkingSet>,
                  confirmations: Map<Long, SetLoadConfirmation>, today: LocalDate,
                  nowMs: Long = System.currentTimeMillis()): WorkoutReport {
        val session = facts.session
        val seen = mutableSetOf<Long>()
        val sets = facts.sets.filter { it.sessionId == session.id && (it.id <= 0 || seen.add(it.id)) }
            .sortedWith(compareBy<ExerciseSet> { it.occurrenceIndex ?: Int.MAX_VALUE }
                .thenBy { it.setNumber ?: Int.MAX_VALUE }.thenBy { it.id })
        fun valid(s: ExerciseSet) = TrainingEvidence.isWorkingSet(s) && (s.setCompletedAt ?: 0L) <= nowMs
        val working = sets.filter(::valid)
        val groups = sets.groupBy {
            it.occurrenceIndex?.let { index -> "occurrence:$index:${it.exerciseId}" } ?: "catalog:${it.exerciseId}"
        }.map { (key, rows) ->
            val w = rows.filter(::valid)
            ReportExercise(key, rows, w.size, w.sumOf(AdvancedSets::totalReps))
        }
        data class Key(val exerciseId: String, val basis: RecordedLoadBasis, val setup: String)
        val comparable = working.mapNotNull { s ->
            if (s.advancedDetails != null) return@mapNotNull null
            val c = confirmations[s.id]?.takeIf { it.fingerprint == LoadConfirmations.fingerprint(s) }
                ?: SetObservationCodec.confirmation(s) ?: return@mapNotNull null
            val kg = when (s.weightUnit.trim().lowercase(java.util.Locale.ROOT)) {
                "kg" -> s.weight
                "lb", "lbs" -> s.weight / AppConstants.KG_TO_LBS
                else -> return@mapNotNull null
            }
            val volume = kg * s.reps
            if (!volume.isFinite() || kg <= 0) return@mapNotNull null
            Key(s.exerciseId, c.basis, LoadConfirmations.normalizedSetup(c.setup)) to volume
        }
        val loads = comparable.groupBy({ it.first }, { it.second }).mapNotNull { (key, values) ->
            val sum = values.sum()
            if (!sum.isFinite()) null else ReportLoadGroup(key.exerciseId, key.setup, key.basis, values.size, sum)
        }
        val validIds = working.map { it.id }.toSet()
        val events = history.groupBy { it.set.exerciseId }.flatMap { (id, rows) ->
            RepRecords.calculate(StrengthAnalytics.calculate(rows, id, LocalDate.of(1, 1, 1), today, nowMs), confirmations)
                .events.filter { it.sessionId == session.id && it.setId in validIds }
        }
        return WorkoutReport(session, groups, working.size, working.sumOf(AdvancedSets::totalReps),
            sets.count { it.completed && it.isWarmup }, sets.count { !it.completed },
            sets.count { it.completed && !it.isWarmup && !valid(it) },
            session.durationMinutes.takeIf { it > 0 }, loads, working.size - loads.sumOf { it.sets },
            events, sets.any { it.occurrenceIndex == null })
    }
}
