package io.github.nevaseme.plandmk1.sharing

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nevaseme.plandmk1.MainActivity
import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.nearby.NearbyState
import io.github.nevaseme.plandmk1.nearby.NearbyTransport
import kotlinx.coroutines.*

class SharingService : Service() {
    private val app get() = application as PlanDApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: SharingSession? = null
    private var ownerToken = -1L
    private val radios = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (session != null && !ready()) app.sharing.stop("通信権限またはBluetooth・Wi-FiがOFFになったため共有を停止しました")
        }
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "安否情報の共有", NotificationManager.IMPORTANCE_LOW))
        ContextCompat.registerReceiver(this, radios, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_EXPORTED)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { app.sharing.stop(); stopSelfResult(startId); return START_NOT_STICKY }
        val token = intent?.getLongExtra("token", -1) ?: -1
        if (intent?.action != START) {
            stopSelfResult(startId); return START_NOT_STICKY
        }
        if (ownerToken == token && app.sharing.isCurrent(token)) return START_NOT_STICKY
        try {
            val notice = notification(NearbyState(active = true, starting = true))
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIFICATION_ID, notice)
        } catch (e: Exception) {
            if (app.sharing.isCurrent(token)) app.sharing.stop("共有を開始できません。権限を確認して、アプリを開いた状態で再試行してください")
            stopSelfResult(startId); return START_NOT_STICKY
        }
        if (!app.sharing.isCurrent(token) || !app.sharing.foreground) {
            stopSelfResult(startId); return START_NOT_STICKY
        }
        ownerToken = token
        if (!ready()) { app.sharing.stop("Bluetooth・Wi-Fiと通信権限を確認して再試行してください"); stopSelfResult(startId); return START_NOT_STICKY }
        val group = intent.getStringExtra("group") ?: ""
        if (group != app.installation.group.value) { app.sharing.stop(); stopSelfResult(startId); return START_NOT_STICKY }
        scope.launch {
          try {
            val own = app.store.get(group, app.installation.localId)
            if (!app.sharing.isCurrent(token)) { stopSelfResult(startId); return@launch }
            if (own == null) { app.sharing.stop("先に自分の情報を登録してください"); stopSelfResult(startId); return@launch }
            val value = SharingSession(group, app.installation.localId, app.sharingPortFactory(scope), app.store, scope,
                publish = { state ->
                    app.sharing.publish(token, state)
                    if (state.active && app.sharing.isCurrent(token)) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                    }
                }, finished = {
                    app.sharing.detach(token)
                    if (ownerToken == token) {
                        session = null
                        ServiceCompat.stopForeground(this@SharingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelfResult(startId)
                    }
                })
            if (!app.sharing.attach(token, value)) { stopSelfResult(startId); return@launch }
            session = value
            value.start(own.name)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            if (app.sharing.isCurrent(token)) app.sharing.stop("共有を開始できませんでした。アプリから再試行してください")
            stopSelfResult(startId)
          }
        }
        return START_NOT_STICKY
    }
    private fun ready(): Boolean = try {
        NearbyTransport.permissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED } &&
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true &&
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (_: SecurityException) { false }

    private fun notification(state: NearbyState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(OPEN_SAFETY, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SharingService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("PlanD Mk1・共有中")
            .setContentText(if (state.starting) "共有を開始しています…" else "接続 ${state.connected.size} 台・接続済みの相手とは画面を消しても共有します")
            .setContentIntent(open).addAction(0, "共有停止", stop).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    override fun onDestroy() {
        session?.stop(); session = null
        app.sharing.detach(ownerToken)
        scope.cancel()
        unregisterReceiver(radios)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val START = "io.github.nevaseme.plandmk1.sharing.START"
        const val STOP = "io.github.nevaseme.plandmk1.sharing.STOP"
        const val OPEN_SAFETY = "open_safety"
        const val CHANNEL = "safety_sharing"
        const val NOTIFICATION_ID = 1001
    }
}
