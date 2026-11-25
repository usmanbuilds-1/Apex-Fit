package com.apexfit.app.domain.importer

internal data class NormalizedWorkoutText(
    val originalText: String,
    val normalizedText: String,
    val truncated: Boolean
)

internal object WorkoutTextNormalizer {
    const val MAX_INPUT_CHARS = 100_000

    fun normalize(original: String): NormalizedWorkoutText {
        var truncated = original.length > MAX_INPUT_CHARS
        val bounded = if (!truncated) original else {
            val cut = original.take(MAX_INPUT_CHARS)
            val lastBreak = cut.lastIndexOf('\n')
            if (lastBreak > MAX_INPUT_CHARS / 2) cut.substring(0, lastBreak) else cut
        }

        val compatibilityText = java.text.Normalizer.normalize(bounded, java.text.Normalizer.Form.NFKC)
        if (compatibilityText.length > MAX_INPUT_CHARS) truncated = true
        val rawLines = compatibilityText.take(MAX_INPUT_CHARS)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { line ->
                if (line.length > 4096) truncated = true
                line.take(4096)
            }
        val lines = WorkoutPasteRecovery.expand(rawLines, ::normalizeLine)

        val output = buildString {
            var previousBlank = true
            lines.forEach { line ->
                val blank = line.isBlank()
                if (!blank || !previousBlank) {
                    if (isNotEmpty()) append('\n')
                    append(line)
                }
                previousBlank = blank
            }
        }.trim()

        if (output.length > MAX_INPUT_CHARS) truncated = true
        val safeOutput = if (output.length <= MAX_INPUT_CHARS) output else {
            val cut = output.take(MAX_INPUT_CHARS)
            val lastBreak = cut.lastIndexOf('\n')
            if (lastBreak > MAX_INPUT_CHARS / 2) cut.substring(0, lastBreak) else cut
        }
        return NormalizedWorkoutText(original, safeOutput, truncated)
    }

    private fun normalizeLine(raw: String): String {
        var line = raw
            .replace('\u00A0', ' ')
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace('×', 'x')
            .replace('✕', 'x')
            .replace('✖', 'x')
            .replace('∙', 'x')
            .replace('⁃', '-')
            .replace('▪', '-')
            .replace('●', '-')
            .replace('⨯', 'x')
            .replace('–', '-')
            .replace('—', '-')
            .replace('−', '-')
            .replace('“', '"')
            .replace('”', '"')
            .replace('’', '\'')
            .replace('•', '-')

        line = line.replace("**", "").replace("__", "").replace("`", "")
        line = line.replace(Regex("""\[([^\]\n]{1,100})\]\(https?://[^)\s]{1,500}\)"""), "$1")
        if (!line.trimStart().startsWith("http", ignoreCase = true)) {
            line = line.replace(Regex("""(?i)\s+https?://\S+"""), " ")
        }
        line = line.replace(Regex("""^\s{0,3}#{1,6}\s+"""), "")
        line = line.replace(Regex("""^\s*>\s?"""), "")
        line = line.replace(Regex("""^\s*[-*+]\s+(?=\D)"""), "")
        line = line.replace(Regex("""^\s*\d{1,2}[.)]\s+(?=\D)"""), "")

        return line.trim().replace(Regex("""[\t ]+"""), " ")
    }
}

internal data class ImportDayHeading(
    val label: String,
    val dayOfWeek: String?,
    val focus: String?
)

internal object WorkoutDayDetector {
    private val weekdays = linkedMapOf(
        "monday" to "Monday", "mon" to "Monday",
        "tuesday" to "Tuesday", "tues" to "Tuesday", "tue" to "Tuesday",
        "wednesday" to "Wednesday", "weds" to "Wednesday", "wed" to "Wednesday",
        "thursday" to "Thursday", "thurs" to "Thursday", "thur" to "Thursday", "thu" to "Thursday",
        "friday" to "Friday", "fri" to "Friday",
        "saturday" to "Saturday", "sat" to "Saturday",
        "sunday" to "Sunday", "sun" to "Sunday"
    )
    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7
    )

    private val weekdayRegex = Regex("""^([A-Za-z]+)\b[.:,-]?\s*(.*)$""")
    private val dayRegex = Regex(
        """(?i)^day\s*(\d{1,2}|one|two|three|four|five|six|seven)\b\s*[-:]?\s*(.*)$"""
    )
    private val structuredRegex = Regex(
        """(?i)^(?:workout\s+[a-e0-9]+|upper(?:\s+[a-e0-9]+)?|lower(?:\s+[a-e0-9]+)?|push(?:\s+(?:day|[a-e0-9]+))?|pull(?:\s+(?:day|[a-e0-9]+))?|legs?(?:\s+(?:day|[a-e0-9]+))?|full\s*body(?:\s+(?:day|[a-e0-9]+))?|rest(?:\s+day)?|chest(?:\s+day)?|back(?:\s+day)?|chest\s*[+&/]\s*triceps?|back\s*[+&/]\s*biceps?|chest\s*[+&/]\s*back|back\s*[+&/]\s*chest|shoulders?(?:\s+day)?|arms?(?:\s+day)?)\s*[: -]?$"""
    )
    private val explicitNameRegex = Regex(
        """(?i)^\s*(?:plan\s*name|routine|program|programme)\s*[:=]\s*(.+)$"""
    )

    fun explicitPlanName(line: String): String? =
        explicitNameRegex.matchEntire(line.trim())?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    fun detect(line: String): ImportDayHeading? {
        val text = line.trim().trimEnd(':')
        if (text.isBlank() || WorkoutPrescriptionParser.hasSetRepCore(text)) return null

        dayRegex.matchEntire(text)?.let { match ->
            val token = match.groupValues[1]
            val number = token.toIntOrNull() ?: numberWords[token.lowercase()] ?: return@let
            val focus = cleanFocus(match.groupValues[2])
            return ImportDayHeading("Day $number", null, focus)
        }

        weekdayRegex.matchEntire(text)?.let { match ->
            val day = weekdays[match.groupValues[1].lowercase()]
            if (day != null) {
                val focus = cleanFocus(match.groupValues[2])
                return ImportDayHeading(
                    label = focus ?: day,
                    dayOfWeek = day,
                    focus = focus
                )
            }
        }

        if (structuredRegex.matches(text)) {
            val label = titleCase(text.trim().trimEnd(':', '-'))
            return ImportDayHeading(label, null, null)
        }

        return null
    }

    private fun cleanFocus(raw: String): String? {
        val focus = raw.trim().trimStart('-', ':', ',', '+').trim()
        if (focus.isBlank() || focus.length > 36 || WorkoutPrescriptionParser.hasSetRepCore(focus)) return null
        return titleCase(focus)
    }

    private fun titleCase(value: String): String {
        if (value.any { it.isLowerCase() }) return value.trim()
        return value.lowercase().split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
    }
}

internal sealed interface ImportLineShape {
    data class FullExercise(val name: String, val prescription: String, val groupTag: String?) : ImportLineShape
    data class NameOnly(val name: String, val groupTag: String?) : ImportLineShape
    data class PrescriptionOnly(val prescription: String) : ImportLineShape
    data class RestOnly(val text: String) : ImportLineShape
    data class GroupMarker(val tag: String?) : ImportLineShape
    data class Other(val text: String) : ImportLineShape
}

internal object WorkoutExerciseLineParser {
    private val groupRegex = Regex("""(?i)^\s*(?:superset|super\s*set|circuit|ss)\s*([a-e])?\s*[: -]?\s*$""")
    private val groupPrefixRegex = Regex("""^\s*([A-Ea-e])\d{1,2}\s*[.): -]?\s+(.+)$""")
    private val restOnlyRegex = Regex(
        """(?i)^\s*(?:(?:rest|load|weight|rpe|rir)\b.*|@\s*.*|\d{1,6}(?:\.\d+)?\s*(?:kg|kgs|lb|lbs)\b.*|\d{1,3}\s*(?:s|sec|secs|second|seconds|min|mins|minute|minutes)\b.*)$"""
    )
    private val coreStartRegex = Regex(
        """(?i)(?:\b\d{1,2}\s*x\s*(?:\d{1,3}|amrap|max(?:\s+reps?)?|(?:to\s+)?failure)|\b\d{1,3}\s+(?:sets?|reps?)\b|\b(?:sets?|reps?)\s*[:=]?\s*\d{1,3}\b|\b\d{1,3}\s*-\s*\d{1,3}\s+reps?\b)"""
    )

    fun parse(line: String): ImportLineShape {
        val text = line.trim()
        if (text.isBlank()) return ImportLineShape.Other("")

        groupRegex.matchEntire(text)?.let {
            return ImportLineShape.GroupMarker(it.groupValues[1].uppercase().takeIf(String::isNotBlank))
        }

        var group: String? = null
        var working = text
        groupPrefixRegex.matchEntire(text)?.let {
            group = it.groupValues[1].uppercase()
            working = it.groupValues[2].trim()
        }

        if (restOnlyRegex.matches(working) && !WorkoutPrescriptionParser.hasSetRepCore(working)) {
            return ImportLineShape.RestOnly(working)
        }

        coreStartRegex.find(working)?.let { match ->
            if (match.range.first == 0) return ImportLineShape.PrescriptionOnly(working)
            val name = working.substring(0, match.range.first)
                .trim()
                .trimEnd('-', ':', ',', '(', '[')
                .trim()
            if (name.length >= 2 && name.any(Char::isLetter)) {
                return ImportLineShape.FullExercise(name, working.substring(match.range.first).trim(), group)
            }
        }

        val colon = working.indexOf(':')
        if (colon in 2 until working.lastIndex) {
            val left = working.substring(0, colon).trim()
            val right = working.substring(colon + 1).trim()
            if (left.any(Char::isLetter) && WorkoutPrescriptionParser.hasSetRepCore(right)) {
                return ImportLineShape.FullExercise(left, right, group)
            }
        }

        if (working.any(Char::isLetter)) return ImportLineShape.NameOnly(working.trim(), group)
        return ImportLineShape.Other(working)
    }
}

internal data class ParsedPrescription(
    val sets: Int?,
    val repsMin: Int?,
    val repsMax: Int?,
    val repTarget: ImportRepTarget,
    val repNote: String?,
    val weight: ParsedImportWeight?,
    val restSeconds: Int?,
    val restNote: String?,
    val rpe: Double?,
    val rir: Int?,
    val leftover: String
)

internal object WorkoutPrescriptionParser {
    private val rangeCore = Regex(
        """(?i)\b(\d{1,2})\s*x\s*(\d{1,3})\s*-\s*(\d{1,3})\b(?!\s*(?:s|sec|secs|second|seconds|min|mins|minute|minutes)\b)"""
    )
    private val fixedCore = Regex("""(?i)\b(\d{1,2})\s*x\s*(\d{1,3})\b""")
    private val semanticCore = Regex("""(?i)\b(\d{1,2})\s*x\s*(amrap|max(?:\s+reps?)?|(?:to\s+)?failure)\b""")
    private val setsRange = Regex(
        """(?i)\b(\d{1,2})\s+sets?\s*(?:x|of)?\s*(\d{1,3})\s*-\s*(\d{1,3})\s*(?:reps?)?\b(?!\s*(?:s|sec|secs|second|seconds|min|mins|minute|minutes)\b)"""
    )
    private val setsFixed = Regex("""(?i)\b(\d{1,2})\s+sets?\s*(?:x|of)?\s*(\d{1,3})\s*(?:reps?)?\b""")
    private val labeledSets = Regex("""(?i)\b(?:sets?\s*[:=]?\s*(\d{1,2})\b|(\d{1,2})\s+sets?\b)""")
    private val labeledReps = Regex("""(?i)\b(?:reps?\s*[:=]?\s*(\d{1,3})(?:\s*-\s*(\d{1,3}))?\b|(\d{1,3})(?:\s*-\s*(\d{1,3}))?\s+reps?\b)""")
    private val sequenceCore = Regex("""(?i)\b(\d{1,2})\s*(?:x|sets?\s*(?:x|of)?)\s*(\d{1,3}(?:\s*,\s*\d{1,3}){2,})(?:\s+reps?)?\b(?!\s*(?:kg|lb|lbs|kgs|sec|min)\b)""")
    private val durationCore = Regex("""(?i)\b(\d{1,2})\s*(?:x|sets?\s*(?:x|of)?)\s*(\d{1,3}(?:\s*-\s*\d{1,3})?)\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\b(?!\s*rest\b)""")
    private val setsFailure = Regex("""(?i)\b(\d{1,2})\s+sets?\s*(?:x|of)?\s*(?:to\s+)?failure\b""")

    private val rpeRegex = Regex("""(?i)(?:@\s*)?\bRPE\s*([0-9]+(?:\.[0-9]+)?)\b""")
    private val rirAfterRegex = Regex("""(?i)\bRIR\s*(\d{1,2})\b""")
    private val rirBeforeRegex = Regex("""(?i)\b(\d{1,2})\s*RIR\b""")

    private val restRegex = Regex(
        """(?i)\brest\s*:?\s*(\d{1,3})(?:\s*-\s*(\d{1,3}))?\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\b"""
    )
    private val restAfterRegex = Regex(
        """(?i)\b(\d{1,3})(?:\s*-\s*(\d{1,3}))?\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\s*rest\b"""
    )
    private val bareRestRegex = Regex(
        """(?i)^\s*(\d{1,3})(?:\s*-\s*(\d{1,3}))?\s*(s|sec|secs|second|seconds|min|mins|minute|minutes)\s*$"""
    )
    private val weightRegex = Regex("""(?i)(?<![\w.\-])(\d{1,6}(?:\.\d{1,4})?)\s*(kg|kgs|lb|lbs)\b""")

    fun hasSetRepCore(text: String): Boolean =
        semanticCore.containsMatchIn(text) || rangeCore.containsMatchIn(text) || fixedCore.containsMatchIn(text) ||
            setsFailure.containsMatchIn(text) || setsRange.containsMatchIn(text) || setsFixed.containsMatchIn(text)

    fun parse(text: String): ParsedPrescription {
        var remaining = text.trim()

        var rpe: Double? = null
        rpeRegex.find(remaining)?.let {
            rpe = it.groupValues[1].toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 }
            if (rpe != null) remaining = removeMatch(remaining, it)
        }

        var rir: Int? = null
        (rirAfterRegex.find(remaining) ?: rirBeforeRegex.find(remaining))?.let {
            val number = it.groupValues.drop(1).firstOrNull(String::isNotBlank)?.toIntOrNull()
            rir = number?.takeIf { value -> value in 0..10 }
            if (rir != null) remaining = removeMatch(remaining, it)
        }

        var weight: ParsedImportWeight? = null
        weightRegex.find(remaining)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull()
            val unitToken = match.groupValues[2].lowercase()
            val unit = if (unitToken.startsWith("kg")) ImportWeightUnit.KG else ImportWeightUnit.LB
            if (value != null) weight = ParsedImportWeight(value, unit, match.value)
            remaining = removeMatch(remaining, match)
        }

        var sets: Int? = null
        var repsMin: Int? = null
        var repsMax: Int? = null
        var target = ImportRepTarget.FIXED
        var repNote: String? = null

        val sequence = sequenceCore.find(remaining)
        if (sequence != null) {
            sets = sequence.groupValues[1].toIntOrNull()
            repNote = "Source per-set rep sequence: " + sequence.groupValues[2] + ". Choose a numeric target before saving."
            remaining = removeMatch(remaining, sequence)
        }
        val duration = if (sequence == null) durationCore.find(remaining) else null
        if (duration != null) {
            sets = duration.groupValues[1].toIntOrNull()
            repNote = "Source timed target: " + duration.value
            remaining = removeMatch(remaining, duration)
        }

        val semantic = semanticCore.find(remaining)
        val failureSets = setsFailure.find(remaining)
        val rangeSets = setsRange.find(remaining)
        val rangeX = rangeCore.find(remaining)
        val fixedSets = setsFixed.find(remaining)
        val fixedX = fixedCore.find(remaining)
        val coreMatch = if (duration != null || sequence != null) null else semantic ?: failureSets ?: rangeSets ?: rangeX ?: fixedSets ?: fixedX

        if (coreMatch != null) {
            when (coreMatch) {
                semantic -> {
                    sets = coreMatch.groupValues[1].toIntOrNull()
                    val semanticToken = coreMatch.groupValues[2].lowercase()
                    target = if (semanticToken.contains("failure")) ImportRepTarget.FAILURE else ImportRepTarget.AMRAP
                    repNote = coreMatch.groupValues[2]
                }
                failureSets -> {
                    sets = coreMatch.groupValues[1].toIntOrNull()
                    target = ImportRepTarget.FAILURE
                    repNote = "to failure"
                }
                rangeSets, rangeX -> {
                    sets = coreMatch.groupValues[1].toIntOrNull()
                    repsMin = coreMatch.groupValues[2].toIntOrNull()
                    repsMax = coreMatch.groupValues[3].toIntOrNull()
                    target = ImportRepTarget.RANGE
                }
                else -> {
                    sets = coreMatch.groupValues[1].toIntOrNull()
                    repsMin = coreMatch.groupValues[2].toIntOrNull()
                    repsMax = repsMin
                    target = ImportRepTarget.FIXED
                }
            }
            remaining = removeMatch(remaining, coreMatch)
        }

        if (coreMatch == null && duration == null && sequence == null) {
            labeledSets.find(remaining)?.let {
                sets = it.groupValues.drop(1).firstOrNull(String::isNotBlank)?.toIntOrNull()
                remaining = removeMatch(remaining, it)
            }
            labeledReps.find(remaining)?.let {
                val before = it.groupValues[1].isNotBlank()
                repsMin = it.groupValues[if (before) 1 else 3].toIntOrNull()
                repsMax = it.groupValues[if (before) 2 else 4].toIntOrNull() ?: repsMin
                target = if (repsMin == repsMax) ImportRepTarget.FIXED else ImportRepTarget.RANGE
                remaining = removeMatch(remaining, it)
            }
        }
        // Invalid prescriptions must stay visible, never silently become usable values.
        if (sets?.let { it !in 1..99 } == true || repsMin?.let { it !in 1..999 } == true ||
            repsMax?.let { it !in 1..999 } == true ||
            (repsMin != null && repsMax != null && repsMin!! > repsMax!!)) {
            repNote = "Invalid source target: " + text
            sets = sets?.takeIf { it in 1..99 }
            repsMin = null
            repsMax = null
        }

        var restSeconds: Int? = null
        var restNote: String? = null
        (restRegex.find(remaining) ?: restAfterRegex.find(remaining) ?: bareRestRegex.find(remaining))?.let { match ->
            val low = match.groupValues[1].toIntOrNull()
            val high = match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.toIntOrNull()
            val unit = match.groupValues.getOrNull(3).orEmpty().lowercase()
            val multiplier = if (unit.startsWith("m")) 60 else 1
            restSeconds = low?.times(multiplier)?.takeIf { it in 0..3600 }
            if (restSeconds == null) restNote = "Invalid source rest: " + match.value
            if (high != null && low != null && restSeconds != null) restNote = "Source rest range: ${low * multiplier}-${high * multiplier} sec"
            remaining = removeMatch(remaining, match)
        }

        val leftover = remaining
            .replace(Regex("""\s*[-,:;]+\s*$"""), "")
            .replace(Regex("""^\s*[-,:;]+\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        return ParsedPrescription(
            sets = sets,
            repsMin = repsMin,
            repsMax = repsMax,
            repTarget = target,
            repNote = repNote,
            weight = weight,
            restSeconds = restSeconds,
            restNote = restNote,
            rpe = rpe,
            rir = rir,
            leftover = leftover
        )
    }

    private fun removeMatch(text: String, match: MatchResult): String =
        text.removeRange(match.range).replace(Regex("""\s{2,}"""), " ").trim()
}

internal object WorkoutImportNoiseFilter {
    private val urlRegex = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val prosePrefixes = listOf(
        "edit:", "edit -", "source:", "here is", "here's", "this is my",
        "i have been", "i've been", "for context", "background:"
    )

    fun isNoise(line: String): Boolean {
        val lower = line.trim().lowercase()
        if (lower.isBlank()) return true
        if (urlRegex.containsMatchIn(lower)) return true
        if (prosePrefixes.any(lower::startsWith)) return true
        return lower.length > 120 && !WorkoutPrescriptionParser.hasSetRepCore(lower)
    }
}
