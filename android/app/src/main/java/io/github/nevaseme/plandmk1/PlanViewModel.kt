package io.github.nevaseme.plandmk1

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nevaseme.plandmk1.core.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


data class MapPoint(val latitude: Double, val longitude: Double)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PlanDApplication
    val localId = app.installation.localId
    val group = app.installation.group
    val message = MutableStateFlow<String?>(null)
    val roster=group.flatMapLatest {app.quakes.observeRoster(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    fun canEndIncident(startedBy:String)=roster.value?.members?.any {localId in it.deviceIds&&it.role==MemberRole.ADMIN} ?: (startedBy==localId)
    fun importParticipants(json:String) {
        viewModelScope.launch {
            try {app.quakes.importRoster(json,localId);message.value="参加者設定を保存しました"}
            catch(e:CancellationException){throw e}
            catch(e:Exception){message.value=e.localizedMessage}
        }
    }
    val incidentUi = group.flatMapLatest { g ->
        val authority=combine(app.quakes.observeRoster(g),app.quakes.observe(g)) {roster,events->roster to events}
        combine(app.store.observe(g),app.store.observeEvents(g),app.store.observeIncidents(g),app.installation.incidentSelectionChanges,authority) { residents, care, journal, _, authorityState ->
            val preferred=canonicalIncidentId(authorityState.first,authorityState.second,app.installation.incidentSelection(g))
            val options=managedIncidentOptions(journal,authorityState.first,authorityState.second)
            val state=projectIncidentUi(g,residents,care,journal,preferred,options)
            if(preferred==null&&state.selection.isNotEmpty()) {
                try {app.installation.selectIncident(g,state.selection)}
                catch(e: Exception) {message.value=e.localizedMessage;return@combine projectIncidentUi(g,residents,care,journal,"",options)}
            }
            state
        }
    }.stateIn(viewModelScope,SharingStarted.Eagerly,IncidentUiState())
    val residents = combine(group,incidentUi) { g,s -> if(g==s.groupId) s.residents else emptyList() }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val placeSource=group.flatMapLatest {g->combine(app.store.observePlaces(g),app.quakes.observeRoster(g),app.quakes.observe(g)) {rows,roster,quakes->g to rows.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))}}}
    val places=combine(group,placeSource) {g,s->if(g==s.first)s.second else emptyList()}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    private val positionSource = group.flatMapLatest { g -> app.store.observePositions(g).map { g to it } }
    val positions = combine(group, positionSource) { g, data -> if (g == data.first) data.second else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val memoSource = group.flatMapLatest { g -> app.store.observeMemos(g).map { g to it } }
    val memos = combine(group, memoSource) { g, data -> if (g == data.first) data.second else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sharing = app.sharing
    val position = app.position
    val lab = app.lab
    val careEvents = combine(group,incidentUi) { g,s -> if(g==s.groupId) s.care else emptyList() }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val responseEvents=group.flatMapLatest {g->combine(app.responses.observe(g),app.quakes.observeRoster(g),app.quakes.observe(g)) {rows,roster,quakes->
        if(roster==null)emptyList() else responseHeads(roster,rows.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))})
    }}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    fun saveResponse(change:ResponseChange,replaces:List<String> = emptyList()) {
        val g=group.value;val incident=if(change is ResponseChange.Availability)null else incidentUi.value.takeIf {it.groupId==g}?.current?.start?.eventId
        viewModelScope.launch {
            try {
                val eventId=if(replaces.isEmpty())when(change){is ResponseChange.Report->change.reportId;is ResponseChange.Material->change.requestId;else->java.util.UUID.randomUUID().toString()}else java.util.UUID.randomUUID().toString()
                app.responses.append(ResponseEvent(g,eventId,localId,1,System.currentTimeMillis(),incident,change,replaces))
                sharing.changed();lab.changed();message.value="対応記録を保存しました"
            }catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.localizedMessage}
        }
    }
    fun cancelEarthquakeReport(reportId:String) {
        val g=group.value
        viewModelScope.launch {
            try {
                val report=app.quakes.events(g).first {it.eventId==reportId&&it.action==QuakeAction.REPORT}
                app.quakes.command(QuakeEvent(g,java.util.UUID.randomUUID().toString(),localId,System.currentTimeMillis(),QuakeAction.CANCEL,report.training,targetId=reportId))
                sharing.changed();lab.changed();message.value="地震報告を取り消しました"
            }catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.localizedMessage}
        }
    }
    val maps = app.offlineMaps
    val saving = MutableStateFlow(false)
    private val saveOperations = SaveOperations(saving)
    val saveStates = saveOperations.states
    fun careSaveKey(subject: String) = "care:${group.value}:${incidentUi.value.selection}:$subject"
    val pickedPoint = MutableStateFlow<MapPoint?>(null)
    val openSafety = MutableStateFlow(false)
    var notificationPrompted: Boolean
        get() = app.installation.notificationPrompted
        set(value) { app.installation.notificationPrompted = value }
    fun saveProfile(groupId: String, name: String, lat: String, lon: String, floor: String, room: String) {
        if (!saveOperations.begin("profile")) return
        viewModelScope.launch {
            val g = groupId.trim()
            val changingGroup = group.value != g
            saveOperations.complete("profile", "登録情報", persist = {
                val candidate = Resident(g, localId, 1, 0, name.trim(), lat.toDoubleOrNull() ?: error("緯度を数値で入力してください"),
                    lon.toDoubleOrNull() ?: error("経度を数値で入力してください"), floor.trim(), room.trim())
                candidate.validate()
                if (changingGroup) { position.stop(); sharing.stop(); lab.stop(text = "グループを変更したため停止しました") }
                app.store.updateOwn(g, localId) { previous -> candidate.copy(safety = previous?.safety ?: Safety.UNREPORTED) }
            }, publish = {
                if (changingGroup) { app.installation.setGroup(g); saveOperations.clearOtherResults() }
                pickedPoint.value = null
                sharing.changed(); lab.changed()
            })
        }
    }

    fun setSafety(safety: Safety, memo: String = "") {
        if (!saveOperations.begin("safety")) return
        val targetGroup=group.value;val incident=incidentUi.value.takeIf { it.groupId==targetGroup }?.selection.orEmpty()
        viewModelScope.launch {
            saveOperations.complete("safety", if (safety == Safety.SOS) "SOS" else "無事", persist = {
                val own=checkNotNull(app.store.get(targetGroup,localId)) { "先に設定で自分の情報を登録してください" }
                app.store.answerWithMemo(IncidentEvent(targetGroup,java.util.UUID.randomUUID().toString(),incident,localId,own.name,1,System.currentTimeMillis(),IncidentKind.ANSWER,safety=safety), if (safety == Safety.SOS) memo else "")
            }, publish = { position.refresh(); sharing.changed(); lab.changed() })
        }
    }

    fun saveSosMemo(answerId: String, text: String) {
        if (!saveOperations.begin("safety")) return
        val g = group.value
        val incident = incidentUi.value.takeIf { it.groupId == g }?.selection.orEmpty()
        viewModelScope.launch {
            saveOperations.complete("safety", "SOSメモ", persist = {
                app.store.saveSosMemo(g, incident, localId, answerId, text, System.currentTimeMillis())
            }, publish = { sharing.changed(); lab.changed() })
        }
    }

    fun selectIncident(id: String) {
        if(saving.value)return
        try { app.installation.selectIncident(group.value,id);saveOperations.clearOtherResults() }
        catch(e: Exception) { message.value=e.localizedMessage }
    }
    fun startIncident(title: String, area: String, training: Boolean) {
        if(!saveOperations.begin("incident"))return
        val g=group.value;val id=java.util.UUID.randomUUID().toString()
        var selected:String?=null
        viewModelScope.launch { saveOperations.complete("incident","地震報告",persist={
            val own=checkNotNull(app.store.get(g,localId)) { "先に自分の情報を登録してください" }
            if(app.quakes.roster(g)!=null) {
                val states=app.quakes.command(QuakeEvent(g,id,localId,System.currentTimeMillis(),QuakeAction.REPORT,training,title=title.trim(),area=area.trim()))
                selected=states.lastOrNull {!it.closed&&it.formation.training==training}?.formation?.incidentId
            }else{
                app.store.mergeIncident(IncidentEvent(g,id,id,localId,own.name,1,System.currentTimeMillis(),IncidentKind.START,title.trim(),area.trim(),training));selected=id
            }
        },publish={ selected?.let {app.installation.selectIncident(g,it)};saveOperations.clearOtherResults();sharing.changed();lab.changed() }) }
    }
    fun endIncident() {
        if(!saveOperations.begin("incident"))return
        val g=group.value;val current=incidentUi.value.takeIf { it.groupId==g }?.current
        viewModelScope.launch { saveOperations.complete("incident","安否確認の終了",persist={
            val own=checkNotNull(app.store.get(g,localId))
            val selected=checkNotNull(current) { "開始中の災害を選んでください" }
            if(app.quakes.roster(g)!=null)app.quakes.command(QuakeEvent(g,java.util.UUID.randomUUID().toString(),localId,System.currentTimeMillis(),QuakeAction.END,selected.start.training,targetId=selected.start.eventId))
            else app.store.appendIncident(IncidentEvent(g,java.util.UUID.randomUUID().toString(),selected.start.eventId,localId,own.name,1,System.currentTimeMillis(),IncidentKind.END))
        },publish={ sharing.changed();lab.changed() }) }
    }

    fun startSharing() {
        if (saving.value) return
        val own = residents.value.find { it.originId == localId }
        if (own == null) message.value = "先に設定で自分の情報を登録してください"
        else { lab.stop(); sharing.start(group.value, own.name) }
    }
    fun startLab(url: String, token: String) {
        if (saving.value) return
        if (residents.value.none { it.originId == localId }) { message.value = "先に自分の情報を登録してください"; return }
        sharing.stop(); lab.start(url.trim(), token.trim(), group.value)
    }
    fun saveCare(subjectId: String, action: CareAction, note: String, supersedes: String? = null, onSaved: (CareEvent) -> Unit = {}) {
        val key = careSaveKey(subjectId)
        if (!saveOperations.begin(key)) return
        val targetGroup = group.value
        val incident=incidentUi.value.takeIf { it.groupId==targetGroup }?.selection.orEmpty()
        viewModelScope.launch {
            var saved: CareEvent? = null
            saveOperations.complete(key, "対応記録", persist = {
                val own = checkNotNull(app.store.get(targetGroup, localId)) { "先に自分の情報を登録してください" }
                checkNotNull(app.store.get(targetGroup, subjectId)) { "対象住民が見つかりません" }
                saved = app.store.appendIncident(IncidentEvent(targetGroup,java.util.UUID.randomUUID().toString(),incident,localId,own.name,1,System.currentTimeMillis(),IncidentKind.CARE,subjectId=subjectId,action=action,note=note.trim(),supersedes=supersedes)).care()
            }, publish = { saved?.let(onSaved); sharing.changed(); lab.changed() })
        }
    }

    fun placeSaveKey(kind: PlaceKind,target: String="")="place:${group.value}:${if(kind==PlaceKind.CARE)incidentUi.value.selection else ""}:$kind:$target"
    private fun savePlace(kind: PlaceKind,target: String="",make: (PlaceEvent)->PlaceEvent,onSaved: (PlaceEvent)->Unit={}) {
        val key=placeSaveKey(kind,target);if(!saveOperations.begin(key))return
        val g=group.value;val incident=incidentUi.value.takeIf {it.groupId==g}?.selection.orEmpty()
        viewModelScope.launch {
            var saved: PlaceEvent?=null
            saveOperations.complete(key,if(kind==PlaceKind.CARE)"建物の対応記録" else "建物・住戸の登録",persist={
                val own=checkNotNull(app.store.get(g,localId)) {"先に自分の情報を登録してください"}
                val base=PlaceEvent(g,java.util.UUID.randomUUID().toString(),localId,own.name,1,System.currentTimeMillis(),kind,incidentId=if(kind==PlaceKind.CARE)incident else null)
                saved=app.store.appendPlace(make(base))
            },publish={saved?.let(onSaved);sharing.changed();lab.changed()})
        }
    }
    fun registerBuilding(buildingId: String?,label: String,lat: String,lon: String,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.BUILDING,buildingId.orEmpty(),{
        it.copy(buildingId=buildingId?:it.eventId,label=label.trim(),latitude=lat.toDoubleOrNull()?:error("緯度を数値で入力してください"),longitude=lon.toDoubleOrNull()?:error("経度を数値で入力してください"))
    },onSaved)
    fun registerUnit(buildingId: String,label: String,unitId: String?=null,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.UNIT,buildingId,{it.copy(buildingId=buildingId,unitId=unitId?:it.eventId,label=label.trim())},onSaved)
    fun assignHome(buildingId: String?,unitId: String?=null)=savePlace(PlaceKind.HOME,make={it.copy(buildingId=buildingId,unitId=unitId)})
    fun saveBuildingCare(buildingId: String,action: BuildingAction,scope: String,unresolved: String,note: String,supersedes: String?,onSaved: (PlaceEvent)->Unit={})=savePlace(PlaceKind.CARE,buildingId,{
        it.copy(buildingId=buildingId,action=action,scope=scope.trim(),unresolved=unresolved.trim(),note=note.trim(),supersedes=supersedes)
    },onSaved)
    fun stopSharing() = sharing.stop()
}

