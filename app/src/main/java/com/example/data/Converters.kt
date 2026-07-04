package com.example.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val list = mutableListOf<String>()
        val jsonArray = JSONArray(value)
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
}
