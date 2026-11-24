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

    fun expand(lines: List<String>, normalize: (String) -> String): List<String> {
        val output = mutableListOf<String>()
        var table: Table? = null
        var activeDay: String? = null
        val customDays = linkedMapOf<String, Int>()
        for (raw in lines) {
            val line = normalize(raw.replace(Regex("""\s+[•●▪]\s+"""), "; "))
            if (line.isBlank()) {
                output += ""
                table = null
                activeDay = null
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
