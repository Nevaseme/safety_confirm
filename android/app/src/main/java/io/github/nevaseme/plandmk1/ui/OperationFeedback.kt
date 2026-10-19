package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.*
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusFeedback(text: String, error: Boolean = false, busy: Boolean = false) {
    if (text.isBlank()) return
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(text)
        }
    }
}

@Composable
fun OperationFeedback(model: PlanViewModel, key: String) {
    val states by model.saveStates.collectAsStateWithLifecycle()
    val state = states[key] ?: return
    val time = state.savedAt?.let { "\n保存時刻：${DateFormat.getTimeInstance().format(Date(it))}" }.orEmpty()
    StatusFeedback(state.text + time, state.phase == SavePhase.ERROR, state.phase == SavePhase.SAVING)
}
