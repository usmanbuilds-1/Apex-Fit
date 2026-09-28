package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.HomeViewModel
import com.apexfit.app.ui.components.IndustrialCard
import com.apexfit.app.ui.components.MuscleSegment
import com.apexfit.app.ui.components.ReadinessPetalDial
import com.apexfit.app.ui.components.TactileButton
import com.apexfit.app.ui.theme.CyberTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel? = null,
    onNavigateToTrain: () -> Unit = {},
    onNavigateToProgress: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val muscleTelemetry = remember {
        listOf(
            MuscleSegment("Chest", 0.92f),
            MuscleSegment("Shoulders", 0.95f),
            MuscleSegment("Triceps", 0.94f),
            MuscleSegment("Lats", 0.88f),
            MuscleSegment("Biceps", 0.85f),
            MuscleSegment("Quads", 0.35f),
            MuscleSegment("Hamstrings", 0.40f),
            MuscleSegment("Abs", 0.90f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberTheme.ChassisBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "APEX // FIT",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = CyberTheme.TextPrimary
                )
                Text(
                    text = "INSTRUMENT CONSOLE // V2.6",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .background(CyberTheme.RecessedPit)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "STATUS : SYS.OK",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.AcidLime
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        IndustrialCard(
            moduleCode = "SYS_MOD // 01",
            title = "Physiological Readiness Core",
            accentColor = CyberTheme.AcidLime
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                ReadinessPetalDial(
                    segments = muscleTelemetry,
                    overallReadiness = 88
                )
            }

            Text(
                text = "INDEX ANALYSIS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTheme.AcidLime
            )
            Text(
                text = "Upper Push complex is 92%+ recovered. Quadriceps remain in active recovery (35%).",
                fontSize = 13.sp,
                color = CyberTheme.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        IndustrialCard(
            moduleCode = "SYS_MOD // 02",
            title = "Assigned Microcycle Target",
            accentColor = CyberTheme.ApexOrange
        ) {
            Text(
                text = "PROTOCOL: UPPER PUSH HYPERTROPHY",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTheme.TextPrimary
            )
            Text(
                text = "5 Exercises  •  18 Total Sets  •  Est: 52 Minutes",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = CyberTheme.TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            TactileButton(
                text = "ENGAGE PROTOCOL",
                subText = "Initialize Active Session Tracker",
                accentColor = CyberTheme.ApexOrange,
                onClick = onNavigateToTrain
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        IndustrialCard(
            moduleCode = "SYS_MOD // 03",
            title = "Weekly Microcycle Register",
            accentColor = CyberTheme.TelemetryCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    "M" to true,
                    "T" to true,
                    "W" to false,
                    "T" to true,
                    "F" to false,
                    "S" to false,
                    "S" to false
                ).forEach { (day, completed) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CyberTheme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (completed) CyberTheme.TelemetryCyan else CyberTheme.RecessedPit
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Text(
                                    text = "■",
                                    fontSize = 12.sp,
                                    color = CyberTheme.ChassisBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}