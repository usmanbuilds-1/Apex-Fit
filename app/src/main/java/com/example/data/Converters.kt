package com.example.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    /**
     * Serializes a List<String> to a JSON array string.
     * Never returns null — empty list produces "[]".
     */
    @TypeConverter
    fun fromStringList(value: List<String>): String = JSONArray(value).toString()

    /**
     * Deserializes a JSON array string to List<String>.
     * Returns emptyList for null input or malformed JSON.
     * Note: Room never passes null to this function (secondary_muscles is NOT NULL after migration 11),
     * but we handle it defensively.
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: org.json.JSONException) {
            android.util.Log.e("Converters", "Failed to parse JSON: $value", e)
            return emptyList()
        }
        return list
    }
}
