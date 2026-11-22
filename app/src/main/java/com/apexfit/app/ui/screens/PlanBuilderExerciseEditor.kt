package com.apexfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.R
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.ApexIcons
import com.apexfit.app.ui.theme.ArchivoFamily
import com.apexfit.app.utils.MuscleGroups

internal enum class PlanBuilderExerciseEditorField {
    NAME, SETS, MIN_REPS, MAX_REPS, WEIGHT, REST, NOTES
}

internal data class PlanBuilderExerciseEditorForm(
    val name: String,
    val muscleGroup: String,
    val sets: String,
    val minReps: String,
    val maxReps: String,
    val weight: String,
    val weightUnit: String,
    val restSeconds: String,
    val notes: String,
    val nameError: String? = null,
    val setsError: String? = null,
    val minRepsError: String? = null,
    val maxRepsError: String? = null,
    val weightError: String? = null,
    val restError: String? = null
)

/** Shared by the production Plan Builder and its render fixtures. */
@Composable
internal fun PlanBuilderExerciseEditorDialog(
    form: PlanBuilderExerciseEditorForm,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onBrowseLibrary: () -> Unit,
    onFieldChange: (PlanBuilderExerciseEditorField, String) -> Unit,
    onMuscleChange: (String) -> Unit,
    onWeightUnitChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var showMuscleDropdown by rememberSaveable { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = ApexColors.TextPrimary,
        unfocusedTextColor = ApexColors.TextPrimary,
        focusedContainerColor = ApexColors.SurfaceSunken,
        unfocusedContainerColor = ApexColors.SurfaceSunken,
        focusedBorderColor = ApexColors.Primary,
        unfocusedBorderColor = ApexColors.BorderSubtle,
        focusedLabelColor = ApexColors.Primary,
        unfocusedLabelColor = ApexColors.TextSecondary,
        cursorColor = ApexColors.Primary,
        errorBorderColor = ApexColors.Alert,
        errorLabelColor = ApexColors.Alert
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ApexColors.Surface,
        title = {
            Text(
                text = if (isEditing) "Edit exercise" else "Add exercise",
                fontFamily = ArchivoFamily,
                fontWeight = FontWeight.SemiBold,
                color = ApexColors.TextPrimary,
                fontSize = 22.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.NAME, it) },
                    isError = form.nameError != null,
                    supportingText = form.nameError?.let { { Text(it) } },
                    label = { Text(stringResource(R.string.plan_builder_exercise_name), color = ApexColors.TextSecondary, fontFamily = ArchivoFamily) },
                    textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary, fontFamily = ArchivoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("exercise_name_input"),
                    colors = fieldColors,
                    singleLine = true
                )

                TextButton(onClick = onBrowseLibrary, modifier = Modifier.heightIn(min = 48.dp).testTag("browse_exercise_library_button")) {
                    Text("Browse exercise library", color = ApexColors.Primary, fontFamily = ArchivoFamily)
                }
                Text(
                    stringResource(R.string.plan_builder_muscle_group),
                    fontFamily = ArchivoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ApexColors.TextSecondary
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showMuscleDropdown = true },
                        modifier = Modifier.fillMaxWidth().testTag("muscle_group_dropdown"),
                        border = BorderStroke(1.dp, ApexColors.BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ApexColors.TextPrimary,
                            containerColor = ApexColors.SurfaceSunken
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(form.muscleGroup, fontFamily = ArchivoFamily, fontSize = 14.sp)
                            Icon(painterResource(ApexIcons.ChevronDown), contentDescription = null, tint = ApexColors.TextSecondary)
                        }
                    }

                    DropdownMenu(
                        expanded = showMuscleDropdown,
                        onDismissRequest = { showMuscleDropdown = false },
                        modifier = Modifier.background(ApexColors.SurfaceRaised).border(BorderStroke(1.dp, ApexColors.BorderSubtle))
                    ) {
                        MuscleGroups.ALL.forEach { muscle ->
                            DropdownMenuItem(
                                text = { Text(muscle, color = ApexColors.TextPrimary, fontFamily = ArchivoFamily, fontSize = 14.sp) },
                                onClick = {
                                    onMuscleChange(muscle)
                                    showMuscleDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorNumberField(
                        value = form.sets,
                        label = stringResource(R.string.plan_builder_sets),
                        error = form.setsError,
                        tag = "exercise_sets_input",
                        weight = 1f,
                        colors = fieldColors,
                        onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.SETS, it) }
                    )
                    EditorNumberField(
                        value = form.minReps,
                        label = stringResource(R.string.plan_builder_min_reps),
                        error = form.minRepsError,
                        tag = "exercise_reps_min_input",
                        weight = 1.1f,
                        colors = fieldColors,
                        onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.MIN_REPS, it) }
                    )
                    EditorNumberField(
                        value = form.maxReps,
                        label = stringResource(R.string.plan_builder_max_reps),
                        error = form.maxRepsError,
                        tag = "exercise_reps_max_input",
                        weight = 1.1f,
                        colors = fieldColors,
                        onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.MAX_REPS, it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = form.weight,
                        onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.WEIGHT, it) },
                        isError = form.weightError != null,
                        supportingText = form.weightError?.let { { Text(it) } },
                        label = { Text("Starting weight (${form.weightUnit})", color = ApexColors.TextSecondary, fontFamily = ArchivoFamily) },
                        textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary, fontFamily = ArchivoFamily),
                        modifier = Modifier.weight(1f).testTag("exercise_weight_input"),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(ApexColors.SurfaceSunken)
                            .border(BorderStroke(1.dp, ApexColors.BorderSubtle), RoundedCornerShape(6.dp))
                            .heightIn(min = 56.dp).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("kg", "lb").forEach { unit ->
                            val isSelected = form.weightUnit.equals(unit, ignoreCase = true)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ApexColors.Primary else Color.Transparent)
                                    .semantics { selected = isSelected }
                                    .clickable(role = Role.Tab) { onWeightUnitChange(unit) }
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("unit_toggle_$unit"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unit.uppercase(),
                                    fontFamily = ArchivoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ApexColors.OnGlow else ApexColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = form.restSeconds,
                    onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.REST, it) },
                    isError = form.restError != null,
                    supportingText = form.restError?.let { { Text(it) } },
                    label = { Text(stringResource(R.string.plan_builder_rest_seconds), color = ApexColors.TextSecondary, fontFamily = ArchivoFamily) },
                    textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary, fontFamily = ArchivoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("exercise_rest_input"),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFieldChange(PlanBuilderExerciseEditorField.NOTES, it) },
                    label = { Text(stringResource(R.string.plan_builder_optional_notes), color = ApexColors.TextSecondary, fontFamily = ArchivoFamily) },
                    textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary, fontFamily = ArchivoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("exercise_notes_input"),
                    colors = fieldColors,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ApexColors.Primary,
                    contentColor = ApexColors.OnGlow
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("save_exercise_confirm_button")
            ) {
                Text(stringResource(R.string.plan_builder_save), fontFamily = ArchivoFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plan_builder_cancel), fontFamily = ArchivoFamily, color = ApexColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EditorNumberField(
    value: String,
    label: String,
    error: String?,
    tag: String,
    weight: Float,
    colors: androidx.compose.material3.TextFieldColors,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        label = { Text(label, color = ApexColors.TextSecondary, fontSize = 12.sp, fontFamily = ArchivoFamily) },
        textStyle = LocalTextStyle.current.copy(color = ApexColors.TextPrimary, fontFamily = ArchivoFamily),
        modifier = Modifier.weight(weight).testTag(tag),
        colors = colors,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

