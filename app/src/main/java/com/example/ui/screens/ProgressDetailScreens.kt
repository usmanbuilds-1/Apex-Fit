package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.FitnessViewModel
import com.example.ProgressViewModel
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SyneFamily

@Composable
fun MuscleRecoveryScreen(fitnessViewModel: FitnessViewModel, progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Muscle Recovery Screen", color = PrimaryText, fontFamily = SyneFamily)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun BodyMeasurementDetailScreen(bodyPart: String, progressViewModel: ProgressViewModel, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Measurement Detail: $bodyPart", color = PrimaryText, fontFamily = SyneFamily)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
