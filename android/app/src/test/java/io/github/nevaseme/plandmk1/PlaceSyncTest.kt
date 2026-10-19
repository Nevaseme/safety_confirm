package io.github.nevaseme.plandmk1
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class PlaceSyncTest {
    @Test fun receiptFollowsSaveAndDisconnectedSenderDoesNotPreventRelay()=runTest {
        val rows=mutableListOf<PlaceEvent>();var fail=false
        val store=object: RecordStore {
            override suspend fun all(groupId:String)=emptyList<Resident>()
            override suspend fun get(groupId:String,originId:String):Resident?=null
            override suspend fun merge(record:Resident)=false
            override suspend fun places(groupId:String)=rows.toList()
            override suspend fun getPlace(groupId:String,eventId:String)=rows.find {it.eventId==eventId}
            override suspend fun mergePlace(event:PlaceEvent):Boolean {check(!fail);if(rows.any {it==event})return false;rows+=event;return true}
        }
        val id=UUID.randomUUID().toString();val e=PlaceEvent("test",id,UUID.randomUUID().toString(),"A",1,1,PlaceKind.BUILDING,buildingId=id,label="家",latitude=35.0,longitude=139.0)
        val sent=mutableListOf<Pair<String,WireMessage>>()
        val engine=SyncEngine("test",UUID.randomUUID().toString(),store,{peer,bytes->val m=Json.decodeFromString<WireMessage>(bytes.decodeToString());if(m.type=="PLACE_ACK"){assertEquals(e,rows.single());throw IllegalStateException("sender disconnected")};sent+=peer to m})
        engine.connected("A");engine.connected("C");sent.clear()
        engine.receive("A",Json.encodeToString(WireMessage(4,"test","PLACE_RECORD",place=e)).encodeToByteArray())
        assertTrue(sent.any {it.first=="C"&&it.second.type=="PLACE_SUMMARY"});sent.clear();fail=true
        engine.receive("A",Json.encodeToString(WireMessage(4,"test","PLACE_RECORD",place=e.copy(eventId=UUID.randomUUID().toString()))).encodeToByteArray())
        assertTrue(sent.isEmpty())
    }
}
