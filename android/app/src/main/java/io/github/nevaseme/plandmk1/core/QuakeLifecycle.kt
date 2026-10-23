package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

@Serializable enum class QuakeAction { REPORT, CANCEL, FORM, END }
@Serializable data class QuakeEvent(
    val groupId:String, val eventId:String, val actorDeviceId:String, val operatedAt:Long,
    val action:QuakeAction, val training:Boolean, val targetId:String?=null,
    val reportIds:List<String> = emptyList(), val title:String="", val area:String="",
    val endedIncidentIds:List<String> = emptyList(),
) {
    fun validate(roster:MemberRoster) {
        require(groupId==roster.groupId && recordIdPattern.matches(eventId) && operatedAt in 0..MAX_SEQUENCE)
        roster.memberForDevice(actorDeviceId)
        require(targetId==null || recordIdPattern.matches(targetId))
        require(reportIds.distinct().size==reportIds.size && reportIds.all(recordIdPattern::matches))
        require(endedIncidentIds.distinct().size==endedIncidentIds.size && endedIncidentIds.all(recordIdPattern::matches))
        require(if(action==QuakeAction.END)endedIncidentIds.isEmpty()||targetId in endedIncidentIds else endedIncidentIds.isEmpty())
        when(action) {
            QuakeAction.REPORT -> require(targetId==null && reportIds.isEmpty() && title.isNotBlank() && title.length<=80 && area.isNotBlank() && area.length<=120)
            QuakeAction.CANCEL,QuakeAction.END -> require(targetId!=null && targetId!=eventId && reportIds.isEmpty() && title.isEmpty() && area.isEmpty())
            QuakeAction.FORM -> require(targetId!=null && targetId!=eventId && reportIds.isNotEmpty() && title.isEmpty() && area.isEmpty())
        }
    }
}
data class QuakeFormation(val groupId:String,val incidentId:String,val firstReportedAt:Long,val training:Boolean,val reportIds:List<String>) {
    fun event(id:String,device:String,at:Long)=QuakeEvent(groupId,id,device,at,QuakeAction.FORM,training,incidentId,reportIds)
}
data class QuakeState(val formation:QuakeFormation,val closed:Boolean,val incidentIds:Set<String> = setOf(formation.incidentId))

private fun reportsFor(roster:MemberRoster,events:List<QuakeEvent>)=events.filter {it.action==QuakeAction.REPORT && it.groupId==roster.groupId}
private fun proof(roster:MemberRoster,events:List<QuakeEvent>,formed:QuakeEvent):QuakeFormation? {
    if(formed.action!=QuakeAction.FORM)return null
    val byId=reportsFor(roster,events).associateBy {it.eventId}
    val reports=formed.reportIds.map {byId[it]?:return null}.sortedWith(compareBy<QuakeEvent> {it.operatedAt}.thenBy {it.eventId})
    if(reports.isEmpty() || reports.any {it.training!=formed.training} || reports.first().eventId!=formed.targetId)return null
    if(reports.last().operatedAt-reports.first().operatedAt>30*60_000L)return null
    if(reports.map {roster.memberForDevice(it.actorDeviceId).memberId}.distinct().size<requiredEarthquakeReporters(roster.members.size))return null
    return QuakeFormation(roster.groupId,reports.first().eventId,reports.first().operatedAt,formed.training,reports.map {it.eventId})
}
fun quakeStates(roster:MemberRoster,events:List<QuakeEvent>):List<QuakeState> {
    val relevant=events.filter {it.groupId==roster.groupId}
    relevant.forEach {it.validate(roster)}
    val forms=relevant.mapNotNull {proof(roster,relevant,it)}.sortedWith(compareBy<QuakeFormation> {it.firstReportedAt}.thenBy {it.incidentId}.thenBy {it.reportIds.joinToString()})
    // Group by the first report, not a rolling window. Stored proofs and their original IDs remain intact.
    return listOf(false,true).flatMap {training->
        val batches=mutableListOf<MutableList<QuakeFormation>>()
        forms.filter {it.training==training}.forEach {form->
            val last=batches.lastOrNull()
            if(last==null || form.firstReportedAt-last.first().firstReportedAt>30*60_000L)batches.add(mutableListOf(form))
            else last.add(form)
        }
        batches.map {batch->
            val selected=batch.first();val ids=batch.map {it.incidentId}.toSet()
            QuakeState(selected,relevant.any {it.action==QuakeAction.END && (it.targetId in ids||it.endedIncidentIds.any {id->id in ids}) && it.training==training && roster.memberForDevice(it.actorDeviceId).role==MemberRole.ADMIN},ids)
        }
    }.sortedWith(compareBy<QuakeState> {it.formation.firstReportedAt}.thenBy {it.formation.incidentId})
}
fun formationCandidates(roster:MemberRoster,events:List<QuakeEvent>):List<QuakeFormation> {
    val reports=reportsFor(roster,events);val byId=reports.associateBy {it.eventId}
    val formed=events.mapNotNull {proof(roster,events,it)}
    val cancelled=events.filter {cancel->
        cancel.groupId==roster.groupId && cancel.action==QuakeAction.CANCEL && byId[cancel.targetId]?.let {
            it.training==cancel.training && roster.memberForDevice(it.actorDeviceId).memberId==roster.memberForDevice(cancel.actorDeviceId).memberId
        }==true
    }.mapNotNull {it.targetId}.toSet()
    val available=reports.filter {report-> report.eventId !in cancelled && formed.none {report.eventId in it.reportIds || (report.training==it.training && report.operatedAt>=it.firstReportedAt && report.operatedAt-it.firstReportedAt<=30*60_000L)} }
    return listOf(false,true).flatMap {training->
        pendingEarthquakeWindows(available.map {EarthquakeReport(it.eventId,it.groupId,roster.memberForDevice(it.actorDeviceId).memberId,it.operatedAt,it.training)},roster.groupId,roster.members.map {it.memberId}.toSet(),training)
            .filter {it.hasEnoughReports}.map {window->
                val evidence=window.reportIds.map {byId.getValue(it)}.distinctBy {roster.memberForDevice(it.actorDeviceId).memberId}.take(window.requiredReporters)
                QuakeFormation(roster.groupId,evidence.first().eventId,window.firstReportedAt,training,evidence.map {it.eventId})
            }
    }
}
fun validateQuakeCommand(roster:MemberRoster,events:List<QuakeEvent>,event:QuakeEvent) {
    event.validate(roster)
    when(event.action) {
        QuakeAction.REPORT -> Unit
        QuakeAction.CANCEL -> {
            val report=requireNotNull(events.find {it.eventId==event.targetId && it.groupId==event.groupId && it.action==QuakeAction.REPORT}) {"取り消す報告がありません"}
            require(report.training==event.training && roster.memberForDevice(report.actorDeviceId).memberId==roster.memberForDevice(event.actorDeviceId).memberId) {"自分の報告だけ取り消せます"}
            require(events.mapNotNull {proof(roster,events,it)}.none {report.eventId in it.reportIds || (report.training==it.training && report.operatedAt>=it.firstReportedAt && report.operatedAt-it.firstReportedAt<=30*60_000L)}) {"成立した地震の報告は取り消せません"}
        }
        QuakeAction.FORM -> require(formationCandidates(roster,events).any {it==proof(roster,events,event)}) {"成立に必要な報告がありません"}
        QuakeAction.END -> {
            require(roster.memberForDevice(event.actorDeviceId).role==MemberRole.ADMIN) {"終了できるのは管理者だけです"}
            require(quakeStates(roster,events).any {event.targetId in it.incidentIds && event.endedIncidentIds.all {id->id in it.incidentIds} && it.formation.training==event.training && !it.closed}) {"開始中の地震を指定してください"}
        }
    }
}

fun managedIncidentOptions(journal:List<IncidentEvent>,roster:MemberRoster?,quakes:List<QuakeEvent>):List<IncidentView> {
    if(roster==null)return incidentOptions(journal)
    val states=quakeStates(roster,quakes);val originals=incidentOptions(journal)
    val managed=states.mapNotNull {state->
        val views=originals.filter {it.start.eventId in state.incidentIds}
        val canonical=views.find {it.start.eventId==state.formation.incidentId}?:return@mapNotNull null
        canonical.copy(closed=state.closed,
            answers=views.flatMap {it.answers.values}.groupBy {it.authorId}.mapValues {(_,answers)->answers.maxWith(compareBy<IncidentEvent> {it.createdAt}.thenBy {it.sequence}.thenBy {it.eventId})},
            care=views.flatMap {it.care}.distinctBy {it.eventId})
    }
    val managedIds=states.flatMap {it.incidentIds}.toSet()
    return (managed+originals.filter {it.start.eventId !in managedIds}.map {it.copy(closed=true)})
        .sortedWith(compareByDescending<IncidentView> {it.start.createdAt}.thenBy {it.start.eventId})
}

fun canonicalIncidentId(roster:MemberRoster?,quakes:List<QuakeEvent>,id:String?):String? =
    if(roster==null)id else quakeStates(roster,quakes).find {id in it.incidentIds}?.formation?.incidentId?:id
