package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResponseWorkTest {
    @Test fun recordMustFitExistingTransportBeforeItIsSaved() {
        val tooLarge=event(a,ResponseChange.Report(id(),"道路",35.0,139.0,"あ".repeat(9000)))
        try {validateResponseShape(roster,tooLarge);fail("oversized")}catch(_:IllegalArgumentException){}
        validateResponseShape(roster,tooLarge.copy(change=(tooLarge.change as ResponseChange.Report).copy(description="倒木")))
    }
    private fun id()=UUID.randomUUID().toString()
    private val a=id();private val b=id();private val c=id();private val incident=id()
    private val roster=MemberRoster(groupId="trial",members=listOf(GroupMember(a,"班長",MemberRole.ADMIN,listOf(a)),GroupMember(b,"B班",MemberRole.MEMBER,listOf(b)),GroupMember(c,"C班",MemberRole.MEMBER,listOf(c))))
    private val person=ResponseTarget(TargetKind.PERSON,id())
    private fun event(actor:String,change:ResponseChange,replaces:List<String> = emptyList(),eventId:String=id())=ResponseEvent("trial",eventId,actor,1,1,if(change is ResponseChange.Availability)null else incident,change,replaces)
    @Test fun independentVolunteersRemainAssignedAndCoordinatorCanRelease() {
        val first=event(b,ResponseChange.Assignment(person,b,true));val second=event(c,ResponseChange.Assignment(person,c,true))
        val release=event(a,ResponseChange.Assignment(person,b,false),listOf(first.eventId))
        validateResponseCommand(roster,listOf(first,second),release)
        val heads=responseHeads(roster,listOf(first,second,release))
        assertEquals(setOf(c),heads.mapNotNull {(it.change as? ResponseChange.Assignment)?.takeIf {it.active}?.memberId}.toSet())
        try {validateResponseCommand(roster,listOf(first),release.copy(actorDeviceId=c));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun reportOwnerAndCoordinatorMayCorrectButOthersCannot() {
        val rootId=id();val report=event(b,ResponseChange.Report(rootId,"道路閉塞",35.0,139.0,"倒木"),eventId=rootId)
        val correction=event(a,(report.change as ResponseChange.Report).copy(description="倒木を確認"),listOf(rootId))
        validateResponseCommand(roster,listOf(report),correction)
        assertEquals(listOf(correction),responseHeads(roster,listOf(correction,report)))
        try {validateResponseCommand(roster,listOf(report),correction.copy(actorDeviceId=c));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun urgencyNeedsCoordinatorAndAvailabilityBelongsToSelf() {
        val priority=event(b,ResponseChange.Priority(person,Urgency.HIGH,"要確認"))
        try {validateResponseCommand(roster,emptyList(),priority);fail()}catch(_:IllegalArgumentException){}
        validateResponseCommand(roster,emptyList(),priority.copy(actorDeviceId=a))
        val available=event(b,ResponseChange.Availability(ActivityState.AVAILABLE))
        assertEquals(listOf(available),responseHeads(roster,listOf(available)))
    }
    @Test fun materialShortfallAndCompletedWorkDoNotAlterPersonalSafety() {
        val root=id();val material=ResponseChange.Material(person,root,"ブルーシート","枚",5.0,2.0)
        assertEquals(3.0,material.shortfall,0.0)
        assertEquals(0.0,material.copy(arranged=7.0).shortfall,0.0)
        val supply=event(b,material,eventId=root)
        validateResponseCommand(roster,emptyList(),supply)
        try {validateResponseCommand(roster,emptyList(),supply.copy(change=material.copy(needed=-1.0)));fail()}catch(_:IllegalArgumentException){}
        val done=event(b,ResponseChange.Progress(person,WorkStatus.DONE,"本人と連絡できた"))
        validateResponseCommand(roster,emptyList(),done)
        assertEquals(WorkStatus.DONE,(responseHeads(roster,listOf(done)).single().change as ResponseChange.Progress).status)
    }
    @Test fun concurrentCorrectionsRemainUntilExplicitlyReconciled() {
        val rootId=id();val original=event(b,ResponseChange.Report(rootId,"通報",35.0,139.0,"初報"),eventId=rootId)
        val one=event(b,(original.change as ResponseChange.Report).copy(description="訂正1"),listOf(rootId))
        val two=event(a,original.change.copy(description="訂正2"),listOf(rootId))
        val history=listOf(original,one,two)
        assertEquals(setOf(one.eventId,two.eventId),responseHeads(roster,history).map {it.eventId}.toSet())
        val reconcile=event(a,original.change.copy(description="確認済み"),listOf(one.eventId,two.eventId))
        validateResponseCommand(roster,history,reconcile)
        assertEquals(listOf(reconcile),responseHeads(roster,(history+reconcile).reversed()))
    }
}
