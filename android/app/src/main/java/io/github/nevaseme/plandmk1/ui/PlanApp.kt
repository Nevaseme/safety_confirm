package io.github.nevaseme.plandmk1.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nevaseme.plandmk1.*
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.nearby.NearbyTransport
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanApp(model: PlanViewModel) {
    val residents by model.residents.collectAsStateWithLifecycle()
    val careEvents by model.careEvents.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    val incident by model.incidentUi.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val nearby by model.sharing.state.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val own = residents.find { it.originId == model.localId }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var lastMain by rememberSaveable { mutableIntStateOf(1) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    val firstAnswer = mainDestination(incident.canEdit, incident.current?.answers?.containsKey(model.localId) == true) == MainDestination.ANSWER
    var awaitingSavedAnswer by rememberSaveable(group, incident.selection) { mutableStateOf(firstAnswer) }
    val saveStates by model.saveStates.collectAsStateWithLifecycle()
    var previousSafetyPhase by remember { mutableStateOf(saveStates["safety"]?.phase) }
    LaunchedEffect(firstAnswer, saveStates["safety"]?.phase) {
        if (firstAnswer) awaitingSavedAnswer = true
        else if (awaitingSavedAnswer) { tab = 1; lastMain = 1; awaitingSavedAnswer = false }
        else if (previousSafetyPhase == SavePhase.SAVING && saveStates["safety"]?.phase == SavePhase.SAVED && tab == 0) { tab = 1; lastMain = 1 }
        previousSafetyPhase = saveStates["safety"]?.phase
    }
    if (preview && BuildConfig.DEBUG) { DesignDemoScreen { preview = false }; return }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable(group,incident.selection) { mutableStateOf<String?>(null) }
    var buildingId by rememberSaveable(group,incident.selection) {mutableStateOf<String?>(null)}
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val openSafety by model.openSafety.collectAsStateWithLifecycle()
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    LaunchedEffect(openSafety) { if (openSafety) { drawer.close(); tab = 0; lastMain = 0; picking = false; detailId = null; buildingId = null; model.openSafety.value = false } }

    fun startWithRadios() {
        val ready = try {
            val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
            val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
            bluetooth && wifi
        } catch (_: SecurityException) { false }
        if (ready) model.startSharing()
        else model.sharing.error("BluetoothとWi-FiをONにして、もう一度「共有開始」を押してください。インターネットへの接続は不要です")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsEnabled) startWithRadios()
        else model.sharing.error("通知を許可して、もう一度「共有開始」を押してください")
    }
    fun requestNotificationAndStart() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (notificationsEnabled) {
            startWithRadios()
            return
        }
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!model.notificationPrompted || activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, android.Manifest.permission.POST_NOTIFICATIONS)
            } == true)) {
            model.notificationPrompted = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val settings = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            }
            context.startActivity(settings)
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) requestNotificationAndStart()
        else model.sharing.error("近くの端末と共有する権限が必要です。端末のアプリ設定で許可してから再試行してください")
    }
    fun start() {
        val required = NearbyTransport.permissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toMutableList()
        // Android 12 requires coarse and fine location in the same precision request.
        if (android.os.Build.VERSION.SDK_INT in 31..32 && android.Manifest.permission.ACCESS_FINE_LOCATION in required && android.Manifest.permission.ACCESS_COARSE_LOCATION !in required) {
            required.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (required.isEmpty()) requestNotificationAndStart() else permissions.launch(required.toTypedArray())
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); if (model.message.value == it) model.message.value = null }
    }
    fun navigate(destination: Int) {
        if (tab in 0..1 && !picking) lastMain = tab
        tab = destination; picking = false
        scope.launch { drawer.close() }
    }
    BackHandler(enabled = picking || tab !in 0..1) {
        if (picking) { picking = false; tab = 3 } else tab = lastMain
    }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen, drawerContent = {
        ModalDrawerSheet(drawerState = drawer) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("メニュー", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = { scope.launch { drawer.close() } }) { PlanIcon("close", "メニューを閉じる") }
                }
                NamedEntity("自分の名前", own?.displayName() ?: "未登録")
                Text("グループ：$group", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                listOf(3 to "自分の情報", 2 to "自分の建物・住戸", 4 to "共有・接続", 5 to "オフライン地図", 6 to "災害・訓練の管理", 7 to "アプリ情報").forEach { (destination, label) ->
                    NavigationDrawerItem(label = { Text(label) }, selected = tab == destination, onClick = { navigate(destination) })
                }
                if (incident.canEdit && !firstAnswer) NavigationDrawerItem(label = { Text("住民一覧") }, selected = false, onClick = { tab = 1; showPeople = true; scope.launch { drawer.close() } })
                if (BuildConfig.DEBUG) NavigationDrawerItem(label = { Text("画面デモ") }, selected = false, onClick = { scope.launch { drawer.close(); preview = true } })
            }
        }
    }) {
        Scaffold(
            topBar = { TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when { picking -> "住居の位置を選択"; tab in 0..1 -> if (incident.canEdit) if (incident.current?.start?.training == true) "訓練" else "地震発生" else "PlanD"; else -> when(tab) {2->"自分の建物・住戸";3->"自分の情報";4->"共有・接続";5->"オフライン地図";6->"災害・訓練の管理";else->"アプリ情報"}}, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    if (tab in 0..1 && !picking && incident.canEdit) Text(java.text.SimpleDateFormat("M月d日", java.util.Locale.JAPAN).format(Date(incident.current!!.start.createdAt)), style = MaterialTheme.typography.labelLarge)
                } },
                navigationIcon = {
                    if (picking || tab !in 0..1) IconButton(onClick = { if(picking){picking=false;tab=3}else tab=lastMain }) { PlanIcon("back", "戻る") }
                    else IconButton(onClick = { scope.launch { drawer.open() } }) { PlanIcon("menu", "メニューを開く") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) },
            bottomBar = { if(tab in 0..1 && !picking && incident.canEdit && !firstAnswer) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                NavigationBarItem(selected=tab==1,onClick={tab=1;lastMain=1},colors=planNavigationColors(),icon={PlanIcon("map",null)},label={Text("地図")})
                NavigationBarItem(selected=tab==0,onClick={tab=0;lastMain=0},colors=planNavigationColors(),icon={PlanIcon("person",null)},label={Text("安否")})
            } },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                screenState.SaveableStateProvider(tab) {
                    when {
                        tab in 0..1 && !picking && firstAnswer -> SafetyScreen(model, own, saving, onProfile={navigate(3)}, onIncident={navigate(6)})
                        tab in 0..1 && !picking && !incident.canEdit -> PeaceHome(model, own != null, onProfile={navigate(3)}, onIncident={navigate(6)}, onPreview={preview=true})
                        else -> when(tab) {
                        0 -> SafetyScreen(model, own, saving, onProfile={navigate(3)}, onIncident={navigate(6)})
                        1 -> MapScreen(model, residents, own, picking,
                            onPick={model.pickedPoint.value=it;picking=false;tab=3},
                            onCancelPick={picking=false;tab=3},onSelect={detailId=it.originId},onBuilding={buildingId=it},openList=showPeople,onListClosed={showPeople=false})
                        2 -> BuildingsScreen(model, onProfile={navigate(3)}) {buildingId=it}
                        3 -> SettingsScreen(model, own, group, saving, onBuildings={navigate(2)}, onPick={picking=true;tab=1})
                        4 -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        item { SectionTitle("近くの端末と共有") }
                        item { StatusFeedback(when {
                            nearby.starting -> "共有を開始しています…"
                            nearby.verifications.isNotEmpty() -> "接続コードを確認してください"
                            nearby.connected.isNotEmpty() -> "接続済み・自動同期中（" + nearby.connected.size + " 台）"
                            nearby.active -> "共有中・接続相手を探しています"
                            else -> "共有停止中"
                        }, busy = nearby.starting) }
                        item {
                            if (nearby.active || nearby.starting) OutlinedButton(onClick = model::stopSharing, modifier = Modifier.fillMaxWidth()) { Text("共有停止") }
                            else Button(onClick = ::start, enabled = own != null && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("共有開始") }
                        }
                        nearby.error?.let { error -> item {
                            InfoCard(error, true)
                            if (nearby.showPlayServicesSettings) TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:com.google.android.gms")))
                            }) { Text("Google Playの権限設定を開く") }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) { Text("通信設定を開く") }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("アプリの権限設定を開く") }
                        } }
                        if (nearby.active && nearby.peers.isEmpty()) item { Text("近くの端末を探しています…") }
                        items(nearby.peers, key = { it.id }) { peer ->
                            OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                NamedEntity("接続相手の名前", samplePersonName(group,peer.name))
                                when (peer.id) {
                                    in nearby.connected -> Text("接続済み・自動同期中")
                                    in nearby.connecting -> Text("接続確認中…")
                                    else -> TextButton(onClick = { model.sharing.connect(peer) }) { Text("接続する") }
                                }
                            } }
                        }
                        item { Text("相手が受け取った情報：${nearby.receiptCount}件", style = MaterialTheme.typography.bodySmall) }
                            if(BuildConfig.DEBUG) item { HorizontalDivider(); LabPanel(model) }
                        }
                        5 -> OfflineMapScreen(model,own)
                        6 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            IncidentSelector(model)
                            IncidentActions(model,own!=null)
                            if(own==null) Button(onClick={navigate(3)}){Text("自分の情報を登録")}
                        }
                        else -> AppInformation()
                    }
                    }
                }
            }
        }
    }
    nearby.verifications.firstOrNull()?.let { verification ->
        AlertDialog(onDismissRequest = { model.sharing.verify(verification.id, false) },
            title = { Text("接続コードの確認") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("相手：${samplePersonName(group,verification.name)}")
                Text(verification.code, style = MaterialTheme.typography.headlineLarge)
                Text("相手の端末とコードを照合")
            } },
            confirmButton = { TextButton(onClick = { model.sharing.verify(verification.id, true) }) { Text("一致している・接続") } },
            dismissButton = { TextButton(onClick = { model.sharing.verify(verification.id, false) }) { Text("接続しない") } })
    }
    buildingId?.let {id->key(group,incident.selection,id){BuildingDetail(model,id,{buildingId=null;detailId=it.originId},{buildingId=null}, registration = tab == 2)}}
    residents.find { it.originId == detailId }?.let { resident ->
        val person = residentMapPerson(resident, places, careEvents)
        ModalBottomSheet(onDismissRequest = { detailId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            PersonMapDetail(person, System.currentTimeMillis(), false, false, {}, {},
                onHome = { home -> if (placeBuildings(places).any { it.buildingId == home.id }) { detailId = null; buildingId = home.id } },
                onClose = { detailId = null }, history = { CareHistoryAndEditor(model, resident, careEvents, own != null && incident.canEdit, saving) })
        }
    }
}

@Composable
private fun SettingsScreen(model: PlanViewModel, own: Resident?, group: String, saving: Boolean, onBuildings: () -> Unit, onPick: () -> Unit) {
    var name by rememberSaveable(own?.originId) { mutableStateOf(own?.displayName().orEmpty()) }
    var groupInput by rememberSaveable(group) { mutableStateOf(group) }
    var lat by rememberSaveable(own?.originId) { mutableStateOf(own?.latitude?.toString().orEmpty()) }
    var lon by rememberSaveable(own?.originId) { mutableStateOf(own?.longitude?.toString().orEmpty()) }
    var floor by rememberSaveable(own?.originId) { mutableStateOf(own?.floor.orEmpty()) }
    var room by rememberSaveable(own?.originId) { mutableStateOf(own?.room.orEmpty()) }
    val point by model.pickedPoint.collectAsStateWithLifecycle()
    LaunchedEffect(point) { point?.let { lat = "%.6f".format(java.util.Locale.US, it.latitude); lon = "%.6f".format(java.util.Locale.US, it.longitude); model.pickedPoint.value = null } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("自分の情報") }
        item { OutlinedTextField(groupInput, { groupInput = it }, label = { Text("実験グループ") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(name, { name = it }, label = { Text("自分の名前") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { SectionTitle("登録住居") }
        item { OutlinedButton(enabled = !saving, onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("地図から位置を選ぶ") } }
        item { OutlinedTextField(lat, { lat = it }, label = { Text("緯度") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(lon, { lon = it }, label = { Text("経度") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(floor, { floor = it }, label = { Text("階数（例：2階）") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(room, { room = it }, label = { Text("寝る部屋・場所の説明") }, minLines = 2, enabled = !saving, modifier = Modifier.fillMaxWidth()) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationFeedback(model, "profile")
            Button(onClick = { model.saveProfile(groupInput, name, lat, lon, floor, room) }, enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (saving) "保存しています…" else "登録情報を保存") }
        } }
        item { OutlinedButton(onClick=onBuildings,modifier=Modifier.fillMaxWidth()) { Text("建物・住戸を登録する") } }
    }
}

@Composable
private fun OfflineMapScreen(model:PlanViewModel,own:Resident?) {
    val offline by model.maps.state.collectAsStateWithLifecycle()
    var deleteConfirm by remember { mutableStateOf(false) }
    val center=own?.let{MapPoint(it.latitude,it.longitude)}?:MapPoint(offline.latitude?:35.681236,offline.longitude?:139.767125)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("オフライン地図", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    offline.busy -> "保存状態を確認中…"
                    offline.complete -> "保存済み・圏外で利用可能"
                    offline.downloading -> "保存中：${offline.resources} / ${offline.expected} 項目"
                    offline.exists -> "保存は未完了です"
                    else -> "未保存・地図の表示には通信が必要です"
                })
                offline.latitude?.let { Text("保存範囲の中心：%.4f, %.4f".format(it, offline.longitude)) }
                if (offline.bytes > 0) Text("保存容量：%.1f MB".format(offline.bytes / 1048576.0))
                offline.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (offline.downloading) OutlinedButton(onClick = model.maps::pause) { Text("保存を一時停止") }
                else if (!offline.busy && !offline.complete) Button(onClick = { if (offline.exists) model.maps.resume() else model.maps.download(center.latitude, center.longitude) }) { Text(if (offline.exists) "保存を再開" else "この中心の周囲2km四方を保存") }
                if (offline.exists && !offline.busy) TextButton(onClick = { deleteConfirm = true }) { Text("保存地図を削除・地域を変更") }
            } }
        }

    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("保存地図を削除しますか？") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; model.maps.delete() }) { Text("地図を削除") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("キャンセル") } })
}

fun formatTime(time: Long): String = if(time == 0L) "今回未回答" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
