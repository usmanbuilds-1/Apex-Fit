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
        require(targetWeight.isFinite() && targetWeight >= 0.0) {
            "targetWeight must be finite and >= 0, got $targetWeight"
        }
        require(barWeight.isFinite() && barWeight >= 0.0) {
            "barWeight must be finite and >= 0, got $barWeight"
        }
        require(collarWeight.isFinite() && collarWeight >= 0.0) {
            "collarWeight must be finite and >= 0, got $collarWeight"
        }

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
        val sideLoadGramsNeeded = totalPlateLoadGrams / 2L

        val plateGrams = availablePlates
            .filter { it.isFinite() && it > 0.0 }
            .map { (it * 1000.0).roundToLong() }
            .filter { it > 0L }
            .distinct()
            .sortedDescending()

        if (plateGrams.isEmpty() || sideLoadGramsNeeded <= 0L) {
            return PlateBreakdown(
                barWeight = barWeight,
                targetWeight = targetWeight,
                achievedWeight = baseTareWeight,
                platesPerSide = emptyList(),
                remainingDifference = targetWeight - baseTareWeight,
                isExact = targetGrams == tareGrams
            )
        }

        // Greedy coin change is not correct for arbitrary plate sets
        // (6 kg/side with 4 kg and 3 kg plates needs 3+3, not a lone 4).
        // Solve the unbounded closest-load problem in plate-GCD units and prefer
        // fewer plates when the same load is reachable in multiple ways.
        fun gcd(a: Long, b: Long): Long {
            var x = kotlin.math.abs(a)
            var y = kotlin.math.abs(b)
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }

        val unitGrams = plateGrams.reduce { a, b -> gcd(a, b) }.coerceAtLeast(1L)
        val capacityUnitsLong = sideLoadGramsNeeded / unitGrams
        require(capacityUnitsLong <= 1_000_000L) {
            "Target/plate granularity is too large to calculate safely"
        }
        val capacityUnits = capacityUnitsLong.toInt()
        val plateUnits = plateGrams.map { (it / unitGrams).toInt() }

        val unreachable = Int.MAX_VALUE / 4
        val minPlateCount = IntArray(capacityUnits + 1) { unreachable }
        val previousPlateIndex = IntArray(capacityUnits + 1) { -1 }
        minPlateCount[0] = 0

        for (load in 1..capacityUnits) {
            for (index in plateUnits.indices) {
                val plate = plateUnits[index]
                if (plate <= load && minPlateCount[load - plate] != unreachable) {
                    val candidateCount = minPlateCount[load - plate] + 1
                    if (candidateCount < minPlateCount[load]) {
                        minPlateCount[load] = candidateCount
                        previousPlateIndex[load] = index
                    }
                }
            }
        }

        var bestUnits = capacityUnits
        while (bestUnits > 0 && minPlateCount[bestUnits] == unreachable) bestUnits--

        val sidePlatesGrams = mutableListOf<Long>()
        var remainingUnits = bestUnits
        while (remainingUnits > 0) {
            val plateIndex = previousPlateIndex[remainingUnits]
            if (plateIndex < 0) break
            sidePlatesGrams += plateGrams[plateIndex]
            remainingUnits -= plateUnits[plateIndex]
        }
        sidePlatesGrams.sortDescending()

        val totalAchievedGrams = tareGrams + (sidePlatesGrams.sum() * 2L)
        val achievedWeight = totalAchievedGrams / 1000.0
        val remainingDiff = targetWeight - achievedWeight

        return PlateBreakdown(
            barWeight = barWeight,
            targetWeight = targetWeight,
            achievedWeight = achievedWeight,
            platesPerSide = sidePlatesGrams.map { it / 1000.0 },
            remainingDifference = remainingDiff,
            isExact = totalAchievedGrams == targetGrams
        )
    }
}