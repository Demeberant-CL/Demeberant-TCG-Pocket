package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DexPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  OutlinedCard(modifier, shape = MaterialTheme.shapes.large,
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)) {
    HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 3.dp)
    content()
  }
}
