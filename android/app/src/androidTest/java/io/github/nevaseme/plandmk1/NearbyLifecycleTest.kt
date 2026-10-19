package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.sharing.SharingSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Real adapter with a controllable Play Services boundary; no radio assumptions. */
class NearbyLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun idle() { instrumentation.waitForIdleSync(); Thread.sleep(100); instrumentation.waitForIdleSync() }
    private class Client {
        val calls = mutableListOf<Pair<String, String?>>()
        lateinit var lifecycle: ConnectionLifecycleCallback
        var advertiseGate: TaskCompletionSource<Void>? = null
        val value = Proxy.newProxyInstance(ConnectionsClient::class.java.classLoader, arrayOf(ConnectionsClient::class.java)) { _, method, args ->
            calls.add(method.name to (args?.firstOrNull() as? String))
            if (method.name == "startAdvertising") {
                lifecycle = args!![2] as ConnectionLifecycleCallback
                advertiseGate?.task ?: Tasks.forResult<Void>(null)
            } else if (Task::class.java.isAssignableFrom(method.returnType)) Tasks.forResult<Void>(null)
            else null
        } as ConnectionsClient
    }
    @Test fun backgroundStopsDiscoveryCancelsPendingAndKeepsApprovedConnections() {
        val client = Client(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main { p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }; p.start("test") }
        idle()
        main {
            client.lifecycle.onConnectionInitiated("A", ConnectionInfo("A", "1234", true))
            p.verify("A", true)
            client.lifecycle.onConnectionResult("A", ConnectionResolution(Status.RESULT_SUCCESS))
            client.lifecycle.onConnectionInitiated("pending", ConnectionInfo("pending", "5678", true))
            p.setVisible(false)
            assertEquals(setOf("A"), p.state.value.connected)
            assertTrue(p.state.value.verifications.isEmpty()); assertTrue(p.state.value.connecting.isEmpty())
            assertTrue(client.calls.contains("stopAdvertising" to null)); assertTrue(client.calls.contains("stopDiscovery" to null))
            assertTrue(client.calls.contains("rejectConnection" to "pending")); assertTrue(client.calls.contains("disconnectFromEndpoint" to "pending"))
            assertFalse(client.calls.any { it.first == "stopAllEndpoints" })
            client.lifecycle.onConnectionResult("pending", ConnectionResolution(Status.RESULT_SUCCESS))
            assertEquals(setOf("A"), p.state.value.connected)
            p.setVisible(true)
        }
        idle()
        main {
            assertEquals(2, client.calls.count { it.first == "startAdvertising" })
            assertEquals(2, client.calls.count { it.first == "startDiscovery" })
            p.stop(); scope.cancel()
        }
    }
    @Test fun completionAfterStopCleansUpButCannotStopNewSession() {
        val old = Client().apply { advertiseGate = TaskCompletionSource() }
        val next = Client()
        val oldScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var a: NearbyTransport; lateinit var b: NearbyTransport
        main {
            a = NearbyTransport(instrumentation.targetContext, oldScope, old.value) { true }; a.start("old"); a.stop(); oldScope.cancel()
            b = NearbyTransport(instrumentation.targetContext, newScope, next.value) { true }; b.start("new")
        }
        idle()
        val before = old.calls.count { it.first == "stopAdvertising" }
        main { old.advertiseGate!!.setResult(null) }; idle()
        assertEquals(before, old.calls.count { it.first == "stopAdvertising" })
        assertTrue(b.state.value.active)
        main { b.stop(); newScope.cancel() }
    }
    @Test fun cancelledAttemptCallbacksCannotReplaceOrDisconnectNewAttempt() {
        val client = Client(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main { p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }; p.start("test") }; idle()
        val old = client.lifecycle
        main {
            old.onConnectionInitiated("B", ConnectionInfo("old", "1234", true))
            p.setVisible(false); p.setVisible(true)
        }; idle()
        main {
            old.onConnectionInitiated("B", ConnectionInfo("cancelled", "1234", true))
            assertTrue("Cancelled verification reappeared", p.state.value.verifications.isEmpty())
            client.lifecycle.onConnectionInitiated("B", ConnectionInfo("new", "5678", true))
            old.onDisconnected("B")
            assertEquals("new", p.state.value.verifications.single().name)
            p.verify("B", true)
            old.onConnectionResult("B", ConnectionResolution(Status.RESULT_SUCCESS))
            assertTrue(p.state.value.connected.isEmpty())
            client.lifecycle.onConnectionResult("B", ConnectionResolution(Status.RESULT_SUCCESS))
            assertEquals(setOf("B"), p.state.value.connected)
            p.stop(); scope.cancel()
        }
    }
    @Test fun failedSearchPublishesReasonBeforeSessionStops() {
        val client = Client().apply { advertiseGate = TaskCompletionSource() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = object : RecordStore {
            override suspend fun all(groupId: String) = emptyList<Resident>()
            override suspend fun get(groupId: String, originId: String): Resident? = null
            override suspend fun merge(record: Resident) = false
        }
        var published = NearbyState(); var ended = false
        main {
            val p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }
            SharingSession("demo", "local", p, store, scope, { published = it }, { ended = true }).start("test")
            client.advertiseGate!!.setException(IllegalStateException("radio rejected"))
        }; idle()
        assertTrue(ended); assertFalse(published.active)
        assertTrue("Missing failure explanation", published.error?.contains("radio rejected") == true)
        main { scope.cancel() }
    }
    @Test fun delayedAdvertisingIsStoppedBeforeRapidForegroundReturnRestartsIt() {
        val client = Client().apply { advertiseGate = TaskCompletionSource() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var p: NearbyTransport
        main {
            p = NearbyTransport(instrumentation.targetContext, scope, client.value) { true }
            p.start("test"); p.setVisible(false); p.setVisible(true)
        }
        val gate = client.advertiseGate!!
        main { client.advertiseGate = null; gate.setResult(null) }; idle()
        main {
            val names = client.calls.map { it.first }
            assertEquals(2, names.count { it == "startAdvertising" })
            assertEquals("stopAdvertising", names[names.lastIndexOf("startAdvertising") - 1])
            assertEquals(1, names.count { it == "startDiscovery" })
            p.stop(); scope.cancel()
        }
    }
}
