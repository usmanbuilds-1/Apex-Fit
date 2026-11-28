package com.apexfit.app.utils

fun Double.toDisplayWeight(preferredUnit: String): Double {
    return if (preferredUnit.lowercase() in listOf("lb", "lbs")) {
        Math.round(this * AppConstants.KG_TO_LBS * 10.0) / 10.0
    } else {
        Math.round(this * 10.0) / 10.0
    }
}

fun Double.fromDisplayWeightToKg(preferredUnit: String): Double {
    return if (preferredUnit.lowercase() in listOf("lb", "lbs")) this / AppConstants.KG_TO_LBS
    else this
}

/** Unrounded conversion for editable/logged load values; display formatting must not change the recorded load. */
fun Double.toEditableWeight(preferredUnit: String): Double =
    if (preferredUnit.lowercase() in listOf("lb", "lbs")) this * AppConstants.KG_TO_LBS else this
