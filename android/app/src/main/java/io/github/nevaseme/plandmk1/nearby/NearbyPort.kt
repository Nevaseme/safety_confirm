package io.github.nevaseme.plandmk1.nearby

import kotlinx.coroutines.flow.StateFlow

interface NearbyPort {
    val state: StateFlow<NearbyState>
    var onConnected: suspend (String) -> Unit
    var onDisconnected: (String) -> Unit
    var onBytes: (String, ByteArray) -> Unit
    fun start(name: String)
    fun setVisible(visible: Boolean)
    fun stop()
    fun connect(peer: Peer)
    fun verify(id: String, accept: Boolean)
    suspend fun send(peer: String, bytes: ByteArray)
    fun error(message: String)
    fun receipt()
}
