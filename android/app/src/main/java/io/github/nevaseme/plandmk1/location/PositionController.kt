package io.github.nevaseme.plandmk1.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PositionController(private val context: Context) {
    private val mutable = MutableStateFlow(PositionState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var recording: PositionRecording? = null
    private var finish: (() -> Unit)? = null
    var foreground = false

    fun start() {
        if (mutable.value.active) { refresh(); return }
        if (!foreground) { error("アプリを開いて位置記録を開始してください"); return }
        val token = ++generation
        mutable.value = PositionState(true, "位置記録を開始しています")
        try { ContextCompat.startForegroundService(context, Intent(context, PositionService::class.java).putExtra("token", token)) }
        catch (e: Exception) { stop(); error("開始できません。位置情報の権限を確認してください") }
    }
    fun stop() {
        generation++
        val end = finish; finish = null
        recording?.stop(); recording = null
        mutable.value = PositionState()
        end?.invoke()
    }
    fun refresh() { recording?.refresh() }
    fun error(text: String) { mutable.value = mutable.value.copy(message = text) }
    internal fun current(token: Long) = token == generation && mutable.value.active
    internal fun attach(token: Long, session: PositionRecording, end: () -> Unit): Boolean {
        if (!current(token)) return false
        recording = session; finish = end; return true
    }
    internal fun publish(token: Long, value: PositionState) { if(current(token)) mutable.value = value }
    internal fun detached(token: Long) {
        if(token == generation) { recording = null; finish = null; mutable.value = PositionState() }
    }
}
