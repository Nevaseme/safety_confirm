package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SyncEngineTest {
    @Test fun stillRelaysWhenTheSenderDisconnectsBeforeAcknowledgement() = runTest {
        val store = MemoryStore()
        val sent = mutableListOf<Pair<String, WireMessage>>()
        val engine = SyncEngine("demo", UUID.randomUUID().toString(), store, send = { peer, bytes ->
            val message = Json.decodeFromString<WireMessage>(bytes.decodeToString())
            if (peer == "A" && message.type == "ACK") error("A disconnected")
            sent.add(peer to message)
        })
        engine.connected("A"); engine.connected("C"); sent.clear()
        val record = Resident("demo", UUID.randomUUID().toString(), 1, 0, "A", 35.0, 139.0, "1", "room", Safety.SOS)
        engine.receive("A", Json.encodeToString(WireMessage(groupId = "demo", type = "RECORD", record = record)).encodeToByteArray())
        assertEquals(record, store.get("demo", record.originId))
        assertTrue(sent.any { (peer, message) -> peer == "C" && message.type == "SUMMARY" && message.entries.single().originId == record.originId })
    }
    private class MemoryStore : RecordStore {
        val records = mutableMapOf<Pair<String, String>, Resident>()
        var failWrites = false
        override suspend fun all(groupId: String) = records.values.filter { it.groupId == groupId }
        override suspend fun get(groupId: String, originId: String) = records[groupId to originId]
        override suspend fun merge(record: Resident): Boolean {
            if (failWrites) error("disk full")
            record.validate()
            val key = record.groupId to record.originId
            if ((records[key]?.revision ?: 0) >= record.revision) return false
            records[key] = record
            return true
        }
    }
    private class Network {
        val stores = mutableMapOf<String, MemoryStore>()
        val nodes = mutableMapOf<String, SyncEngine>()
        val ids = mutableMapOf<String, String>()
        val receipts = mutableMapOf<String, Int>()
        val links = mutableSetOf<Set<String>>()
        val queue = ArrayDeque<Triple<String, String, ByteArray>>()
        var messages = 0
        fun node(name: String, group: String = "demo") {
            val store = stores.getOrPut(name) { MemoryStore() }
            val id = ids.getOrPut(name) { UUID.randomUUID().toString() }
            nodes[name] = SyncEngine(group, id, store, send = { peer, bytes ->
                check(bytes.size <= 24_000)
                if (setOf(name, peer) in links) queue.add(Triple(name, peer, bytes))
            }, onReceipt = { receipts[name] = (receipts[name] ?: 0) + 1 })
        }
        suspend fun connect(a: String, b: String) {
            links.add(setOf(a, b)); nodes.getValue(a).connected(b); nodes.getValue(b).connected(a); drain()
        }
        fun disconnect(a: String, b: String) {
            links.remove(setOf(a, b)); nodes.getValue(a).disconnected(b); nodes.getValue(b).disconnected(a)
        }
        suspend fun drain() {
            var count = 0
            while (queue.isNotEmpty()) {
                check(++count < 3000) { "message loop" }
                val (from, to, bytes) = queue.removeFirst()
                messages++
                if (setOf(from, to) in links) nodes.getValue(to).receive(from, bytes)
            }
        }
        suspend fun update(name: String, revision: Long, safety: Safety, time: Long = 100) {
            stores.getValue(name).merge(Resident("demo", ids.getValue(name), revision, time, "住民$name", 35.68, 139.76, "2階", "北側の部屋", safety))
            nodes.getValue(name).changed(); drain()
        }
    }
    @Test fun carriesInformationThroughAnOfflineRelayAfterRestart() = runTest {
        val n = Network(); listOf("A", "B", "C").forEach { n.node(it) }
        n.update("A", 1, Safety.SOS)
        n.connect("A", "B"); n.disconnect("A", "B")
        n.node("B")
        n.connect("B", "C")
        assertEquals(Safety.SOS, n.stores.getValue("C").get("demo", n.ids.getValue("A"))?.safety)
        assertEquals(1, n.stores.getValue("C").all("demo").size)
    }
    @Test fun newerRevisionWinsEvenWhenClockMovesBackwards() = runTest {
        val n = Network(); listOf("A", "B", "C").forEach { n.node(it) }
        n.update("A", 1, Safety.SOS, 9999); n.connect("A", "B"); n.disconnect("A", "B")
        n.update("A", 2, Safety.SAFE, 1); n.connect("A", "C"); n.disconnect("A", "C")
        n.connect("B", "C")
        assertEquals(Safety.SAFE, n.stores.getValue("B").get("demo", n.ids.getValue("A"))?.safety)
        assertEquals(2L, n.stores.getValue("C").get("demo", n.ids.getValue("A"))?.revision)
    }
    @Test fun cyclesConvergeAndReconnectionSendsNoDuplicateRecords() = runTest {
        val n = Network(); listOf("A", "B", "C").forEach { n.node(it) }
        n.connect("A", "B"); n.connect("B", "C"); n.connect("C", "A")
        n.update("A", 1, Safety.SOS)
        n.stores.values.forEach { assertEquals(1, it.all("demo").size) }
        assertTrue(n.messages < 30)
        n.disconnect("A", "B"); val before = n.messages; n.connect("A", "B")
        assertEquals(2, n.messages - before)
    }
    @Test fun rejectsDifferentGroupsAndMalformedPackets() = runTest {
        val n = Network(); n.node("A"); n.node("B", "other")
        n.update("A", 1, Safety.SOS); n.connect("A", "B")
        n.nodes.getValue("B").receive("A", "not json".toByteArray())
        n.nodes.getValue("B").receive("A", ByteArray(40_000))
        assertTrue(n.stores.getValue("B").records.isEmpty())
    }
    @Test fun retriesAfterDisconnectBeforeDelivery() = runTest {
        val n = Network(); n.node("A"); n.node("B"); n.connect("A", "B")
        n.disconnect("A", "B"); n.update("A", 1, Safety.SOS); n.connect("A", "B")
        assertEquals(Safety.SOS, n.stores.getValue("B").get("demo", n.ids.getValue("A"))?.safety)
    }
    @Test fun dividesLargeInventoriesIntoBoundedPackets() = runTest {
        val n = Network(); n.node("A"); n.node("B")
        repeat(250) { i -> n.stores.getValue("A").merge(Resident("demo", UUID.randomUUID().toString(), 1, 0, "住民$i", 35.0, 139.0, "1", "room", Safety.SAFE)) }
        n.connect("A", "B")
        assertEquals(250, n.stores.getValue("B").all("demo").size)
    }
    @Test fun doesNotAcknowledgeFailedStorageAndRecoversOnReconnect() = runTest {
        val n = Network(); n.node("A"); n.node("B"); n.update("A", 1, Safety.SOS)
        n.stores.getValue("B").failWrites = true
        n.connect("A", "B")
        assertTrue(n.stores.getValue("B").records.isEmpty())
        assertEquals(0, n.receipts["A"] ?: 0)
        n.disconnect("A", "B"); n.stores.getValue("B").failWrites = false; n.connect("A", "B")
        assertEquals(1, n.stores.getValue("B").all("demo").size)
        assertEquals(1, n.receipts["A"])
    }
    @Test fun rejectsOwnRecordAndUnsupportedProtocolVersion() = runTest {
        val store = MemoryStore()
        val own = Resident("demo", UUID.randomUUID().toString(), 1, 0, "自分", 35.0, 139.0, "1", "room", Safety.SAFE)
        store.merge(own)
        val sent = mutableListOf<WireMessage>()
        val engine = SyncEngine("demo", own.originId, store, send = { _, bytes -> sent.add(Json.decodeFromString(bytes.decodeToString())) })
        engine.connected("peer"); sent.clear()
        engine.receive("peer", Json.encodeToString(WireMessage(groupId = "demo", type = "RECORD", record = own.copy(revision = 99, safety = Safety.SOS))).encodeToByteArray())
        engine.receive("peer", Json.encodeToString(WireMessage(version = 2, groupId = "demo", type = "RECORD", record = own.copy(originId = UUID.randomUUID().toString()))).encodeToByteArray())
        assertEquals(listOf(own), store.all("demo"))
        assertTrue(sent.isEmpty())
    }
}
