package io.github.nevaseme.plandmk1.location

import io.github.nevaseme.plandmk1.core.positionRequestInterval
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PositionFix(val latitude: Double, val longitude: Double, val accuracy: Double, val acquiredAt: Long)
data class PositionState(val active: Boolean = false, val message: String = "位置記録は停止中")

/** One cancellable acquisition/save pipeline, owned by the service rather than a screen. */
class PositionRecording(
    private val parent: CoroutineScope,
    private val acquire: suspend () -> PositionFix,
    private val save: suspend (PositionFix) -> Boolean,
) {
    private val mutable = MutableStateFlow(PositionState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var scope: CoroutineScope? = null
    private var timer: Job? = null
    private var triggers: Channel<Unit>? = null
    private var disasterActive = false

    fun start(disaster: Boolean) {
        if (job != null) return
        disasterActive = disaster
        val session = Job(parent.coroutineContext[Job]); job = session
        val worker = CoroutineScope(parent.coroutineContext + session); scope = worker
        val queue = Channel<Unit>(Channel.CONFLATED); triggers = queue
        mutable.value = PositionState(true, "位置を取得しています")
        worker.launch {
            for (ignored in queue) {
                mutable.value = PositionState(true, "位置を取得しています")
                try {
                    val fix = withTimeoutOrNull(30_000) { acquire() }
                    ensureActive()
                    if (fix == null) mutable.value = PositionState(true, "位置を取得できませんでした。再試行できます")
                    else {
                        val saved = save(fix); ensureActive()
                        mutable.value = PositionState(true, if(saved) "位置を保存しました" else "最後の保存位置を保持しています")
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.value = PositionState(true, e.localizedMessage ?: "位置を取得・保存できませんでした") }
            }
        }
        schedule(); refresh()
    }
    private fun schedule() {
        timer?.cancel()
        timer = scope?.launch { while (isActive) { delay(positionRequestInterval(disasterActive)); refresh() } }
    }
    fun disaster(active: Boolean) {
        if (disasterActive == active) return
        disasterActive = active
        if (job != null) { schedule(); refresh() }
    }
    fun refresh() { triggers?.trySend(Unit) }
    fun stop() {
        job?.cancel(); job = null; scope = null; timer = null
        triggers?.close(); triggers = null
        mutable.value = PositionState()
    }
}
