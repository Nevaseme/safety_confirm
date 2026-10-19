package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.*

fun BuildingAction.label()=when(this) {BuildingAction.CHECKING->"確認中";BuildingAction.NEEDS_HELP->"要対応";BuildingAction.COMPLETED->"対応完了の記録";BuildingAction.RECHECK->"再確認が必要";BuildingAction.REVOKED->"記録を撤回"}
fun buildingSummary(rows: List<PlaceEvent>,buildingId: String,incident: String)=currentBuildingCare(buildingCare(rows,buildingId,incident)).takeIf {it.isNotEmpty()}?.joinToString(" / "){it.action!!.label()}?:"建物の対応記録なし"
fun homeLabel(rows: List<PlaceEvent>,person: String): String {
    val home=homeFor(rows,person)?:return "建物・住戸は未登録"
    if(home.buildingId==null)return "建物の関連付けなし"
    val valid=validHome(rows,person)?:return "建物・住戸の詳細を受信待ち"
    val building=placeBuildings(rows).first {it.buildingId==valid.buildingId}
    return "建物名：${building.displayLabel()}\n部屋・住戸："+(placeUnits(rows,valid.buildingId).find {it.unitId==valid.unitId}?.label?:"未登録")
}
@Composable fun BuildingCard(building: PlaceEvent,rows: List<PlaceEvent>,incident: String,residents: List<Resident>,onClick: ()->Unit) {
    val members=residents.filter {validHome(rows,it.originId)?.buildingId==building.buildingId}
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        NamedEntity("建物名", building.displayLabel())
        BuildingResponse(rows,building.buildingId!!,incident)
        Text("住民の安否：SOS ${members.count {it.safety==Safety.SOS}}人 / 無事 ${members.count {it.safety==Safety.SAFE}}人 / 未回答 ${members.count {it.safety==Safety.UNREPORTED}}人")
        Text("詳細・対応履歴を見る",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
    }}
}
@Composable fun BuildingsScreen(model: PlanViewModel,onProfile: ()->Unit = {},onSelect: (String)->Unit) {
    val rows by model.places.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle();val saving by model.saving.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle();val own=residents.find {it.originId==model.localId}
    var create by rememberSaveable(group){mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("自分の住居を選ぶ",style=MaterialTheme.typography.headlineSmall)}
        if(own==null)item {Button(onClick=onProfile){Text("先に自分の情報を登録")}}
        item {Text("自分の住まい\n${homeLabel(rows,model.localId)}")}
        item {Button(enabled=own!=null&&!saving,onClick={create=true}){Text("建物を登録")}}
        item {OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING))}
        if(homeFor(rows,model.localId)?.buildingId!=null)item {TextButton(enabled=!saving,onClick={model.assignHome(null)}){Text("自分の建物の関連付けを解除")}}
        item {OperationFeedback(model,model.placeSaveKey(PlaceKind.HOME))}
        if(placeBuildings(rows).isEmpty())item {Text("建物はまだ登録されていません")}
        items(placeBuildings(rows),key={it.buildingId!!}) {b->BuildingCard(b,rows,incident.selection,residents){onSelect(b.buildingId!!)}}
    }
    if(create)BuildingForm(model,null,own){create=false}
}
@Composable private fun BuildingForm(model: PlanViewModel,building: PlaceEvent?,own: Resident?,close: ()->Unit) {
    val saving by model.saving.collectAsStateWithLifecycle()
    var label by rememberSaveable {mutableStateOf(building?.displayLabel().orEmpty())}
    var lat by rememberSaveable {mutableStateOf((building?.latitude?:own?.latitude)?.toString().orEmpty())}
    var lon by rememberSaveable {mutableStateOf((building?.longitude?:own?.longitude)?.toString().orEmpty())}
    AlertDialog(onDismissRequest={if(!saving)close()},title={Text(if(building==null)"建物を登録" else "建物情報を訂正")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(label,{label=it.take(80)},label={Text("建物名")},enabled=!saving)
        OutlinedTextField(lat,{lat=it},label={Text("建物の緯度")},enabled=!saving)
        OutlinedTextField(lon,{lon=it},label={Text("建物の経度")},enabled=!saving)
        OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING,building?.buildingId.orEmpty()))
    }},confirmButton={TextButton(enabled=!saving,onClick={model.registerBuilding(building?.buildingId,label,lat,lon){close()}}){Text("建物情報を保存")}},dismissButton={TextButton(enabled=!saving,onClick=close){Text("閉じる")}})
}
@Composable fun BuildingDetail(model: PlanViewModel,buildingId: String,onResident: (Resident)->Unit,onClose: ()->Unit,registration: Boolean = false) {
    val rows by model.places.collectAsStateWithLifecycle();val residents by model.residents.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle();val saving by model.saving.collectAsStateWithLifecycle()
    val own=residents.find {it.originId==model.localId};val building=placeBuildings(rows).find {it.buildingId==buildingId}?:return
    var editing by rememberSaveable {mutableStateOf(false)};var newUnit by rememberSaveable {mutableStateOf(false)}
    var unitName by rememberSaveable {mutableStateOf("")}
    var editingUnit by rememberSaveable {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!saving)onClose()},title={NamedEntity("建物名", building.displayLabel())},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        BuildingResponse(rows,buildingId,incident.selection)
        if(registration) {
        if(building.authorId==model.localId)TextButton(enabled=!saving,onClick={editing=true}){Text("建物情報を訂正")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.BUILDING,buildingId))
        Text("自分の住まい\n${homeLabel(rows,model.localId)}")
        TextButton(enabled=own!=null&&!saving,onClick={model.assignHome(buildingId)}){Text("この建物を自分の住居に設定（住戸未指定）")}
        Text("住戸",style=MaterialTheme.typography.titleMedium)
        placeUnits(rows,buildingId).forEach {unit->
            TextButton(enabled=own!=null&&!saving,onClick={model.assignHome(buildingId,unit.unitId)}){Text("${unit.label} を自分の住戸に設定")}
            if(unit.authorId==model.localId)TextButton(enabled=!saving,onClick={editingUnit=unit.unitId;unitName=unit.label;newUnit=true}){Text("${unit.label} の住戸名を訂正")}
        }
        TextButton(enabled=own!=null&&!saving,onClick={editingUnit=null;unitName="";newUnit=true}){Text("住戸を追加")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.UNIT,buildingId))
        OperationFeedback(model,model.placeSaveKey(PlaceKind.HOME))
        }
        HorizontalDivider()
        Text("この建物に登録されている人",style=MaterialTheme.typography.titleMedium)
        BuildingPeople(rows,buildingId,residents,model.localId,onResident)
        key(incident.selection,buildingId) {BuildingCareEditor(model,buildingId,rows,incident.selection,own!=null&&incident.canEdit,saving)}
    }},confirmButton={TextButton(enabled=!saving,onClick=onClose){Text("閉じる")}})
    if(editing)BuildingForm(model,building,own){editing=false}
    if(newUnit)AlertDialog(onDismissRequest={if(!saving)newUnit=false},title={Text(if(editingUnit==null)"住戸を追加" else "住戸名を訂正")},text={Column {
        OutlinedTextField(unitName,{unitName=it.take(80)},label={Text("住戸名（例：201号室・戸建て）")},enabled=!saving)
        OperationFeedback(model,model.placeSaveKey(PlaceKind.UNIT,buildingId))
    }},confirmButton={TextButton(enabled=!saving,onClick={model.registerUnit(buildingId,unitName,editingUnit){newUnit=false;unitName=""}}){Text("住戸を保存")}},dismissButton={TextButton(enabled=!saving,onClick={newUnit=false}){Text("閉じる")}})
}
@Composable private fun BuildingCareEditor(model: PlanViewModel,buildingId: String,rows: List<PlaceEvent>,incident: String,canEdit: Boolean,saving: Boolean) {
    var action by rememberSaveable {mutableStateOf(BuildingAction.CHECKING)}
    var scope by rememberSaveable {mutableStateOf("")};var unresolved by rememberSaveable {mutableStateOf("")};var note by rememberSaveable {mutableStateOf("")};var supersedes by rememberSaveable {mutableStateOf<String?>(null)}
    val history=buildingCare(rows,buildingId,incident).sortedWith(compareByDescending<PlaceEvent>{it.createdAt}.thenBy {it.eventId})
    val heads=currentBuildingCare(history).map {it.eventId}.toSet()
    HorizontalDivider();Text("建物への対応履歴",style=MaterialTheme.typography.titleMedium)
    if(history.isEmpty())Text("この災害の建物対応記録はありません")
    history.forEach {e->
        Text(e.action!!.label()+if(e.eventId !in heads)"（訂正前）" else "",style=MaterialTheme.typography.titleSmall)
        Text("記録した人：${samplePersonName(e.groupId,e.authorName)}\n記録日時：${formatTime(e.createdAt)}\n確認範囲：${e.scope}\n未確認事項：${e.unresolved}\n${e.note}")
        if(canEdit&&e.authorId==model.localId&&e.eventId in heads)TextButton(enabled=!saving,onClick={supersedes=e.eventId;action=e.action;scope=e.scope;unresolved=e.unresolved;note=e.note}){Text("この建物記録を訂正")}
        HorizontalDivider()
    }
    if(canEdit) {
        Text(if(supersedes==null)"建物の対応を記録" else "自分の建物記録を訂正")
        if(supersedes!=null)TextButton(enabled=!saving,onClick={supersedes=null;action=BuildingAction.CHECKING;scope="";unresolved="";note=""}){Text("新しい建物記録に戻す")}
        BuildingAction.entries.filter {it!=BuildingAction.REVOKED||supersedes!=null}.forEach {a->FilterChip(enabled=!saving,selected=a==action,onClick={action=a},label={Text(a.label())})}
        OutlinedTextField(scope,{scope=it.take(240)},label={Text("確認した範囲（必須）")},enabled=!saving)
        OutlinedTextField(unresolved,{unresolved=it.take(240)},label={Text("未確認事項（ない場合は「なし」）")},enabled=!saving)
        OutlinedTextField(note,{note=it.take(500)},label={Text("建物の対応内容・訂正理由")},enabled=!saving,minLines=2)
        Button(enabled=!saving,onClick={model.saveBuildingCare(buildingId,action,scope,unresolved,note,supersedes){supersedes=it.eventId}}){Text(if(saving)"保存しています…" else "建物の対応記録を保存")}
        OperationFeedback(model,model.placeSaveKey(PlaceKind.CARE,buildingId))
    }else Text("対応記録は閲覧のみ")
}
