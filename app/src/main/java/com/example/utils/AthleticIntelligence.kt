package com.example.utils

import com.example.domain.model.DomainAlert

object AthleticIntelligence {
    fun generateAlerts(
        sessions: List<com.example.utils.TrainingSession>,
        fatigue: com.example.utils.FatigueResult,
        plateau: com.example.utils.PlateauResult,
        compliance: com.example.utils.ComplianceResult,
        deload: com.example.utils.DeloadResult,
        injuries: List<String>,
        readiness: com.example.utils.SessionReadiness?
    ): List<DomainAlert> {
        val alerts = mutableListOf<DomainAlert>()
        
        // Base condition: Alerts show when you have session data
        if (sessions.isEmpty()) {
            return emptyList()
        }

        // 1. ACR > 1.5
        fatigue.ratio?.let { ratio ->
            if (ratio > 1.5) {
                alerts.add(
                    DomainAlert(
                        id = "acr_critical",
                        title = "CRITICAL WORKLOAD SPIKE",
                        message = "Your Acute-to-Chronic ratio is %.2f (Threshold > 1.50). Injury risk has increased significantly.".format(ratio),
                        severity = "critical",
                        actionable = true,
                        suggestedAction = "Cap intensity at RPE 7, reduce working sets by 50%, or proceed to an active deload.",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // 2. Plateau detected
        if (plateau.plateau) {
            alerts.add(
                DomainAlert(
                    id = "plateau_alert",
                    title = "TRAINING PLATEAU DETECTED",
                    message = "Systemic stagnation identified across key movements: ${plateau.severity.uppercase()} plateau in progress.",
                    severity = "warning",
                    actionable = true,
                    suggestedAction = "Initiate progression reset: drop working weights by 10% and focus on velocity.",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // 3. Low Compliance (< 75%)
        if (compliance.overall < 75) {
            alerts.add(
                DomainAlert(
                    id = "compliance_low",
                    title = "METABOLIC COMPLIANCE DROP",
                    message = "Weekly lifestyle compliance has fallen to ${compliance.overall}%. ${compliance.weakestDay?.let { "Weakest day identified: $it." } ?: "Struggles with hitting intake metrics."}",
                    severity = "warning",
                    actionable = true,
                    suggestedAction = "Maintain core meal plan consistency and log all missed nutritional tracking days.",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // 4. Deload Due
        if (deload.urgency.equals("high", ignoreCase = true) || deload.urgency.equals("critical", ignoreCase = true)) {
            alerts.add(
                DomainAlert(
                    id = "deload_due",
                    title = "SYSTEMIC DELOAD REQUIRED",
                    message = "Deload status set to HIGH urgency (${deload.signals} overreaching markers triggered). Systemic fatigue is critical.",
                    severity = "critical",
                    actionable = true,
                    suggestedAction = "Execute default active deload protocol: reduced volume / lower intensity for 1 week.",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // 5. Injury risk signals
        if (injuries.isNotEmpty()) {
            alerts.add(
                DomainAlert(
                    id = "injury_risk_signals",
                    title = "INJURY RISK INDICATORS DETECTED",
                    message = "Biomechanical overload detected: ${injuries.joinToString(", ")}.",
                    severity = "critical",
                    actionable = true,
                    suggestedAction = "Avoid movements that cause discomfort. Ensure 48 hours complete rest between heavy session types.",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // 6. Low Readiness (< 60)
        readiness?.let { r ->
            if (r.score < 60) {
                alerts.add(
                    DomainAlert(
                        id = "readiness_low",
                        title = "LOW SESSION READINESS",
                        message = "Session readiness is compromised (${r.score}/100 - ${r.label}). ${r.prediction}",
                        severity = "warning",
                        actionable = true,
                        suggestedAction = "Limit loading, extend warmup periods to 15+ minutes, and respect RPE constraints.",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return alerts
    }
}
