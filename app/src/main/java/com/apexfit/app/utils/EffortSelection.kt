package com.apexfit.app.utils

/** UI selection only; unrated is not a numerical RIR measurement. */
object EffortSelection {
    const val UNRATED = -1
    fun isRated(selection: Int): Boolean = selection in 0..5
    fun actualRir(selection: Int): Int? = selection.takeIf(::isRated)
    fun rpe(selection: Int): Int =
        actualRir(selection)?.let(ProgressionEngine::calculateRPEFromRIR) ?: 0
}
