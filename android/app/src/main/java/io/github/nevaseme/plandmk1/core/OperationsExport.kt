package io.github.nevaseme.plandmk1.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class OperationExportRow(
    val region:String,val targetId:String,val kind:String,val name:String,val status:String,
    val urgency:String,val assignees:String,val result:String,val materials:String,val updatedAt:Long,
)

/** Regions are supplied explicitly; geographic assignment and aggregation are separate operations. */
fun operationRows(
    roster:MemberRoster,incidentId:String,residents:List<Resident>,journal:List<IncidentEvent>,
    quakes:List<QuakeEvent>,responses:List<ResponseEvent>,regions:Map<ResponseTarget,List<String>>,
):List<OperationExportRow> {
    roster.validate()
    val selected=canonicalIncidentId(roster,quakes,incidentId)
    val view=requireNotNull(managedIncidentOptions(journal.filter {it.groupId==roster.groupId},roster,quakes).find {it.start.eventId==selected}) {"出力する災害を選んでください"}
    val normalized=responses.filter {it.groupId==roster.groupId}.map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))}
    val heads=responseHeads(roster,normalized).filter {it.incidentId==selected}
    fun row(target:ResponseTarget,kind:String,name:String,safety:String,updatedAt:Long):OperationExportRow {
        val records=heads.filter {it.change.target()==target}
        val changes=records.map {it.change}
        val progress=changes.filterIsInstance<ResponseChange.Progress>()
        val statuses=progress.map {when(it.status){WorkStatus.UNASSIGNED->"未対応";WorkStatus.IN_PROGRESS->"対応中";WorkStatus.DONE->"対応済み"}}.distinct().sorted().ifEmpty {listOf("未対応")}
        val priorities=changes.filterIsInstance<ResponseChange.Priority>().map {when(it.urgency){Urgency.UNSET->"未設定";Urgency.LOW->"低";Urgency.MEDIUM->"中";Urgency.HIGH->"高"}}.distinct().sorted().ifEmpty {listOf("未設定")}
        val assignees=changes.filterIsInstance<ResponseChange.Assignment>().filter {it.active}.map {it.memberId}.distinct().sorted().map {id->roster.members.first {it.memberId==id}.activityName}
        fun quantity(n:Double)=java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
        val materials=changes.filterIsInstance<ResponseChange.Material>().sortedWith(compareBy<ResponseChange.Material> {it.requestId}.thenBy {it.name}.thenBy {it.needed}.thenBy {it.arranged}).map {"${it.name}：必要${quantity(it.needed)}${it.unit}・手配済み${quantity(it.arranged)}${it.unit}・不足${quantity(it.shortfall)}${it.unit}"}
        return OperationExportRow(regions[target].orEmpty().distinct().sorted().joinToString(" / ").ifEmpty {"未設定"},target.id,kind,name,
            listOf(safety,statuses.joinToString(" / ")).filter {it.isNotEmpty()}.joinToString("・"),priorities.joinToString(" / "),assignees.joinToString(" / "),
            progress.map {it.result}.filter {it.isNotBlank()}.distinct().sorted().joinToString(" / "),materials.joinToString(" / "),maxOf(updatedAt,records.maxOfOrNull {it.operatedAt}?:0))
    }
    val people=residents.filter {it.groupId==roster.groupId}.groupBy {it.originId}.values.map {it.maxBy {r->r.revision}}.map {person->
        val answer=view.answers[person.originId]
        val care=currentCareEvents(view.care.filter {it.subjectId==person.originId})
        val rescued=care.any {it.action==CareAction.RESCUED&&it.createdAt>=(answer?.createdAt?:0)}
        val safety=when {rescued->"救助済み";answer?.safety==Safety.SAFE->"無事";answer?.safety==Safety.SOS->"SOS";else->"応答なし"}
        row(ResponseTarget(TargetKind.PERSON,person.originId),"住民",if(safety=="SOS"||safety=="応答なし")person.name else "",safety,
            maxOf(person.updatedAt,answer?.createdAt?:view.start.createdAt,care.maxOfOrNull {it.createdAt}?:0))
    }
    val reports=heads.filter {it.change is ResponseChange.Report}.groupBy {(it.change as ResponseChange.Report).reportId}.map {(id,events)->
        val categories=events.map {(it.change as ResponseChange.Report).category}.distinct().sorted()
        row(ResponseTarget(TargetKind.REPORT,id),"通報：${categories.joinToString(" / ")}","","",events.maxOf {it.operatedAt})
    }
    return (people+reports).sortedWith(compareBy<OperationExportRow> {it.kind}.thenBy {it.targetId})
}

fun operationCsv(rows:List<OperationExportRow>,zone:TimeZone=TimeZone.getDefault()):String {
    val date=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).also {it.timeZone=zone}
    fun cell(value:String):String {
        val text=if(value.trimStart().firstOrNull() in listOf('=','+','-','@')||value.startsWith('\t')||value.startsWith('\r')||value.startsWith('\n'))"'$value" else value
        return "\"${text.replace("\"","\"\"")}\""
    }
    val header=listOf("地域","対象ID","種別","氏名","状況","緊急度","担当の活動名","対応結果","必要資材と不足数","更新日時")
    return "\uFEFF"+(listOf(header)+rows.map {listOf(it.region,it.targetId,it.kind,it.name,it.status,it.urgency,it.assignees,it.result,it.materials,date.format(Date(it.updatedAt)))}).joinToString("\r\n",postfix="\r\n") {line->line.joinToString(",",transform=::cell)}
}
