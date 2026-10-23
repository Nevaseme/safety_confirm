package io.github.nevaseme.plandmk1.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun PositionSettings(model: PlanViewModel, registered: Boolean) {
    val context = LocalContext.current
    val state by model.position.state.collectAsStateWithLifecycle()
    val positions by model.positions.collectAsStateWithLifecycle()
    val last = positions.find { it.originId == model.localId }
    fun allowed() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun start() {
        if (!allowed()) model.position.error("アプリ設定で位置情報を許可してください")
        else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) model.position.error("アプリ設定で通知を許可してください")
        else model.position.start()
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (state.active) "位置記録中" else "位置記録は停止中", style = MaterialTheme.typography.headlineSmall)
        Text(state.message)
        if (last != null) {
            Text("最後に保存した位置", style = MaterialTheme.typography.titleMedium)
            Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(last.acquiredAt))}取得\n精度 約${last.accuracyMeters.toInt()}m\n緯度 ${last.latitude}・経度 ${last.longitude}")
        } else Text("位置はまだ取得していません")
        if (!registered) Text("先にメニューの「自分の情報」を登録してください")
        if (state.active) {
            Button(onClick = { model.position.refresh() }, modifier = Modifier.fillMaxWidth()) { Text("今の位置を取得") }
            OutlinedButton(onClick = { model.position.stop() }, modifier = Modifier.fillMaxWidth()) { Text("位置記録を停止") }
        } else Button(enabled = registered, onClick = {
            val needed = mutableListOf<String>()
            if (!allowed()) needed.addAll(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
            if (needed.isEmpty()) start() else permissions.launch(needed.toTypedArray())
        }, modifier = Modifier.fillMaxWidth()) { Text("位置記録を開始") }
        Text("平常時は5分ごと、災害中は1分ごとに位置を取得します。", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("端末の位置情報設定を開く") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("アプリの権限設定を開く") }
    }
}
