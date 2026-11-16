package com.apexfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.apexfit.app.ui.theme.ApexColors

private data class AnatomyRegion(val muscle: String, val points: List<Offset>)

private fun region(muscle: String, vararg xy: Int): AnatomyRegion =
    AnatomyRegion(muscle, xy.toList().chunked(2).map { Offset(it[0].toFloat(), it[1].toFloat()) })

private fun pair(muscle: String, vararg xy: Int): List<AnatomyRegion> {
    val left = region(muscle, *xy)
    return listOf(left, left.copy(points = left.points.map { Offset(160f - it.x, it.y) }))
}

private val outline = region(
    "", 67, 13, 80, 5, 93, 13, 96, 33, 90, 46, 90, 54, 114, 59, 125, 76,
    136, 112, 149, 151, 146, 164, 137, 160, 122, 134, 111, 104, 108, 130,
    111, 156, 103, 195, 101, 225, 102, 252, 93, 259, 86, 250, 80, 177, 74, 250,
    67, 259, 58, 252, 59, 225, 57, 195, 49, 156, 52, 130, 49, 104, 38, 134,
    23, 160, 14, 164, 11, 151, 24, 112, 35, 76, 46, 59, 70, 54, 70, 46, 64, 33
)

private val shoulders = pair("shoulders", 36, 76, 46, 63, 57, 62, 54, 78, 42, 88)
private val forearms = pair("forearms", 27, 111, 38, 118, 23, 151, 16, 153)
private val calves = pair("calves", 60, 207, 72, 208, 70, 246, 63, 246)
private val front = shoulders + forearms + calves +
    pair("chest", 58, 65, 77, 65, 77, 85, 61, 91, 52, 84) +
    pair("biceps", 39, 89, 48, 87, 40, 112, 30, 110) +
    pair("core", 57, 94, 77, 89, 77, 130, 63, 139, 55, 127) +
    pair("quads", 53, 145, 73, 148, 76, 171, 68, 199, 60, 199, 51, 167)

private val back = shoulders + forearms + calves +
    pair("trapezius", 66, 55, 78, 55, 78, 80, 58, 75) +
    pair("back", 57, 80, 77, 84, 76, 110, 61, 123, 53, 99) +
    pair("triceps", 38, 88, 47, 87, 40, 112, 30, 110) +
    pair("lower_back", 63, 122, 77, 111, 77, 136, 60, 138) +
    pair("glutes", 54, 138, 77, 139, 76, 154, 62, 162, 51, 155) +
    pair("hamstrings", 53, 163, 68, 165, 76, 157, 72, 187, 67, 201, 59, 194)

internal fun showsOnBackOnly(muscle: String): Boolean =
    back.any { it.muscle == muscle } && front.none { it.muscle == muscle }

private fun List<Offset>.containsPoint(point: Offset): Boolean {
    var contains = false
    var previous = last()
    for (current in this) {
        if ((current.y > point.y) != (previous.y > point.y) &&
            point.x < (previous.x - current.x) * (point.y - current.y) /
            (previous.y - current.y) + current.x
        ) contains = !contains
        previous = current
    }
    return contains
}

/** Original schematic regions. Screen-level lists provide equivalent non-visual controls. */
@Composable
internal fun ApexAnatomy(
    showBack: Boolean,
    modifier: Modifier = Modifier,
    directSets: Map<String, Int> = emptyMap(),
    selectedMuscle: String? = null,
    onMuscleSelected: (String) -> Unit
) {
    val regions = if (showBack) back else front
    val description = if (showBack) "Back anatomy" else "Front anatomy"
    Canvas(modifier.aspectRatio(160f / 260f)
        .semantics { contentDescription = description }
        .pointerInput(regions, onMuscleSelected) {
            detectTapGestures { tap ->
                val scaled = Offset(tap.x * 160f / size.width, tap.y * 260f / size.height)
                regions.lastOrNull { it.points.containsPoint(scaled) }?.let { onMuscleSelected(it.muscle) }
            }
        }
    ) {
        fun AnatomyRegion.path() = Path().apply {
            points.forEachIndexed { index, point ->
                val x = point.x * size.width / 160
                val y = point.y * size.height / 260
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(outline.path(), ApexColors.SurfaceRaised)
        for (region in regions) {
            val count = directSets[region.muscle] ?: 0
            val color = when {
                region.muscle == selectedMuscle -> ApexColors.Primary
                count >= 10 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, .80f)
                count >= 5 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, .55f)
                count > 0 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, .30f)
                else -> ApexColors.BorderStrong
            }
            drawPath(region.path(), color)
        }
    }
}
