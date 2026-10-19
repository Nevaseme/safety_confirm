package io.github.nevaseme.plandmk1.lab

import io.github.nevaseme.plandmk1.BuildConfig
import io.github.nevaseme.plandmk1.core.RecordStore
import io.github.nevaseme.plandmk1.core.SyncEngine
import io.github.nevaseme.plandmk1.core.ConnectionProgress
import io.github.nevaseme.plandmk1.core.ConnectionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LabState(val active: Boolean = false, val peers: Set<String> = emptySet(), val phase: String? = null, val receipts: Int = 0, val error: String? = null, val connection: ConnectionStatus = ConnectionStatus())

/** Foreground-only test transport. The same SyncEngine/Room are used as Nearby. */
class LabController(private val store: RecordStore, private val localId: String) {
    private val mutable = MutableStateFlow(LabState())
    val state = mutable.asStateFlow()
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var job: Job? = null
    private var commands: Channel<suspend () -> Unit>? = null
    private var engine: SyncEngine? = null
    private var generation = 0L
    private var visible = false
    private val progress = ConnectionProgress()
    private var deadline: Job? = null

    fun setVisible(value: Boolean) { visible = value; if (!value && mutable.value.active) stop(text = "画面を閉じたため実験通信を停止しました") }
    fun start(url: String, token: String, group: String) {
        if (!BuildConfig.DEBUG || !visible || mutable.value.active) return
        stop()
        val epoch = generation
        try {
            require(url.startsWith("wss://") && token.isNotBlank()) { "WSSアドレスと接続コードを入力してください" }
            val request = Request.Builder().url(url).build()
            require(request.url.encodedPath == "/socket" && request.url.username.isEmpty() && request.url.password.isEmpty()) { "アドレスの末尾は /socket にしてください" }
            val connectionEpoch = checkNotNull(progress.start())
            mutable.value = LabState(active = true, connection = progress.state)
            deadline = main.launch {
                delay(15_000)
                if (epoch == generation && progress.timeout(connectionEpoch)) stop(progress.state.error)
            }
            val channel = Channel<suspend () -> Unit>(256); commands = channel
            val sync = SyncEngine(group, localId, store, send = { peer, data ->
                check(epoch == generation && socket?.send(JSONObject().put("kind", "data").put("to", peer).put("body", JSONObject(data.decodeToString())).toString()) == true) { "実験接続が終了しました" }
            }, onError = { if (epoch == generation) mutable.value = mutable.value.copy(error = it) },
                onReceipt = { if (epoch == generation) mutable.value = mutable.value.copy(receipts = mutable.value.receipts + 1) })
            engine = sync
            job = main.launch {
                for (command in channel) {
                    ensureActive(); if (epoch != generation) break
                    try { command() } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (epoch == generation) stop("実験通信に失敗しました。再接続してください") }
                }
            }
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = post(epoch) {
                    webSocket.send(JSONObject().put("kind", "join").put("role", "A").put("group", group).put("token", token).toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) = post(epoch) {
                    if (text.toByteArray().size > 32000) { stop("受信サイズが上限を超えました"); return@post }
                    val m = JSONObject(text)
                    when (m.optString("kind")) {
                        "joined" -> { if (progress.joined(connectionEpoch)) { deadline?.cancel(); deadline = null; mutable.value = mutable.value.copy(phase = m.getString("phase"), connection = progress.state, error = null) } }
                        "phase" -> mutable.value = mutable.value.copy(phase = m.getString("phase"))
                        "peer-up" -> { val peer = m.getString("peer"); val peers = mutable.value.peers + peer; progress.peers(connectionEpoch, peers.size); mutable.value = mutable.value.copy(peers = peers, connection = progress.state); sync.connected(peer) }
                        "peer-down" -> { val peer = m.getString("peer"); sync.disconnected(peer); val peers = mutable.value.peers - peer; progress.peers(connectionEpoch, peers.size); mutable.value = mutable.value.copy(peers = peers, connection = progress.state) }
                        "data" -> sync.receive(m.getString("from"), m.getJSONObject("body").toString().encodeToByteArray())
                        "error" -> mutable.value = mutable.value.copy(error = m.optString("message"))
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = post(epoch) {
                    stop("接続できません。証明書・同じWi-Fi・PCの実験サーバー・接続コードを確認してください")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = post(epoch) { stop("実験接続が終了しました。役割Aの重複や接続コードを確認してください") }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = post(epoch) { stop() }
            })
        } catch (e: Exception) { stop(e.localizedMessage ?: "接続設定を確認してください") }
    }
    private fun post(epoch: Long, action: suspend () -> Unit) {
        main.launch {
            if (epoch == generation && commands?.trySend(action)?.isSuccess == false) stop("受信が混み合ったため停止しました")
        }
    }
    fun changed() { val sync = engine ?: return; post(generation) { sync.changed() } }
    fun stop(reason: String? = null, text: String = "実験通信停止中") {
        deadline?.cancel(); deadline = null; progress.stop(text, reason)
        generation++; commands?.cancel(); commands = null; job?.cancel(); job = null; engine = null
        val old = socket; socket = null; old?.close(1000, "stopped"); old?.cancel()
        mutable.value = LabState(error = reason, connection = progress.state)
    }
}
