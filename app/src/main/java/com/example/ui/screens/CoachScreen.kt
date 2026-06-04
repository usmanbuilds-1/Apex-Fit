package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FitnessViewModel
import com.example.CoachViewModel
import com.example.data.AlgorithmViewModel
import com.example.ui.theme.*

@Composable
fun CoachScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    coachViewModel: CoachViewModel,
    onNavigateTo: (Int) -> Unit
) {
    val messages by coachViewModel.chatMessages.collectAsStateWithLifecycle()
    val isCoachLoading by coachViewModel.isCoachLoading.collectAsStateWithLifecycle()
    val apiKey by coachViewModel.geminiApiKey.collectAsStateWithLifecycle()

    var inputMessage by remember { mutableStateOf("") }

    val lazyListState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Coach Profile head block
        PremiumCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = "Coach logo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "APEX ADAPTIVE AI COACH",
                        fontFamily = SyneFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = "Powered by Gemini Science Reasoning models",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = SecondaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (apiKey.isBlank()) {
            PremiumCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add your Gemini API key in Settings",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1)
                    )
                }
            }
        }

        // Chat text dialogue list vertical scrollable
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(lazyListState)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEach { msg ->
                val align = if (msg.isUser) Alignment.End else Alignment.Start
                val shape = if (msg.isUser) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
                val background = if (msg.isUser) Color(0xFF6366F1) else DarkCardSurface
                val borderStyle = if (msg.isUser) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, BorderSubtle)
                val textColor = if (msg.isUser) Color.White else PrimaryText

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(shape)
                            .background(background)
                            .border(borderStyle, shape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.message,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = textColor,
                            lineHeight = 16.sp
                        )
                    }
                    Text(
                        text = if (msg.isUser) "ATHLETE LOG" else "AI ADIVSOR FILE",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 8.sp,
                        color = MutedText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isCoachLoading) {
                Box(modifier = Modifier.padding(vertical = 4.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF6366F1))
                }
            }
        }

        // Quick Preset Prompts rows
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val promptChips = listOf(
                "Analyse my week",
                "Why is my weight stalling?",
                "Suggest deload target sets?",
                "What is my weakest muscle point?"
            )
            items(promptChips) { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkCardSurface)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                        .clickable {
                            inputMessage = chip
                            fitnessViewModel.sendCoachMessage(
                                text = chip,
                                coachContext = algorithmViewModel.buildCoachContext()
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(chip, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = SecondaryText)
                }
            }
        }

        // Send Text field row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Consult sports science model database...", fontSize = 11.sp, color = MutedText) },
                textStyle = TextStyle(color = PrimaryText, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach_chat_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkRaised,
                    unfocusedContainerColor = DarkRaised,
                    focusedIndicatorColor = Color(0xFF6366F1),
                    unfocusedIndicatorColor = BorderSubtle
                )
            )

            IconButton(
                onClick = {
                    if (inputMessage.trim().isNotEmpty()) {
                        fitnessViewModel.sendCoachMessage(
                            text = inputMessage,
                            coachContext = algorithmViewModel.buildCoachContext()
                        )
                        inputMessage = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF6366F1))
                    .testTag("coach_send_button")
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send Message", tint = Color.White)
            }
        }
    }
}
