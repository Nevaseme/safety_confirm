package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResponseStorageTest {
    @Test fun realStorageKeepsAssignmentsCorrectionsMaterialsAndAvailability()=runBlocking {
        fun id()=UUID.randomUUID().toString()
        val context=ApplicationProvider.getApplicationContext<Context>();val name="responses-${id()}.db"
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).build()
        var db=open();val admin=id();val member=id();val incident=id()
        val roster=MemberRoster(groupId="trial",members=listOf(GroupMember(admin,"管理者",MemberRole.ADMIN,listOf(admin)),GroupMember(member,"確認班",MemberRole.MEMBER,listOf(member))))
        try {
            QuakeStore(db).importRoster(roster.encode(),member)
            val residentStore=RoomRecordStore(db)
            residentStore.updateOwn("trial",member){Resident("trial",member,1,0,"住民",35.0,139.0,"2階","北")}
            QuakeStore(db).command(QuakeEvent("trial",incident,admin,0,QuakeAction.REPORT,true,title="訓練",area="地域"))
            QuakeStore(db).command(QuakeEvent("trial",id(),member,1,QuakeAction.REPORT,true,title="訓練",area="地域"))
            var store=ResponseStore(db)
            fun event(change:ResponseChange,eventId:String=id(),replaces:List<String> = emptyList(),actor:String=member)=ResponseEvent("trial",eventId,actor,1,1,if(change is ResponseChange.Availability)null else incident,change,replaces)
            val reportId=id()
            val report=store.append(event(ResponseChange.Report(reportId,"道路閉塞",35.0,139.0,"倒木"),reportId))
            val target=ResponseTarget(TargetKind.REPORT,reportId)
            store.append(event(ResponseChange.Assignment(target,member,true)))
            store.append(event(ResponseChange.Progress(target,WorkStatus.IN_PROGRESS,"確認へ向かう")))
            store.append(event(ResponseChange.Priority(target,Urgency.HIGH,"通行不能"),actor=admin))
            store.append(event(ResponseChange.Availability(ActivityState.WORKING)))
            val materialId=id()
            store.append(event(ResponseChange.Material(target,materialId,"のこぎり","本",2.0,1.0),materialId))
            val corrected=store.append(event((report.change as ResponseChange.Report).copy(description="通行止め"),replaces=listOf(report.eventId)))
            val before=store.events("trial")
            try {store.append(event(ResponseChange.Priority(target,Urgency.LOW,"変更")));fail("member priority")}catch(_:IllegalArgumentException){}
            assertEquals(before,store.events("trial"))
            db.close();db=open();store=ResponseStore(db)
            assertEquals(7,store.events("trial").size)
            assertEquals(6,store.heads("trial").size)
            assertTrue(store.heads("trial").contains(corrected))
            assertEquals(1.0,(store.heads("trial").single {it.change is ResponseChange.Material}.change as ResponseChange.Material).shortfall,0.0)
            val csv=store.exportCsv("trial",incident,mapOf(target to listOf("A町内会")))
            assertTrue(csv.startsWith("\uFEFF\"地域\""))
            assertTrue(csv.contains("\"通報：道路閉塞\""))
            assertTrue(csv.contains("\"A町内会\""))
            assertTrue(csv.contains("\"確認へ向かう\""))
            assertTrue(csv.contains("不足1本"))
            assertEquals(3,csv.trimEnd().lines().size)
            val catalog=RegionCatalog(regions=listOf(RegionDefinition("city","試験市"),RegionDefinition("a","A町内会","city"),RegionDefinition("b","B町内会","city")))
            val counts=store.reportRegionSummary("trial",incident,catalog,mapOf(reportId to listOf("a","b")))
            assertEquals(1,counts.regions.single {it.regionId=="city"}.count)
            assertEquals(setOf(target),counts.regions.single {it.regionId=="city"}.targets)
        }finally {db.close();context.deleteDatabase(name)}
    }
}
