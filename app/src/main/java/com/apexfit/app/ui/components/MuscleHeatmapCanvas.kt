package com.apexfit.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.apexfit.app.ui.theme.JetBrainsMonoFamily
import com.apexfit.app.ui.theme.SyneFamily
import com.apexfit.app.utils.HeatmapEntry

// ── Constants ─────────────────────────────────────────────────────
private val ACTIVE_ALPHA   = 0.75f
private val EMPTY_ALPHA    = 0.30f
private val BODY_BASE            = Color(0xFF13131D)  // matches DarkCardSurface
private val BODY_OUTLINE         = Color.White.copy(alpha = 0.10f)
private val MUSCLE_STROKE        = Color.White.copy(alpha = 0.15f)
private val EMPTY_FILL           = Color(0xFF252535)  // matches BorderSubtle

// ─────────────────────────────────────────────────────────────────
// PUBLIC COMPOSABLE
// ─────────────────────────────────────────────────────────────────

private fun buildHeatmapDescription(heatmap: Map<String, HeatmapEntry>, view: String): String {
    val active = heatmap.filter { it.value.volume > 0 }
    return if (active.isEmpty()) {
        "$view body view: no training data in the last 7 days"
    } else {
        val topMuscles = active.entries.sortedByDescending { it.value.volume }.take(5)
            .joinToString(", ") { "${it.key}: ${it.value.volume} sets" }
        "$view body view: $topMuscles"
    }
}

@Composable
fun SingleFrontHeatmapCanvas(
    heatmap: Map<String, HeatmapEntry>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = buildHeatmapDescription(heatmap, "front")
        }
    ) {
        drawFrontView(heatmap)
    }
}

@Composable
fun MuscleHeatmapCanvas(
    heatmap: Map<String, HeatmapEntry>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Front view
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "FRONT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A9A),
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .height(280.dp)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = buildHeatmapDescription(heatmap, "front")
                        }
                ) { drawFrontView(heatmap) }
            }

            // Back view
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A9A),
                    fontFamily = SyneFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .height(280.dp)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = buildHeatmapDescription(heatmap, "back")
                        }
                ) { drawBackView(heatmap) }
            }
        }

        Spacer(Modifier.height(12.dp))
        HeatmapLegend()
    }
}

// ─────────────────────────────────────────────────────────────────
// FRONT VIEW
// ─────────────────────────────────────────────────────────────────

private fun Map<String, HeatmapEntry>.getMuscle(key: String): HeatmapEntry? {
    val exact = this[key]
    if (exact != null) return exact
    val norm = key.lowercase().trim()
    return when (norm) {
        "shoulders" -> this["shoulders"] ?: this["front_delt"] ?: this["side_delt"] ?: this["rear_delt"]
        "biceps" -> this["biceps"] ?: this["bicep"]
        "triceps" -> this["triceps"] ?: this["tricep"]
        "quads" -> this["quads"] ?: this["quad"]
        "hamstrings" -> this["hamstrings"] ?: this["hamstring"]
        "glutes" -> this["glutes"] ?: this["glute"]
        "calves" -> this["calves"] ?: this["calf"]
        "core" -> this["core"]
        "back" -> this["back"]
        "chest" -> this["chest"]
        "bicep" -> this["bicep"] ?: this["biceps"]
        "tricep" -> this["tricep"] ?: this["triceps"]
        "quad" -> this["quad"] ?: this["quads"]
        "hamstring" -> this["hamstring"] ?: this["hamstrings"]
        "glute" -> this["glute"] ?: this["glutes"]
        "calf" -> this["calf"] ?: this["calves"]
        else -> null
    }
}

private fun DrawScope.drawFrontView(heatmap: Map<String, HeatmapEntry>) {
    val w = size.width; val h = size.height
    drawBodyStructure(w, h)

    // Lats (back) visible at the sides on the front view
    val back = heatmap.getMuscle("back").toColorPair()
    drawLat(w, h, isLeft = true, back)
    drawLat(w, h, isLeft = false, back)

    // Chest — overlapping ovals create a natural pec shape
    val chest = heatmap.getMuscle("chest").toColorPair()
    drawOvalMuscle(w * 0.370f, h * 0.248f, w * 0.092f, h * 0.068f, chest)
    drawOvalMuscle(w * 0.630f, h * 0.248f, w * 0.092f, h * 0.068f, chest)

    // Core — 3 pairs of oval segments
    val core = heatmap.getMuscle("core").toColorPair()
    drawOvalMuscle(w * 0.440f, h * 0.370f, w * 0.062f, h * 0.038f, core)
    drawOvalMuscle(w * 0.560f, h * 0.370f, w * 0.062f, h * 0.038f, core)
    drawOvalMuscle(w * 0.440f, h * 0.425f, w * 0.062f, h * 0.036f, core)
    drawOvalMuscle(w * 0.560f, h * 0.425f, w * 0.062f, h * 0.036f, core)
    drawOvalMuscle(w * 0.440f, h * 0.476f, w * 0.058f, h * 0.034f, core)
    drawOvalMuscle(w * 0.560f, h * 0.476f, w * 0.058f, h * 0.034f, core)

    // Shoulders (Front + Side delts)
    val shoulders = heatmap.getMuscle("shoulders").toColorPair()
    drawOvalMuscle(w * 0.238f, h * 0.200f, w * 0.062f, h * 0.048f, shoulders)
    drawOvalMuscle(w * 0.762f, h * 0.200f, w * 0.062f, h * 0.048f, shoulders)
    drawOvalMuscle(w * 0.172f, h * 0.215f, w * 0.042f, h * 0.062f, shoulders)
    drawOvalMuscle(w * 0.828f, h * 0.215f, w * 0.042f, h * 0.062f, shoulders)

    // Biceps (inner upper arm)
    val biceps = heatmap.getMuscle("biceps").toColorPair()
    drawOvalMuscle(w * 0.185f, h * 0.318f, w * 0.035f, h * 0.082f, biceps)
    drawOvalMuscle(w * 0.815f, h * 0.318f, w * 0.035f, h * 0.082f, biceps)

    // Triceps (outer upper arm in front view)
    val triceps = heatmap.getMuscle("triceps").toColorPair()
    drawOvalMuscle(w * 0.145f, h * 0.325f, w * 0.030f, h * 0.078f, triceps)
    drawOvalMuscle(w * 0.855f, h * 0.325f, w * 0.030f, h * 0.078f, triceps)

    // Quads — 2 visible heads per leg
    val quads = heatmap.getMuscle("quads").toColorPair()
    drawOvalMuscle(w * 0.330f, h * 0.645f, w * 0.052f, h * 0.082f, quads)
    drawOvalMuscle(w * 0.388f, h * 0.638f, w * 0.048f, h * 0.082f, quads)
    drawOvalMuscle(w * 0.612f, h * 0.638f, w * 0.048f, h * 0.082f, quads)
    drawOvalMuscle(w * 0.670f, h * 0.645f, w * 0.052f, h * 0.082f, quads)
}

// ─────────────────────────────────────────────────────────────────
// BACK VIEW
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawBackView(heatmap: Map<String, HeatmapEntry>) {
    val w = size.width; val h = size.height
    drawBodyStructure(w, h)

    val back = heatmap.getMuscle("back").toColorPair()
    drawTrapezius(w, h, back)
    drawLat(w, h, isLeft = true, back)
    drawLat(w, h, isLeft = false, back)

    // Shoulders
    val shoulders = heatmap.getMuscle("shoulders").toColorPair()
    drawOvalMuscle(w * 0.238f, h * 0.205f, w * 0.065f, h * 0.046f, shoulders)
    drawOvalMuscle(w * 0.762f, h * 0.205f, w * 0.065f, h * 0.046f, shoulders)
    drawOvalMuscle(w * 0.172f, h * 0.215f, w * 0.042f, h * 0.062f, shoulders)
    drawOvalMuscle(w * 0.828f, h * 0.215f, w * 0.042f, h * 0.062f, shoulders)

    val triceps = heatmap.getMuscle("triceps").toColorPair()
    drawOvalMuscle(w * 0.172f, h * 0.318f, w * 0.048f, h * 0.082f, triceps)
    drawOvalMuscle(w * 0.828f, h * 0.318f, w * 0.048f, h * 0.082f, triceps)

    val glutes = heatmap.getMuscle("glutes").toColorPair()
    drawOvalMuscle(w * 0.378f, h * 0.592f, w * 0.082f, h * 0.068f, glutes)
    drawOvalMuscle(w * 0.622f, h * 0.592f, w * 0.082f, h * 0.068f, glutes)

    val hamstrings = heatmap.getMuscle("hamstrings").toColorPair()
    drawOvalMuscle(w * 0.370f, h * 0.672f, w * 0.068f, h * 0.085f, hamstrings)
    drawOvalMuscle(w * 0.630f, h * 0.672f, w * 0.068f, h * 0.085f, hamstrings)

    val calves = heatmap.getMuscle("calves").toColorPair()
    drawOvalMuscle(w * 0.368f, h * 0.805f, w * 0.055f, h * 0.072f, calves)
    drawOvalMuscle(w * 0.632f, h * 0.805f, w * 0.055f, h * 0.072f, calves)
}

// ─────────────────────────────────────────────────────────────────
// BODY STRUCTURE — replaced with single continuous silhouette path
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawBodyStructure(w: Float, h: Float) {
    val silhouette = buildBodySilhouette(w, h)
    drawPath(silhouette, BODY_BASE)
    drawPath(silhouette, BODY_OUTLINE, style = Stroke(1.2.dp.toPx()))
}

private fun buildBodySilhouette(w: Float, h: Float): Path = Path().apply {
    // ── Head ─────────────────────────────────────────────────────
    val headCx = w * 0.500f; val headCy = h * 0.070f
    val headRx = w * 0.110f; val headRy = h * 0.065f
    addOval(Rect(headCx - headRx, headCy - headRy, headCx + headRx, headCy + headRy))
    close()

    // ── Torso + arms + legs — single connected path ───────────────
    // Starting at left neck base, going clockwise
    moveTo(w * 0.435f, h * 0.132f)

    // Left neck → left shoulder
    cubicTo(
        w * 0.390f, h * 0.138f,
        w * 0.295f, h * 0.150f,
        w * 0.245f, h * 0.168f  // left shoulder peak
    )

    // Left shoulder cap (outer deltoid curve)
    cubicTo(
        w * 0.188f, h * 0.178f,
        w * 0.168f, h * 0.198f,
        w * 0.155f, h * 0.228f  // left delt bottom
    )

    // Left upper arm outer edge going down
    cubicTo(
        w * 0.138f, h * 0.268f,
        w * 0.132f, h * 0.308f,
        w * 0.136f, h * 0.358f  // elbow outer
    )

    // Left forearm outer
    cubicTo(
        w * 0.138f, h * 0.395f,
        w * 0.140f, h * 0.445f,
        w * 0.148f, h * 0.510f  // wrist outer
    )

    // Left wrist → inner forearm bottom
    cubicTo(
        w * 0.154f, h * 0.528f,
        w * 0.168f, h * 0.530f,
        w * 0.182f, h * 0.520f  // wrist inner
    )

    // Left forearm inner going up
    cubicTo(
        w * 0.192f, h * 0.460f,
        w * 0.195f, h * 0.405f,
        w * 0.192f, h * 0.358f  // elbow inner
    )

    // Left upper arm inner going up
    cubicTo(
        w * 0.192f, h * 0.300f,
        w * 0.198f, h * 0.255f,
        w * 0.218f, h * 0.222f  // armpit
    )

    // Left chest edge → waist left
    cubicTo(
        w * 0.268f, h * 0.198f,
        w * 0.305f, h * 0.282f,
        w * 0.312f, h * 0.378f
    )
    cubicTo(
        w * 0.318f, h * 0.452f,
        w * 0.322f, h * 0.495f,
        w * 0.330f, h * 0.540f  // waist left
    )

    // Hip left flare
    cubicTo(
        w * 0.325f, h * 0.558f,
        w * 0.308f, h * 0.568f,
        w * 0.298f, h * 0.578f  // hip outer left
    )

    // Left thigh outer
    cubicTo(
        w * 0.285f, h * 0.618f,
        w * 0.282f, h * 0.668f,
        w * 0.290f, h * 0.748f  // knee outer left
    )
    cubicTo(
        w * 0.295f, h * 0.762f,
        w * 0.302f, h * 0.772f,
        w * 0.308f, h * 0.778f
    )

    // Left calf outer
    cubicTo(
        w * 0.302f, h * 0.818f,
        w * 0.298f, h * 0.860f,
        w * 0.308f, h * 0.918f  // ankle left
    )
    cubicTo(
        w * 0.312f, h * 0.940f,
        w * 0.322f, h * 0.948f,
        w * 0.338f, h * 0.950f  // foot left outer
    )

    // Left foot → right foot
    lineTo(w * 0.432f, h * 0.950f)
    cubicTo(
        w * 0.450f, h * 0.950f,
        w * 0.460f, h * 0.942f,
        w * 0.462f, h * 0.928f  // inner ankle left
    )

    // Left inner calf up
    cubicTo(
        w * 0.465f, h * 0.878f,
        w * 0.468f, h * 0.830f,
        w * 0.462f, h * 0.782f
    )
    cubicTo(
        w * 0.458f, h * 0.760f,
        w * 0.452f, h * 0.748f,
        w * 0.448f, h * 0.742f  // knee inner left
    )

    // Left inner thigh up to crotch
    cubicTo(
        w * 0.445f, h * 0.698f,
        w * 0.448f, h * 0.648f,
        w * 0.455f, h * 0.608f
    )
    cubicTo(
        w * 0.460f, h * 0.585f,
        w * 0.468f, h * 0.572f,
        w * 0.478f, h * 0.568f  // crotch left
    )

    // Crotch curve
    cubicTo(
        w * 0.488f, h * 0.562f,
        w * 0.512f, h * 0.562f,
        w * 0.522f, h * 0.568f  // crotch right
    )

    // Right inner thigh down
    cubicTo(
        w * 0.522f, h * 0.568f,
        w * 0.540f, h * 0.585f,
        w * 0.545f, h * 0.608f
    )
    cubicTo(
        w * 0.552f, h * 0.648f,
        w * 0.555f, h * 0.698f,
        w * 0.552f, h * 0.742f  // knee inner right
    )

    // Right calf inner
    cubicTo(
        w * 0.548f, h * 0.748f,
        w * 0.542f, h * 0.760f,
        w * 0.538f, h * 0.782f
    )
    cubicTo(
        w * 0.532f, h * 0.830f,
        w * 0.535f, h * 0.878f,
        w * 0.538f, h * 0.928f
    )
    cubicTo(
        w * 0.540f, h * 0.942f,
        w * 0.550f, h * 0.950f,
        w * 0.568f, h * 0.950f  // foot right inner
    )

    // Right foot
    lineTo(w * 0.662f, h * 0.950f)
    cubicTo(
        w * 0.678f, h * 0.948f,
        w * 0.688f, h * 0.940f,
        w * 0.692f, h * 0.918f  // ankle right
    )

    // Right calf outer
    cubicTo(
        w * 0.702f, h * 0.860f,
        w * 0.698f, h * 0.818f,
        w * 0.692f, h * 0.778f
    )
    cubicTo(
        w * 0.698f, h * 0.772f,
        w * 0.705f, h * 0.762f,
        w * 0.710f, h * 0.748f  // knee outer right
    )

    // Right thigh outer
    cubicTo(
        w * 0.718f, h * 0.668f,
        w * 0.715f, h * 0.618f,
        w * 0.702f, h * 0.578f  // hip outer right
    )
    cubicTo(
        w * 0.692f, h * 0.568f,
        w * 0.675f, h * 0.558f,
        w * 0.670f, h * 0.540f  // waist right
    )

    // Waist right → right armpit
    cubicTo(
        w * 0.678f, h * 0.495f,
        w * 0.682f, h * 0.452f,
        w * 0.688f, h * 0.378f
    )
    cubicTo(
        w * 0.695f, h * 0.282f,
        w * 0.732f, h * 0.198f,
        w * 0.782f, h * 0.222f  // armpit right
    )

    // Right upper arm inner going down
    cubicTo(
        w * 0.802f, h * 0.255f,
        w * 0.808f, h * 0.300f,
        w * 0.808f, h * 0.358f  // elbow inner right
    )

    // Right forearm inner
    cubicTo(
        w * 0.805f, h * 0.405f,
        w * 0.808f, h * 0.460f,
        w * 0.818f, h * 0.520f  // wrist inner right
    )
    cubicTo(
        w * 0.832f, h * 0.530f,
        w * 0.846f, h * 0.528f,
        w * 0.852f, h * 0.510f  // wrist outer right
    )

    // Right forearm outer going up
    cubicTo(
        w * 0.860f, h * 0.445f,
        w * 0.862f, h * 0.395f,
        w * 0.864f, h * 0.358f  // elbow outer right
    )

    // Right upper arm outer going up
    cubicTo(
        w * 0.868f, h * 0.308f,
        w * 0.862f, h * 0.268f,
        w * 0.845f, h * 0.228f  // right delt bottom
    )

    // Right shoulder cap
    cubicTo(
        w * 0.832f, h * 0.198f,
        w * 0.812f, h * 0.178f,
        w * 0.755f, h * 0.168f  // right shoulder peak
    )

    // Right shoulder → neck right
    cubicTo(
        w * 0.705f, h * 0.150f,
        w * 0.610f, h * 0.138f,
        w * 0.565f, h * 0.132f  // right neck base
    )

    close()
}

// ─────────────────────────────────────────────────────────────────
// COMPLEX MUSCLE SHAPES
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawTrapezius(w: Float, h: Float, cp: Pair<Color, Float>) {
    val path = Path().apply {
        moveTo(w * 0.352f, h * 0.163f)
        lineTo(w * 0.500f, h * 0.200f)
        lineTo(w * 0.648f, h * 0.163f)
        quadraticTo(w * 0.690f, h * 0.215f, w * 0.635f, h * 0.290f)
        lineTo(w * 0.500f, h * 0.268f)
        lineTo(w * 0.365f, h * 0.290f)
        quadraticTo(w * 0.310f, h * 0.215f, w * 0.352f, h * 0.163f)
        close()
    }
    drawPath(path, cp.first.copy(alpha = cp.second))
    drawPath(path, MUSCLE_STROKE, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawLat(w: Float, h: Float, isLeft: Boolean, cp: Pair<Color, Float>) {
    val path = Path().apply {
        if (isLeft) {
            moveTo(w * 0.295f, h * 0.222f)
            quadraticTo(w * 0.228f, h * 0.285f, w * 0.315f, h * 0.385f)
            lineTo(w * 0.395f, h * 0.428f)
            quadraticTo(w * 0.425f, h * 0.325f, w * 0.375f, h * 0.222f)
        } else {
            moveTo(w * 0.705f, h * 0.222f)
            quadraticTo(w * 0.772f, h * 0.285f, w * 0.685f, h * 0.385f)
            lineTo(w * 0.605f, h * 0.428f)
            quadraticTo(w * 0.575f, h * 0.325f, w * 0.625f, h * 0.222f)
        }
        close()
    }
    drawPath(path, cp.first.copy(alpha = cp.second))
    drawPath(path, MUSCLE_STROKE, style = Stroke(1.dp.toPx()))
}

// ─────────────────────────────────────────────────────────────────
// PRIMITIVE DRAW HELPERS
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawOvalMuscle(
    cx: Float, cy: Float, rx: Float, ry: Float,
    cp: Pair<Color, Float>
) {
    val tl = Offset(cx - rx, cy - ry)
    val sz = Size(rx * 2f, ry * 2f)
    drawOval(cp.first.copy(alpha = cp.second), tl, sz)
    drawOval(MUSCLE_STROKE, tl, sz, style = Stroke(1.dp.toPx()))
}

/** Resolves a HeatmapEntry to (fillColor, alpha) */
private fun HeatmapEntry?.toColorPair(): Pair<Color, Float> {
    if (this == null || intensity <= 0) return EMPTY_FILL to EMPTY_ALPHA
    return try {
        val parsedColor = Color(AndroidColor.parseColor(colorHex))
        when {
            intensity >= 80 -> parsedColor to 1.0f
            intensity >= 40 -> parsedColor to 0.75f
            intensity >= 10 -> parsedColor to 0.50f
            else -> parsedColor to 0.25f
        }
    } catch (e: Exception) {
        EMPTY_FILL to EMPTY_ALPHA
    }
}

// ─────────────────────────────────────────────────────────────────
// LEGEND
// ─────────────────────────────────────────────────────────────────

@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(Color(0xFFEF4444), ">80%")
        LegendDot(Color(0xFFF97316), "40–80%")
        LegendDot(Color(0xFF34D399), "10–40%")
        LegendDot(Color(0xFF252535).copy(alpha = 0.9f), "None")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8A8A9A),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp
        )
    }
}
