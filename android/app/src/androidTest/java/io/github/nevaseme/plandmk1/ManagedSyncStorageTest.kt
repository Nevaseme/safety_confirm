package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ManagedSyncStorageTest {
    @Test fun independentReportsFormOnReceiptAndWorkRelaysAfterReopen()=runBlocking {
        fun id()=UUID.randomUUID().toString()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val ids=List(3){id()};val names=List(3){"sync-${id()}.db"}
        fun open(i:Int)=Room.databaseBuilder(context,PlanDatabase::class.java,names[i]).build()
        val dbs=MutableList(3){open(it)}
        val roster=MemberRoster(groupId="trial",members=ids.mapIndexed {i,device->GroupMember(device,"確認$i",if(i==0)MemberRole.ADMIN else MemberRole.MEMBER,listOf(device))})
        val queue=ArrayDeque<Triple<Int,Int,ByteArray>>();val errors=mutableListOf<String>()
        val stores=MutableList(3){RoomRecordStore(dbs[it],ids[it])}
        fun engine(i:Int)=SyncEngine("trial",ids[i],stores[i],{to,bytes->queue.add(Triple(i,to.toInt(),bytes))},errors::add)
        val engines=MutableList(3){engine(it)}
        suspend fun drain(){var n=0;while(queue.isNotEmpty()){check(++n<1000);val(from,to,bytes)=queue.removeFirst();engines[to].receive(from.toString(),bytes)}}
        fun report(i:Int,time:Long)=QuakeEvent("trial",id(),ids[i],time,QuakeAction.REPORT,false,title="地震",area="地域")
        try {
            dbs.forEachIndexed {i,db->QuakeStore(db).importRoster(roster.encode(),ids[i])}
            val first=report(0,1);val second=report(1,2)
            QuakeStore(dbs[0]).command(first);QuakeStore(dbs[1]).command(second)
            assertTrue(QuakeStore(dbs[0]).states("trial").isEmpty())
            engines[0].connected("1");engines[1].connected("0");drain()
            assertEquals(1,QuakeStore(dbs[0]).states("trial").size)
            assertEquals(1,QuakeStore(dbs[1]).states("trial").size)
            val reportId=id();val store=ResponseStore(dbs[0])
            store.append(ResponseEvent("trial",reportId,ids[0],1,3,first.eventId,ResponseChange.Report(reportId,"道路",35.0,139.0,"倒木")))
            store.append(ResponseEvent("trial",id(),ids[0],1,4,first.eventId,ResponseChange.Assignment(ResponseTarget(TargetKind.REPORT,reportId),ids[0],true)))
            engines[0].changed();drain()
            engines[0].disconnected("1");engines[1].disconnected("0")
            dbs[1].close();dbs[1]=open(1);stores[1]=RoomRecordStore(dbs[1],ids[1]);engines[1]=engine(1)
            engines[1].connected("2");engines[2].connected("1");drain()
            assertEquals(2,ResponseStore(dbs[2]).heads("trial").size)
            QuakeStore(dbs[0]).command(QuakeEvent("trial",id(),ids[0],5,QuakeAction.END,false,targetId=first.eventId))
            engines[0].connected("1");engines[1].connected("0");drain()
            assertTrue(QuakeStore(dbs[2]).states("trial").single().closed)
            assertEquals(emptyList<String>(),errors)
            try {ResponseStore(dbs[2]).append(ResponseEvent("trial",id(),ids[2],1,6,first.eventId,ResponseChange.Progress(ResponseTarget(TargetKind.REPORT,reportId),WorkStatus.DONE,"確認済み")));fail("closed")}catch(_:IllegalArgumentException){}
        }finally {dbs.forEach {it.close()};names.forEach {context.deleteDatabase(it)}}
    }
}
