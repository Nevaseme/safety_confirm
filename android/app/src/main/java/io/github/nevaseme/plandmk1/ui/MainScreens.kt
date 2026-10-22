package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.*
import io.github.nevaseme.plandmk1.core.*

@Composable fun PlanIcon(kind: String, description: String?) {
    val ink = if (kind == "menu" || kind == "back") MaterialTheme.colorScheme.primary else LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { description?.let { contentDescription = it } }) {
        val s = size.width / 24f; val stroke = 2f * s
        fun line(x: Float, y: Float, u: Float, v: Float) = drawLine(ink, Offset(x*s,y*s), Offset(u*s,v*s), stroke)
        when (kind) {
            "menu" -> { line(3f,6f,21f,6f); line(3f,12f,21f,12f); line(3f,18f,21f,18f) }
            "back" -> { line(20f,12f,4f,12f); line(4f,12f,11f,5f); line(4f,12f,11f,19f) }
            "close" -> { line(5f,5f,19f,19f); line(5f,19f,19f,5f) }
            "map" -> {
                drawPath(Path().apply { moveTo(3*s,5*s);lineTo(9*s,3*s);lineTo(15*s,6*s);lineTo(21*s,4*s);lineTo(21*s,19*s);lineTo(15*s,21*s);lineTo(9*s,18*s);lineTo(3*s,20*s);close() },ink,style=Stroke(stroke))
                line(9f,3f,9f,18f);line(15f,6f,15f,21f)
            }
            "target" -> { drawCircle(ink,7*s,Offset(12*s,12*s),style=Stroke(stroke));line(12f,1f,12f,6f);line(12f,18f,12f,23f);line(1f,12f,6f,12f);line(18f,12f,23f,12f) }
            else -> { drawCircle(ink,4*s,Offset(12*s,7*s));drawPath(Path().apply {moveTo(3*s,23*s);cubicTo(3*s,10*s,21*s,10*s,21*s,23*s);close()},ink) }
        }
    }
}

@Composable fun planNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF7C8793), unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF7C8793),
    indicatorColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable fun SafetyScreen(model: PlanViewModel, own: Resident?, saving: Boolean, onProfile: () -> Unit, onIncident: () -> Unit) {
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    if (own == null || !incident.canEdit) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("自分の安否", style = MaterialTheme.typography.headlineSmall)
            Text(if (own == null) "名前と住居を登録してください" else "安否確認の開始を待っています")
            Button(onClick = if (own == null) onProfile else onIncident) { Text(if (own == null) "自分の情報を登録" else "災害・訓練の管理") }
        }
    } else {
        val first = incident.current?.answers?.containsKey(model.localId) != true
        AnswerPanel(first, when (own.safety) { Safety.SAFE -> MapSafety.SAFE; Safety.SOS -> MapSafety.SOS; else -> null }, "", saving, allowMemo = false,
            save = { state, _ -> model.setSafety(if (state == MapSafety.SAFE) Safety.SAFE else Safety.SOS) },
            feedback = { OperationFeedback(model, "safety") })
    }
}

@Composable fun PeaceHome(model: PlanViewModel, registered: Boolean, onProfile: () -> Unit, onIncident: () -> Unit, onPreview: () -> Unit) {
    val nearby by model.sharing.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("もしものときに、\n地域でつながる。", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(if (registered) "安否確認の開始を待っています" else "はじめに自分の住居・寝室を登録しましょう", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!registered) Button(onClick = onProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("自分の情報を登録") }
        else OutlinedButton(onClick = onIncident, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("災害・訓練の管理") }
        Text(if (nearby.active) "共有中・接続 ${nearby.connected.size}台" else "共有は停止中", style = MaterialTheme.typography.bodyMedium)
        if (BuildConfig.DEBUG) { Spacer(Modifier.weight(1f)); TextButton(onClick = onPreview) { Text("画面デモを試す") } }
    }
}

@Composable fun MapScreen(model: PlanViewModel, residents: List<Resident>, own: Resident?, picking: Boolean, onPick: (MapPoint) -> Unit, onCancelPick: () -> Unit, onSelect: (Resident) -> Unit, onBuilding: (String) -> Unit, openList: Boolean = false, onListClosed: () -> Unit = {}) {
    val care by model.careEvents.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val people = remember(residents, places, care) { residents.map { residentMapPerson(it, places, care) } }
    val homes = placeBuildings(places).map { MapHome(it.buildingId!!, it.displayLabel(), MapPoint(it.latitude!!, it.longitude!!)) }
    val center = own?.let { MapPoint(it.latitude, it.longitude) } ?: MapPoint(32.7904, 130.819)
    if (picking) Box(Modifier.fillMaxSize()) {
        ResidentMap(emptyList(), center, true, onPick, onSelect, {}, Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = MaterialTheme.shapes.medium) { Text("住居の位置をタップ", Modifier.padding(12.dp)) }
        Button(onClick = onCancelPick, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text("位置選択をやめる") }
    } else SituationScreen(people, homes, center, now, canRescue = own != null && incident.canEdit && !saving,
        canUndo = { p -> currentCareEvents(care.filter { it.subjectId == p.id }).any { it.action == CareAction.RESCUED && it.authorId == model.localId } },
        onRescue = { model.saveCare(it.id, CareAction.RESCUED, "本人の救助完了を確認") },
        onUndo = { p -> currentCareEvents(care.filter { it.subjectId == p.id }).find { it.action == CareAction.RESCUED && it.authorId == model.localId }?.let { model.saveCare(p.id, CareAction.REVOKED, "救助完了の記録を訂正", it.eventId) } },
        history = { p -> residents.find { it.originId == p.id }?.let { CareHistoryAndEditor(model, it, care, own != null && incident.canEdit, saving) } },
        feedback = { p -> OperationFeedback(model, model.careSaveKey(p.id)) },
        openList = openList, onListClosed = onListClosed)
}

@Composable fun AppInformation() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("PlanD Mk1", style = MaterialTheme.typography.headlineMedium)
        Text("バージョン ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
        HorizontalDivider()
        Text("地図：OpenFreeMap / OpenMapTiles / OpenStreetMap", style = MaterialTheme.typography.bodySmall)
        Text("衝撃アイコン：Font Awesome Free Burst / Fonticons, Inc. / CC BY 4.0", style = MaterialTheme.typography.bodySmall)
    }
}
