package com.apexfit.app.ui.models
import com.apexfit.app.ui.theme.ApexIcons

import androidx.annotation.DrawableRes
import com.apexfit.app.R

enum class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    HOME("home", "Today", ApexIcons.Calendar),
    TRAIN("train", "Train", ApexIcons.Barbell),
    NUTRITION("nutrition", "Nutrition", ApexIcons.Kitchen),
    PROGRESS("progress", "Progress", ApexIcons.TrendingUp)
}
