package io.github.nevaseme.plandmk1.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Peer(val id: String, val name: String)
data class Verification(val id: String, val name: String, val code: String)
data class NearbyState(
    val active: Boolean = false,
    val starting: Boolean = false,
    val discovering: Boolean = false,
    val peers: List<Peer> = emptyList(),
    val connected: Set<String> = emptySet(),
    val connecting: Set<String> = emptySet(),
    val verifications: List<Verification> = emptyList(),
    val error: String? = null,
    val receiptCount: Int = 0,
    val showPlayServicesSettings: Boolean = false,
)

class NearbyTransport(
    private val context: Context, private val scope: CoroutineScope,
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context),
    private val available: () -> Boolean = { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS },
) : NearbyPort {
    private val mutable = MutableStateFlow(NearbyState())
    override val state = mutable.asStateFlow()
    private var generation = 0
    private var endpointName = "PlanD"
    private var visible = true
    private var searchEpoch = 0
    private var searchJob: Job? = null
    private val accepted = mutableSetOf<String>()
    private val connectionOwners = mutableMapOf<String, Long>()
    private var nextListener = 0L
    private var nativeOwner = 0L
    override var onConnected: suspend (String) -> Unit = {}
    override var onDisconnected: (String) -> Unit = {}
    override var onBytes: (String, ByteArray) -> Unit = { _, _ -> }

    override fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    override fun receipt() { mutable.update { it.copy(receiptCount = it.receiptCount + 1) } }

    override fun start(name: String) {
        if (mutable.value.active) return
        if (!available()) {
            error("Google Play開発者サービスを更新・有効化してください"); return
        }
        endpointName = name.take(60)
        nativeOwner = ++latestOwner
        ++generation
        mutable.value = NearbyState(active = true)
        resumeSearch()
    }

    private fun validSearch(token: Int, epoch: Int) = token == generation && epoch == searchEpoch && visible && mutable.value.active

    private fun resumeSearch() {
        if (!mutable.value.active || !visible || mutable.value.discovering) return
        val token = generation
        val epoch = ++searchEpoch
        val owner = nativeOwner
        val previous = searchJob
        mutable.update { it.copy(discovering = true) }
        searchJob = scope.launch {
            previous?.join()
            if (!validSearch(token, epoch)) return@launch
            try {
                client.startAdvertising(endpointName, SERVICE_ID, lifecycle(token), AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                    .addOnSuccessListener { if (owner == latestOwner && !validSearch(token, epoch)) client.stopAdvertising() }.awaitResult()
                if (!validSearch(token, epoch)) return@launch
                client.startDiscovery(SERVICE_ID, discovery(token, epoch), DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                    .addOnSuccessListener { if (owner == latestOwner && !validSearch(token, epoch)) client.stopDiscovery() }.awaitResult()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (validSearch(token, epoch)) {
                    val permission = Manifest.permission.BLUETOOTH_ADVERTISE
                    val ownGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    val gmsGranted = if (Build.VERSION.SDK_INT >= 31) runCatching {
                        context.packageManager.getApplicationInfo("com.google.android.gms", 0)
                        context.packageManager.checkPermission(permission, "com.google.android.gms") == PackageManager.PERMISSION_GRANTED
                    }.getOrNull() else null
                    val failure = sharingFailure((e as? ApiException)?.statusCode, ownGranted, gmsGranted, e.localizedMessage.orEmpty())
                    stopWithReason(failure.message, failure.showPlayServicesSettings)
                }
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) { resumeSearch(); return }
        searchEpoch++
        client.stopAdvertising(); client.stopDiscovery()
        val pending = mutable.value.connecting + mutable.value.verifications.map { it.id }
        pending.filterNot { it in mutable.value.connected }.forEach {
            accepted.remove(it); connectionOwners.remove(it); client.rejectConnection(it); client.disconnectFromEndpoint(it)
        }
        mutable.update { s -> s.copy(discovering = false, connecting = emptySet(), verifications = emptyList(), peers = s.peers.filter { it.id in s.connected }) }
    }

    override fun stop() = stopWithReason(null)
    private fun stopWithReason(reason: String?, showPlayServicesSettings: Boolean = false) {
        generation++
        searchEpoch++; accepted.clear(); connectionOwners.clear()
        runCatching { client.stopAdvertising() }; runCatching { client.stopDiscovery() }; runCatching { client.stopAllEndpoints() }
        mutable.value.connected.forEach(onDisconnected)
        mutable.value = NearbyState(error = reason, showPlayServicesSettings = showPlayServicesSettings)
    }

    override fun connect(peer: Peer) {
        if (!visible || !mutable.value.active || peer.id in mutable.value.connecting || peer.id in mutable.value.connected) return
        val token = generation
        mutable.update { it.copy(connecting = it.connecting + peer.id) }
        val callback = lifecycle(token, peer.id)
        val owner = connectionOwners[peer.id]
        client.requestConnection(endpointName, peer.id, callback).addOnFailureListener {
            if (generation == token && connectionOwners[peer.id] == owner) {
                connectionOwners.remove(peer.id)
                mutable.update { s -> s.copy(connecting = s.connecting - peer.id) }
                error("接続できませんでした。相手の共有状態を確認して再試行してください")
            }
        }
    }

    override fun verify(id: String, accept: Boolean) {
        if (!visible || mutable.value.verifications.none { it.id == id }) return
        val token = generation
        val owner = connectionOwners[id] ?: return
        mutable.update { it.copy(verifications = it.verifications.filterNot { v -> v.id == id }) }
        if (accept) {
            accepted.add(id)
            client.acceptConnection(id, payload(token, owner)).addOnFailureListener {
            if (token == generation && connectionOwners[id] == owner) {
                connectionOwners.remove(id)
                accepted.remove(id)
                client.disconnectFromEndpoint(id)
                mutable.update { s -> s.copy(connecting = s.connecting - id) }
                error("接続の確認に失敗しました。再接続してください")
            }
            }
        } else {
            accepted.remove(id)
            connectionOwners.remove(id)
            client.rejectConnection(id)
            mutable.update { it.copy(connecting = it.connecting - id) }
        }
    }

    override suspend fun send(peer: String, bytes: ByteArray) {
        require(bytes.size <= 24_000)
        check(peer in mutable.value.connected) { "接続が切れました" }
        client.sendPayload(peer, Payload.fromBytes(bytes)).awaitResult()
    }

    private fun discovery(token: Int, epoch: Int) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (!validSearch(token, epoch)) return
            mutable.update { it.copy(peers = it.peers.filterNot { p -> p.id == id } + Peer(id, info.endpointName.take(60))) }
        }
        override fun onEndpointLost(id: String) {
            if (validSearch(token, epoch)) mutable.update { it.copy(peers = it.peers.filterNot { p -> p.id == id && id !in it.connected }) }
        }
    }

    private fun lifecycle(token: Int, requestedId: String? = null): ConnectionLifecycleCallback {
      val epoch = searchEpoch
      val owner = ++nextListener
      if (requestedId != null) connectionOwners[requestedId] = owner
      return object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (token != generation) return
            if (!validSearch(token, epoch)) {
                if (connectionOwners[id] == null) client.rejectConnection(id)
                return
            }
            if (id in mutable.value.connected || (connectionOwners[id] != null && connectionOwners[id] != owner)) return
            connectionOwners[id] = owner
            mutable.update { s -> s.copy(
                peers = s.peers.filterNot { it.id == id } + Peer(id, info.endpointName.take(60)),
                connecting = s.connecting + id,
                verifications = s.verifications.filterNot { it.id == id } + Verification(id, info.endpointName.take(60), info.authenticationDigits),
            ) }
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (token != generation || connectionOwners[id] != owner) return
            if (result.status.isSuccess && (!visible || id !in accepted)) {
                client.disconnectFromEndpoint(id)
                connectionOwners.remove(id); accepted.remove(id)
                mutable.update { it.copy(connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
                return
            }
            mutable.update { it.copy(connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
            if (result.status.isSuccess) {
                mutable.update { it.copy(connected = it.connected + id) }
                scope.launch { if (token == generation && connectionOwners[id] == owner) onConnected(id) }
            } else {
                connectionOwners.remove(id); accepted.remove(id)
                error("接続が成立しませんでした。双方でコードを確認して再接続してください")
            }
        }
        override fun onDisconnected(id: String) {
            if (token != generation || connectionOwners[id] != owner) return
            connectionOwners.remove(id)
            accepted.remove(id)
            mutable.update { it.copy(connected = it.connected - id, connecting = it.connecting - id, verifications = it.verifications.filterNot { v -> v.id == id }) }
            this@NearbyTransport.onDisconnected(id)
        }
    }
    }

    private fun payload(token: Int, owner: Long) = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (generation == token && connectionOwners[id] == owner && id in mutable.value.connected && payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { if (it.size <= 24_000) onBytes(id, it) }
            }
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            if (generation == token && connectionOwners[id] == owner && update.status == PayloadTransferUpdate.Status.FAILURE) error("転送が中断しました。共有を再接続すると再送されます")
        }
    }

    companion object {
        // Main-thread ownership prevents a retired client's late task completion from stopping a new session.
        private var latestOwner = 0L
        const val SERVICE_ID = "io.github.nevaseme.plandmk1.sync.v1"
        fun permissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT <= 30) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }.toTypedArray()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

