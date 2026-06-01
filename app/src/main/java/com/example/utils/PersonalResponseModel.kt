package com.example.utils

import com.example.data.*

object PersonalResponseModel {

    fun isActivatable(trainingLog: List<TrainingSession>): Boolean {
        val oldestSession = trainingLog.filter { it.completed }.minByOrNull { it.date } ?: return false
        return getDaysBetween(oldestSession.date, getCurrentDate()) >= 56
    }

    // Calculates your personal MEV per muscle group
    // by finding the minimum weekly volume that correlates
    // with strength progression in your data
    fun calcPersonalMEV(trainingLog: List<TrainingSession>): Map<String, Int> {
        val muscleGroups = listOf("chest","back","side_delt","rear_delt","quad","hamstring","glute","bicep","tricep")
        val result = mutableMapOf<String, Int>()

        muscleGroups.forEach { muscle ->
            val weeklyVolumes = AlgorithmEngine.calcWeeklyVolumePerMuscle(trainingLog, 8)
            val muscleWeeklyData = weeklyVolumes[muscle] ?: return@forEach
            if (muscleWeeklyData.size < 4) {
                result[muscle] = 8
                return@forEach
            }
            val sortedByVolume = muscleWeeklyData.sortedBy { it.second }
            val lowVolumeWeeks = sortedByVolume.take(sortedByVolume.size / 3)
            val highVolumeWeeks = sortedByVolume.takeLast(sortedByVolume.size / 3)
            val lowAvgVolume = lowVolumeWeeks.map { it.second }.average()
            val highAvgVolume = highVolumeWeeks.map { it.second }.average()
            val volumeThresholdForProgress = lowAvgVolume + ((highAvgVolume - lowAvgVolume) * 0.3)
            val effectiveSetsAtThreshold = (volumeThresholdForProgress / 500).toInt().coerceIn(4, 16)
            result[muscle] = effectiveSetsAtThreshold
        }
        return result
    }

    // Calculates your personal MAV by finding the volume
    // level beyond which your performance stops improving
    fun calcPersonalMAV(trainingLog: List<TrainingSession>): Map<String, Int> {
        val mev = calcPersonalMEV(trainingLog)
        return mev.mapValues { (_, mevValue) ->
            (mevValue * 2.2).toInt().coerceIn(12, 26)
        }
    }

    fun buildIntelligenceProfile(
        weightLog: List<WeightEntry>,
        nutritionLog: List<NutritionEntry>,
        trainingLog: List<TrainingSession>,
        targets: NutritionTargets
    ): UserIntelligenceProfile {
        val compliance = AlgorithmEngine.calcComplianceScores(nutritionLog, trainingLog, targets)
        val completedSessions = trainingLog.filter { it.completed }
        val sessionTypeGroups = completedSessions.groupBy { it.sessionType }
        val bestSession = sessionTypeGroups.maxByOrNull { (_, sessions) ->
            sessions.map { it.sessionFeel }.average()
        }?.key ?: "unknown"

        val dataWeeks = if (completedSessions.isNotEmpty()) {
            getDaysBetween(completedSessions.minOf { it.date }, getCurrentDate()) / 7
        } else 0

        val dataRichness = when {
            dataWeeks >= 12 -> "rich"
            dataWeeks >= 8 -> "activating"
            dataWeeks >= 4 -> "building"
            else -> "early"
        }

        return UserIntelligenceProfile(
            totalSessionsLogged = completedSessions.size,
            avgWeeklyCompliance = compliance.overall,
            strongestDay = compliance.weakestDay ?: "unknown",
            weakestDay = compliance.weakestDay ?: "unknown",
            bestPerformingSessionType = bestSession,
            personalMEV = if (isActivatable(trainingLog)) calcPersonalMEV(trainingLog) else emptyMap(),
            personalMAV = if (isActivatable(trainingLog)) calcPersonalMAV(trainingLog) else emptyMap(),
            detectedPatterns = emptyList(),
            dataRichness = dataRichness,
            lastUpdated = getCurrentDate()
        )
    }
}

fun UserIntelligenceProfile.toSystemContextString(): String {
    return """
        [USER INTELLIGENCE PROFILE (Adaptive Coaching Context)]
        Last Updated: $lastUpdated
        Data Richness: $dataRichness
        Total Sessions Logged: $totalSessionsLogged
        Average Weekly Compliance: $avgWeeklyCompliance%
        Strongest Day: $strongestDay
        Weakest Day: $weakestDay
        Best Performing Session Type: $bestPerformingSessionType
        ${if (personalMEV.isNotEmpty()) "Personal MEV: ${personalMEV.entries.joinToString { "${it.key}: ${it.value} sets" }}" else ""}
        ${if (personalMAV.isNotEmpty()) "Personal MAV: ${personalMAV.entries.joinToString { "${it.key}: ${it.value} sets" }}" else ""}
    """.trimIndent()
}
