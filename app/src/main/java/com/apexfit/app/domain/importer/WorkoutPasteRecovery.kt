package com.apexfit.app.domain.importer

/**
 * Conservative recovery of pasted layout. Only explicit table headers assign
 * unlabeled numeric columns; no guess that two bare numbers mean sets/reps.
 * The original paste is retained by the normalizer for review.
 */
internal object WorkoutPasteRecovery {
    private enum class Field { DAY, EXERCISE, SETS, REPS, PRESCRIPTION, LOAD, REST, RPE, RIR, NOTE, OTHER }
    private data class Column(val field: Field, val label: String)
    private data class Table(val delimiter: Char, val columns: List<Column>)

    // Some rich-text clipboard serializers emit a separate empty Markdown table
    // before EACH cell. Only reconstruct a run with explicit, recognizable headers.
    // Ordinary one-column tables and bare unlabeled numbers remain untouched.
    private val emptyCell = Regex("""^\s*\|\s*\|\s*$""")
    private val separatorCell = Regex("""^\s*\|\s*:?-+:?\s*\|\s*$""")
    private val scheduleWeek = Regex("""(?i)^week\s*[1-9]\d?$""")

    internal fun restoreFragmentedTables(lines: List<String>, normalize: (String) -> String): List<String> {
        val output = mutableListOf<String>()
        fun skipBlank(from: Int): Int {
            var i = from
            while (i < lines.size && lines[i].isBlank()) i++
            return i
        }
        fun cellAt(from: Int): Pair<String, Int>? {
            val first = skipBlank(from)
            if (first >= lines.size || !emptyCell.matches(lines[first])) return null
            val second = skipBlank(first + 1)
            if (second >= lines.size || !separatorCell.matches(lines[second])) return null
            val third = skipBlank(second + 1)
            if (third >= lines.size || lines[third].trimStart().startsWith("#") ||
                emptyCell.matches(lines[third]) || separatorCell.matches(lines[third])) return null
            return normalize(lines[third]) to (third + 1)
        }
        var i = 0
        while (i < lines.size) {
            val first = cellAt(i)
            if (first == null) { output += lines[i++]; continue }
            val values = mutableListOf(first.first)
            var cursor = first.second
            while (true) {
                val cell = cellAt(cursor) ?: break
                values += cell.first
                cursor = cell.second
            }
            val headers = values.takeWhile { value ->
                column(value).field != Field.OTHER || scheduleWeek.matches(value)
            }
            val isExerciseTable = headers.count { column(it).field == Field.EXERCISE } == 1 &&
                headers.any { column(it).field == Field.SETS } && headers.any { column(it).field == Field.REPS }
            val isSchedule = headers.firstOrNull()?.let { column(it).field == Field.DAY } == true &&
                headers.drop(1).isNotEmpty() && headers.drop(1).all { scheduleWeek.matches(it) }
            val width = headers.size
            if ((isExerciseTable || isSchedule) && width in 2..12 && values.size > width) {
                // Keep explicit column order; never shift a truncated final row.
                fun row(cells: List<String>): String = cells.joinToString(" | ", "| ", " |") { it.replace("|", " / ") }
                output += row(headers)
                output += row(List(width) { "---" })
                for (chunk in values.drop(width).chunked(width)) {
                    if (chunk.size == width) output += row(chunk)
                    else output += "Note: Incomplete pasted table row: " + chunk.joinToString(" / ")
                }
            } else {
                output += lines.subList(i, cursor)
            }
            i = cursor
        }
        return output
    }

    fun expand(lines: List<String>, normalize: (String) -> String): List<String> {
        val output = mutableListOf<String>()
        var table: Table? = null
        var activeDay: String? = null
        var scheduleColumns: List<String>? = null
        val customDays = linkedMapOf<String, Int>()
        for (raw in restoreFragmentedTables(lines, normalize)) {
            val line = normalize(raw.replace(Regex("""\s+[•●▪]\s+"""), "; "))
            if (line.isBlank()) {
                output += ""
                table = null
                activeDay = null
                scheduleColumns = null
                continue
            }
            val delimiter = when {
                '|' in raw -> '|'
                '\t' in raw -> '\t'
                ',' in raw -> ','
                else -> null
            }
            val cells = delimiter?.let { cells(raw, it).map(normalize) }
            if (cells != null && delimiter != null) {
                val columns = cells.map(::column)
                if (columns.firstOrNull()?.field == Field.DAY && cells.size >= 2 &&
                    cells.drop(1).all { scheduleWeek.matches(it) }) {
                    scheduleColumns = cells.drop(1)
                    table = null
                    continue
                }
                val schedule = scheduleColumns
                if (schedule != null) {
                    if (cells.all { it.matches(Regex("""[:\- ]*""")) }) continue
                    if (cells.size == schedule.size + 1) {
                        val weekday = WorkoutDayDetector.detect(cells.first())?.dayOfWeek
                        if (weekday != null) {
                            // Internal marker; consumed by the parser, never interpreted as exercise.
                            output += "Schedule row: " + (listOf(weekday) + schedule.zip(cells.drop(1)).map { (week, workout) -> "$week=$workout" }).joinToString(" | ")
                            continue
                        }
                    }
                    output += "Schedule unresolved: " + line
                    continue
                }
                if (columns.count { it.field == Field.EXERCISE } == 1 &&
                    columns.any { it.field in setOf(Field.SETS, Field.REPS, Field.PRESCRIPTION) }) {
                    table = Table(delimiter, columns)
                    activeDay = null
                    continue
                }
                val current = table
                if (current != null && current.delimiter == delimiter) {
                    if (cells.all { it.matches(Regex("""[:\- ]*""")) }) continue
                    if (cells.size == current.columns.size) {
                        fun value(field: Field): String = current.columns.indexOfFirst { it.field == field }
                            .let { if (it < 0) "" else cells[it] }
                        fun label(field: Field): String = current.columns.firstOrNull { it.field == field }?.label.orEmpty()
                        val exercise = value(Field.EXERCISE)
                        if (exercise.isNotBlank()) {
                            val day = value(Field.DAY)
                            if (day.isNotBlank() && day != activeDay) {
                                val heading = when {
                                    WorkoutDayDetector.detect(day) != null -> day
                                    day.matches(Regex("""\d{1,2}""")) -> "Day " + day
                                    day.matches(Regex("""(?i)[a-e]""")) -> "Workout " + day
                                    else -> "Day " + customDays.getOrPut(day.lowercase()) { customDays.size + 1 } + " " + day
                                }
                                output += heading
                                activeDay = day
                            }
                            val sets = value(Field.SETS)
                            val reps = value(Field.REPS)
                            val prescription = buildList {
                                if (sets.isNotBlank() && reps.isNotBlank() &&
                                    sets.matches(Regex("""\d{1,2}""")) &&
                                    reps.matches(Regex("""(?i)(?:\d{1,3}(?:\s*-\s*\d{1,3})?|amrap|failure|to failure)"""))) {
                                    add(sets + "x" + reps)
                                } else {
                                    if (sets.isNotBlank()) add("sets: " + sets)
                                    if (reps.isNotBlank()) add("reps: " + reps)
                                }
                                if (value(Field.PRESCRIPTION).isNotBlank()) add(value(Field.PRESCRIPTION))
                                if (value(Field.LOAD).isNotBlank()) {
                                    val load = value(Field.LOAD)
                                    val unit = when {
                                        "kg" in label(Field.LOAD) -> "kg"
                                        "lb" in label(Field.LOAD) -> "lb"
                                        else -> ""
                                    }
                                    add(if (unit.isNotEmpty() && load.matches(Regex("""\d+(?:\.\d+)?"""))) "$load $unit" else "load: " + load)
                                }
                                if (value(Field.REST).isNotBlank()) {
                                    val rest = value(Field.REST)
                                    val unit = when {
                                        "min" in label(Field.REST) -> "min"
                                        "sec" in label(Field.REST) -> "sec"
                                        else -> ""
                                    }
                                    add("rest: " + rest + if (unit.isNotEmpty() && rest.matches(Regex("""\d+(?:-\d+)?"""))) " " + unit else "")
                                }
                                if (value(Field.RPE).isNotBlank()) add("RPE " + value(Field.RPE))
                                if (value(Field.RIR).isNotBlank()) add("RIR " + value(Field.RIR))
                            }.joinToString(" ")
                            output += listOf(exercise, prescription).filter(String::isNotBlank).joinToString(" ")
                            val notes = current.columns.indices
                                .filter { current.columns[it].field in setOf(Field.NOTE, Field.OTHER) && cells[it].isNotBlank() }
                                .joinToString("; ") { current.columns[it].label + ": " + cells[it] }
                            if (notes.isNotBlank()) output += "Note: " + notes
                            continue
                        }
                    }
                    // Keep malformed rows visible in review instead of shifting columns.
                    output += "Note: Unread table row: " + line
                    continue
                }
                if (delimiter != ',' && cells.all { it.matches(Regex("""[:\- ]*""")) }) continue
                if (delimiter != ',' && cells.size >= 2) {
                    val joined = cells.joinToString(" ")
                    if (WorkoutExerciseLineParser.parse(joined) is ImportLineShape.FullExercise) {
                        output += joined
                        continue
                    }
                }
            }
            table = null
            activeDay = null
            scheduleColumns = null
            if (line.matches(Regex("""(?i)^(?:weekly|training|workout)\s+schedule\s*:?$"""))) {
                output += "Schedule heading: " + line
                continue
            }
            val colon = line.indexOf(':')
            val heading = if (colon > 0) line.substring(0, colon).trim() else ""
            val tail = if (colon > 0) line.substring(colon + 1).trim() else ""
            if (WorkoutDayDetector.detect(heading) != null && tail.isNotBlank() &&
                WorkoutExerciseLineParser.parse(tail) is ImportLineShape.FullExercise) {
                output += heading
                output += splitInline(tail)
            } else {
                output += splitInline(line)
            }
        }
        return output
    }

    private fun splitInline(line: String): List<String> {
        val delimiters = Regex("""[;,]\s*""").findAll(line).toList()
        if (delimiters.isEmpty()) return listOf(line)
        val first = line.substring(0, delimiters.first().range.first).trim()
        if (WorkoutExerciseLineParser.parse(first) !is ImportLineShape.FullExercise) return listOf(line)
        val result = mutableListOf(first)
        for ((index, delimiter) in delimiters.withIndex()) {
            val end = delimiters.getOrNull(index + 1)?.range?.first ?: line.length
            val text = line.substring(delimiter.range.last + 1, end).trim()
            val shape = WorkoutExerciseLineParser.parse(text)
            if (shape is ImportLineShape.FullExercise && shape.name.length <= 90) result += text
            else result[result.lastIndex] = result.last() + delimiter.value + text
        }
        return result
    }

    private fun cells(raw: String, delimiter: Char): List<String> {
        if (delimiter == '|') return raw.trim().removePrefix("|").removeSuffix("|").split('|')
        if (delimiter == '\t') return raw.split('\t')
        val values = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i++]
            when {
                ch == '"' && quoted && i < raw.length && raw[i] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { values += cell.toString(); cell.clear() }
                else -> cell.append(ch)
            }
        }
        values += cell.toString()
        return values
    }

    private fun column(raw: String): Column {
        val name = raw.lowercase().trim().replace(Regex("""[^a-z0-9 /]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        val field = when {
            name in setOf("day", "weekday", "session", "workout", "training day") -> Field.DAY
            name in setOf("exercise", "exercises", "exercise name", "movement", "name") -> Field.EXERCISE
            name in setOf("sets", "set", "working sets") -> Field.SETS
            name in setOf("reps", "rep", "repetitions", "rep range", "target reps") -> Field.REPS
            name in setOf("sets/reps", "sets / reps", "sets x reps", "prescription", "sets and reps") -> Field.PRESCRIPTION
            name.startsWith("weight") || name.startsWith("load") -> Field.LOAD
            name.startsWith("rest") -> Field.REST
            name == "rpe" -> Field.RPE
            name == "rir" -> Field.RIR
            name in setOf("note", "notes", "instructions", "comment", "comments") -> Field.NOTE
            else -> Field.OTHER
        }
        return Column(field, name)
    }
}
