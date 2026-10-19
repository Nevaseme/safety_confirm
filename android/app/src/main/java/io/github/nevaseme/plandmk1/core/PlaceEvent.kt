package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

@Serializable enum class PlaceKind { BUILDING, UNIT, HOME, CARE }
@Serializable enum class BuildingAction { CHECKING, NEEDS_HELP, COMPLETED, RECHECK, REVOKED }
@Serializable data class PlaceEvent(
    val groupId: String,val eventId: String,val authorId: String,val authorName: String,
    val sequence: Long,val createdAt: Long,val kind: PlaceKind,
    val buildingId: String?=null,val unitId: String?=null,val incidentId: String?=null,
    val label: String="",val latitude: Double?=null,val longitude: Double?=null,
    val action: BuildingAction?=null,val scope: String="",val unresolved: String="",val note: String="",val supersedes: String?=null,
) {
    fun validate() {
        val uuid=Regex("[0-9a-fA-F-]{36}")
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,40}"))&&eventId.matches(uuid)&&authorId.matches(uuid)) {"建物記録のIDが不正です"}
        require(listOf(buildingId,unitId,incidentId,supersedes).all {it==null||it.matches(uuid)}) {"関連先のIDが不正です"}
        require(sequence in 1..9_007_199_254_740_991L&&createdAt in 0..9_007_199_254_740_991L) {"建物記録の更新情報が不正です"}
        require(authorName.isNotBlank()&&authorName.length<=60&&label.length<=80&&scope.length<=240&&unresolved.length<=240&&note.length<=500) {"建物記録の文字数を確認してください"}
        if(kind==PlaceKind.BUILDING) require(buildingId!=null&&unitId==null&&label.isNotBlank()&&latitude!=null&&latitude.isFinite()&&latitude in -85.0..85.0&&longitude!=null&&longitude.isFinite()&&longitude in -180.0..180.0) {"建物名と緯度・経度を確認してください"}
        else {
            require(latitude==null&&longitude==null) {"不要な位置情報です"}
            if(kind==PlaceKind.UNIT)require(buildingId!=null&&unitId!=null&&label.isNotBlank()) {"建物と住戸名を指定してください"}
            else require(label.isEmpty()) {"不要な名称です"}
        }
        if(kind==PlaceKind.HOME)require(buildingId!=null||unitId==null) {"住戸には建物が必要です"}
        if(kind==PlaceKind.CARE)require(buildingId!=null&&unitId==null&&incidentId!=null&&action!=null&&scope.isNotBlank()&&unresolved.isNotBlank()&&supersedes!=eventId&&(action!=BuildingAction.REVOKED||supersedes!=null)) {"確認範囲・未確認事項を入力し、訂正元を確認してください"}
        else require(incidentId==null&&action==null&&scope.isEmpty()&&unresolved.isEmpty()&&note.isEmpty()&&supersedes==null) {"不要な対応情報です"}
    }
}
fun placeBuildings(rows: List<PlaceEvent>)=rows.filter {it.kind==PlaceKind.BUILDING&&it.eventId==it.buildingId}.map {root->rows.filter {it.kind==PlaceKind.BUILDING&&it.buildingId==root.buildingId&&it.groupId==root.groupId&&it.authorId==root.authorId}.maxBy {it.sequence}}.sortedWith(compareBy({it.label},{it.buildingId}))
fun placeUnits(rows: List<PlaceEvent>,buildingId: String?)=rows.filter {it.kind==PlaceKind.UNIT&&it.eventId==it.unitId&&it.buildingId==buildingId}.map {root->rows.filter {it.kind==PlaceKind.UNIT&&it.unitId==root.unitId&&it.buildingId==root.buildingId&&it.groupId==root.groupId&&it.authorId==root.authorId}.maxBy {it.sequence}}.sortedWith(compareBy({it.label},{it.unitId}))
fun homeFor(rows: List<PlaceEvent>,authorId: String)=rows.filter {it.kind==PlaceKind.HOME&&it.authorId==authorId}.maxByOrNull {it.sequence}
fun validHome(rows: List<PlaceEvent>,authorId: String): PlaceEvent? {
    val home=homeFor(rows,authorId)?:return null
    return home.takeIf {h->placeBuildings(rows).any {it.buildingId==h.buildingId}&&(h.unitId==null||placeUnits(rows,h.buildingId).any {it.unitId==h.unitId})}
}
fun buildingCare(rows: List<PlaceEvent>,buildingId: String,incidentId: String)=rows.filter {it.kind==PlaceKind.CARE&&it.buildingId==buildingId&&it.incidentId==incidentId}
fun currentBuildingCare(rows: List<PlaceEvent>): List<PlaceEvent> {
    val byId=rows.associateBy {it.eventId}
    val replaced=rows.filter {e->byId[e.supersedes]?.let {p->p.groupId==e.groupId&&p.authorId==e.authorId&&p.buildingId==e.buildingId&&p.incidentId==e.incidentId&&p.sequence<e.sequence}==true}.map {it.supersedes}.toSet()
    return rows.filter {it.eventId !in replaced}
}
fun preparePlace(event: PlaceEvent,rows: List<PlaceEvent>,incidents: List<IncidentEvent>): PlaceEvent {
    val building=placeBuildings(rows).find {it.buildingId==event.buildingId}
    if(event.kind==PlaceKind.BUILDING) {
        if(building!=null) require(building.authorId==event.authorId) {"建物を訂正できるのは登録者だけです"}
        else require(event.buildingId==event.eventId) {"建物IDを確認してください"}
    } else if(event.kind!=PlaceKind.HOME||event.buildingId!=null) require(building!=null) {"建物が未受信です。再接続してください"}
    if(event.kind==PlaceKind.UNIT) {
        val unit=placeUnits(rows,event.buildingId).find {it.unitId==event.unitId}
        if(unit!=null)require(unit.authorId==event.authorId) {"住戸を訂正できるのは登録者だけです"}
        else require(event.unitId==event.eventId) {"住戸IDを確認してください"}
    }
    if(event.kind==PlaceKind.HOME&&event.unitId!=null)require(placeUnits(rows,event.buildingId).any {it.unitId==event.unitId}) {"選んだ建物の住戸を指定してください"}
    if(event.kind==PlaceKind.CARE) {
        val incident=incidentView(incidents,event.incidentId.orEmpty())
        require(incident!=null&&!incident.closed) {"開始中の災害を選んでください"}
        if(event.supersedes!=null)require(buildingCare(rows,event.buildingId!!,event.incidentId!!).any {it.eventId==event.supersedes&&it.authorId==event.authorId}) {"同じ建物・災害の自分の記録だけ訂正できます"}
    }
    return event.copy(sequence=Math.addExact(rows.filter {it.authorId==event.authorId}.maxOfOrNull {it.sequence}?:0,1)).also {it.validate()}
}
