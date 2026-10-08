package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.HeatmapEntry
import com.apexfit.app.ui.components.MuscleHeatmapCanvas
import com.apexfit.app.ui.theme.*
import com.apexfit.app.utils.FormGuideContent

@Composable
fun FormGuideSheet(
    exercise: Exercise,
    content: FormGuideContent,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.name,
                        fontFamily = SyneFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                    }
                }

                // Heatmap Data Preparation
                val heatmap = remember(exercise) {
                    buildMap {
                        this[exercise.primaryMuscle.lowercase()] = HeatmapEntry(
                            volume = 100,
                            intensity = 80,
                            level = "high",
                            colorHex = "#E84040"
                        )
                        exercise.secondaryMuscles.forEach { secondary ->
                            this[secondary.lowercase()] = HeatmapEntry(
                                volume = 50,
                                intensity = 40,
                                level = "medium",
                                colorHex = "#E8A020"
                            )
                        }
                    }
                }

                MuscleHeatmapCanvas(
                    heatmap = heatmap,
                    modifier = Modifier.height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FormGuideSection("SETUP", content.setup)
                    FormGuideSection("EXECUTION", content.execution)
                    FormGuideSection("COMMON MISTAKES", content.commonMistake)
                    FormGuideSection("MUSCLES", content.muscleNote)
                }
            }
        }
    }
}

@Composable
fun FormGuideOverlay(
    exercise: Exercise,
    content: FormGuideContent,
    onDismiss: () -> Unit
) {
    FormGuideSheet(
        exercise = exercise,
        content = content,
        onDismiss = onDismiss
    )
}

@Composable
fun FormGuideSection(title: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontFamily = SyneFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AmberAccent
        )
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            color = SecondaryText,
            lineHeight = 16.sp
        )
    }
}
