package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Luxury Wellness Palettes
val CanvasDarkBg = Color(0xFF0F0F1A)
val LineColor = Color(0x66F0F0F5)
val AccentColor = com.example.ui.theme.IndigoAccent
val SecondaryAccent = Color(0xFFA78BFA)

@Composable
fun AnatomicalBodyIllustration(
    modifier: Modifier = Modifier,
    volumeIntensities: Map<String, Float> = emptyMap(), // Muscle group name (lowercase) to value [0.0 - 1.0]
    highlightedMuscle: String? = null // Muscle group to highlight bright purple, override others
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color(0xFF131324))
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Sizing parameters
        val w = canvasWidth / 2f
        val h = canvasHeight

        // Centered anchors for Front (Left) and Back (Right) views
        val frontMidX = w * 0.5f
        val backMidX = w * 1.5f

        // Thin sketch body guidelines
        val strokeThickness = 1.2f
        val stroke = Stroke(width = strokeThickness)
        val faintStroke = Stroke(width = 0.8f, miter = 1f)

        // Draw center vertical guidelines
        drawLine(
            color = Color(0x1A8A8A9A),
            start = Offset(frontMidX, 0f),
            end = Offset(frontMidX, h),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0x1A8A8A9A),
            start = Offset(backMidX, 0f),
            end = Offset(backMidX, h),
            strokeWidth = 1f
        )

        // --- Helper for muscle fill or outline ---
        fun drawMusclePart(
            muscleName: String,
            pathLeft: Path,
            pathRight: Path? = null // Optional if single centerline muscle like Abs
        ) {
            val isHighlighted = highlightedMuscle?.lowercase() == muscleName.lowercase()
            val volume = volumeIntensities[muscleName.lowercase()] ?: 0f

            // Fill color based on state
            val fillColor = when {
                isHighlighted -> SecondaryAccent.copy(alpha = 0.85f)
                volume > 0f -> AccentColor.copy(alpha = 0.15f + volume * 0.65f)
                else -> Color.Transparent
            }

            val outlineColor = when {
                isHighlighted -> SecondaryAccent
                volume > 0f -> AccentColor.copy(alpha = 0.8f)
                else -> LineColor
            }

            // Draw Left Path
            if (fillColor != Color.Transparent) {
                drawPath(pathLeft, color = fillColor)
            }
            drawPath(pathLeft, color = outlineColor, style = stroke)

            // Draw Right Path if present
            if (pathRight != null) {
                if (fillColor != Color.Transparent) {
                    drawPath(pathRight, color = fillColor)
                }
                drawPath(pathRight, color = outlineColor, style = stroke)
            }
        }

        // ================= FRONT VIEW (LEFT SIDE) =================
        val midX = frontMidX

        // 1. Head & Neck
        val headPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(midX, h * 0.12f),
                    radius = w * 0.10f
                )
            )
        }
        val neckPath = Path().apply {
            moveTo(midX - w * 0.04f, h * 0.19f)
            lineTo(midX - w * 0.04f, h * 0.23f)
            lineTo(midX + w * 0.04f, h * 0.23f)
            lineTo(midX + w * 0.04f, h * 0.19f)
            close()
        }
        drawPath(headPath, color = LineColor, style = faintStroke)
        drawPath(neckPath, color = LineColor, style = faintStroke)

        // 2. Chest (Pectorals)
        val chestLeft = Path().apply {
            moveTo(midX, h * 0.24f)
            lineTo(midX - w * 0.14f, h * 0.23f)
            quadraticTo(midX - w * 0.15f, h * 0.28f, midX - w * 0.13f, h * 0.31f)
            lineTo(midX, h * 0.32f)
            close()
        }
        val chestRight = Path().apply {
            moveTo(midX, h * 0.24f)
            lineTo(midX + w * 0.14f, h * 0.23f)
            quadraticTo(midX + w * 0.15f, h * 0.28f, midX + w * 0.13f, h * 0.31f)
            lineTo(midX, h * 0.32f)
            close()
        }
        drawMusclePart("chest", chestLeft, chestRight)

        // 3. Shoulders (Anterior/Lateral Delts)
        val shoulderLeft = Path().apply {
            moveTo(midX - w * 0.14f, h * 0.23f)
            quadraticTo(midX - w * 0.22f, h * 0.24f, midX - w * 0.20f, h * 0.31f)
            quadraticTo(midX - w * 0.15f, h * 0.33f, midX - w * 0.14f, h * 0.28f)
            close()
        }
        val shoulderRight = Path().apply {
            moveTo(midX + w * 0.14f, h * 0.23f)
            quadraticTo(midX + w * 0.22f, h * 0.24f, midX + w * 0.18f, h * 0.31f)
            quadraticTo(midX + w * 0.15f, h * 0.33f, midX + w * 0.14f, h * 0.28f)
            close()
        }
        drawMusclePart("shoulders", shoulderLeft, shoulderRight)

        // 4. Biceps
        val bicepLeft = Path().apply {
            moveTo(midX - w * 0.16f, h * 0.31f)
            quadraticTo(midX - w * 0.22f, h * 0.35f, midX - w * 0.19f, h * 0.43f)
            lineTo(midX - w * 0.14f, h * 0.41f)
            close()
        }
        val bicepRight = Path().apply {
            moveTo(midX + w * 0.16f, h * 0.31f)
            quadraticTo(midX + w * 0.22f, h * 0.35f, midX + w * 0.19f, h * 0.43f)
            lineTo(midX + w * 0.14f, h * 0.41f)
            close()
        }
        drawMusclePart("biceps", bicepLeft, bicepRight)

        // 5. Forearms (Front)
        val forearmLeft = Path().apply {
            moveTo(midX - w * 0.19f, h * 0.43f)
            quadraticTo(midX - w * 0.23f, h * 0.52f, midX - w * 0.17f, h * 0.60f)
            lineTo(midX - w * 0.14f, h * 0.41f)
            close()
        }
        val forearmRight = Path().apply {
            moveTo(midX + w * 0.19f, h * 0.43f)
            quadraticTo(midX + w * 0.23f, h * 0.52f, midX + w * 0.17f, h * 0.60f)
            lineTo(midX + w * 0.14f, h * 0.41f)
            close()
        }
        drawMusclePart("forearms", forearmLeft, forearmRight)

        // 6. Abs (Abdominals)
        val absPath = Path().apply {
            moveTo(midX - w * 0.08f, h * 0.33f)
            lineTo(midX + w * 0.08f, h * 0.33f)
            lineTo(midX + w * 0.07f, h * 0.46f)
            lineTo(midX - w * 0.07f, h * 0.46f)
            close()
        }
        drawMusclePart("abs", absPath, null)

        // 7. Quadriceps (Quads)
        val quadLeft = Path().apply {
            moveTo(midX - w * 0.10f, h * 0.48f)
            lineTo(midX - w * 0.02f, h * 0.48f)
            quadraticTo(midX - w * 0.02f, h * 0.60f, midX - w * 0.03f, h * 0.68f)
            quadraticTo(midX - w * 0.08f, h * 0.68f, midX - w * 0.10f, h * 0.60f)
            close()
        }
        val quadRight = Path().apply {
            moveTo(midX + w * 0.10f, h * 0.48f)
            lineTo(midX + w * 0.02f, h * 0.48f)
            quadraticTo(midX + w * 0.02f, h * 0.60f, midX + w * 0.03f, h * 0.68f)
            quadraticTo(midX + w * 0.08f, h * 0.68f, midX + w * 0.10f, h * 0.60f)
            close()
        }
        drawMusclePart("quads", quadLeft, quadRight)

        // 8. Calves (Front)
        val calfFrontLeft = Path().apply {
            moveTo(midX - w * 0.09f, h * 0.72f)
            lineTo(midX - w * 0.04f, h * 0.72f)
            lineTo(midX - w * 0.04f, h * 0.90f)
            lineTo(midX - w * 0.07f, h * 0.90f)
            close()
        }
        val calfFrontRight = Path().apply {
            moveTo(midX + w * 0.09f, h * 0.72f)
            lineTo(midX + w * 0.04f, h * 0.72f)
            lineTo(midX + w * 0.04f, h * 0.90f)
            lineTo(midX + w * 0.07f, h * 0.90f)
            close()
        }
        drawMusclePart("calves", calfFrontLeft, calfFrontRight)


        // ================= BACK VIEW (RIGHT SIDE) =================
        val bMid = backMidX

        // 1. Back Head Outline
        val headBack = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(bMid, h * 0.12f),
                    radius = w * 0.10f
                )
            )
        }
        drawPath(headBack, color = LineColor, style = faintStroke)

        // 2. Trapezius / Neck Area
        val trapezLeft = Path().apply {
            moveTo(bMid, h * 0.19f)
            lineTo(bMid - w * 0.12f, h * 0.23f)
            lineTo(bMid, h * 0.25f)
            close()
        }
        val trapezRight = Path().apply {
            moveTo(bMid, h * 0.19f)
            lineTo(bMid + w * 0.12f, h * 0.23f)
            lineTo(bMid, h * 0.25f)
            close()
        }
        drawMusclePart("back", trapezLeft, trapezRight) // Highlight "back" maps to Back/Lats

        // 3. Upper Back & Lats
        val backMainLeft = Path().apply {
            moveTo(bMid, h * 0.25f)
            lineTo(bMid - w * 0.14f, h * 0.24f)
            quadraticTo(bMid - w * 0.15f, h * 0.32f, bMid - w * 0.08f, h * 0.36f)
            lineTo(bMid, h * 0.36f)
            close()
        }
        val backMainRight = Path().apply {
            moveTo(bMid, h * 0.25f)
            lineTo(bMid + w * 0.14f, h * 0.24f)
            quadraticTo(bMid + w * 0.15f, h * 0.32f, bMid + w * 0.08f, h * 0.36f)
            lineTo(bMid, h * 0.36f)
            close()
        }
        drawMusclePart("back", backMainLeft, backMainRight)

        // 4. Triceps
        val tricepLeft = Path().apply {
            moveTo(bMid - w * 0.15f, h * 0.30f)
            quadraticTo(bMid - w * 0.21f, h * 0.35f, bMid - w * 0.18f, h * 0.42f)
            lineTo(bMid - w * 0.13f, h * 0.40f)
            close()
        }
        val tricepRight = Path().apply {
            moveTo(bMid + w * 0.15f, h * 0.30f)
            quadraticTo(bMid + w * 0.21f, h * 0.35f, bMid + w * 0.18f, h * 0.42f)
            lineTo(bMid + w * 0.13f, h * 0.40f)
            close()
        }
        drawMusclePart("triceps", tricepLeft, tricepRight)

        // 5. Lower Back
        val lowerBackPath = Path().apply {
            moveTo(bMid - w * 0.08f, h * 0.36f)
            lineTo(bMid + w * 0.08f, h * 0.36f)
            lineTo(bMid + w * 0.07f, h * 0.44f)
            lineTo(bMid - w * 0.07f, h * 0.44f)
            close()
        }
        drawMusclePart("lower back", lowerBackPath, null)

        // 6. Glutes
        val gluteLeft = Path().apply {
            moveTo(bMid - w * 0.09f, h * 0.45f)
            lineTo(bMid, h * 0.45f)
            quadraticTo(bMid, h * 0.54f, bMid - w * 0.01f, h * 0.54f)
            quadraticTo(bMid - w * 0.08f, h * 0.54f, bMid - w * 0.09f, h * 0.49f)
            close()
        }
        val gluteRight = Path().apply {
            moveTo(bMid + w * 0.09f, h * 0.45f)
            lineTo(bMid, h * 0.45f)
            quadraticTo(bMid, h * 0.54f, bMid + w * 0.01f, h * 0.54f)
            quadraticTo(bMid + w * 0.08f, h * 0.54f, bMid + w * 0.09f, h * 0.49f)
            close()
        }
        drawMusclePart("glutes", gluteLeft, gluteRight)

        // 7. Hamstrings
        val hamstringLeft = Path().apply {
            moveTo(bMid - w * 0.10f, h * 0.55f)
            lineTo(bMid - w * 0.01f, h * 0.55f)
            quadraticTo(bMid - w * 0.02f, h * 0.65f, bMid - w * 0.03f, h * 0.69f)
            quadraticTo(bMid - w * 0.08f, h * 0.69f, bMid - w * 0.10f, h * 0.62f)
            close()
        }
        val hamstringRight = Path().apply {
            moveTo(bMid + w * 0.10f, h * 0.55f)
            lineTo(bMid + w * 0.01f, h * 0.55f)
            quadraticTo(bMid + w * 0.02f, h * 0.65f, bMid + w * 0.03f, h * 0.69f)
            quadraticTo(bMid + w * 0.08f, h * 0.69f, bMid + w * 0.10f, h * 0.62f)
            close()
        }
        drawMusclePart("hamstrings", hamstringLeft, hamstringRight)

        // 8. Calves (Back)
        val calfBackLeft = Path().apply {
            moveTo(bMid - w * 0.09f, h * 0.72f)
            lineTo(bMid - w * 0.03f, h * 0.72f)
            lineTo(bMid - w * 0.04f, h * 0.90f)
            lineTo(bMid - w * 0.08f, h * 0.90f)
            close()
        }
        val calfBackRight = Path().apply {
            moveTo(bMid + w * 0.09f, h * 0.72f)
            lineTo(bMid + w * 0.03f, h * 0.72f)
            lineTo(bMid + w * 0.04f, h * 0.90f)
            lineTo(bMid + w * 0.08f, h * 0.90f)
            close()
        }
        drawMusclePart("calves", calfBackLeft, calfBackRight)
    }
}
