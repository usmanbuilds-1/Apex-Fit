package com.apexfit.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.ui.models.UiNutritionEntry
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme

private val nutritionPreviewMeals = listOf(
    UiNutritionEntry(
        id = 1,
        name = "Oats with milk",
        calories = 520,
        protein = 27.0,
        carbs = 74.0,
        fat = 14.0,
        timestamp = 0L,
        date = "2025-11-14",
        time = "08:40"
    ),
    UiNutritionEntry(
        id = 2,
        name = "Chicken & rice",
        calories = 710,
        protein = 46.0,
        carbs = 91.0,
        fat = 13.0,
        timestamp = 0L,
        date = "2025-11-14",
        time = "13:15"
    ),
    UiNutritionEntry(
        id = 3,
        name = "Banana and peanut butter",
        calories = 360,
        protein = 12.0,
        carbs = 45.0,
        fat = 17.0,
        timestamp = 0L,
        date = "2025-11-14",
        time = "16:20"
    )
)

@PreviewTest
@Preview(widthDp = 390, heightDp = 884, locale = "en-US")
@Composable
fun NutritionLoggedDayScreen() {
    ApexFitTheme {
        Scaffold(
            containerColor = ApexColors.Canvas,
            bottomBar = {
                Column {
                    NutritionAddFoodBar(onAddFood = {})
                    BottomNavBar(activeTab = 2, onTabSelected = {})
                }
            }
        ) { insets ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    NutritionHeader(
                        date = "2025-11-14",
                        onPreviousDay = {},
                        onNextDay = {},
                        canGoForward = false
                    )
                }
                item {
                    NutritionDailySummary(
                        calories = 2140,
                        target = 2450,
                        trainingDay = true,
                        macros = listOf(
                            "Protein" to "142 / 180 g",
                            "Carbs" to "195 / 250 g",
                            "Fat" to "52 / 70 g"
                        )
                    )
                }
                item {
                    Text(
                        "Today’s meals",
                        color = ApexColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(nutritionPreviewMeals, key = UiNutritionEntry::id) { meal ->
                    NutritionMealRow(meal = meal, onDelete = {})
                }
            }
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 844, locale = "en-US", fontScale = 1.2f)
@Composable
fun NutritionEmptyDayScreenLargeText() {
    ApexFitTheme {
        Scaffold(
            containerColor = ApexColors.Canvas,
            bottomBar = {
                Column {
                    NutritionAddFoodBar(onAddFood = {})
                    BottomNavBar(activeTab = 2, onTabSelected = {})
                }
            }
        ) { insets ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    NutritionHeader(
                        date = "2025-11-15",
                        onPreviousDay = {},
                        onNextDay = {},
                        canGoForward = false
                    )
                }
                item {
                    NutritionDailySummary(
                        calories = 0,
                        target = null,
                        trainingDay = false,
                        macros = listOf("Protein" to "0 g", "Carbs" to "0 g", "Fat" to "0 g")
                    )
                }
                item {
                    Text(
                        "Today's meals",
                        color = ApexColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                item {
                    Text(
                        "No meals logged yet.",
                        color = ApexColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}
