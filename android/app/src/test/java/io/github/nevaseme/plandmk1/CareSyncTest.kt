package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CareSyncTest {
    private val person = UUID.randomUUID().toString()
    private val author = UUID.randomUUID().toString()
    private fun event() = CareEvent("demo", UUID.randomUUID().toString(), person, author, "確認B", 2, CareAction.RESCUED, "本人を確認")
    private class Store : RecordStore {
        val care = mutableMapOf<String, CareEvent>()
        var fail = false
        override suspend fun all(groupId: String) = emptyList<Resident>()
        override suspend fun get(groupId: String, originId: String): Resident? = null
        override suspend fun merge(record: Resident) = false
        override suspend fun events(groupId: String) = care.values.filter { it.groupId == groupId }
        override suspend fun getEvent(groupId: String, eventId: String) = care[eventId]?.takeIf { it.groupId == groupId }
        override suspend fun mergeEvent(event: CareEvent): Boolean {
            check(!fail); event.validate()
            care[event.eventId]?.let { check(it == event); return false }
            care[event.eventId] = event; return true
        }
    }
    private fun bytes(e: CareEvent) = """{"version":2,"groupId":"demo","type":"CARE_RECORD","event":${Json.encodeToString(e)}}""".encodeToByteArray()
    @Test fun savesBeforeAckAndRelaysToAnotherPeer() = runTest {
        val store=Store(); val e=event(); val sent=mutableListOf<Pair<String,String>>()
        val engine=SyncEngine("demo",UUID.randomUUID().toString(),store,{peer,data ->
            val text=data.decodeToString(); if(text.contains("CARE_ACK"))assertEquals(e,store.care[e.eventId]);sent.add(peer to text)
        })
        engine.connected("A");engine.connected("C");sent.clear();engine.receive("A",bytes(e))
        assertEquals(e,store.care[e.eventId]);assertTrue(sent.any { it.first=="A"&&it.second.contains("CARE_ACK") });
        assertTrue(sent.any { it.first=="C"&&it.second.contains("CARE_SUMMARY") })
        store.fail=true;sent.clear();engine.receive("A",bytes(event()));assertFalse(sent.any { it.second.contains("CARE_ACK") })
    }
    @Test fun correctionsKeepHistoryAndCannotReplaceAnotherAuthorsObservation() {
        val base=event();val own=base.copy(eventId=UUID.randomUUID().toString(),action=CareAction.NEEDS_HELP,supersedes=base.eventId)
        assertEquals(listOf(own),currentCareEvents(listOf(base,own)))
        val foreign=own.copy(authorId=UUID.randomUUID().toString())
        assertEquals(2,currentCareEvents(listOf(base,foreign)).size)
    }
}
