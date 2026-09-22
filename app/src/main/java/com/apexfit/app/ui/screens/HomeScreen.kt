package com.apexfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.AlgorithmViewModel
import com.apexfit.app.FitnessViewModel
import com.apexfit.app.HomeViewModel
import com.apexfit.app.NutritionViewModel
import com.apexfit.app.TrainViewModel
import com.apexfit.app.ui.models.UiPlateauResult
import com.apexfit.app.ui.theme.*

@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCardSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

val UiPlateauResult.isPlateaued: Boolean get() = this.isPlateau

@Composable
fun PlateauCard(plateauResult: UiPlateauResult) {
    if (!plateauResult.isPlateaued) return

    val daysText = if (plateauResult.daysStalled > 0)
        "${plateauResult.daysStalled} days without progress"
    else "Progress has stalled"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkRaised,
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(AmberAccent)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "PLATEAU DETECTED",
                    fontFamily = SyneFamily,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = AmberAccent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = daysText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = SecondaryText
                )
                if (plateauResult.interventions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    plateauResult.interventions.take(3).forEach { action ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "→ ",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = AmberAccent
                            )
                            Text(
                                text = action,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = PrimaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    fitnessViewModel: FitnessViewModel,
    algorithmViewModel: AlgorithmViewModel,
    homeViewModel: HomeViewModel,
    trainViewModel: TrainViewModel,
    nutritionViewModel: NutritionViewModel,
    onNavigateTo: (Int) -> Unit
) {
    HomeWeightErrorBanner(homeViewModel = homeViewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Header Section
        HomeHeaderSection(
            fitnessViewModel = fitnessViewModel,
            algorithmViewModel = algorithmViewModel,
            trainViewModel = trainViewModel,
            homeViewModel = homeViewModel
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Today's Training Card
        HomeTodayWorkoutCard(
            trainViewModel = trainViewModel,
            algorithmViewModel = algorithmViewModel,
            fitnessViewModel = fitnessViewModel,
            homeViewModel = homeViewModel,
            onNavigateTo = onNavigateTo
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Algorithm Cards (Plateau Detection, etc.)
        HomeAlgorithmCards(
            algorithmViewModel = algorithmViewModel
        )

        // 4. Streak Section
        HomeStreakSection(
            algorithmViewModel = algorithmViewModel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Matrix Card Row (Nutrition & Body Weight)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeNutritionRingCard(
                fitnessViewModel = fitnessViewModel,
                homeViewModel = homeViewModel,
                nutritionViewModel = nutritionViewModel,
                trainViewModel = trainViewModel,
                algorithmViewModel = algorithmViewModel,
                onNavigateTo = onNavigateTo,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            HomeWeightChartCard(
                fitnessViewModel = fitnessViewModel,
                homeViewModel = homeViewModel,
                onNavigateTo = onNavigateTo,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // 6. Analytics Section
        HomeAnalyticsSection(
            homeViewModel = homeViewModel,
            fitnessViewModel = fitnessViewModel
        )
    }
}
