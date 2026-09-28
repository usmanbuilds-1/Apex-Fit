package com.apexfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.ui.theme.CyberTheme
import kotlin.math.cos
import kotlin.math.sin

data class MuscleSegment(
    val name: String,
    val recoveryPercent: Float
)

@Composable
fun ReadinessPetalDial(
    modifier: Modifier = Modifier,
    segments: List<MuscleSegment>,
    overallReadiness: Int
) {
    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width * 0.45f
            val minRadius = size.width * 0.12f
            val count = segments.size
            val sweepAngleDeg = 360f / count

            segments.forEachIndexed { i, seg ->
                val startAngleDeg = i * sweepAngleDeg - 90f
                val startAngleRad = Math.toRadians(startAngleDeg.toDouble()).toFloat()
                val endAngleRad = Math.toRadians((startAngleDeg + sweepAngleDeg).toDouble()).toFloat()
                val targetRadius = minRadius + (maxRadius - minRadius) * seg.recoveryPercent.coerceIn(0.1f, 1.0f)

                val socketPath = Path().apply {
                    arcTo(
                        rect = Rect(center, maxRadius),
                        startAngleDegrees = startAngleDeg + 2f,
                        sweepAngleDegrees = sweepAngleDeg - 4f,
                        forceMoveTo = true
                    )
                    lineTo(center.x + minRadius * cos(endAngleRad), center.y + minRadius * sin(endAngleRad))
                    arcTo(
                        rect = Rect(center, minRadius),
                        startAngleDegrees = startAngleDeg + sweepAngleDeg - 2f,
                        sweepAngleDegrees = -(sweepAngleDeg - 4f),
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(socketPath, CyberTheme.ModuleSurfaceHigh, style = Fill)
                drawPath(socketPath, CyberTheme.ReticleBorder, style = Stroke(width = 1.dp.toPx()))

                val petalPath = Path().apply {
                    arcTo(
                        rect = Rect(center, targetRadius),
                        startAngleDegrees = startAngleDeg + 3f,
                        sweepAngleDegrees = sweepAngleDeg - 6f,
                        forceMoveTo = true
                    )
                    lineTo(center.x + minRadius * cos(endAngleRad), center.y + minRadius * sin(endAngleRad))
                    close()
                }

                val petalColor = when {
                    seg.recoveryPercent >= 0.85f -> CyberTheme.AcidLime
                    seg.recoveryPercent >= 0.60f -> CyberTheme.CyberYellow
                    else -> CyberTheme.ApexOrange
                }
                drawPath(petalPath, petalColor.copy(alpha = 0.85f), style = Fill)
            }

            drawCircle(CyberTheme.ChassisBackground, radius = minRadius)
            drawCircle(CyberTheme.RimHighlight, radius = minRadius, style = Stroke(width = 2.dp.toPx()))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$overallReadiness%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = CyberTheme.TextPrimary
            )
            Text(
                text = "INDEX",
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CyberTheme.AcidLime,
                letterSpacing = 1.5.sp
            )
        }
    }
}