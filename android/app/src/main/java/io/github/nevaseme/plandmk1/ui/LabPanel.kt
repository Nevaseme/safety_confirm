package io.github.nevaseme.plandmk1.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.PlanViewModel
import io.github.nevaseme.plandmk1.core.ConnectionPhase
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun LabPanel(model: PlanViewModel) {
    val state by model.lab.state.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("wss://192.168.128.186:8443/socket") }
    var token by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) model.startLab(url, token) else model.message.value = "実験通信にはローカルネットワークの権限が必要です"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("iPhone・PCとの通信実験")
        OutlinedTextField(url, { url = it }, label = { Text("実験サーバーのWSSアドレス") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, enabled = !state.active && !saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("実験用接続コード") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), singleLine = true, enabled = !state.active && !saving, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        StatusFeedback(state.connection.text, busy = state.connection.phase == ConnectionPhase.CONNECTING)
        state.phase?.let { Text("現在の経路：${if (it == "AB") "A ↔ B" else "B ↔ C"}") }
        Text("相手が受け取った情報：${state.receipts}件")
        state.error?.let { StatusFeedback(it, true) }
        if (state.active) OutlinedButton(onClick = { model.lab.stop() }, modifier = Modifier.fillMaxWidth()) { Text("実験通信を停止") }
        else Button(enabled = !saving, onClick = {
            val name = "android.permission.ACCESS_LOCAL_NETWORK"
            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, name) != PackageManager.PERMISSION_GRANTED) permission.launch(name)
            else model.startLab(url, token)
        }, modifier = Modifier.fillMaxWidth()) { Text("実験通信を開始") }
    }
}
