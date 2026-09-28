package com.apexfit.app.utils

import kotlin.math.roundToLong

object PlateCalculatorEngine {

    val DEFAULT_METRIC_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
    val DEFAULT_IMPERIAL_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)

    data class PlateBreakdown(
        val barWeight: Double,
        val targetWeight: Double,
        val achievedWeight: Double,
        val platesPerSide: List<Double>,
        val remainingDifference: Double,
        val isExact: Boolean
    )

    fun calculatePlatesPerSide(
        targetWeight: Double,
        barWeight: Double = 20.0,
        availablePlates: List<Double> = DEFAULT_METRIC_PLATES,
        collarWeight: Double = 0.0
    ): PlateBreakdown {
        val baseTareWeight = barWeight + collarWeight
        if (targetWeight <= baseTareWeight) {
            return PlateBreakdown(
                barWeight = barWeight,
                targetWeight = targetWeight,
                achievedWeight = baseTareWeight,
                platesPerSide = emptyList(),
                remainingDifference = targetWeight - baseTareWeight,
                isExact = (targetWeight == baseTareWeight)
            )
        }

        val targetGrams = (targetWeight * 1000.0).roundToLong()
        val tareGrams = (baseTareWeight * 1000.0).roundToLong()
        val totalPlateLoadGrams = targetGrams - tareGrams

        var sideLoadGramsNeeded = totalPlateLoadGrams / 2L

        val sortedPlatesGrams = availablePlates
            .map { (it * 1000.0).roundToLong() }
            .sortedDescending()

        val sidePlatesGrams = mutableListOf<Long>()

        for (plateGrams in sortedPlatesGrams) {
            if (plateGrams <= 0L) continue
            while (sideLoadGramsNeeded >= plateGrams) {
                sidePlatesGrams.add(plateGrams)
                sideLoadGramsNeeded -= plateGrams
            }
        }

        val totalAchievedGrams = tareGrams + (sidePlatesGrams.sum() * 2L)
        val achievedWeight = totalAchievedGrams / 1000.0
        val remainingDiff = targetWeight - achievedWeight

        return PlateBreakdown(
            barWeight = barWeight,
            targetWeight = targetWeight,
            achievedWeight = achievedWeight,
            platesPerSide = sidePlatesGrams.map { it / 1000.0 },
            remainingDifference = remainingDiff,
            isExact = (totalAchievedGrams == targetGrams)
        )
    }
}