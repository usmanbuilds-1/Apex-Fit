package com.apexfit.app.utils

fun exerciseNameToSlug(name: String): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
