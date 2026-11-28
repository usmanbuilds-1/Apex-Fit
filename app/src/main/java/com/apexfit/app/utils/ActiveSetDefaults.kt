package com.apexfit.app.utils

import com.apexfit.app.data.ActiveSet

/** Copy a load/rep suggestion, never copy an observation of effort, completion or rest. */
object ActiveSetDefaults {
    fun extraSet(existing: List<ActiveSet>): ActiveSet {
        val previous = existing.lastOrNull()
        return ActiveSet(
            setNumber = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1,
            weight = previous?.weight ?: 50.0,
            reps = previous?.reps ?: 10,
            weightUnit = previous?.weightUnit ?: "kg",
            rpe = 0
        )
    }
}
