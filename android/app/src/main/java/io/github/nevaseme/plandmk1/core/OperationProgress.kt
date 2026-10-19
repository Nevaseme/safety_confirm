package io.github.nevaseme.plandmk1.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SavePhase { IDLE, SAVING, SAVED, ERROR }
data class SaveStatus(val phase: SavePhase = SavePhase.IDLE, val text: String = "", val savedAt: Long? = null)

/** Called on Main. Durable commit and subsequent publication are deliberately separate. */
class SaveOperations(private val busy: MutableStateFlow<Boolean>, private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow<Map<String, SaveStatus>>(emptyMap())
    val states = mutable.asStateFlow()
    private var active: String? = null
    fun clearOtherResults() { mutable.value = mutable.value.filterKeys { it == active } }
    private fun set(key: String, status: SaveStatus) { mutable.value = mutable.value + (key to status) }
    fun begin(key: String): Boolean {
        if (busy.value) return false
        active = key; busy.value = true; set(key, SaveStatus(SavePhase.SAVING, "保存しています…")); return true
    }
    suspend fun complete(key: String, label: String, persist: suspend () -> Unit, publish: suspend () -> Unit) {
        check(active == key)
        var committed = false
        try {
            persist(); committed = true
            set(key, SaveStatus(SavePhase.SAVED, "${label}をこの端末に保存しました", now()))
            try { publish() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { set(key, mutable.value.getValue(key).copy(text = "${label}はこの端末に保存済みです。${e.localizedMessage.orEmpty()}。表示グループを確認して画面を開き直し、通信は再接続してください。")) }
        } catch (e: CancellationException) {
            if (!committed) set(key, SaveStatus(SavePhase.ERROR, "保存処理が中断されました。保存内容を確認してから再試行してください。"))
            throw e
        } catch (e: Exception) {
            set(key, SaveStatus(SavePhase.ERROR, "保存できませんでした：${e.localizedMessage ?: "保存先を確認してください"}。入力を確認して再試行してください。"))
        } finally { active = null; busy.value = false }
    }
}

enum class ConnectionPhase { STOPPED, CONNECTING, WAITING, CONNECTED }
data class ConnectionStatus(val phase: ConnectionPhase = ConnectionPhase.STOPPED, val text: String = "実験通信停止中", val error: String? = null)
class ConnectionProgress {
    var state = ConnectionStatus(); private set
    private var epoch = 0L
    fun start(): Long? {
        if (state.phase != ConnectionPhase.STOPPED) return null
        state = ConnectionStatus(ConnectionPhase.CONNECTING, "接続しています…"); return ++epoch
    }
    fun joined(token: Long): Boolean {
        if (token != epoch || state.phase != ConnectionPhase.CONNECTING) return false
        state = ConnectionStatus(ConnectionPhase.WAITING, "サーバー接続済み・相手待ち"); return true
    }
    fun peers(token: Long, count: Int) {
        if (token != epoch || state.phase !in listOf(ConnectionPhase.WAITING, ConnectionPhase.CONNECTED)) return
        state = ConnectionStatus(if (count > 0) ConnectionPhase.CONNECTED else ConnectionPhase.WAITING,
            if (count > 0) "相手 ${count} 台と接続中" else "サーバー接続済み・相手待ち")
    }
    fun timeout(token: Long): Boolean {
        if (token != epoch || state.phase != ConnectionPhase.CONNECTING) return false
        stop(error = "接続を確認できませんでした。サーバー・接続コードを確認して再試行してください。"); return true
    }
    fun stop(text: String = "実験通信停止中", error: String? = null) { epoch++; state = ConnectionStatus(text = text, error = error) }
}
