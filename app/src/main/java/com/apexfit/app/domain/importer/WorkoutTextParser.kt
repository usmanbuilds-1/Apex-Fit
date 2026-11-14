package com.apexfit.app.domain.importer

class WorkoutTextParser(
    library: List<ExerciseLibraryEntry>,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    private val matcher = WorkoutExerciseMatcher(library)

    private data class DraftExercise(
        val rawName: String,
        var rawPrescription: String?,
        val lineIndex: Int,
        var groupTag: String?,
        val extraNotes: MutableList<String> = mutableListOf()
    )

    private data class DraftDay(
        val heading: ImportDayHeading,
        val lineIndex: Int?,
        val exercises: MutableList<DraftExercise> = mutableListOf()
    )

    fun parse(originalText: String): WorkoutImportParseResult {
        val normalized = WorkoutTextNormalizer.normalize(originalText)
        if (normalized.normalizedText.isBlank()) {
            return WorkoutImportParseResult(
                plan = null,
                fatalError = ImportWarning(
                    ImportWarning.EMPTY_INPUT,
                    "Paste a workout before parsing.",
                    ImportWarningSeverity.ERROR
                )
            )
        }

        val globalWarnings = mutableListOf<ImportWarning>()
        if (normalized.truncated) {
            globalWarnings += ImportWarning(
                ImportWarning.INPUT_TOO_LARGE,
                "Only the first ${WorkoutTextNormalizer.MAX_INPUT_CHARS} characters were parsed.",
                ImportWarningSeverity.WARNING
            )
        }

        val lines = normalized.normalizedText.split('\n')
        val explicitName = lines.asSequence().mapNotNull(WorkoutDayDetector::explicitPlanName).firstOrNull()
        val inferredName = explicitName ?: inferPlanName(lines) ?: "Imported Plan"
        val nameInferred = explicitName == null

        val days = mutableListOf<DraftDay>()
        var currentDay: DraftDay? = null
        var activeGroupTag: String? = null
        var groupCounter = 0
        var sawStructuredDay = false
        val ignoredTitleLine = lines.indexOfFirst { it.trim() == inferredName.trim() }

        fun ensureDay(lineIndex: Int): DraftDay {
            currentDay?.let { return it }
            val day = DraftDay(
                heading = ImportDayHeading(label = "Workout 1", dayOfWeek = null, focus = null),
                lineIndex = lineIndex
            )
            days += day
            currentDay = day
            globalWarnings += ImportWarning(
                ImportWarning.NO_DAYS_FOUND,
                "No training-day heading was found, so exercises were placed in Workout 1.",
                ImportWarningSeverity.INFO,
                lineIndex
            )
            return day
        }

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) {
                activeGroupTag = null
                return@forEachIndexed
            }

            if (WorkoutDayDetector.explicitPlanName(line) != null) return@forEachIndexed
            if (index == ignoredTitleLine && looksLikePlanTitle(line)) return@forEachIndexed

            WorkoutDayDetector.detect(line)?.let { heading ->
                sawStructuredDay = true
                val day = DraftDay(heading = heading, lineIndex = index)
                days += day
                currentDay = day
                activeGroupTag = null
                return@forEachIndexed
            }

            if (WorkoutImportNoiseFilter.isNoise(line)) return@forEachIndexed

            when (val shape = WorkoutExerciseLineParser.parse(line)) {
                is ImportLineShape.GroupMarker -> {
                    groupCounter += 1
                    activeGroupTag = shape.tag ?: "S$groupCounter"
                }
                is ImportLineShape.FullExercise -> {
                    val day = ensureDay(index)
                    day.exercises += DraftExercise(
                        rawName = shape.name,
                        rawPrescription = shape.prescription,
                        lineIndex = index,
                        groupTag = shape.groupTag ?: activeGroupTag
                    )
                }
                is ImportLineShape.NameOnly -> {
                    val day = currentDay
                    if (day == null && looksLikePlanTitle(shape.name)) return@forEachIndexed
                    val targetDay = ensureDay(index)
                    val libraryMatch = matcher.match(shape.name)
                    val likelyExercise = libraryMatch.entry != null || libraryMatch.candidates.isNotEmpty() ||
                        WorkoutExerciseMatcher.tokens(shape.name).any { token -> token in EXERCISE_HINT_TOKENS }
                    if (likelyExercise && shape.name.length <= 90) {
                        targetDay.exercises += DraftExercise(
                            rawName = shape.name,
                            rawPrescription = null,
                            lineIndex = index,
                            groupTag = shape.groupTag ?: activeGroupTag
                        )
                    } else if (targetDay.exercises.isNotEmpty() && shape.name.length <= 100) {
                        targetDay.exercises.last().extraNotes += shape.name
                    } else {
                        globalWarnings += ImportWarning(
                            ImportWarning.LINE_NOT_PARSED,
                            "Ignored text that did not look like an exercise: ${shape.name.take(70)}",
                            ImportWarningSeverity.INFO,
                            index
                        )
                    }
                }
                is ImportLineShape.PrescriptionOnly -> {
                    val last = currentDay?.exercises?.lastOrNull()
                    if (last != null && last.rawPrescription.isNullOrBlank()) {
                        last.rawPrescription = shape.prescription
                    } else if (last != null) {
                        last.extraNotes += shape.prescription
                    } else {
                        globalWarnings += ImportWarning(
                            ImportWarning.LINE_NOT_PARSED,
                            "Ignored a set/rep line because no exercise preceded it.",
                            ImportWarningSeverity.INFO,
                            index
                        )
                    }
                }
                is ImportLineShape.RestOnly -> {
                    val last = currentDay?.exercises?.lastOrNull()
                    if (last != null) {
                        last.rawPrescription = listOfNotNull(last.rawPrescription, shape.text).joinToString(" ")
                    } else {
                        globalWarnings += ImportWarning(
                            ImportWarning.LINE_NOT_PARSED,
                            "Ignored a rest line because no exercise preceded it.",
                            ImportWarningSeverity.INFO,
                            index
                        )
                    }
                }
                is ImportLineShape.Other -> Unit
            }
        }

        val nonEmptyDays = days.filter { it.exercises.isNotEmpty() }
        if (nonEmptyDays.isEmpty()) {
            return WorkoutImportParseResult(
                plan = null,
                fatalError = ImportWarning(
                    ImportWarning.NO_EXERCISES_FOUND,
                    "No exercises with recognizable workout structure were found.",
                    ImportWarningSeverity.ERROR
                )
            )
        }

        if (!sawStructuredDay && nonEmptyDays.size == 1 && globalWarnings.none { it.code == ImportWarning.NO_DAYS_FOUND }) {
            globalWarnings += ImportWarning(
                ImportWarning.NO_DAYS_FOUND,
                "No training-day heading was found, so exercises were placed in Workout 1.",
                ImportWarningSeverity.INFO
            )
        }

        val parsedDays = nonEmptyDays.map { day ->
            ParsedImportedDay(
                label = day.heading.label,
                dayOfWeek = day.heading.dayOfWeek,
                focus = day.heading.focus,
                exercises = day.exercises.map(::parseExercise),
                lineIndex = day.lineIndex
            )
        }

        return WorkoutImportParseResult(
            plan = ParsedImportedPlan(
                name = inferredName.take(80).ifBlank { "Imported Plan" },
                nameInferred = nameInferred,
                days = parsedDays,
                warnings = globalWarnings,
                provenance = WorkoutImportProvenance(
                    originalText = normalized.originalText.take(WorkoutTextNormalizer.MAX_INPUT_CHARS),
                    normalizedText = normalized.normalizedText,
                    importedAtEpochMs = nowEpochMs()
                )
            )
        )
    }

    private fun parseExercise(draft: DraftExercise): ParsedImportedExercise {
        val prescription = WorkoutPrescriptionParser.parse(draft.rawPrescription.orEmpty())
        val match = matcher.match(draft.rawName)
        val warnings = mutableListOf<ImportWarning>()

        when (match.confidence) {
            MatchConfidence.UNRESOLVED -> warnings += ImportWarning(
                ImportWarning.EXERCISE_UNRESOLVED,
                "Choose an Apex Fit exercise for ${draft.rawName}.",
                ImportWarningSeverity.WARNING,
                draft.lineIndex
            )
            MatchConfidence.MEDIUM -> warnings += ImportWarning(
                ImportWarning.EXERCISE_NEEDS_REVIEW,
                "Check the suggested exercise match for ${draft.rawName}.",
                ImportWarningSeverity.WARNING,
                draft.lineIndex
            )
            else -> Unit
        }

        if (prescription.sets == null) {
            warnings += ImportWarning(
                ImportWarning.SETS_MISSING,
                "Sets are missing for ${draft.rawName}.",
                ImportWarningSeverity.WARNING,
                draft.lineIndex
            )
        }
        if (prescription.repsMin == null || prescription.repsMax == null) {
            warnings += ImportWarning(
                ImportWarning.PRESCRIPTION_LEFTOVER,
                when (prescription.repTarget) {
                    ImportRepTarget.AMRAP -> "AMRAP was preserved. Choose a numeric rep target before saving."
                    ImportRepTarget.FAILURE -> "Failure training was preserved. Choose a numeric rep target before saving."
                    else -> "Rep target is missing for ${draft.rawName}."
                },
                ImportWarningSeverity.WARNING,
                draft.lineIndex
            )
        }
        if (prescription.leftover.isNotBlank()) {
            warnings += ImportWarning(
                ImportWarning.PRESCRIPTION_LEFTOVER,
                "Some source text was preserved as a note: ${prescription.leftover.take(90)}",
                ImportWarningSeverity.INFO,
                draft.lineIndex
            )
        }
        if (draft.groupTag != null) {
            warnings += ImportWarning(
                ImportWarning.SUPERSET_FLATTENED,
                "Superset/circuit grouping will be preserved in notes because Apex Fit does not model exercise groups yet.",
                ImportWarningSeverity.INFO,
                draft.lineIndex
            )
        }

        val notes = buildList {
            prescription.repNote?.let { add(it) }
            prescription.restNote?.let { add(it) }
            prescription.rpe?.let { add("Source target: RPE ${formatNumber(it)}") }
            prescription.rir?.let { add("Source target: RIR $it") }
            draft.groupTag?.let { add("Imported group: $it") }
            if (prescription.leftover.isNotBlank()) add(prescription.leftover)
            addAll(draft.extraNotes)
        }.distinct()

        return ParsedImportedExercise(
            rawName = draft.rawName,
            rawPrescription = draft.rawPrescription,
            matchedEntryId = match.entry?.id,
            matchedName = match.entry?.name,
            matchConfidence = match.confidence,
            matchFlagged = match.flagged,
            candidates = match.candidates,
            sets = prescription.sets,
            repsMin = prescription.repsMin,
            repsMax = prescription.repsMax,
            repTarget = prescription.repTarget,
            repNote = prescription.repNote,
            weight = prescription.weight,
            restSeconds = prescription.restSeconds,
            rpe = prescription.rpe,
            rir = prescription.rir,
            supersetTag = draft.groupTag,
            notes = notes,
            warnings = warnings,
            lineIndex = draft.lineIndex
        )
    }

    private fun inferPlanName(lines: List<String>): String? {
        val firstHeading = lines.indexOfFirst { WorkoutDayDetector.detect(it) != null }.let { if (it < 0) lines.size else it }
        return lines.take(firstHeading.coerceAtMost(6)).asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !WorkoutImportNoiseFilter.isNoise(it) }
            .firstOrNull(::looksLikePlanTitle)
    }

    private fun looksLikePlanTitle(line: String): Boolean {
        if (line.length !in 2..80 || WorkoutPrescriptionParser.hasSetRepCore(line)) return false
        val lower = line.lowercase()
        return PLAN_TITLE_HINT.containsMatchIn(lower) ||
            (line.length <= 12 && line.all { it.isLetter() || it.isWhitespace() } && line.count(Char::isUpperCase) >= 3)
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {
        private val PLAN_TITLE_HINT = Regex(
            """(?i)\b(?:workout|routine|program|programme|split|ppl|phul|phat|5x5|upper\s+lower|push\s+pull\s+legs|reddit)\b"""
        )

        private val EXERCISE_HINT_TOKENS = setOf(
            "press", "row", "curl", "raise", "fly", "squat", "deadlift", "extension", "pulldown",
            "pull", "push", "dip", "lunge", "crunch", "plank", "thrust", "bridge", "kickback",
            "shrug", "pullover", "rollout", "carry", "swing", "clean", "snatch", "jerk", "stretch", "jump"
        )
    }
}
