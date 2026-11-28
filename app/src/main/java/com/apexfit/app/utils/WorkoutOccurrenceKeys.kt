package com.apexfit.app.utils

/** Allocate deterministic distinct keys without colliding with any catalog ID.
 * Unique exercises keep their existing keys for compatibility. Repeated exercises
 * get separate keys even if plan row IDs are zero or repeated.
 */
internal object WorkoutOccurrenceKeys {
    fun allocate(catalogIds: List<String>): List<String> {
        val counts = catalogIds.groupingBy { it }.eachCount()
        val reserved = catalogIds.toMutableSet()
        return catalogIds.mapIndexed { index, id ->
            if (counts.getValue(id) == 1) id else {
                var key = "occurrence:$index:$id"
                while (!reserved.add(key)) key = ":$key"
                key
            }
        }
    }
}
