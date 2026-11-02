package com.apexfit.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Spacing ──────────────────────────────────────────────────────────────────
object ApexSpacing {
    val xs            = 4.dp
    val sm            = 8.dp
    val md            = 12.dp
    val lg            = 16.dp
    val xl            = 20.dp
    val xxl           = 24.dp
    val xxxl          = 64.dp   // bottom content padding (above nav bar)
    val screenPadding = 16.dp
    val cardPadding   = 16.dp
    val cardGap       = 12.dp
}

// ─── Data text styles (monospaced numerics) ───────────────────────────────────
object ApexTextStyles {
    val dataXL = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp
    )
    val dataMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 20.sp
    )
}

// ─── Motion ───────────────────────────────────────────────────────────────────
private val decelEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

object ApexMotion {
    fun momentCoach() = tween<Float>(durationMillis = 420, easing = decelEasing)
}
