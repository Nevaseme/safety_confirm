package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nevaseme.plandmk1.core.Safety

fun Safety.label() = when (this) { Safety.SAFE -> "無事"; Safety.SOS -> "SOS"; Safety.UNREPORTED -> "安否未登録" }

@Composable fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable fun SafetyBadge(safety: Safety) {
    val color = when (safety) { Safety.SAFE -> Color(0xFF176B43); Safety.SOS -> Color(0xFFB3261E); Safety.UNREPORTED -> Color(0xFF52606D) }
    Surface(color = color, contentColor = Color.White, shape = MaterialTheme.shapes.small) {
        Text(safety.label(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable fun InfoCard(text: String, error: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) { Text(text, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}
