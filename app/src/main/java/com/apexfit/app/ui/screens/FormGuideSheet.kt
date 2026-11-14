package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.apexfit.app.ui.components.ExerciseMediaPanel
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
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            fontFamily = SyneFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${exercise.primaryMuscle} · ${exercise.equipmentRequired}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                    }
                }

                ExerciseMediaPanel(exerciseId = exercise.id)

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (content.instructions.isNotEmpty()) {
                        FormGuideInstructions(content.instructions)
                    } else {
                        FormGuideSection("SETUP", content.setup)
                        FormGuideSection("EXECUTION", content.execution)
                    }
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
private fun FormGuideInstructions(instructions: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "INSTRUCTIONS",
            fontFamily = SyneFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AmberAccent
        )
        instructions.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ApexColors.Primary
                )
                Text(
                    text = step,
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = SecondaryText,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontFamily = InterFamily,
            fontSize = 13.sp,
            color = SecondaryText,
            lineHeight = 19.sp
        )
    }
}
