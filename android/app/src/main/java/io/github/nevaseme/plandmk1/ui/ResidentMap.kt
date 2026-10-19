package io.github.nevaseme.plandmk1.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.core.Safety
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Suppress("DEPRECATION")
@Composable
fun ResidentMap(
    residents: List<Resident>, center: MapPoint, picking: Boolean,
    onPick: (MapPoint) -> Unit, onSelect: (Resident) -> Unit, onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    careLabels: Map<String, String> = emptyMap(),
    places: List<PlaceEvent> = emptyList(),incident: String="",onBuilding: (String)->Unit={},
) {
    val context = LocalContext.current
    val supportsMap = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }
    if (!supportsMap) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("この端末では地図を描画できません。住民一覧と対応記録は利用できます。住居の位置は設定の緯度・経度で登録してください。", Modifier.padding(16.dp))
        }
        return
    }
    val owner = LocalLifecycleOwner.current
    val view = remember { MapView(context).also { it.onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    val latestResidents by rememberUpdatedState(residents)
    val latestCenter by rememberUpdatedState(center)
    val latestPicking by rememberUpdatedState(picking)
    val latestPick by rememberUpdatedState(onPick)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestBuildingSelect by rememberUpdatedState(onBuilding)
    val latestError by rememberUpdatedState(onError)
    val markerIds = remember { mutableMapOf<Long, String>() }
    val buildingIds=remember {mutableMapOf<Long,String>()}

    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        view.addOnDidFailLoadingMapListener { latestError("地図を表示できません。未保存の地域では通信が必要です。一覧は引き続き利用できます") }
        view.getMapAsync { controller ->
            map = controller
            controller.cameraPosition = CameraPosition.Builder().target(LatLng(latestCenter.latitude, latestCenter.longitude)).zoom(15.0).build()
            controller.uiSettings.isRotateGesturesEnabled = false
            controller.uiSettings.isTiltGesturesEnabled = false
            controller.addOnMapClickListener { location ->
                if (latestPicking) { latestPick(MapPoint(location.latitude, location.longitude)); true } else false
            }
            controller.setOnMarkerClickListener { marker ->
                buildingIds[marker.id]?.let(latestBuildingSelect)
                latestResidents.find { it.originId == markerIds[marker.id] }?.let(latestSelect)
                true
            }
            controller.setStyle(OfflineMapRepository.STYLE) { ready = true }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            view.onPause(); view.onStop(); view.onDestroy()
            map = null
        }
    }
    LaunchedEffect(map, center) {
        map?.let { controller ->
            controller.cameraPosition = CameraPosition.Builder(controller.cameraPosition)
                .target(LatLng(center.latitude, center.longitude)).build()
        }
    }
    AndroidView(factory = { view }, modifier = modifier, update = {
        val controller = map
        if (ready && controller != null) {
            controller.clear(); markerIds.clear();buildingIds.clear()
            residents.forEach { resident ->
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = when (resident.safety) { Safety.SOS -> 0xFFB3261E.toInt(); Safety.SAFE -> 0xFF176B43.toInt(); Safety.UNREPORTED -> 0xFF52606D.toInt() }
                canvas.drawCircle(32f, 32f, 27f, paint)
                paint.color = android.graphics.Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                canvas.drawCircle(32f, 32f, 27f, paint)
                paint.style = Paint.Style.FILL; paint.textSize = 23f; paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
                canvas.drawText(when (resident.safety) { Safety.SOS -> "SOS"; Safety.SAFE -> "✓"; else -> "?" }, 32f, 40f, paint)
                if (resident.originId in careLabels) {
                    paint.color = 0xFF875000.toInt(); canvas.drawCircle(52f, 12f, 11f, paint)
                    paint.color = android.graphics.Color.WHITE; paint.textSize = 15f; canvas.drawText("記", 52f, 18f, paint)
                }
                val marker = controller.addMarker(MarkerOptions().position(LatLng(resident.latitude, resident.longitude))
                    .title("住民の名前：${resident.displayName()}").snippet("登録住居・${resident.safety.label()}・${careLabels[resident.originId].orEmpty()}").icon(IconFactory.getInstance(context).fromBitmap(bitmap)))
                markerIds[marker.id] = resident.originId
            }
            placeBuildings(places).forEach {building->
                val actions=currentBuildingCare(buildingCare(places,building.buildingId!!,incident)).mapNotNull {it.action}.distinct()
                val response=when {actions.isEmpty()->"記録なし";actions.size>1->"複数の記録";else->when(actions.single()) {BuildingAction.COMPLETED->"対応完了";BuildingAction.NEEDS_HELP->"要対応";BuildingAction.RECHECK->"再確認";BuildingAction.CHECKING->"確認中";BuildingAction.REVOKED->"記録撤回"}}
                val bitmap=Bitmap.createBitmap(150,80,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color=0xFF244F69.toInt();canvas.drawRect(5f,5f,145f,75f,paint)
                paint.color=android.graphics.Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=18f;canvas.drawText("建物",75f,29f,paint)
                paint.textSize=24f;canvas.drawText(response,75f,59f,paint)
                val marker=controller.addMarker(MarkerOptions().position(LatLng(building.latitude!!,building.longitude!!)).title("建物名：${building.displayLabel()}").snippet(buildingSummary(places,building.buildingId!!,incident)).icon(IconFactory.getInstance(context).fromBitmap(bitmap)))
                buildingIds[marker.id]=building.buildingId
            }
        }
    })
}
