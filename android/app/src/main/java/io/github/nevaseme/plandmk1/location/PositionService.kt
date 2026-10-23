package io.github.nevaseme.plandmk1.location

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.nevaseme.plandmk1.MainActivity
import io.github.nevaseme.plandmk1.PlanDApplication
import io.github.nevaseme.plandmk1.core.managedIncidentOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PositionService : Service() {
    private val app get() = application as PlanDApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: PositionRecording? = null
    private var token = -1L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { app.position.stop(); stopSelf(); return START_NOT_STICKY }
        val requested = intent?.getLongExtra("token", -1) ?: -1
        if (recording != null && app.position.current(requested) && requested == token) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL, "位置記録", NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification("位置を取得しています"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION, notification("位置を取得しています"))
        } catch (e: Exception) {
            if(app.position.current(requested)) { app.position.stop(); app.position.error("位置情報の権限と端末の位置情報設定を確認してください") }
            stopSelf(startId); return START_NOT_STICKY
        }
        if (!app.position.current(requested)) { stopSelf(startId); return START_NOT_STICKY }
        recording?.stop(); scope.coroutineContext.cancelChildren(); token = requested
        val group = app.installation.group.value
        val source = app.positionSourceFactory()
        val session = PositionRecording(scope, source, { fix ->
            check(app.position.current(requested) && app.installation.group.value == group)
            app.store.recordOwnPosition(group, app.installation.localId, fix).also {saved->
                if(saved) {app.sharing.changed();app.lab.changed()}
            }
        })
        recording = session
        if (!app.position.attach(token, session) { session.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }) {
            stopSelf(startId); return START_NOT_STICKY
        }
        session.start(false)
        scope.launch { session.state.collect {
            app.position.publish(requested, it)
            if (it.active) notifications.notify(NOTIFICATION, notification(it.message))
        } }
        scope.launch { app.installation.group.filter { it != group }.first(); app.position.stop() }
        scope.launch {
            var previous: Set<String>? = null
            combine(app.store.observeIncidents(group),app.quakes.observeRoster(group),app.quakes.observe(group)) {events,roster,quakes->managedIncidentOptions(events,roster,quakes).filter {!it.closed}.map {it.start.eventId}.toSet()}
                .distinctUntilChanged().collect { active ->
                    session.disaster(active.isNotEmpty())
                    if(previous != null && active.any { it !in previous!! }) session.refresh()
                    previous = active
                }
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 51, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 52, Intent(this, PositionService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PlanD 位置記録中").setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(0, "位置記録を停止", stop).build()
    }
    override fun onDestroy() {
        recording?.stop(); scope.cancel(); app.position.detached(token)
        super.onDestroy()
    }
    companion object { const val STOP = "io.github.nevaseme.plandmk1.STOP_POSITION"; private const val CHANNEL = "position"; private const val NOTIFICATION = 51 }
}
