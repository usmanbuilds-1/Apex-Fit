package com.apexfit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.PersonalRecord
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.ui.components.ExerciseMediaPanel
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.FormGuideContent
import com.apexfit.app.utils.toDisplayWeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FormGuideSheet(
    exercise: Exercise,
    content: FormGuideContent,
    preferredUnits: String = "kg",
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    FormGuideScreenContent(
        exercise = exercise,
        content = content,
        preferredUnits = preferredUnits,
        onBack = onDismiss,
        mediaContent = { ExerciseMediaPanel(exerciseId = exercise.id) }
    )
}

@Composable
internal fun FormGuideScreenContent(
    exercise: Exercise,
    content: FormGuideContent,
    preferredUnits: String,
    onBack: () -> Unit,
    mediaContent: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(ApexColors.Canvas).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, color = ApexColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text("${exercise.primaryMuscle} · ${exercise.equipmentRequired}",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        content.prescription?.let { prescription ->
            FormGuidePrescription(prescription, preferredUnits)
            HorizontalDivider(color = ApexColors.BorderSubtle)
        }
        Text("Form reference", color = ApexColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
        if (content.instructions.isNotEmpty()) {
            content.instructions.forEachIndexed { index, instruction ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}.", color = ApexColors.Primary, style = MaterialTheme.typography.bodyMedium)
                    Text(instruction, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text("Written instructions aren’t available for this exercise.", color = ApexColors.TextSecondary)
            content.sourceNote?.let { FormGuideSection("Notes", it) }
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        FormGuideSection("Muscles", content.muscleNote)
        mediaContent()
        val maxWeightRecord = content.personalRecords
            .filter { it.type == "max_weight" }
            .maxByOrNull { it.value }
        if (maxWeightRecord != null || content.recentHistory.isNotEmpty()) {
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Text("Recent performance", color = ApexColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge)
            maxWeightRecord?.let { FormGuidePersonalRecord(it, preferredUnits) }
            content.recentHistory.groupBy { it.sessionId }.values.take(3).forEach { sessionSets ->
                FormGuideHistoryEntry(sessionSets, preferredUnits)
            }
        }
        if (content.alternativeExercises.isNotEmpty()) {
            HorizontalDivider(color = ApexColors.BorderSubtle)
            Text("Alternatives", color = ApexColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge)
            Text("Other exercises that train ${exercise.primaryMuscle}; equipment is shown for comparison.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            content.alternativeExercises.forEachIndexed { index, alternative ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(alternative.name, color = ApexColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium)
                    Text("${alternative.primaryMuscle} · ${alternative.equipmentRequired}",
                        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (index != content.alternativeExercises.lastIndex) {
                    HorizontalDivider(color = ApexColors.BorderSubtle)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FormGuideOverlay(
    exercise: Exercise,
    content: FormGuideContent,
    preferredUnits: String = "kg",
    onDismiss: () -> Unit
) {
    FormGuideSheet(exercise, content, preferredUnits, onDismiss)
}

@Composable
private fun FormGuidePrescription(prescription: PlanExercise, preferredUnits: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Program prescription", color = ApexColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge)
        val repTarget = if (prescription.repsMin == prescription.repsMax) {
            "${prescription.repsMin} reps"
        } else {
            "${prescription.repsMin}–${prescription.repsMax} reps"
        }
        Text("${prescription.sets} sets · $repTarget · ${prescription.restSeconds}s rest",
            color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        val loadDescription = if (prescription.weight > 0.0) {
            "Planned load · ${formatGuideWeight(prescription.weight, preferredUnits)} $preferredUnits"
        } else {
            "Load · Calibrate a comfortable starting weight on first exposure"
        }
        Text(loadDescription, color = ApexColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium)
        if (prescription.sets > 0 && prescription.repsMax > 0) {
            Text("Progression target · Reach ${prescription.repsMax} reps on all ${prescription.sets} sets at RPE 8 or lower.",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        prescription.notes.trim().takeIf { it.isNotEmpty() }?.let {
            FormGuideSection("Plan note", it)
        }
    }
}

@Composable
private fun FormGuidePersonalRecord(record: PersonalRecord, preferredUnits: String) {
    val date = formatGuideDate(record.date)
    Text("Personal record · ${formatGuideWeight(record.value, preferredUnits)} $preferredUnits${date?.let { " · $it" }.orEmpty()}",
        color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FormGuideHistoryEntry(
    sets: List<com.apexfit.app.data.LastSetWithDate>,
    preferredUnits: String
) {
    val first = sets.firstOrNull() ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatGuideDate(first.date) ?: first.date,
            color = ApexColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
        sets.joinToString(" · ") { set ->
            val weight = "${formatGuideWeight(set.weight, preferredUnits)} $preferredUnits × ${set.reps}"
            if (set.rpe in 1..10) "$weight · RPE ${set.rpe}" else "$weight · RPE not rated"
        }.let { Text(it, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun formatGuideWeight(weightKg: Double, preferredUnits: String): String =
    String.format(Locale.getDefault(), "%.1f", weightKg.toDisplayWeight(preferredUnits))

private fun formatGuideDate(date: String): String? = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}.getOrNull()

@Composable
fun FormGuideSection(title: String, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ApexColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(text, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
