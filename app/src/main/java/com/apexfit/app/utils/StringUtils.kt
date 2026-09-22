package com.apexfit.app.utils

private val SLUG_REGEX = Regex("[^a-z0-9]+")

fun exerciseNameToSlug(name: String): String =
    name.lowercase()
        .replace(SLUG_REGEX, "-")
        .trim('-')

