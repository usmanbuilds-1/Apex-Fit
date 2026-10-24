package com.apexfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.ui.theme.CyberTheme

@Composable
fun IndustrialCard(
    modifier: Modifier = Modifier,
    moduleCode: String,
    title: String,
    accentColor: Color = CyberTheme.ApexOrange,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberTheme.ModuleSurface, RoundedCornerShape(8.dp))
            .border(1.dp, CyberTheme.ReticleBorder, RoundedCornerShape(8.dp))
            .padding(1.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val bracketLength = 12.dp.toPx()
            val strokeW = 1.5f

            drawLine(CyberTheme.RimHighlight, Offset(0f, 0f), Offset(bracketLength, 0f), strokeW)
            drawLine(CyberTheme.RimHighlight, Offset(0f, 0f), Offset(0f, bracketLength), strokeW)
            drawLine(CyberTheme.RimHighlight, Offset(size.width - bracketLength, 0f), Offset(size.width, 0f), strokeW)
            drawLine(CyberTheme.RimHighlight, Offset(size.width, 0f), Offset(size.width, bracketLength), strokeW)
            drawLine(CyberTheme.ReticleBorder, Offset(0f, size.height), Offset(bracketLength, size.height), strokeW)
            drawLine(CyberTheme.ReticleBorder, Offset(0f, size.height - bracketLength), Offset(0f, size.height), strokeW)

            val stripeCount = 4
            val stripeSpacing = 6.dp.toPx()
            val startX = size.width - 40.dp.toPx()
            for (i in 0 until stripeCount) {
                val offset = i * stripeSpacing
                drawLine(
                    color = CyberTheme.TechnicalCrosshair.copy(alpha = 0.4f),
                    start = Offset(startX + offset, 6.dp.toPx()),
                    end = Offset(startX + offset - 8.dp.toPx(), 18.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = moduleCode.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "SPEC.26 // TELEMETRY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CyberTheme.TextTertiary
                )
            }

            Text(
                text = title.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = CyberTheme.TextPrimary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            content()
        }
    }
}