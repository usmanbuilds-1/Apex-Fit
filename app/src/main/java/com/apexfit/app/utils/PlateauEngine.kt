package com.apexfit.app.utils

import com.apexfit.app.data.DeloadResult
import com.apexfit.app.data.ExerciseSet
import com.apexfit.app.data.FatigueResult
import com.apexfit.app.data.PlateauResult

object PlateauEngine {

    // ── CONSTANTS ──────────────────────────────────────────────
    //
    // Engineering thresholds, not research-derived values.
    // The e1RM comparison approach is consistent with progressive
    // overload monitoring in resistance training literature.
    // Epley formula source: Epley B. (1985). Boyd Epley Workout.
    //
    private const val MIN_SESSIONS_FOR_DETECTION = 4
    private const val MAX_SESSIONS_TO_EXAMINE = 6
    private const val CONSECUTIVE_FAILS_EARLY = 2
    private const val CONSECUTIVE_FAILS_CONFIRMED = 3
    private const val RPE_WORKING_MIN = 7
    private const val RPE_WORKING_MAX = 9
    private const val MIN_WORKING_SETS = 2
    private const val E1RM_PROGRESS_MIN_PCT = 0.02
    private const val MAX_REPS_FOR_FORMULA = 15

    // ── STRENGTH PLATEAU DETECTION ─────────────────────────────
    //
    // Detects when one or more exercises have stalled in performance.
    //
    // Method:
    //   For each exercise with sufficient session history, compute
    //   the best estimated 1RM per session using the Epley formula:
    //     e1RM = weight × (1 + reps / 30)
    //   on working sets only (RPE 7–9, not warm-up, not RPE 10).
    //   Count consecutive sessions where e1RM did not improve by
    //   at least 2%. Two consecutive stalled sessions = "early".
    //   Three or more = "confirmed". Returns the most severe plateau
    //   found, or isPlateaued = false if none or insufficient data.
    //
    // Limitations (documented honestly):
    //   - Epley is accurate to ±5–8% for reps 1–10; less so above 10
    //   - Does not account for inter-session variability (sleep, stress)
    //   - Does not distinguish technical regression from strength loss
    //   - MIN_SESSIONS (4) and stall thresholds (2/3) are engineering
    //     choices without specific research support for these exact values
    //
    fun detectStrengthPlateau(sessions: List<TrainingSession>): PlateauResult {
        val completed = sessions.filter { it.completed }.sortedBy { it.date }
        if (completed.size < MIN_SESSIONS_FOR_DETECTION) return PlateauResult(isPlateaued = false)

        val exerciseHistory: Map<String, List<Pair<String, List<ExerciseSet>>>> = completed
            .flatMap { session ->
                session.exercises.map { ex -> Triple(ex.name, session.date, ex.sets) }
            }
            .groupBy { it.first }
            .mapValues { (_, entries) -> entries.map { Pair(it.second, it.third) } }

        var worstResult = PlateauResult(isPlateaued = false)
        var worstConsecutiveFails = 0

        for ((exerciseName, history) in exerciseHistory) {
            if (history.size < MIN_SESSIONS_FOR_DETECTION) continue

            val recent = history.takeLast(MAX_SESSIONS_TO_EXAMINE)

            val sessionE1RMs: List<Pair<String, Double>> = recent.mapNotNull { (date, sets) ->
                val workingSets = sets.filter { set ->
                    !set.isWarmup &&
                        set.completed &&
                        set.rpe in RPE_WORKING_MIN..RPE_WORKING_MAX &&
                        set.reps > 0 &&
                        set.weight > 0.0
                }
                if (workingSets.size < MIN_WORKING_SETS) return@mapNotNull null
                val bestE1RM = workingSets.maxOf { set -> epleyE1RM(set.weight, set.reps) }
                Pair(date, bestE1RM)
            }

            if (sessionE1RMs.size < MIN_SESSIONS_FOR_DETECTION) continue

            var consecutiveFails = 0
            var lastProgressDate: String? = null

            for (i in sessionE1RMs.indices.reversed()) {
                if (i == 0) break
                val current = sessionE1RMs[i].second
                val previous = sessionE1RMs[i - 1].second
                if (previous <= 0.0) break
                val pctChange = (current - previous) / previous
                if (pctChange > E1RM_PROGRESS_MIN_PCT) {
                    lastProgressDate = sessionE1RMs[i].first
                    break
                }
                consecutiveFails++
            }

            if (consecutiveFails < CONSECUTIVE_FAILS_EARLY) continue
            if (consecutiveFails <= worstConsecutiveFails) continue

            worstConsecutiveFails = consecutiveFails
            val severity = if (consecutiveFails >= CONSECUTIVE_FAILS_CONFIRMED) "confirmed" else "early"

            val daysStalled = lastProgressDate?.let { progressDate ->
                getDaysBetween(progressDate, sessionE1RMs.last().first).toInt()
            } ?: getDaysBetween(sessionE1RMs.first().first, sessionE1RMs.last().first).toInt()

            val interventions = buildInterventions(exerciseName, severity)

            worstResult = PlateauResult(
                isPlateaued = true,
                interventionRecommendation = interventions.first(),
                severity = severity,
                interventions = interventions,
                daysStalled = daysStalled
            )
        }

        return worstResult
    }

    // ── DELOAD PLANNER ─────────────────────────────────────────
    //
    // Signal-based deload recommendation. Unchanged from prior version.
    // Thresholds consistent with Foster et al. training load monitoring.
    //
    fun calcDeloadRecommendation(
        trainingLog: List<TrainingSession>,
        complianceScore: Int,
        precomputedFatigue: FatigueResult? = null
    ): DeloadResult {
        val fatigue = precomputedFatigue ?: FatigueEngine.calcFatigueToFitnessRatio(trainingLog)
        val recent6 = trainingLog.filter { it.completed }.sortedByDescending { it.date }.take(6)
        val avgFeel = recent6.map { it.sessionFeel }.average().takeIf { !it.isNaN() } ?: 3.0
        var signals = 0
        if ((fatigue.ratio ?: 0.0) >= 1.3) signals++
        if (avgFeel <= 2.5) signals++
        // complianceScore intentionally excluded: low compliance = low training load,
        // which is the opposite of needing a deload. Removed to avoid false positives.
        if ((fatigue.ratio ?: 0.0) >= 1.5) signals += 2
        return when {
            signals >= 3 || (fatigue.ratio ?: 0.0) >= 1.5 -> DeloadResult(
                "deload_now", "immediate", signals,
                listOf("Keep same exercises", "Reduce sets by half", "Drop weight 20%", "No sets to failure")
            )
            signals == 2 -> DeloadResult(
                "deload_soon", "this_week", signals,
                listOf("Reduce to 2-3 sets per exercise", "Stay well within RIR")
            )
            else -> DeloadResult("continue", "none", signals)
        }
    }

    // ── PRIVATE HELPERS ────────────────────────────────────────

    // Epley estimated 1RM formula.
    // Source: Epley B. (1985). Boyd Epley Workout. Lincoln, NE.
    // Accurate for reps 1–10. Reps are capped at MAX_REPS_FOR_FORMULA
    // to limit overestimation on high-rep sets. Cross-session comparisons
    // remain consistent as long as the same formula is applied throughout.
    private fun epleyE1RM(weight: Double, reps: Int): Double {
        if (reps <= 0) return weight
        val cappedReps = reps.coerceAtMost(MAX_REPS_FOR_FORMULA)
        return weight * (1.0 + cappedReps / 30.0)
    }

    private fun buildInterventions(exerciseName: String, severity: String): List<String> {
        return when (severity) {
            "confirmed" -> listOf(
                "$exerciseName: reduce load 10–15% for one session to reset",
                "Shift rep range: try lower reps at higher load for 2–3 weeks",
                "Consider a similar movement variation"
            )
            else -> listOf(
                "$exerciseName: retry at the same load — ensure rest intervals are 3+ minutes",
                "Confirm recovery is adequate before this session",
                "Check whether fatigue from other sessions is limiting this lift"
            )
        }
    }
}
