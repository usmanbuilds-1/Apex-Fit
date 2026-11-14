package com.apexfit.app.domain.importer

import kotlin.math.roundToInt

object WorkoutExerciseAliases {
    private val aliasesByLibraryName: Map<String, List<String>> = mapOf(
        "Standing Overhead Press" to listOf("ohp", "overhead press", "standing ohp", "military press"),
        "Romanian Deadlift" to listOf("rdl", "romanian dead lift"),
        "Flat Dumbbell Press" to listOf("db bench", "db bench press", "dumbbell bench", "dumbbell bench press"),
        "Dumbbell Lateral Raise" to listOf("lateral raise", "lateral raises", "lat raise", "side lateral raise"),
        "Barbell Back Squat" to listOf("back squat", "barbell squat"),
        "Chest-Supported Dumbbell Row" to listOf("chest supported row", "chest supported rows", "chest supported db row"),
        "Hammer Bicep Curl" to listOf("hammer curl", "hammer curls"),
        "Incline Dumbbell Bicep Curl" to listOf("incline dumbbell curl", "incline db curl"),
        "Dual Rope Tricep Pushdown" to listOf("tricep pushdown", "triceps pushdown", "rope pushdown", "rope tricep pushdown"),
        "Conventional Deadlift" to listOf("deadlift", "conventional deadlift"),
        "Bulgarian Split Squat" to listOf("bulgarian split squat", "bss"),
        "Standing Calf Raise" to listOf("calf raise", "calf raises", "standing calves"),
        "Weighted Pull-Up" to listOf("weighted pullup", "weighted pull up", "weighted pullups", "weighted pull ups"),
        "Pullups" to listOf("pullup", "pullups", "pull up", "pull ups"),
        "Barbell Bench Press" to listOf("bench press", "barbell bench", "bb bench"),
        "Incline Barbell Bench Press" to listOf("incline bench press", "incline barbell press"),
        "Barbell Row" to listOf("barbell row", "bb row"),
        "Dumbbell Row" to listOf("dumbbell row", "db row"),
        "Leg Extension" to listOf("leg extensions"),
        "Leg Curl" to listOf("leg curls", "hamstring curl", "hamstring curls"),
        "Lying Leg Curl" to listOf("lying leg curls", "lying hamstring curl"),
        "Cable Lateral Raise" to listOf("cable lateral raises", "cable lat raise")
    )

    fun forExercise(name: String): List<String> = aliasesByLibraryName[name].orEmpty()
}

class WorkoutExerciseMatcher(entries: List<ExerciseLibraryEntry>) {

    data class Match(
        val entry: ExerciseLibraryEntry?,
        val confidence: MatchConfidence,
        val flagged: Boolean,
        val score: Int,
        val candidates: List<ExerciseCandidate>
    )

    private data class IndexedEntry(
        val entry: ExerciseLibraryEntry,
        val tokens: List<String>,
        val movement: String?,
        val axis: String?,
        val bodyPart: String?,
        val equipment: String?
    )

    private val indexed = entries.map { entry ->
        val tokens = tokens(entry.name)
        IndexedEntry(
            entry = entry,
            tokens = tokens,
            movement = movementOf(tokens),
            axis = axisOf(tokens),
            bodyPart = bodyPartOf(tokens),
            equipment = equipmentOf(tokens)
        )
    }

    private val exactOwners: Map<String, List<ExerciseLibraryEntry>> = buildMap {
        entries.forEach { entry ->
            val names = buildList {
                add(entry.name)
                addAll(entry.aliases)
            }
            names.forEach { raw ->
                val key = normalize(raw)
                if (key.isNotBlank()) {
                    put(key, get(key).orEmpty() + entry)
                }
            }
        }
    }

    fun match(rawName: String): Match {
        val normalized = normalize(rawName)
        if (normalized.isBlank()) {
            return Match(null, MatchConfidence.UNRESOLVED, true, 0, emptyList())
        }

        exactOwners[normalized]?.distinctBy { it.id }?.let { owners ->
            if (owners.size == 1) {
                return Match(owners.first(), MatchConfidence.EXACT, false, 100, emptyList())
            }
            val candidates = owners.take(5).map {
                ExerciseCandidate(it, MatchConfidence.MEDIUM, 100, true)
            }
            return Match(null, MatchConfidence.MEDIUM, true, 100, candidates)
        }

        val queryTokens = tokens(rawName)
        if (queryTokens.isEmpty()) {
            return Match(null, MatchConfidence.UNRESOLVED, true, 0, emptyList())
        }

        val querySet = queryTokens.toSet()
        val queryMovement = movementOf(queryTokens)
        val queryAxis = axisOf(queryTokens)
        val queryBody = bodyPartOf(queryTokens)
        val queryEquipment = equipmentOf(queryTokens)

        val scored = indexed.mapNotNull { candidate ->
            val candidateSet = candidate.tokens.toSet()
            val overlap = querySet.intersect(candidateSet)
            if (overlap.isEmpty()) return@mapNotNull null

            val movementConflict = queryMovement != null &&
                candidate.movement != null &&
                queryMovement != candidate.movement
            val axisConflict = queryAxis != null &&
                candidate.axis != null &&
                queryAxis != candidate.axis
            if (movementConflict || axisConflict) return@mapNotNull null

            val bodyConflict = queryBody != null &&
                candidate.bodyPart != null &&
                queryBody != candidate.bodyPart

            val coverage = overlap.size.toDouble() / querySet.size
            val precision = overlap.size.toDouble() / candidateSet.size
            var score = 70.0 * coverage + 30.0 * precision

            if (queryEquipment != null && candidate.equipment != null) {
                score += if (queryEquipment == candidate.equipment) 8.0 else -18.0
            }
            if (bodyConflict) score -= 30.0

            val queryDistinctive = querySet - GENERIC_TOKENS
            val candidateDistinctive = candidateSet - GENERIC_TOKENS
            if (queryDistinctive.isNotEmpty() && candidateDistinctive.isNotEmpty()) {
                val distinctiveOverlap = queryDistinctive.intersect(candidateDistinctive)
                if (distinctiveOverlap.isEmpty()) score -= 15.0
            }

            val clamped = score.roundToInt().coerceIn(0, 100)
            if (clamped <= 0) null else Triple(candidate, clamped, bodyConflict)
        }.sortedWith(
            compareByDescending<Triple<IndexedEntry, Int, Boolean>> { it.second }
                .thenBy { it.first.entry.name.length }
                .thenBy { it.first.entry.name }
        )

        if (scored.isEmpty()) {
            return Match(null, MatchConfidence.UNRESOLVED, true, 0, emptyList())
        }

        val best = scored.first()
        val isGenericSingleToken = (querySet - GENERIC_TOKENS).size <= 1 && queryTokens.size == 1
        val closeCompetitors = scored.drop(1).count { it.second >= best.second - 3 }

        var confidence = when {
            isGenericSingleToken -> MatchConfidence.UNRESOLVED
            best.third -> MatchConfidence.MEDIUM
            best.second >= 82 && querySet.intersect(best.first.tokens.toSet()).size.toDouble() / querySet.size >= 0.80 ->
                MatchConfidence.HIGH
            best.second >= 55 -> MatchConfidence.MEDIUM
            else -> MatchConfidence.UNRESOLVED
        }

        if (confidence == MatchConfidence.HIGH && closeCompetitors > 0) {
            confidence = MatchConfidence.MEDIUM
        }

        val candidates = scored.take(5).map { (candidate, score, conflict) ->
            val candidateConfidence = when {
                conflict -> MatchConfidence.MEDIUM
                score >= 82 -> MatchConfidence.HIGH
                score >= 55 -> MatchConfidence.MEDIUM
                else -> MatchConfidence.UNRESOLVED
            }
            ExerciseCandidate(
                entry = candidate.entry,
                confidence = candidateConfidence,
                score = score,
                flagged = conflict
            )
        }

        val selected = when (confidence) {
            MatchConfidence.EXACT, MatchConfidence.HIGH, MatchConfidence.MEDIUM -> best.first.entry
            MatchConfidence.UNRESOLVED -> null
        }
        val flagged = confidence == MatchConfidence.MEDIUM ||
            confidence == MatchConfidence.UNRESOLVED ||
            best.third ||
            closeCompetitors > 0

        return Match(selected, confidence, flagged, best.second, candidates)
    }

    companion object {
        private val SYNONYMS = mapOf(
            "db" to listOf("dumbbell"),
            "bb" to listOf("barbell"),
            "kb" to listOf("kettlebell"),
            "ohp" to listOf("overhead", "press"),
            "rdl" to listOf("romanian", "deadlift"),
            "pullups" to listOf("pull", "up"),
            "pullup" to listOf("pull", "up"),
            "pushups" to listOf("push", "up"),
            "pushup" to listOf("push", "up"),
            "chinups" to listOf("chin", "up"),
            "chinup" to listOf("chin", "up")
        )

        private val CANONICAL = mapOf(
            "raises" to "raise",
            "raise" to "raise",
            "rows" to "row",
            "presses" to "press",
            "curls" to "curl",
            "flies" to "fly",
            "flyes" to "fly",
            "squats" to "squat",
            "deadlifts" to "deadlift",
            "extensions" to "extension",
            "pulldowns" to "pulldown",
            "lunges" to "lunge",
            "dips" to "dip",
            "crunches" to "crunch",
            "shrugs" to "shrug",
            "calves" to "calf",
            "biceps" to "bicep",
            "triceps" to "tricep",
            "hamstrings" to "hamstring",
            "quads" to "quad",
            "glutes" to "glute",
            "shoulders" to "shoulder",
            "dumbbells" to "dumbbell",
            "kettlebells" to "kettlebell"
        )

        private val FILLER = setOf("the", "a", "an", "of", "and", "with", "exercise", "exercises")

        private val MOVEMENTS = setOf(
            "press", "row", "curl", "raise", "fly", "squat", "deadlift", "extension",
            "pulldown", "pull", "push", "dip", "lunge", "crunch", "plank", "thrust",
            "bridge", "kickback", "shrug", "pullover", "rollout", "carry", "swing",
            "clean", "snatch", "jerk", "stretch", "jump"
        )

        private val EQUIPMENT = setOf(
            "barbell", "dumbbell", "cable", "machine", "kettlebell", "smith",
            "band", "bodyweight", "lever", "sled"
        )

        private val BODY_PARTS = setOf(
            "chest", "leg", "calf", "hamstring", "quad", "glute", "shoulder",
            "bicep", "tricep", "forearm", "lat", "hip", "ab", "core"
        )

        private val GENERIC_TOKENS = MOVEMENTS + EQUIPMENT + setOf(
            "one", "arm", "two", "standing", "seated", "lying", "incline", "decline",
            "flat", "weighted", "bodyweight", "grip"
        )

        fun normalize(raw: String): String = tokens(raw).joinToString(" ")

        fun tokens(raw: String): List<String> {
            val cleaned = raw
                .lowercase()
                .replace("pull-ups", "pullups")
                .replace("push-ups", "pushups")
                .replace("chin-ups", "chinups")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

            if (cleaned.isBlank()) return emptyList()

            return cleaned.split(Regex("\\s+"))
                .flatMap { token -> SYNONYMS[token] ?: listOf(token) }
                .map { token -> CANONICAL[token] ?: simpleStem(token) }
                .filter { token -> token.length > 1 && token !in FILLER && !token.all { it.isDigit() } }
        }

        private fun simpleStem(token: String): String = when {
            token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
            token.endsWith("s") && !token.endsWith("ss") && token.length > 4 -> token.dropLast(1)
            else -> token
        }

        private fun movementOf(tokens: List<String>): String? =
            tokens.lastOrNull { it in MOVEMENTS }

        private fun equipmentOf(tokens: List<String>): String? =
            tokens.firstOrNull { it in EQUIPMENT }

        private fun axisOf(tokens: List<String>): String? = when {
            "front" in tokens -> "front"
            tokens.any { it == "lateral" || it == "side" } -> "side"
            tokens.any { it == "rear" || it == "reverse" || it == "behind" } -> "rear"
            else -> null
        }

        private fun bodyPartOf(tokens: List<String>): String? =
            tokens.firstOrNull { it in BODY_PARTS }
    }
}
