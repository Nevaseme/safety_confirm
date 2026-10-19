package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.*
import io.github.nevaseme.plandmk1.core.*

/** Small bundled glyphs keep navigation available offline without an icon dependency. */
@Composable fun PlanIcon(kind: String, description: String?) {
    val ink=LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { if(description!=null)contentDescription=description }) {
        val s=size.width/24f;val stroke=2f*s
        fun line(x:Float,y:Float,u:Float,v:Float)=drawLine(ink,Offset(x*s,y*s),Offset(u*s,v*s),stroke)
        when(kind) {
            "menu" -> {line(3f,6f,21f,6f);line(3f,12f,21f,12f);line(3f,18f,21f,18f)}
            "back" -> {line(20f,12f,4f,12f);line(4f,12f,11f,5f);line(4f,12f,11f,19f)}
            "close" -> {line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
            "map" -> {val p=Path().apply {moveTo(3*s,5*s);lineTo(9*s,3*s);lineTo(15*s,6*s);lineTo(21*s,4*s);lineTo(21*s,19*s);lineTo(15*s,21*s);lineTo(9*s,18*s);lineTo(3*s,20*s);close()};drawPath(p,ink,style=Stroke(stroke));line(9f,3f,9f,18f);line(15f,6f,15f,21f)}
            else -> {val p=Path().apply {moveTo(12*s,2*s);lineTo(21*s,6*s);lineTo(20*s,14*s);quadraticTo(18*s,20*s,12*s,23*s);quadraticTo(6*s,20*s,4*s,14*s);lineTo(3*s,6*s);close()};drawPath(p,ink,style=Stroke(stroke));line(7f,12f,11f,16f);line(11f,16f,17f,9f)}
        }
    }
}

@Composable fun SafetyScreen(model:PlanViewModel,own:Resident?,saving:Boolean,onProfile:()->Unit,onIncident:()->Unit) {
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {Text("自分の安否を知らせる",style=MaterialTheme.typography.headlineSmall)}
        if(own==null) {
            item {Text("最初に名前と住居を登録してください。")}
            item {Button(onClick=onProfile,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text("自分の情報を登録")}}
        } else {
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {NamedEntity("自分の名前", own.displayName());Text("更新：${formatTime(own.updatedAt)}",style=MaterialTheme.typography.bodySmall)}
                    SafetyBadge(own.safety)
                }
            }
            if(!incident.canEdit) item {
                Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(incident.current?.closed==true)"安否確認は終了しました" else "安否確認を始めましょう")
                        TextButton(onClick=onIncident){Text("安否確認を開始・選択")}
                    }
                }
            }
            item {
                Button(onClick={model.setSafety(Safety.SAFE)},enabled=!saving&&incident.canEdit,
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF18794E),contentColor=Color.White),
                    modifier=Modifier.fillMaxWidth().heightIn(min=80.dp),shape=MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {Text("無事を登録",style=MaterialTheme.typography.titleLarge);Text("自分の無事を知らせる",style=MaterialTheme.typography.bodyMedium)}
                }
            }
            item {
                Button(onClick={model.setSafety(Safety.SOS)},enabled=!saving&&incident.canEdit,
                    colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),
                    modifier=Modifier.fillMaxWidth().heightIn(min=80.dp),shape=MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {Text("SOSを登録",style=MaterialTheme.typography.titleLarge);Text("助けが必要なことを知らせる",style=MaterialTheme.typography.bodyMedium)}
                }
            }
            item {OperationFeedback(model,"safety")}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapScreen(model:PlanViewModel,residents:List<Resident>,own:Resident?,picking:Boolean,onPick:(MapPoint)->Unit,onCancelPick:()->Unit,onSelect:(Resident)->Unit,onBuilding:(String)->Unit) {
    val offline by model.maps.state.collectAsStateWithLifecycle()
    val care by model.careEvents.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    var error by remember {mutableStateOf<String?>(null)}
    var reload by remember {mutableIntStateOf(0)}
    var list by rememberSaveable {mutableStateOf(false)}
    var buildingsLayer by rememberSaveable {mutableStateOf(false)}
    val center=own?.let{MapPoint(it.latitude,it.longitude)}?:MapPoint(offline.latitude?:35.681236,offline.longitude?:139.767125)
    Box(Modifier.fillMaxSize()) {
        key(reload) {ResidentMap(if(buildingsLayer||picking) emptyList() else residents,center,picking,onPick,onSelect,{error=it},Modifier.fillMaxSize(),
            careLabels=residents.filter {r->care.any {it.subjectId==r.originId}}.associate {it.originId to careSummary(care,it.originId)},
            places=if(buildingsLayer&&!picking) places else emptyList(),incident=incident.selection,onBuilding=onBuilding)}
        if(!picking) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!buildingsLayer,onClick={buildingsLayer=false},label={Text("人の安否")})
                FilterChip(selected=buildingsLayer,onClick={buildingsLayer=true},label={Text("建物への対応")})
            }
        }
        if(picking) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.96f),shape=MaterialTheme.shapes.small,shadowElevation=1.dp) {
            Text("住居の位置をタップ",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=48.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp)) {Text("地図を読み込めません。一覧は利用できます。",style=MaterialTheme.typography.bodySmall);TextButton(onClick={error=null;reload++}){Text("地図を再読み込み")}}
            }
            if(picking) Button(onClick=onCancelPick,modifier=Modifier.fillMaxWidth()){Text("位置選択をやめる")}
            else FilledTonalButton(onClick={list=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Text("住民・建物の一覧",style=MaterialTheme.typography.titleMedium)
                    Text("${residents.size}人の登録  /  SOS ${residents.count {it.safety==Safety.SOS}}人",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(list) ModalBottomSheet(onDismissRequest={list=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.86f),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("建物と住民",style=MaterialTheme.typography.titleLarge);TextButton(onClick={list=false}){Text("閉じる")}}}
            items(placeBuildings(places),key={"building:${it.buildingId}"}) {b->
                HouseholdCard(b,places,incident.selection,residents,model.localId,
                    onBuilding={list=false;onBuilding(b.buildingId!!)},onResident={list=false;onSelect(it)})
            }
            val unlinked=residents.filter {validHome(places,it.originId)==null}
            if(unlinked.isNotEmpty()) item {SectionTitle("住まい未登録・受信待ち")}
            items(unlinked.sortedWith(compareBy<Resident>{if(it.safety==Safety.SOS)0 else 1}.thenBy{it.name}),key={it.originId}) {person->
                ResidentEntry(person,model.localId){list=false;onSelect(person)}
            }
            if(residents.isEmpty()&&placeBuildings(places).isEmpty()) item {Text("住民・建物の登録はまだありません")}
        }
    }
}

@Composable fun AppInformation() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("PlanD Mk1",style=MaterialTheme.typography.headlineMedium)
        Text("バージョン ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.labelLarge)
        HorizontalDivider()
        Text("地図：OpenFreeMap / OpenMapTiles / OpenStreetMap",style=MaterialTheme.typography.bodySmall)
    }
}
