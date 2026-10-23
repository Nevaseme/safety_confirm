package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class OperationsExportTest {
    private fun id(n:Int)="00000000-0000-0000-0000-${n.toString().padStart(12,'0')}"
    private val roster=MemberRoster(groupId="trial",members=listOf(GroupMember(id(1),"確認班",MemberRole.ADMIN,listOf(id(1))),GroupMember(id(2),"連絡班",MemberRole.MEMBER,listOf(id(2)))))
    private val incident=id(10)
    private val journal=listOf(IncidentEvent("trial",incident,incident,id(1),"確認班",1,1,IncidentKind.START,"訓練","地域"))+(1..3).map {
        IncidentEvent("trial",id(10+it),incident,id(it),"住民$it",it+1L,10,IncidentKind.ANSWER,safety=if(it==2)Safety.SAFE else Safety.SOS)
    }+IncidentEvent("trial",id(20),incident,id(1),"確認班",5,20,IncidentKind.CARE,subjectId=id(3),action=CareAction.RESCUED)
    private val people=(1..4).map {Resident("trial",id(it),1,1,"住民$it",35.0,139.0,"2階","北")}
    private fun event(n:Int,change:ResponseChange,replaces:List<String> = emptyList())=ResponseEvent("trial",id(n),id(1),n.toLong(),n.toLong(),incident,change,replaces)
    @Test fun exportsOnlySelectedIncidentAndHidesSafeAndRescuedNames() {
        val rows=operationRows(roster,incident,people,journal,emptyList(),emptyList(),emptyMap())
        assertEquals(4,rows.size)
        assertEquals("住民1",rows.first {it.targetId==id(1)}.name)
        assertEquals("",rows.first {it.targetId==id(2)}.name)
        assertEquals("",rows.first {it.targetId==id(3)}.name)
        assertEquals("住民4",rows.first {it.targetId==id(4)}.name)
        assertTrue(rows.first {it.targetId==id(3)}.status.startsWith("救助済み"))
    }
    @Test fun projectsCorrectionsMultipleAssigneesAndMaterialShortfall() {
        val target=ResponseTarget(TargetKind.REPORT,id(30))
        val history=listOf(event(30,ResponseChange.Report(id(30),"道路閉塞",35.0,139.0,"倒木")),
            event(31,ResponseChange.Report(id(30),"道路閉塞",35.0,139.0,"枝を撤去"),listOf(id(30))),
            event(32,ResponseChange.Assignment(target,id(1),true)),
            event(33,ResponseChange.Assignment(target,id(2),true)),
            event(34,ResponseChange.Progress(target,WorkStatus.DONE,"通行可能を確認")),
            event(35,ResponseChange.Priority(target,Urgency.HIGH,"通行不能")),
            event(36,ResponseChange.Material(target,id(36),"のこぎり","本",3.0,1.0)))
        val rows=operationRows(roster,incident,people,journal,emptyList(),history,mapOf(target to listOf("A町内会","A町内会")))
        val report=rows.single {it.targetId==id(30)}
        assertEquals("A町内会",report.region);assertEquals("対応済み",report.status)
        assertEquals("確認班 / 連絡班",report.assignees)
        assertEquals("高",report.urgency);assertEquals("通行可能を確認",report.result)
        assertTrue(report.materials.contains("不足2本"))
        assertEquals(36L,report.updatedAt)
    }
    @Test fun csvHandlesJapaneseQuotesNewlinesAndFormulaText() {
        val row=OperationExportRow("A町内会",id(1),"住民","=1+1","SOS・未対応","未設定","班\"A","確認,\n継続","",0)
        val csv=operationCsv(listOf(row),TimeZone.getTimeZone("Asia/Tokyo"))
        assertTrue(csv.startsWith("\uFEFF\"地域\""));assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"班\"\"A\""));assertTrue(csv.contains("\"確認,\n継続\""))
        assertTrue(csv.contains("1970-01-01T09:00:00+09:00"));assertTrue(csv.endsWith("\r\n"))
    }
    @Test fun retainsIndependentMaterialRequestsWithIdenticalQuantities() {
        val target=ResponseTarget(TargetKind.PERSON,id(1))
        val history=listOf(40,41).map {event(it,ResponseChange.Material(target,id(it),"水","本",3.0,1.0))}
        val row=operationRows(roster,incident,people,journal,emptyList(),history,emptyMap()).single {it.targetId==id(1)}
        assertEquals(2,row.materials.split(" / ").size)
        assertTrue(row.materials.split(" / ").all {it.contains("不足2本")})
    }
    @Test fun writesFictionalReviewSampleUsingProductionExporter() {
        val samplePeople=people.mapIndexed {i,p->p.copy(name=listOf("佐藤 一郎（架空）","非公開の住民","非公開の救助済み住民","鈴木 花子（架空）")[i])}
        val reportTarget=ResponseTarget(TargetKind.REPORT,id(30));val personTarget=ResponseTarget(TargetKind.PERSON,id(1))
        val history=listOf(event(30,ResponseChange.Report(id(30),"道路閉塞",35.0,139.0,"倒木")),
            event(31,ResponseChange.Assignment(reportTarget,id(2),true)),
            event(32,ResponseChange.Progress(reportTarget,WorkStatus.IN_PROGRESS,"現地を確認。撤去の応援待ち")),
            event(33,ResponseChange.Priority(reportTarget,Urgency.MEDIUM,"迂回路あり")),
            event(34,ResponseChange.Material(reportTarget,id(34),"のこぎり","本",3.0,1.0)),
            event(35,ResponseChange.Assignment(personTarget,id(1),true)),
            event(36,ResponseChange.Progress(personTarget,WorkStatus.IN_PROGRESS,"北側の寝室を確認中")),
            event(37,ResponseChange.Priority(personTarget,Urgency.HIGH,"SOSを受信")))
        val regions=samplePeople.associate {ResponseTarget(TargetKind.PERSON,it.originId) to listOf("A町内会")}+mapOf(reportTarget to listOf("B町内会"))
        val timestamp=java.time.Instant.parse("2026-10-24T01:00:00Z").toEpochMilli()
        val rows=operationRows(roster,incident,samplePeople,journal,emptyList(),history,regions).mapIndexed {i,r->r.copy(updatedAt=timestamp+i*60_000)}
        val csv=operationCsv(rows,TimeZone.getTimeZone("Asia/Tokyo"))
        assertFalse(csv.contains("非公開の"));assertEquals(5,rows.size)
        java.io.File("build/reports/exports/pland-review.csv").also {it.parentFile.mkdirs();it.writeText(csv,Charsets.UTF_8)}
    }
}
