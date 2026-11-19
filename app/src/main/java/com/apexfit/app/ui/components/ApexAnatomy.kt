package com.apexfit.app.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.PathParser
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.utils.MuscleAliases
import com.google.gson.Gson
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private data class AnatomyPathAsset(
    val id: String? = null,
    val group: String? = null,
    val side: String,
    val d: String
)

private data class AnatomyDiagramAsset(
    val view: String,
    val viewBox: String,
    val centerX: Float,
    val outline: List<AnatomyPathAsset>,
    val muscles: List<AnatomyPathAsset>
)

private data class AnatomyPath(
    val path: Path,
    val side: String,
    val muscle: String? = null,
    val hitRegion: Region? = null
)

private data class LoadedAnatomy(
    val viewBoxLeft: Float,
    val viewBoxTop: Float,
    val width: Float,
    val height: Float,
    val centerX: Float,
    val outline: List<AnatomyPath>,
    val muscles: List<AnatomyPath>
) {
    fun muscleAt(x: Float, y: Float): String? {
        val diagramX = x + viewBoxLeft
        val diagramY = y + viewBoxTop
        return muscles.asReversed().firstNotNullOfOrNull { region ->
            val hitRegion = region.hitRegion ?: return@firstNotNullOfOrNull null
            val hitX = if (region.side == "LEFT") centerX * 2f - diagramX else diagramX
            if (hitRegion.contains(hitX.roundToInt(), diagramY.roundToInt())) region.muscle else null
        }
    }
}

private object AnatomyAssetLoader {
    private val cache = ConcurrentHashMap<String, LoadedAnatomy>()
    private val gson = Gson()

    fun cached(showBack: Boolean): LoadedAnatomy? = cache[assetName(showBack)]

    fun load(context: Context, showBack: Boolean): LoadedAnatomy? {
        val name = assetName(showBack)
        return cache[name] ?: synchronized(cache) {
            cache[name] ?: runCatching {
                context.assets.open("anatomy/$name.json").bufferedReader().use { reader ->
                    val asset = gson.fromJson(reader, AnatomyDiagramAsset::class.java)
                    parse(asset)
                }
            }.getOrNull()?.also { cache[name] = it }
        }
    }

    private fun assetName(showBack: Boolean) = if (showBack) "male_back" else "male_front"

    private fun parse(asset: AnatomyDiagramAsset): LoadedAnatomy {
        val viewBox = asset.viewBox.trim().split(Regex("\\s+")).map(String::toFloat)
        require(viewBox.size == 4)
        val left = viewBox[0]
        val top = viewBox[1]
        val width = viewBox[2]
        val height = viewBox[3]
        val clip = Region(
            floor(left).toInt(),
            floor(top).toInt(),
            ceil(left + width).toInt(),
            ceil(top + height).toInt()
        )

        fun convert(entry: AnatomyPathAsset, clickable: Boolean): AnatomyPath? {
            val path = PathParser.createPathFromPathData(entry.d) ?: return null
            val muscle = entry.group?.let(::canonicalApexMuscle)
            val hitRegion = if (clickable && muscle != null) {
                Region().apply { setPath(path, clip) }
            } else null
            return AnatomyPath(path, entry.side.uppercase(Locale.ROOT), muscle, hitRegion)
        }

        return LoadedAnatomy(
            viewBoxLeft = left,
            viewBoxTop = top,
            width = width,
            height = height,
            centerX = asset.centerX,
            outline = asset.outline.mapNotNull { convert(it, false) },
            muscles = asset.muscles.mapNotNull { convert(it, true) }
        )
    }
}

/**
 * Native, selectable anatomy paths derived from MuscleMap's MIT-licensed flat-vector
 * diagrams. Only the traced vector paths are bundled; no reference-body photographs ship.
 */
@Composable
internal fun ApexAnatomy(
    showBack: Boolean,
    modifier: Modifier = Modifier,
    directSets: Map<String, Int> = emptyMap(),
    selectedMuscle: String? = null,
    onMuscleSelected: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    // These bundled vector files are small and cached after parsing; loading here
    // keeps first-entry and screenshot renders from capturing an empty placeholder.
    val anatomy = remember(context, showBack) { AnatomyAssetLoader.load(context, showBack) }

    val selected = selectedMuscle?.let(MuscleAliases::getCanonical)
    val normalizedDirectSets = remember(directSets) {
        directSets.entries.associate { MuscleAliases.getCanonical(it.key) to it.value }
    }

    if (anatomy == null) {
        Box(modifier.aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
            Text("Anatomy unavailable", color = ApexColors.TextSecondary)
        }
        return
    }

    val currentAnatomy = anatomy ?: return
    val viewName = if (showBack) "back" else "front"
    Canvas(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .semantics {
                contentDescription = "Muscle anatomy map, $viewName view"
                stateDescription = selected?.let { "${it.replace('_', ' ')} selected" } ?: "No muscle selected"
            }
            .pointerInput(currentAnatomy, onMuscleSelected) {
                detectTapGestures { tap ->
                    val scale = min(
                        size.width / currentAnatomy.width,
                        size.height / currentAnatomy.height
                    )
                    if (scale > 0f) {
                        val drawnWidth = currentAnatomy.width * scale
                        val drawnHeight = currentAnatomy.height * scale
                        val offsetX = (size.width - drawnWidth) / 2f
                        val offsetY = (size.height - drawnHeight) / 2f
                        val diagramX = (tap.x - offsetX) / scale
                        val diagramY = (tap.y - offsetY) / scale
                        if (diagramX in 0f..currentAnatomy.width && diagramY in 0f..currentAnatomy.height) {
                            currentAnatomy.muscleAt(diagramX, diagramY)?.let(onMuscleSelected)
                        }
                    }
                }
            }
    ) {
        val scale = min(size.width / currentAnatomy.width, size.height / currentAnatomy.height)
        val offsetX = (size.width - currentAnatomy.width * scale) / 2f
        val offsetY = (size.height - currentAnatomy.height * scale) / 2f

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val save = nativeCanvas.save()
            nativeCanvas.translate(offsetX, offsetY)
            nativeCanvas.scale(scale, scale)
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ApexColors.SurfaceRaised.toArgb()
                style = Paint.Style.FILL
            }
            val musclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ApexColors.Canvas.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

            currentAnatomy.outline.forEach { drawMirrored(nativeCanvas, it, currentAnatomy.centerX, outlinePaint) }
            currentAnatomy.muscles.forEach { region ->
                val group = region.muscle ?: return@forEach
                val count = normalizedDirectSets[group] ?: 0
                musclePaint.color = muscleColor(group, selected, count).toArgb()
                drawMirrored(nativeCanvas, region, currentAnatomy.centerX, musclePaint)
            }
            currentAnatomy.muscles.forEach { region ->
                drawMirrored(nativeCanvas, region, currentAnatomy.centerX, boundaryPaint)
            }
            nativeCanvas.restoreToCount(save)
        }
    }
}

private fun drawMirrored(
    canvas: android.graphics.Canvas,
    region: AnatomyPath,
    centerX: Float,
    paint: Paint
) {
    canvas.drawPath(region.path, paint)
    if (region.side == "LEFT") {
        val save = canvas.save()
        canvas.scale(-1f, 1f, centerX, 0f)
        canvas.drawPath(region.path, paint)
        canvas.restoreToCount(save)
    }
}

private fun muscleColor(group: String, selected: String?, directSets: Int) = when {
    group == selected -> ApexColors.Primary
    directSets >= 10 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, 0.80f)
    directSets >= 5 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, 0.55f)
    directSets > 0 -> lerp(ApexColors.PrimaryMist, ApexColors.Primary, 0.30f)
    else -> ApexColors.BorderStrong
}

private fun canonicalApexMuscle(sourceGroup: String): String? = when (sourceGroup.uppercase(Locale.ROOT)) {
    "CHEST" -> "chest"
    "BACK_UPPER", "LATS", "RHOMBOIDS" -> "back"
    "BACK_LOWER" -> "lower_back"
    "TRAPEZIUS" -> "trapezius"
    "SHOULDERS_FRONT", "SHOULDERS_SIDE", "SHOULDERS_REAR" -> "shoulders"
    "BICEPS" -> "biceps"
    "TRICEPS" -> "triceps"
    "FOREARMS" -> "forearms"
    "CORE", "OBLIQUES" -> "core"
    "GLUTES" -> "glutes"
    "QUADS" -> "quads"
    "HAMSTRINGS" -> "hamstrings"
    "CALVES" -> "calves"
    "HIP_FLEXORS" -> "hip_flexors"
    "ADDUCTORS" -> "hip_adductors"
    "ABDUCTORS" -> "hip_abductors"
    else -> null
}

internal fun showsOnBackOnly(muscle: String): Boolean =
    MuscleAliases.getCanonical(muscle) in setOf("back", "lower_back", "glutes", "hamstrings")

