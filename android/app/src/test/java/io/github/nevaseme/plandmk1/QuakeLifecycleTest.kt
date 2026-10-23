package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class QuakeLifecycleTest {
    private fun id() = UUID.randomUUID().toString()
    private val a=id(); private val b=id(); private val c=id(); private val secondA=id()
    private val roster=MemberRoster(groupId="trial", members=listOf(
        GroupMember(a,"班長",MemberRole.ADMIN,listOf(a,secondA)),
        GroupMember(b,"確認B",MemberRole.MEMBER,listOf(b)), GroupMember(c,"確認C",MemberRole.MEMBER,listOf(c))))
    private fun report(actor:String,time:Long=0)=QuakeEvent("trial",id(),actor,time,QuakeAction.REPORT,false,title="地震",area="試験地域")

    @Test fun rosterRoundTripAndDuplicateDeviceRejection() {
        assertEquals(roster,MemberRoster.decode(roster.encode()))
        try { roster.copy(members=roster.members+GroupMember(id(),"重複",MemberRole.MEMBER,listOf(a))).validate();fail() }
        catch (_:IllegalArgumentException) { }
    }
    @Test fun twoDevicesOfOneMemberCannotFormAndTwoPeopleCan() {
        val first=report(a); val duplicate=report(secondA,1);val other=report(b,2)
        assertTrue(formationCandidates(roster,listOf(first,duplicate)).isEmpty())
        val formed=formationCandidates(roster,listOf(first,duplicate,other)).single()
        assertEquals(first.eventId,formed.incidentId)
        assertEquals(0L,formed.firstReportedAt)
    }
    @Test fun ownCancellationBeforeFormationAndForeignCancellationRejected() {
        val first=report(a); val other=report(b,1)
        val cancel=QuakeEvent("trial",id(),a,2,QuakeAction.CANCEL,false,targetId=first.eventId)
        validateQuakeCommand(roster,listOf(first),cancel)
        assertTrue(formationCandidates(roster,listOf(first,other,cancel)).isEmpty())
        try {validateQuakeCommand(roster,listOf(first),cancel.copy(actorDeviceId=b));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun formationSurvivesLateCancellationAndEndSurvivesReordering() {
        val reports=listOf(report(a),report(b,1));val candidate=formationCandidates(roster,reports).single()
        val formed=candidate.event(id(),a,2)
        val cancel=QuakeEvent("trial",id(),b,1,QuakeAction.CANCEL,false,targetId=reports[1].eventId)
        val end=QuakeEvent("trial",id(),a,3,QuakeAction.END,false,targetId=candidate.incidentId)
        validateQuakeCommand(roster,reports+formed,end)
        val events=reports+formed+cancel+end
        assertTrue(quakeStates(roster,events).single().closed)
        assertEquals(quakeStates(roster,events),quakeStates(roster,events.reversed()))
        try { validateQuakeCommand(roster,reports+formed,end.copy(actorDeviceId=b));fail() }catch(_:IllegalArgumentException){}
        try { validateQuakeCommand(roster,reports+formed,cancel);fail() }catch(_:IllegalArgumentException){}
    }
    @Test fun independentlyFormedReportsShareOneIncidentAndEnd() {
        val d=id();val members=roster.copy(members=roster.members+GroupMember(d,"D",MemberRole.MEMBER,listOf(d)))
        val left=listOf(report(a,0),report(b,1));val right=listOf(report(c,100),report(d,101))
        val f1=formationCandidates(members,left).single().event(id(),a,1)
        val f2=formationCandidates(members,right).single().event(id(),c,101)
        val end=QuakeEvent("trial",id(),a,200,QuakeAction.END,false,targetId=f2.targetId)
        val history=left+right+f1+f2+end
        val state=quakeStates(members,history).single()
        assertTrue(state.closed)
        assertEquals(setOf(f1.targetId,f2.targetId),state.incidentIds)
        assertEquals(quakeStates(members,history),quakeStates(members,history.reversed()))
        fun start(r:QuakeEvent)=IncidentEvent("trial",r.eventId,r.eventId,r.actorDeviceId,"班",1,r.operatedAt,IncidentKind.START,"地震","地域",false)
        val answer=IncidentEvent("trial",id(),right.first().eventId,b,"B",1,150,IncidentKind.ANSWER,training=false,safety=Safety.SOS)
        val views=managedIncidentOptions(listOf(start(left.first()),start(right.first()),answer),members,history)
        assertEquals(1,views.size);assertTrue(views.single().closed);assertEquals(answer,views.single().answers[b])
    }
    @Test fun staleLocalFormationCannotReuseCancelledVote() {
        val reports=listOf(report(a),report(b,1));val candidate=formationCandidates(roster,reports).single()
        val cancel=QuakeEvent("trial",id(),a,2,QuakeAction.CANCEL,false,targetId=reports.first().eventId)
        try {validateQuakeCommand(roster,reports+cancel,candidate.event(id(),b,3));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun adminEndKeepsItsOriginalScopeWhenAnEarlierFormationArrives() {
        val early=listOf(report(a,0),report(b,1))
        val middle=listOf(report(a,20*60_000L),report(b,20*60_000L+1))
        val late=listOf(report(a,40*60_000L),report(b,40*60_000L+1))
        fun form(reports:List<QuakeEvent>)=formationCandidates(roster,reports).single().event(id(),a,reports.last().operatedAt)
        val firstHistory=middle+late+form(middle)+form(late)
        val before=quakeStates(roster,firstHistory).single()
        val end=QuakeEvent("trial",id(),a,50*60_000L,QuakeAction.END,false,targetId=late.first().eventId,endedIncidentIds=before.incidentIds.sorted())
        val after=quakeStates(roster,firstHistory+end+early+form(early))
        assertTrue(after.all {it.closed})
        assertEquals(after,quakeStates(roster,(firstHistory+end+early+form(early)).reversed()))
    }
}
