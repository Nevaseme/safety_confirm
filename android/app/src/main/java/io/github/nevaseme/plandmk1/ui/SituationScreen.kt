package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StateSymbol(state: MapSafety, modifier: Modifier = Modifier, description: String? = state.label) {
    Canvas(modifier.size(36.dp).semantics { description?.let { contentDescription = it } }) {
        drawState(drawContext.canvas.nativeCanvas, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG), state,
            size.width / 2, size.height / 2, size.minDimension / 2 - 3.dp.toPx())
    }
}

fun positionDescription(person: MapPerson, now: Long): String = when {
    person.acquiredAt == null || person.point == null -> "位置は未受信"
    !person.hasPosition(now) -> "3日以上更新なし"
    else -> "${SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(person.acquiredAt))}取得・${((now - person.acquiredAt) / 60_000).coerceAtLeast(0)}分前" + (person.accuracy?.let { "・精度 約${it}m" } ?: "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationScreen(
    people: List<MapPerson>, homes: List<MapHome>, center: MapPoint, now: Long,
    preview: Boolean = false, canRescue: Boolean = true,
    canUndo: (MapPerson) -> Boolean = { true },
    onRescue: (MapPerson) -> Unit = {}, onUndo: (MapPerson) -> Unit = {},
    history: @Composable (MapPerson) -> Unit = {},
    feedback: @Composable (MapPerson) -> Unit = {},
    openList: Boolean = false, onListClosed: () -> Unit = {},
) {
    var showSafe by rememberSaveable { mutableStateOf(true) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var homeId by rememberSaveable { mutableStateOf<String?>(null) }
    var overlapping by remember { mutableStateOf<List<MapPerson>>(emptyList()) }
    var extraHome by remember { mutableStateOf<MapHome?>(null) }
    var failed by remember { mutableStateOf(false) }
    val camera = remember { SituationCamera() }
    val unknown = people.filterNot { it.hasPosition(now) }
    LaunchedEffect(openList) { if (openList) sheet = "all" }
    val close = { sheet = null; onListClosed() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("無事を表示", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Switch(showSafe, { showSafe = it }, Modifier.semantics { contentDescription = "無事を表示" })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet = "unknown" }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                PlanIcon("person", null)
                Spacer(Modifier.width(4.dp)); Text("位置不明 ${unknown.size}人", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SituationMap(people, homes, center, showSafe, now, preview, camera,
                onPerson = { selectedId = it.id; sheet = "person" },
                onHome = { extraHome = it; homeId = it.id; sheet = "home" },
                onOverlapping = { overlapping = it; sheet = "overlap" }, onError = { failed = true }, modifier = Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, color = Color.White) {
                Column {
                    IconButton(onClick = { camera.zoom(1.0) }) { Text("+", fontSize = 26.sp, color = Color(0xFF183447), modifier = Modifier.semantics { contentDescription = "地図を拡大" }) }
                    HorizontalDivider(Modifier.width(40.dp))
                    IconButton(onClick = { camera.zoom(-1.0) }) { Text("−", fontSize = 26.sp, color = Color(0xFF183447), modifier = Modifier.semantics { contentDescription = "地図を縮小" }) }
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 32.dp), shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                TextButton(onClick = { sheet = "legend" }) { Text("凡例", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface); Text(" ›") }
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 36.dp), shape = CircleShape, shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = { camera.move(center) }) { PlanIcon("target", "表示の中心に戻る") }
            }
            if (failed) Surface(Modifier.align(Alignment.TopStart).padding(12.dp), shape = RoundedCornerShape(12.dp)) {
                TextButton(onClick = { sheet = "all" }) { Text("地図を読み込めません・一覧を見る", fontSize = 12.sp) }
            }
        }
    }
    if (sheet != null) {
        key(sheet) {
        ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            when (sheet) {
                "person" -> people.find { it.id == selectedId }?.let { person ->
                    PersonMapDetail(person, now, canRescue, canUndo(person), onRescue, onUndo, onHome = { home -> extraHome = home; homeId = home.id; sheet = "home" }, onClose = close, history = { history(person) }, feedback = { feedback(person) })
                }
                "home" -> (homes.find { it.id == homeId } ?: extraHome)?.let { home ->
                    Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailHeading("建物の詳細", home.address, close)
                        HomeAttributes(home)
                        Text("この建物に登録されている人", style = MaterialTheme.typography.titleMedium)
                        val members = people.filter { it.home?.id == home.id }
                        if (members.isEmpty()) Text("登録はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        members.forEach { person -> PersonRow(person, now, false) {
                            selectedId = person.id; sheet = "person"
                            if (person.hasPosition(now)) camera.move(person.point!!)
                        } }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                "legend" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailHeading("凡例", null, close)
                    Text("スマホの安否", style = MaterialTheme.typography.titleMedium)
                    MapSafety.entries.forEach { state -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { StateSymbol(state); Text(state.label) } }
                    Text("建物の強さ", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFFFBE8C5 to "強い", 0xFFF2AE5D to "中間", 0xFFC5672E to "弱い", 0xFFD4D7DC to "不明").forEach { (color, text) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.fillMaxWidth().height(22.dp).background(Color(color), RoundedCornerShape(4.dp))); Text(text, fontSize = 12.sp) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Observation(R.drawable.impact, "衝撃"); Observation(R.drawable.enclosed, "閉所") }
                    TextButton(onClick = { sheet = "all" }) { Text("住民一覧を開く") }
                    Spacer(Modifier.height(20.dp))
                }
                else -> {
                    val rows = when (sheet) { "unknown" -> unknown; "overlap" -> overlapping; else -> people }
                    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.8f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)) {
                        item { DetailHeading(if (sheet == "unknown") "位置不明 ${rows.size}人" else "住民一覧 ${rows.size}人", null, close) }
                        items(rows, key = { it.id }) { person -> PersonRow(person, now, sheet == "unknown") { selectedId = person.id; sheet = "person" } }
                        if (rows.isEmpty()) item { Text("該当する人はいません", Modifier.padding(vertical = 20.dp)) }
                    }
                }
            }
        }
        }
    }
}

@Composable private fun DetailHeading(title: String, subtitle: String?, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) } }
        IconButton(onClick = close) { PlanIcon("close", "詳細を閉じる") }
    }
}

@Composable private fun Observation(resource: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Image(painterResource(resource), null, Modifier.size(24.dp)); Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun PersonRow(person: MapPerson, now: Long, unknown: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!unknown) StateSymbol(person.state) else PlanIcon("person", null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person.publicName, style = MaterialTheme.typography.titleSmall)
            Text(if (unknown) person.home?.address ?: "住居未登録" else if (person.hasPosition(now)) person.state.label else "位置不明", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unknown) Text(person.bedroom, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun HomeAttributes(home: MapHome) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("建物の強さ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val color = when (home.rating) { 0 -> Color(0xFFFBE8C5); 1 -> Color(0xFFF2AE5D); 2 -> Color(0xFFC5672E); else -> Color(0xFFD4D7DC) }
        Surface(color = color.copy(alpha = .58f), shape = RoundedCornerShape(6.dp)) {
            Text(when (home.rating) { 0 -> "強い"; 1 -> "中間"; 2 -> "弱い"; else -> "不明" }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color(0xFF183447), style = MaterialTheme.typography.labelMedium)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("築年数" to (home.built?.let { "築${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - it}年" } ?: "不明"), "建材・構造" to home.material, "階数" to (home.floors?.let { "${it}階建" } ?: "不明")).forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable fun PersonMapDetail(person: MapPerson, now: Long, canRescue: Boolean, canUndo: Boolean, rescue: (MapPerson) -> Unit, undo: (MapPerson) -> Unit, onHome: (MapHome) -> Unit, onClose: () -> Unit, history: @Composable () -> Unit, feedback: @Composable () -> Unit = {}) {
    val needsHelp = person.state in listOf(MapSafety.SOS, MapSafety.UNANSWERED)
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (person.hasPosition(now)) StateSymbol(person.state, Modifier.size(46.dp)) else PlanIcon("person", null)
            Column(Modifier.weight(1f)) {
                Text(person.publicName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (person.hasPosition(now)) person.state.label else "位置不明", color = if (person.hasPosition(now)) Color(person.state.argb) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onClose) { PlanIcon("close", "詳細を閉じる") }
        }
        Text("最後のスマホ位置　${positionDescription(person, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (needsHelp) {
            if (person.hasPosition(now) && person.state == MapSafety.SOS && person.memo.isNotBlank()) {
                Text("SOSメモ", style = MaterialTheme.typography.titleMedium)
                Surface(color = Color(0xFFFFF0F3), shape = RoundedCornerShape(12.dp)) { Text(person.memo, Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFF7A2537)) }
            }
            HorizontalDivider()
            Text("普段の住居・寝室", style = MaterialTheme.typography.titleMedium)
            person.home?.let { home ->
                Row(verticalAlignment = Alignment.CenterVertically) { Text(home.address, Modifier.weight(1f), fontWeight = FontWeight.Bold); TextButton(onClick = { onHome(home) }) { Text("建物を見る ›") } }
            }
            Text(person.bedroom, style = MaterialTheme.typography.bodyLarge)
            person.home?.let { HomeAttributes(it) }
            if (person.impactAt != null || person.enclosedAt != null) {
                HorizontalDivider(); Text("スマホの観測", style = MaterialTheme.typography.titleMedium)
                person.impactAt?.let { Observation(R.drawable.impact, "衝撃　${formatTime(it)}") }
                person.enclosedAt?.let { Observation(R.drawable.enclosed, "閉所　${formatTime(it)}") }
            }
        }
        var expanded by rememberSaveable(person.id) { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text("救助・確認の記録 ${if (expanded) "⌃" else "⌄"}") }
        if (expanded) history()
        if (canRescue && needsHelp) Button(onClick = { rescue(person) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008C7D)), shape = RoundedCornerShape(14.dp)) { Text("救助済みにする", fontWeight = FontWeight.Bold) }
        if (canRescue && canUndo && person.state == MapSafety.RESCUED) TextButton(onClick = { undo(person) }, modifier = Modifier.fillMaxWidth()) { Text("救助済みの記録を訂正") }
        feedback()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnswerPanel(first: Boolean, initial: MapSafety?, initialMemo: String, saving: Boolean, allowMemo: Boolean = true, save: (MapSafety, String) -> Unit, feedback: @Composable () -> Unit = {}) {
    var selected by rememberSaveable(initial) { mutableStateOf(initial?.name) }
    var memo by rememberSaveable(initialMemo) { mutableStateOf(initialMemo) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(if (first) "いまの安否を教えてください" else "自分の安否", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (first) "回答すると地図を開きます" else "状況が変わったら更新できます", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(MapSafety.SAFE, MapSafety.SOS).forEach { state ->
                val chosen = selected == state.name
                OutlinedCard(onClick = { selected = state.name }, modifier = Modifier.weight(1f).height(126.dp), enabled = !saving,
                    border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { StateSymbol(state, Modifier.size(44.dp), null); Spacer(Modifier.height(8.dp)); Text(state.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (allowMemo && selected == MapSafety.SOS.name) OutlinedTextField(memo, { memo = it.take(500) }, label = { Text("SOSメモ") }, placeholder = { Text("今いる場所や、助けてほしいこと") }, minLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth())
        Button(onClick = { selected?.let { save(MapSafety.valueOf(it), memo) } }, enabled = selected != null && !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
            if (saving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            Text(if (saving) "保存しています…" else if (first) "安否を登録して地図へ" else "安否を更新する", fontWeight = FontWeight.Bold)
        }
        feedback()
    }
}
