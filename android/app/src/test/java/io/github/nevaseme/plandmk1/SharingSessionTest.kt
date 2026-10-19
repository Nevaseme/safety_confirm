package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SharingSessionTest {
    @Test fun preservesPermissionRecoveryWhenFailedTransportEndsSession() = runTest {
        val port = Port(); var latest = NearbyState()
        val session = SharingSession("demo", UUID.randomUUID().toString(), port, Store(), backgroundScope, { latest = it }, {})
        session.start("自分"); runCurrent()
        port.state.value = NearbyState(error = "Google Play権限不足", showPlayServicesSettings = true)
        runCurrent()
        assertFalse(latest.active)
        assertTrue(latest.showPlayServicesSettings)
        assertEquals("Google Play権限不足", latest.error)
        assertEquals(1, port.stops)
    }
    private class Port : NearbyPort {
        override val state = MutableStateFlow(NearbyState())
        override var onConnected: suspend (String) -> Unit = {}
        override var onDisconnected: (String) -> Unit = {}
        override var onBytes: (String, ByteArray) -> Unit = { _, _ -> }
        var discovering = true
        var starts = 0
        var stops = 0
        var gate: CompletableDeferred<Unit>? = null
        val packets = mutableListOf<Pair<String, WireMessage>>()
        override fun start(name: String) { starts++; state.value = NearbyState(active = true) }
        override fun setVisible(visible: Boolean) { this.discovering = visible }
        override fun stop() { stops++; state.value = NearbyState() }
        override fun connect(peer: Peer) = Unit
        override fun verify(id: String, accept: Boolean) = Unit
        override suspend fun send(peer: String, bytes: ByteArray) {
            gate?.await()
            packets.add(peer to Json.decodeFromString<WireMessage>(bytes.decodeToString()))
        }
        override fun error(message: String) { state.value = state.value.copy(error = message) }
        override fun receipt() = Unit
        suspend fun join(id: String) { state.value = state.value.copy(connected = state.value.connected + id); onConnected(id) }
        fun leave(id: String) { state.value = state.value.copy(connected = state.value.connected - id); onDisconnected(id) }
    }
    private class Store : RecordStore {
        val rows = mutableMapOf<String, Resident>()
        override suspend fun all(groupId: String) = rows.values.filter { it.groupId == groupId }
        override suspend fun get(groupId: String, originId: String) = rows[originId]?.takeIf { it.groupId == groupId }
        override suspend fun merge(record: Resident): Boolean {
            if ((rows[record.originId]?.revision ?: 0) >= record.revision) return false
            rows[record.originId] = record; return true
        }
    }
    @Test fun receivesPersistsAcknowledgesAndRelaysWhileBackgrounded() = runTest {
        val p = Port(); val db = Store(); var ended = 0
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, db, backgroundScope, {}, { ended++ })
        s.start("自分"); runCurrent(); p.join("A"); p.join("C"); runCurrent(); p.packets.clear()
        s.setForeground(false); runCurrent()
        val r = Resident("demo", UUID.randomUUID().toString(), 1, 1, "住民A", 35.0, 139.0, "2", "北", Safety.SOS)
        p.onBytes("A", Json.encodeToString(WireMessage(groupId = "demo", type = "RECORD", record = r)).encodeToByteArray()); runCurrent()
        assertFalse(p.discovering); assertEquals(r, db.rows[r.originId]); assertEquals(0, ended)
        assertTrue(p.packets.any { it.first == "A" && it.second.type == "ACK" })
        assertTrue(p.packets.any { it.first == "C" && it.second.type == "SUMMARY" })
        s.stop()
    }
    @Test fun lastBackgroundDisconnectStopsButOneRemainingPeerDoesNot() = runTest {
        val p = Port(); var ended = 0
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, Store(), backgroundScope, {}, { ended++ })
        s.start("自分"); runCurrent(); p.join("A"); p.join("B"); runCurrent(); s.setForeground(false)
        p.leave("A"); runCurrent(); assertEquals(0, ended)
        p.leave("B"); runCurrent(); assertEquals(1, ended); assertEquals(1, p.stops)
    }
    @Test fun stopsWithoutPeersAndNeverRestartsOnForeground() = runTest {
        val p = Port(); var ended = 0
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, Store(), backgroundScope, {}, { ended++ })
        s.start("自分"); runCurrent(); s.setForeground(false); runCurrent(); s.setForeground(true)
        assertEquals(1, ended); assertEquals(1, p.starts)
    }
    @Test fun foregroundReturnResumesDiscoveryWithoutRestartingConnections() = runTest {
        val p = Port()
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, Store(), backgroundScope, {}, {})
        s.start("自分"); runCurrent(); p.join("A"); runCurrent(); s.setForeground(false); s.setForeground(true)
        assertTrue(p.discovering); assertEquals(setOf("A"), p.state.value.connected); assertEquals(1, p.starts)
        s.stop()
    }
    @Test fun repeatedStartAndStopAreIdempotentAndLateCallbacksCannotWrite() = runTest {
        val p = Port(); val db = Store(); var ended = 0
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, db, backgroundScope, {}, { ended++ })
        s.start("自分"); s.start("自分"); runCurrent(); p.join("A"); runCurrent()
        val late = p.onBytes
        s.stop(); s.stop();
        val r = Resident("demo", UUID.randomUUID().toString(), 1, 1, "A", 35.0, 139.0, "", "")
        late("A", Json.encodeToString(WireMessage(groupId = "demo", type = "RECORD", record = r)).encodeToByteArray()); runCurrent()
        assertTrue(db.rows.isEmpty()); assertEquals(1, p.starts); assertEquals(1, ended)
    }
    @Test fun stopCancelsSuspendedSendingAndQueuedChanges() = runTest {
        val p = Port()
        val s = SharingSession("demo", UUID.randomUUID().toString(), p, Store(), backgroundScope, {}, {})
        s.start("自分"); runCurrent(); p.gate = CompletableDeferred(); p.join("A"); runCurrent(); s.changed(); s.stop()
        p.gate!!.complete(Unit); runCurrent(); assertTrue(p.packets.isEmpty())
    }
}

