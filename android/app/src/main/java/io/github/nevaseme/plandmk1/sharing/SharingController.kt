package io.github.nevaseme.plandmk1.sharing

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.nevaseme.plandmk1.nearby.NearbyState
import io.github.nevaseme.plandmk1.nearby.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Main-thread UI facade. Only the service owns a running session. */
class SharingController(private val context: Context) {
    private val mutable = MutableStateFlow(NearbyState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var session: SharingSession? = null
    var foreground = false
        private set

    fun start(groupId: String, name: String) {
        if (mutable.value.active) return
        if (!foreground) { error("アプリを開いてから共有を開始してください"); return }
        val token = ++generation
        mutable.value = NearbyState(active = true, starting = true)
        try {
            ContextCompat.startForegroundService(context, Intent(context, SharingService::class.java)
                .setAction(SharingService.START).putExtra("token", token).putExtra("group", groupId).putExtra("name", name))
        } catch (e: Exception) { stop("共有を開始できません。権限や無線設定を確認してください：${e.localizedMessage.orEmpty()}") }
    }
    fun stop(reason: String? = null) {
        generation++
        val previous = session; session = null
        previous?.stop()
        // A pending startForegroundService must reach onStartCommand and promote before
        // stopping. Killing it here can leave Android's foreground-start deadline armed.
        // An attached session stops its service through its finished callback.
        mutable.value = NearbyState(error = reason)
    }
    fun setForeground(visible: Boolean) {
        foreground = visible
        session?.setForeground(visible)
        if (!visible && session == null && mutable.value.active) stop(SharingSession.NO_PEERS)
    }
    fun connect(peer: Peer) { if (foreground) session?.connect(peer) }
    fun verify(id: String, accept: Boolean) { if (foreground) session?.verify(id, accept) }
    fun changed() { session?.changed() }
    fun error(message: String) { mutable.update { it.copy(error = message) } }
    internal fun isCurrent(token: Long) = generation == token && mutable.value.active
    internal fun attach(token: Long, value: SharingSession): Boolean {
        if (!isCurrent(token) || !foreground) return false
        session = value; return true
    }
    internal fun publish(token: Long, value: NearbyState) { if (generation == token) mutable.value = value }
    internal fun detach(token: Long) { if (generation == token) { session = null; mutable.update { it.copy(active = false, starting = false) } } }
}
