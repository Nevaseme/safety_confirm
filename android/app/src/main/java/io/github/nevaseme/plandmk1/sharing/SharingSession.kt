package io.github.nevaseme.plandmk1.sharing

import io.github.nevaseme.plandmk1.core.RecordStore
import io.github.nevaseme.plandmk1.core.SyncEngine
import io.github.nevaseme.plandmk1.nearby.NearbyPort
import io.github.nevaseme.plandmk1.nearby.NearbyState
import io.github.nevaseme.plandmk1.nearby.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class SharingSession(
    private val groupId: String, private val localId: String,
    private val port: NearbyPort, private val store: RecordStore,
    parentScope: CoroutineScope,
    private val publish: (NearbyState) -> Unit,
    private val finished: () -> Unit,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val commands = Channel<suspend () -> Unit>(256)
    private val engine = SyncEngine(groupId, localId, store, port::send, port::error, port::receipt)
    private var started = false
    private var alive = false
    private var foreground = true

    fun start(name: String) {
        if (started) return
        started = true; alive = true
        port.onConnected = { id -> enqueue { engine.connected(id) } }
        port.onDisconnected = { id -> enqueue { engine.disconnected(id) } }
        port.onBytes = { id, bytes -> enqueue { engine.receive(id, bytes) } }
        scope.launch {
            for (command in commands) {
                ensureActive()
                try { command() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { port.error("同期処理に失敗しました。再接続してください：${e.localizedMessage.orEmpty()}") }
            }
        }
        try { port.start(name) }
        catch (e: Exception) { stop("共有を開始できません：${e.localizedMessage.orEmpty()}"); return }
        scope.launch {
            port.state.collect { state ->
                if (!alive) return@collect
                if (!state.active) stop(state.error, state.showPlayServicesSettings)
                else if (!foreground && state.connected.isEmpty()) stop(NO_PEERS)
                else publish(state)
            }
        }
    }
    fun setForeground(visible: Boolean) {
        foreground = visible
        if (!alive) return
        try { port.setVisible(visible) }
        catch (e: Exception) { stop("通信権限や無線設定を確認して、共有を開始し直してください"); return }
        if (!visible && port.state.value.connected.isEmpty()) stop(NO_PEERS)
    }
    fun connect(peer: Peer) { if (alive && foreground) port.connect(peer) }
    fun verify(id: String, accept: Boolean) { if (alive && foreground) port.verify(id, accept) }
    fun changed() { enqueue { engine.changed() } }
    private fun enqueue(command: suspend () -> Unit) {
        if (alive && !commands.trySend(command).isSuccess) stop("受信が混み合ったため共有を停止しました。再接続してください")
    }
    fun stop(reason: String? = null, showPlayServicesSettings: Boolean = false) {
        if (!alive) return
        alive = false
        commands.cancel(); job.cancel()
        port.onConnected = {}; port.onDisconnected = {}; port.onBytes = { _, _ -> }
        try { port.stop() } finally { publish(NearbyState(error = reason, showPlayServicesSettings = showPlayServicesSettings)); finished() }
    }
    companion object { const val NO_PEERS = "接続相手がいないため共有を停止しました。再開するには「共有開始」を押してください" }
}
