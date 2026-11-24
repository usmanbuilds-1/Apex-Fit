@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.apexfit.app.data.PlanExercise
import com.apexfit.app.data.PlanSession
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.ui.theme.ApexIcons
import com.apexfit.app.ui.theme.InterFamily

@PreviewTest
@Preview(widthDp = 390, heightDp = 1026, locale = "en-US")
@Composable
fun PlanBuilderFullScreenNightLedger() {
    val sessions = listOf(
        PlanSession(1, 1, "Upper A", "Monday", "Chest, Back, Shoulders"),
        PlanSession(2, 1, "Lower A", "Tuesday", "Quads, Hamstrings"),
        PlanSession(3, 1, "Upper B", "Thursday", "Chest, Back, Arms"),
        PlanSession(4, 1, "Lower B", "Friday", "Quads, Glutes")
    )
    val exercises = listOf(
        PlanExercise(1, 1, "db-chest-press", "Dumbbell Chest Press", "Chest", 3, 8, 12, 14.0, "kg", 120, ""),
        PlanExercise(2, 1, "lat-pulldown", "Lat Pulldown", "Back", 3, 8, 12, 30.0, "kg", 120, ""),
        PlanExercise(3, 1, "lateral-raise", "Lateral Raise", "Shoulders", 3, 12, 15, 6.0, "kg", 90, "")
    )

    ApexFitTheme {
        Scaffold(
            containerColor = ApexColors.Canvas,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Plan Builder", fontFamily = InterFamily, fontSize = 28.sp,
                            color = ApexColors.TextPrimary)
                    },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(ApexIcons.ArrowLeft), contentDescription = "Back",
                                tint = ApexColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ApexColors.Canvas)
                )
            },
            bottomBar = { PlanBuilderSaveBar(isSaving = false, onSave = {}) }
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanBuilderMetadataFields(
                    planName = "APEX Upper / Lower",
                    planGoal = "Build muscle",
                    planNameError = null,
                    onPlanNameChange = {},
                    onPlanGoalChange = {}
                )
                Text("Days", style = MaterialTheme.typography.titleLarge,
                    color = ApexColors.TextPrimary)
                sessions.forEachIndexed { index, session ->
                    PlanBuilderSessionRow(
                        session = session,
                        isSelected = index == 0,
                        canMoveUp = index > 0,
                        canMoveDown = index < sessions.lastIndex,
                        onSelect = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onEdit = {},
                        onDelete = {}
                    )
                }
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(ApexIcons.Add), contentDescription = null)
                        Text("Add day")
                    }
                }
                Text("Upper A", style = MaterialTheme.typography.titleLarge,
                    color = ApexColors.TextPrimary)
                exercises.forEach { exercise ->
                    PlanBuilderExerciseRow(exercise, "kg", onEdit = {}, onDelete = {})
                }
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(ApexIcons.Add), contentDescription = null)
                        Text("Add exercise")
                    }
                }
            }
        }
    }
}

@PreviewTest
@Preview(widthDp = 390, heightDp = 890, locale = "en-US")
@Composable
fun SettingsFullScreenNightLedger() {
    ApexFitTheme {
        Scaffold(
            containerColor = ApexColors.Canvas,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Settings", fontFamily = InterFamily, fontSize = 28.sp,
                            color = ApexColors.TextPrimary)
                    },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(ApexIcons.ArrowLeft), contentDescription = "Back",
                                tint = ApexColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ApexColors.Canvas)
                )
            }
        ) { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsSectionTitle("Profile")
                SettingsDisclosureRow("Name", "Ayesha", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Training goal", "Build muscle", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Nutrition target", "Automatic", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Personal details", "168 cm · 29 years · Female", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Weekly sessions", "4 per week", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Units", "kg · cm", expanded = false, onClick = {}) {}
                SettingsDisclosureRow("Weight", "Now 78.4 · Goal 74.0 kg", expanded = false, onClick = {}) {}

                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ApexColors.Primary)) {
                        Text("Save profile", color = ApexColors.OnGlow)
                    }
                }

                SettingsSectionTitle("Data")
                SettingsActionRow(Modifier.fillMaxWidth(), onClick = {}) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Privacy Policy", color = ApexColors.TextPrimary)
                        Icon(painterResource(ApexIcons.ExternalLink), contentDescription = "Opens in browser",
                            tint = ApexColors.TextSecondary)
                    }
                }
                SettingsActionRow(Modifier.fillMaxWidth(), onClick = {}) {
                    Text("Export My Data (CSV)", color = ApexColors.TextPrimary)
                }
                SettingsActionRow(Modifier.fillMaxWidth(), onClick = {}) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Terms of Service", color = ApexColors.TextPrimary)
                        Icon(painterResource(ApexIcons.ExternalLink), contentDescription = "Opens in browser",
                            tint = ApexColors.TextSecondary)
                    }
                }
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Delete local data", color = ApexColors.Alert)
                    }
                }
            }
        }
    }
}
