package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SyneFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OrangeAccent

@Composable
fun PlateCalculatorCard(
    initialWeight: Double,
    units: String = "kg",
    modifier: Modifier = Modifier
) {
    var targetWeightStr by remember(initialWeight) { mutableStateOf(initialWeight.toString()) }
    var targetWeight by remember(initialWeight) { mutableStateOf(initialWeight) }

    val isKg = units.lowercase() == "kg"
    val barWeight = if (isKg) 20.0 else 45.0
    val presets = if (isKg) listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25) else listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    val suffix = if (isKg) "kg" else "lb"
    val presetValues = if (isKg) listOf(40.0, 60.0, 80.0, 100.0) else listOf(95.0, 135.0, 185.0, 225.0)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "BARBELL PLATE CALCULATOR",
                fontFamily = SyneFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OrangeAccent,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = targetWeightStr,
                    onValueChange = { input ->
                        val normalized = input.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.')
                        targetWeightStr = normalized
                        targetWeight = normalized.toDoubleOrNull() ?: barWeight
                    },
                    label = { Text(stringResource(R.string.plate_calc_weight_target, suffix), color = Color(0xFF8A8A9A), fontSize = 11.sp) },
                    textStyle = TextStyle(color = Color(0xFFF0F0F5), fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.4f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F0F1A),
                        unfocusedContainerColor = Color(0xFF0F0F1A),
                        focusedIndicatorColor = OrangeAccent,
                        unfocusedIndicatorColor = Color(0xFF131324)
                    )
                )

                // Presets
                Row(
                    modifier = Modifier.weight(0.6f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetValues.forEach { preset ->
                        Button(
                            onClick = {
                                targetWeight = preset
                                targetWeightStr = preset.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0F1A)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(stringResource(R.string.plate_calc_fmt_str, preset.toInt(), suffix), color = Color(0xFFF0F0F5), fontSize = 11.sp, fontFamily = JetBrainsMonoFamily)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Plate calculation logic
            val remains = targetWeight - barWeight
            val sideWeight = if (remains > 0) remains / 2.0 else 0.0

            val plateTypes = if (isKg) listOf(
                PlateDesignInfo(25.0, Color(0xFFEF4444), "25"),   // Red
                PlateDesignInfo(20.0, Color(0xFF3B82F6), "20"),   // Blue
                PlateDesignInfo(15.0, Color(0xFFFBBF24), "15"),   // Yellow
                PlateDesignInfo(10.0, Color(0xFF10B981), "10"),   // Green
                PlateDesignInfo(5.0, Color(0xFFF3F4F6), "5", textColor = Color.Black), // White
                PlateDesignInfo(2.5, Color(0xFF1F2937), "2.5"),   // Black
                PlateDesignInfo(1.25, Color(0xFF9CA3AF), "1.25", textColor = Color.Black) // Grey
            ) else listOf(
                PlateDesignInfo(45.0, Color(0xFFEF4444), "45"),   // Red
                PlateDesignInfo(35.0, Color(0xFF3B82F6), "35"),   // Blue
                PlateDesignInfo(25.0, Color(0xFFFBBF24), "25"),   // Yellow
                PlateDesignInfo(10.0, Color(0xFF10B981), "10"),   // Green
                PlateDesignInfo(5.0, Color(0xFFF3F4F6), "5", textColor = Color.Black), // White
                PlateDesignInfo(2.5, Color(0xFF1F2937), "2.5")    // Black
            )

            val loadedPlates = mutableListOf<PlateDesignInfo>()
            var temp = sideWeight
            while (temp >= (if (isKg) 1.25 else 2.5)) {
                val matched = plateTypes.firstOrNull { it.weight <= temp }
                if (matched != null) {
                    loadedPlates.add(matched)
                    temp -= matched.weight
                } else {
                    break
                }
            }

            Text(
                text = "Plates per side: (Barbell: $barWeight$suffix • Load/side: ${String.format(java.util.Locale.US, "%.2f", sideWeight)}$suffix)",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Color(0xFF8A8A9A),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (loadedPlates.isEmpty()) {
                Text(
                    text = "No plates to load (Empty barbell or below)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Color(0xFF8A8A9A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Color(0xFF0F0F1A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Core barbell sleeve
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(8.dp)
                            .background(Color.Gray)
                    )
                    // Barbell collar
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(32.dp)
                            .background(Color.DarkGray)
                    )

                    // Color coded plates
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        loadedPlates.forEach { plate ->
                            val heightFactor = when (plate.weight) {
                                25.0 -> 0.90f
                                20.0 -> 0.82f
                                15.0 -> 0.74f
                                10.0 -> 0.66f
                                5.0 -> 0.58f
                                2.5 -> 0.50f
                                else -> 0.42f
                            }

                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .fillMaxHeight(heightFactor)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(plate.color)
                                    .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = plate.label,
                                    color = plate.textColor,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
 
                    // Remaining sleeve extension
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.4f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    plateTypes.forEach { plate ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(plate.color)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${plate.label}$suffix",
                                fontSize = 11.sp,
                                color = Color(0xFF8A8A9A),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PlateDesignInfo(
    val weight: Double,
    val color: Color,
    val label: String,
    val textColor: Color = Color.White
)
