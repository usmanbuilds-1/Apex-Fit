package com.apexfit.app.utils

import com.apexfit.app.data.TrainingSession
import com.apexfit.app.data.WeightEntry
import java.time.LocalDate
import java.util.UUID
import java.util.Locale

enum class FitnessGoalKind(val label: String) {
    LOAD("Load at minimum reps"), REPS("Reps at minimum load"), SESSION_VOLUME("Session volume"),
    TRAINING_DAYS("Completed training days"), MINUTES("Logged completed workout minutes"),
    WEIGHT_UP("Recorded bodyweight at least"), WEIGHT_DOWN("Recorded bodyweight at most")
}

data class FitnessGoalSpec(
    val kind: FitnessGoalKind, val target: Double, val exerciseId: String = "",
    val basis: RecordedLoadBasis? = null, val setup: String = "", val minimum: Double = 0.0,
    val deadline: LocalDate? = null
) {
    val exerciseGoal get() = kind in setOf(FitnessGoalKind.LOAD, FitnessGoalKind.REPS, FitnessGoalKind.SESSION_VOLUME)
    fun valid(): Boolean {
        if (!target.isFinite() || target <= 0 || target > 1_000_000 || !minimum.isFinite()) return false
        if (exerciseGoal && (exerciseId.isBlank() || basis == null || !LoadConfirmations.validSetup(setup))) return false
        return when (kind) {
            FitnessGoalKind.LOAD -> minimum in 1.0..1000.0 && minimum % 1 == 0.0
            FitnessGoalKind.REPS -> minimum > 0 && target <= 1000 && target % 1 == 0.0
            FitnessGoalKind.SESSION_VOLUME -> minimum == 0.0
            FitnessGoalKind.TRAINING_DAYS, FitnessGoalKind.MINUTES -> target % 1 == 0.0 && minimum == 0.0
            FitnessGoalKind.WEIGHT_UP, FitnessGoalKind.WEIGHT_DOWN -> target in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG && minimum == 0.0
        }
    }
}

data class FitnessGoal(
    val id: String, val spec: FitnessGoalSpec, val start: LocalDate, val baseline: Double?,
    val baselineDate: LocalDate?, val archivedOn: LocalDate? = null
)
data class GoalObservation(val value: Double, val date: LocalDate, val source: String)
data class FitnessGoalProgress(val goal: FitnessGoal, val current: GoalObservation?, val achievement: GoalObservation?, val ended: Boolean)

object CustomFitnessGoals {
    fun observations(spec: FitnessGoalSpec, points: List<StrengthSessionPoint>,
                     confirmations: Map<Long, SetLoadConfirmation>, sessions: List<TrainingSession>,
                     weights: List<WeightEntry>, start: LocalDate, end: LocalDate): List<GoalObservation> {
        if (end < start) return emptyList()
        if (spec.exerciseGoal) return points.filter { it.date in start..end }.flatMap { p ->
            val eligible = p.observations.filter { o ->
                val c = LoadConfirmations.match(o, confirmations)
                o.setId > 0 && (o.loadKg ?: 0.0) > 0 && c?.basis == spec.basis &&
                    c != null && LoadConfirmations.normalizedSetup(c.setup) == LoadConfirmations.normalizedSetup(spec.setup)
            }
            when (spec.kind) {
                FitnessGoalKind.LOAD -> eligible.filter { it.reps >= spec.minimum }.map {
                    GoalObservation(it.loadKg!!, p.date, "set:${it.setId}")
                }
                FitnessGoalKind.REPS -> eligible.filter { it.loadKg!! >= spec.minimum }.map {
                    GoalObservation(it.reps.toDouble(), p.date, "set:${it.setId}")
                }
                FitnessGoalKind.SESSION_VOLUME -> if (eligible.isEmpty()) emptyList() else
                    listOf(GoalObservation(eligible.sumOf { it.loadKg!! * it.reps }, p.date, "session:${p.sessionId}"))
                else -> emptyList()
            }
        }.filter { it.value.isFinite() }.sortedBy { it.date }
        if (spec.kind == FitnessGoalKind.WEIGHT_UP || spec.kind == FitnessGoalKind.WEIGHT_DOWN) {
            return weights.mapNotNull { w ->
                val date = runCatching { LocalDate.parse(w.date) }.getOrNull() ?: return@mapNotNull null
                val kg = when (w.unit.trim().lowercase(Locale.ROOT)) {
                    "kg" -> w.weight
                    "lb", "lbs" -> w.weight / AppConstants.KG_TO_LBS
                    else -> return@mapNotNull null
                }
                if (date !in start..end || !kg.isFinite() || kg !in AppConstants.MIN_WEIGHT_KG..AppConstants.MAX_WEIGHT_KG) null
                else date to kg
            }.groupBy({ it.first }, { it.second }).map { (date, kg) ->
                GoalObservation(kg.average(), date, "daily mean")
            }.sortedBy { it.date }
        }
        val byDate = sessions.filter { s ->
            val date = runCatching { LocalDate.parse(s.date) }.getOrNull()
            s.completed && date != null && date in start..end &&
                (s.completedAt ?: s.startedAt ?: 0L) <= System.currentTimeMillis()
        }.distinctBy { it.id }.groupBy { LocalDate.parse(it.date) }.toSortedMap()
        var total = 0.0
        return byDate.map { (date, rows) ->
            total += if (spec.kind == FitnessGoalKind.TRAINING_DAYS) 1.0
                else rows.sumOf { it.durationMinutes.coerceAtLeast(0).toLong() }.toDouble()
            GoalObservation(total, date, "completed workouts")
        }
    }

    fun create(spec: FitnessGoalSpec, today: LocalDate, baselineObservations: List<GoalObservation>): FitnessGoal {
        require(spec.valid() && (spec.deadline == null || spec.deadline >= today.plusDays(1)))
        val start = today.plusDays(1)
        val baseline = when (spec.kind) {
            FitnessGoalKind.TRAINING_DAYS, FitnessGoalKind.MINUTES -> GoalObservation(0.0, today, "new tracking period")
            FitnessGoalKind.WEIGHT_UP, FitnessGoalKind.WEIGHT_DOWN -> baselineObservations.filter { it.date < today && it.date >= today.minusDays(7) }.maxByOrNull { it.date }
            else -> baselineObservations.filter { it.date < today }.maxByOrNull { it.value }
        }
        return FitnessGoal(UUID.randomUUID().toString(), spec, start, baseline?.value, baseline?.date)
    }

    fun progress(goal: FitnessGoal, today: LocalDate, observations: List<GoalObservation>): FitnessGoalProgress {
        val end = listOfNotNull(today, goal.spec.deadline, goal.archivedOn).minOrNull()!!
        val eligible = observations.filter { it.date in goal.start..end && it.value.isFinite() }.sortedBy { it.date }
        fun met(o: GoalObservation) = if (goal.spec.kind == FitnessGoalKind.WEIGHT_DOWN) o.value <= goal.spec.target else o.value >= goal.spec.target
        val current = when (goal.spec.kind) {
            FitnessGoalKind.WEIGHT_UP, FitnessGoalKind.WEIGHT_DOWN, FitnessGoalKind.TRAINING_DAYS, FitnessGoalKind.MINUTES -> eligible.lastOrNull()
            else -> eligible.maxByOrNull { it.value }
        }
        return FitnessGoalProgress(goal, current, eligible.firstOrNull(::met),
            goal.archivedOn != null || (goal.spec.deadline?.let { today > it } == true))
    }

    private fun enc(s: String) = s.toByteArray(Charsets.UTF_8).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
    private fun dec(s: String): String {
        require(s.length % 2 == 0)
        return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
    }
    fun encode(g: FitnessGoal): String = with(g) {
        listOf(id, spec.kind.name, spec.target, enc(spec.exerciseId), spec.basis?.name.orEmpty(),
            enc(spec.setup), spec.minimum, spec.deadline?.toString().orEmpty(), start,
            baseline?.toString().orEmpty(), baselineDate?.toString().orEmpty(), archivedOn?.toString().orEmpty()).joinToString("|")
    }
    fun decode(raw: String): FitnessGoal? = runCatching {
        val p = raw.split('|')
        require(p.size == 12)
        UUID.fromString(p[0])
        fun date(i: Int) = p[i].takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        val spec = FitnessGoalSpec(FitnessGoalKind.valueOf(p[1]), p[2].toDouble(), dec(p[3]),
            p[4].takeIf { it.isNotEmpty() }?.let(RecordedLoadBasis::valueOf), dec(p[5]), p[6].toDouble(), date(7))
        val start = LocalDate.parse(p[8])
        val baseline = p[9].takeIf { it.isNotEmpty() }?.toDouble()
        require(spec.valid() && (spec.deadline == null || spec.deadline >= start) &&
            (baseline == null || baseline.isFinite()) && (baseline == null) == (date(10) == null))
        FitnessGoal(p[0], spec, start, baseline, date(10), date(11))
    }.getOrNull()
    fun decodeAll(raw: Set<String>): List<FitnessGoal> = raw.mapNotNull(::decode)
        .groupBy { it.id }.values.mapNotNull { it.singleOrNull() }.sortedByDescending { it.start }
}
