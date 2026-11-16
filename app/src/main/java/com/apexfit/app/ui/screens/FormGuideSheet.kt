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
import com.apexfit.app.ui.components.ExerciseMediaPanel
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.FormGuideContent

@Composable
fun FormGuideSheet(exercise: Exercise, content: FormGuideContent, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Column(
        Modifier.fillMaxSize().background(ApexColors.Canvas).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onDismiss) { Text("Back") }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(exercise.name, color = ApexColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text("${exercise.primaryMuscle} · ${exercise.equipmentRequired}",
                color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        Text("Form reference", color = ApexColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
        if (content.instructions.isNotEmpty()) {
            content.instructions.forEachIndexed { index, instruction ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}.", color = ApexColors.Primary, style = MaterialTheme.typography.bodyMedium)
                    Text(instruction, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            FormGuideSection("Setup", content.setup)
            FormGuideSection("Execution", content.execution)
        }
        HorizontalDivider(color = ApexColors.BorderSubtle)
        FormGuideSection("Common mistakes", content.commonMistake)
        FormGuideSection("Muscles", content.muscleNote)
        ExerciseMediaPanel(exerciseId = exercise.id)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FormGuideOverlay(exercise: Exercise, content: FormGuideContent, onDismiss: () -> Unit) {
    FormGuideSheet(exercise, content, onDismiss)
}

@Composable
fun FormGuideSection(title: String, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ApexColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(text, color = ApexColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
