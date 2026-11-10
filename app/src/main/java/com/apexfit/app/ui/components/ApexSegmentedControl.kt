package com.apexfit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexfit.app.ui.theme.ApexColors
import com.apexfit.app.ui.theme.InterFamily

@Composable
fun ApexSegmentedControl(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup()
            .clip(RoundedCornerShape(12.dp)).background(ApexColors.Surface).padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) ApexColors.Primary else ApexColors.Surface)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected) ApexColors.OnGlow else ApexColors.TextSecondary,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
