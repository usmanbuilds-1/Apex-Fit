package com.apexfit.app.ui.models

import androidx.annotation.DrawableRes
import com.apexfit.app.R

enum class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    HOME("home", "Home", R.drawable.ic_home),
    TRAIN("train", "Train", R.drawable.ic_fitness_center),
    NUTRITION("nutrition", "Nutrition", R.drawable.ic_restaurant_menu),
    PROGRESS("progress", "Progress", R.drawable.ic_insights)
}
