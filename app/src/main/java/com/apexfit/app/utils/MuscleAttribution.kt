package com.apexfit.app.utils

import com.google.gson.Gson
import com.google.gson.JsonParser

/** A frozen catalog attribution, not a physiological measurement. Null means not captured. */
object MuscleAttribution {
    data class Resolved(val secondary: List<String>, val source: Source)
    enum class Source { CAPTURED, LEGACY_INFERRED, UNKNOWN }

    private fun canonical(primary: String, secondary: List<String>): List<String> =
        secondary.map(MuscleAliases::getCanonical).filter { it.isNotBlank() && it != MuscleAliases.getCanonical(primary) }
            .distinct().sorted()

    fun encode(primary: String, secondary: List<String>): String = Gson().toJson(canonical(primary, secondary))

    fun decode(raw: String?): List<String>? = raw?.let { value ->
        runCatching {
            require(value.length <= 16384)
            val array = JsonParser.parseString(value).asJsonArray
            require(array.size() <= 128)
            array.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString.also { label -> require(label.isNotBlank() && label.length <= 128) }
            }.also { require(it == canonical("", it)) }
        }.getOrNull()
    }

    fun resolve(primary: String, snapshot: String?, fallback: List<String>?): Resolved {
        val captured = decode(snapshot)
        return when {
            captured != null -> Resolved(canonical(primary, captured), Source.CAPTURED)
            snapshot != null || fallback == null -> Resolved(emptyList(), Source.UNKNOWN)
            else -> Resolved(canonical(primary, fallback), Source.LEGACY_INFERRED)
        }
    }
}
