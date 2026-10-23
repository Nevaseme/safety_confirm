package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PositionSyncTest {
    private fun id()=UUID.randomUUID().toString()
    private val owner=id()
    private val position=PhonePosition("trial",owner,1,1,35.0,139.0,10.0)
    private val memo=SosMemo("trial",id(),owner,id(),1,2,"北の寝室")
    private class Store:RecordStore {
        val points=mutableMapOf<String,PhonePosition>();val notes=mutableMapOf<String,SosMemo>();var fail=false
        override suspend fun all(groupId:String)=emptyList<Resident>()
        override suspend fun get(groupId:String,originId:String):Resident?=null
        override suspend fun merge(record:Resident)=false
        override suspend fun positions(groupId:String)=points.values.filter {it.groupId==groupId}
        override suspend fun memos(groupId:String)=notes.values.filter {it.groupId==groupId}
        override suspend fun savePosition(position:PhonePosition):Boolean {check(!fail);if(!shouldReplacePosition(points[position.originId],position))return false;points[position.originId]=position;return true}
        override suspend fun mergeMemo(memo:SosMemo):Boolean {check(!fail);if(!shouldReplaceMemo(notes[memo.answerId],memo))return false;notes[memo.answerId]=memo;return true}
    }
    private fun bytes(message:WireMessage)=Json.encodeToString(message).encodeToByteArray()
    @Test fun savesBeforeAckRejectsOldAndConflictingMemoAndFailedWrites()=runTest {
        val store=Store();val sent=mutableListOf<WireMessage>()
        val engine=SyncEngine("trial",id(),store,{_,data->sent.add(Json.decodeFromString(data.decodeToString()))})
        engine.connected("A");sent.clear();store.fail=true
        val wire=WireMessage(5,"trial","POSITION_RECORD",position=position)
        engine.receive("A",bytes(wire));assertTrue(sent.isEmpty())
        store.fail=false;engine.receive("A",bytes(wire));assertEquals(position,store.points[owner]);assertEquals("POSITION_ACK",sent.single().type)
        engine.receive("A",bytes(WireMessage(5,"trial","MEMO_RECORD",memo=memo)))
        engine.receive("A",bytes(WireMessage(5,"trial","MEMO_RECORD",memo=memo.copy(revision=2,text="更新"))))
        engine.receive("A",bytes(WireMessage(5,"trial","MEMO_RECORD",memo=memo)))
        assertEquals("更新",store.notes[memo.answerId]?.text)
        val count=sent.size
        engine.receive("A",bytes(WireMessage(5,"trial","MEMO_RECORD",memo=memo.copy(revision=3,authorId=id()))))
        assertEquals(count,sent.size)
    }
    @Test fun disconnectRestartRelayAndReconnectUseStoredLatestRevision()=runTest {
        val stores=List(3){Store()};val queue=ArrayDeque<Triple<Int,Int,ByteArray>>()
        val engines=List(3){from->SyncEngine("trial",id(),stores[from],{to,data->queue.add(Triple(from,to.toInt(),data))})}
        suspend fun drain(){var count=0;while(queue.isNotEmpty()){check(++count<100);val(from,to,data)=queue.removeFirst();engines[to].receive(from.toString(),data)}}
        stores[0].savePosition(position);stores[0].mergeMemo(memo)
        engines[0].connected("1");engines[1].connected("0");drain()
        engines[0].disconnected("1");engines[1].disconnected("0")
        engines[1].connected("2");engines[2].connected("1");drain()
        assertEquals(position,stores[2].points[owner]);assertEquals(memo,stores[2].notes[memo.answerId])
        stores[0].savePosition(position.copy(revision=3,acquiredAt=3))
        engines[0].connected("1");engines[1].connected("0");drain()
        assertEquals(3L,stores[2].points[owner]?.revision)
    }
    @Test fun stoppingPositionSharingBlocksPendingRequestsButKeepsOtherData()=runTest {
        val store=Store();store.savePosition(position);store.mergeMemo(memo)
        val sent=mutableListOf<WireMessage>();var allowed=true
        val engine=SyncEngine("trial",owner,store,{_,data->sent.add(Json.decodeFromString(data.decodeToString()))},allowOwnPosition={allowed})
        engine.connected("A");assertTrue(sent.any {it.type=="POSITION_SUMMARY"})
        allowed=false;sent.clear();engine.changed()
        engine.receive("A",bytes(WireMessage(5,"trial","POSITION_REQUEST",listOf(VersionEntry(owner,1)))))
        assertTrue(sent.none {it.type.startsWith("POSITION_")})
        assertTrue(sent.any {it.type=="MEMO_SUMMARY"})
    }
}
