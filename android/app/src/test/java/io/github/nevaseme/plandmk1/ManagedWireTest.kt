package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ManagedWireTest {
    private val fixture=Json.parseToJsonElement(checkNotNull(javaClass.getResource("/managed-v6.json")).readText()).jsonObject
    private val roster=Json.decodeFromString<MemberRoster>(fixture.getValue("roster").toString())
    private val quakes=Json.decodeFromString<List<QuakeEvent>>(fixture.getValue("quakes").toString())
    private val responses=Json.decodeFromString<List<ResponseEvent>>(fixture.getValue("responses").toString())
    @Test fun sharedKotlinJavaScriptFixtureKeepsEveryWireField() {
        roster.validate();quakes.forEach {it.validate(roster)};responses.forEach {validateResponseShape(roster,it)}
        assertEquals(1,quakeStates(roster,quakes).size);assertEquals(6,responseHeads(roster,responses).size)
        val json=Json {encodeDefaults=true}
        assertEquals(fixture.getValue("quakes"),json.encodeToJsonElement(quakes))
        assertEquals(fixture.getValue("responses"),json.encodeToJsonElement(responses))
    }
    @Test fun saveFailureHasNoAckAndDuplicateNeverForwardsAgain()=runTest {
        val saved=mutableListOf<ResponseEvent>();var fail=true
        val store=object:RecordStore {
            override suspend fun all(groupId:String)=emptyList<Resident>()
            override suspend fun get(groupId:String,originId:String):Resident?=null
            override suspend fun merge(record:Resident)=false
            override suspend fun responseEvents(groupId:String)=saved.toList()
            override suspend fun mergeResponse(event:ResponseEvent):Boolean {
                check(!fail);validateResponseShape(roster,event)
                saved.find {it.eventId==event.eventId}?.let {require(it==event);return false}
                saved.add(event);return true
            }
        }
        val sent=mutableListOf<Pair<String,WireMessage>>()
        val engine=SyncEngine("trial",roster.members.last().deviceIds.single(),store,{to,b->sent.add(to to Json.decodeFromString(b.decodeToString()))})
        engine.connected("A");engine.connected("C");sent.clear()
        val bytes=Json.encodeToString(WireMessage(6,"trial","RESPONSE_RECORD",response=responses.first())).encodeToByteArray()
        engine.receive("A",bytes);assertTrue(sent.isEmpty());assertTrue(saved.isEmpty())
        fail=false;engine.receive("A",bytes);assertEquals(1,saved.size);assertTrue(sent.any {it.first=="C"&&it.second.type=="RESPONSE_SUMMARY"})
        sent.clear();engine.receive("A",bytes);assertEquals(listOf("RESPONSE_ACK"),sent.map {it.second.type})
    }
}
