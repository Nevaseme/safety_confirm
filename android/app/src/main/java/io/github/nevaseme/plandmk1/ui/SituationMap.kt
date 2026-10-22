package io.github.nevaseme.plandmk1.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.R
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import kotlin.math.*

class SituationCamera {
    internal var map: MapLibreMap? = null
    fun zoom(delta: Double) { map?.let { it.animateCamera(CameraUpdateFactory.zoomTo((it.cameraPosition.zoom + delta).coerceIn(2.0, 20.0))) } }
    fun move(point: MapPoint, zoom: Double = 16.8) { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom)) }
}

@Suppress("DEPRECATION")
@Composable
fun SituationMap(
    people: List<MapPerson>, homes: List<MapHome>, center: MapPoint, showSafe: Boolean,
    now: Long, preview: Boolean, camera: SituationCamera,
    onPerson: (MapPerson) -> Unit, onHome: (MapHome) -> Unit,
    onOverlapping: (List<MapPerson>) -> Unit, onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val supports = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion >= 0x30000 }
    if (!supports) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("地図を表示できません。メニューの住民一覧を利用できます。", Modifier.padding(24.dp)) }
        return
    }
    val owner = LocalLifecycleOwner.current
    val view = remember { MapView(context).also { it.onCreate(null); it.setMaximumFps(60) } }
    var controller by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableDoubleStateOf(16.8) }
    var latitude by rememberSaveable { mutableDoubleStateOf(center.latitude) }
    var longitude by rememberSaveable { mutableDoubleStateOf(center.longitude) }
    val latestPeople by rememberUpdatedState(people)
    val latestHomes by rememberUpdatedState(homes)
    val latestSelect by rememberUpdatedState(onPerson)
    val latestHome by rememberUpdatedState(onHome)
    val latestOverlap by rememberUpdatedState(onOverlapping)
    val latestError by rememberUpdatedState(onError)
    val latestSafe by rememberUpdatedState(showSafe)
    val latestNow by rememberUpdatedState(now)
    val markers = remember { mutableMapOf<Long, MapCluster>() }
    val renderedMarkers = remember { mutableListOf<Marker>() }
    val iconCache = remember { mutableMapOf<String, Icon>() }

    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        view.addOnDidFailLoadingMapListener { latestError() }
        view.getMapAsync { map ->
            controller = map; camera.map = map
            map.cameraPosition = CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).build()
            map.uiSettings.apply { isRotateGesturesEnabled = false; isTiltGesturesEnabled = false; isCompassEnabled = false }
            map.addOnCameraIdleListener {
                zoom = map.cameraPosition.zoom
                map.cameraPosition.target?.let { latitude = it.latitude; longitude = it.longitude }
            }
            map.setOnMarkerClickListener { marker ->
                markers[marker.id]?.let { cluster ->
                    if (cluster.people.size == 1) {
                        val person = latestPeople.find { it.id == cluster.people.single().id } ?: cluster.people.single()
                        val samePoint = latestPeople.filter { it.point == person.point && it.hasPosition(latestNow) && (latestSafe || it.state !in listOf(MapSafety.SAFE, MapSafety.RESCUED)) }
                        if (samePoint.size > 1) latestOverlap(samePoint) else latestSelect(person)
                    }
                    else if (cluster.people.map { it.point }.distinct().size == 1) latestOverlap(cluster.people)
                    else {
                        val bounds = LatLngBounds.Builder().includes(cluster.people.map { LatLng(it.point!!.latitude, it.point.longitude) }).build()
                        val density = context.resources.displayMetrics.density
                        val fitted = map.getCameraForLatLngBounds(bounds, intArrayOf((52 * density).toInt(), (72 * density).toInt(), (52 * density).toInt(), (88 * density).toInt()))
                        if (fitted != null) {
                            val maximum = minOf(16.6, map.cameraPosition.zoom + 1.8)
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(fitted).zoom(minOf(fitted.zoom, maximum)).build()))
                        }
                    }
                }
                true
            }
            map.addOnMapClickListener { point ->
                val features = map.queryRenderedFeatures(map.projection.toScreenLocation(point), "building")
                if (features.isEmpty()) false else {
                    val polygons = features.flatMap { feature -> when (val geometry = feature.geometry()) {
                        is org.maplibre.geojson.Polygon -> listOf(geometry.coordinates())
                        is org.maplibre.geojson.MultiPolygon -> geometry.coordinates()
                        else -> emptyList()
                    } }.map { rings -> rings.map { ring -> ring.map { MapPoint(it.latitude(), it.longitude()) } } }
                    val registered = latestHomes.firstOrNull { home -> polygons.any { insideFootprint(home.point, it) } }
                    if (registered != null) latestHome(registered)
                    else latestHome(MapHome("selected", "選択した建物", MapPoint(point.latitude, point.longitude)))
                    true
                }
            }
            map.setStyle(OfflineMapRepository.STYLE) { style ->
                if (preview) {
                    val buildings = style.getLayer("building") as? FillLayer
                    // Preview classes only. Real strength assessments will supply their own attributes.
                    val rating = raw("""["%",["floor",["/",["to-number",["coalesce",["id"],30],30],10]],4]""")
                    val color = match(rating, literal("#D4D7DC"),
                        stop(0, "#FBE8C5"), stop(1, "#F2AE5D"), stop(2, "#C5672E"))
                    val layer = FillLayer("design-ratings", buildings?.sourceId ?: "openmaptiles")
                        .withSourceLayer(buildings?.sourceLayer ?: "building")
                        .withProperties(fillColor(color), fillOpacity(.58f), fillOutlineColor("#B5A696"))
                    layer.minZoom = buildings?.minZoom ?: 12f
                    buildings?.filter?.let { layer.setFilter(it) }
                    val before = style.layers.firstOrNull { it is org.maplibre.android.style.layers.SymbolLayer }?.id
                    if (before != null) style.addLayerBelow(layer, before) else style.addLayer(layer)
                    val hatch = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                    for (y in 0..7) for (x in 0..7) hatch.setPixel(x, y, if ((x + y) % 8 < 2) 0xFF949AA4.toInt() else 0xFFDADDE1.toInt())
                    style.addImage("design-hatch", hatch)
                    val unknown = FillLayer("design-rating-unknown", buildings?.sourceId ?: "openmaptiles").withSourceLayer(buildings?.sourceLayer ?: "building").withProperties(fillPattern("design-hatch"), fillOpacity(.6f)).withFilter(eq(rating, literal(3)))
                    unknown.minZoom = layer.minZoom
                    buildings?.filter?.let { unknown.setFilter(all(it, eq(rating, literal(3)))) }
                    if (before != null) style.addLayerBelow(unknown, before) else style.addLayer(unknown)

                }
                ready = true
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            camera.map = null; controller = null
            view.onPause(); view.onStop(); view.onDestroy()
            renderedMarkers.clear(); markers.clear(); iconCache.clear()
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
    LaunchedEffect(ready, people, showSafe, zoom, now) {
        val map = controller ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val clusters = mapClusters(people, zoom, showSafe, now)
        val previous = renderedMarkers.toList()
        val reuse = reuseClusterMarkers(previous.map { markers.getValue(it.id) }, clusters)
        val next = mutableListOf<Marker>()
        val usedIcons = mutableSetOf<String>()
        clusters.forEachIndexed { index, cluster ->
            val bubble = zoom < INDIVIDUAL_ZOOM
            val key = if (bubble) cluster.counts.toString() else personMarkerKey(cluster.people.single(), now)
            usedIcons += key
            val icon = iconCache.getOrPut(key) { IconFactory.getInstance(context).fromBitmap(mapMarkerBitmap(context, cluster, bubble, now)) }
            val p = cluster.point
            val position = LatLng(p.latitude, p.longitude)
            val label = if (bubble) cluster.counts.joinToString("、") { "${it.first.label}${it.second}人" } else "${cluster.people.single().publicName}、${cluster.people.single().state.label}"
            val marker = reuse[index]?.let(previous::get)?.apply {
                if (this.icon != icon) this.icon = icon
                if (this.position != position) this.position = position
                if (title != label) title = label
            } ?: map.addMarker(MarkerOptions().position(position).title(label).icon(icon))
            next += marker
            markers[marker.id] = cluster
        }
        val retained = next.map { it.id }.toSet()
        previous.filter { it.id !in retained }.forEach { map.removeMarker(it); markers.remove(it.id) }
        renderedMarkers.clear(); renderedMarkers.addAll(next)
        if (iconCache.size > 128) iconCache.keys.retainAll(usedIcons)
    }
}

private fun mapMarkerBitmap(context: Context, cluster: MapCluster, bubble: Boolean, now: Long): Bitmap {
    val d = context.resources.displayMetrics.density
    val width = if (bubble) CLUSTER_WIDTH else 60
    val age = cluster.people.singleOrNull()?.let { markerAgeLabel(it, now) }.orEmpty()
    val height = if (bubble) clusterHeight(cluster) else if (age.isNotEmpty()) 80 else 62
    val bitmap = Bitmap.createBitmap((width * d).roundToInt(), (height * d).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); canvas.scale(d, d)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bubble) {
        paint.color = Color.WHITE; canvas.drawRoundRect(1f, 1f, 81f, height - 1f, 17f, 17f, paint)
        cluster.counts.forEachIndexed { i, (state, count) ->
            drawState(canvas, paint, state, 21f, 22f + i * 30, 13f)
            paint.color = 0xFF142B42.toInt(); paint.textSize = 17f; paint.textAlign = Paint.Align.LEFT; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas.drawText("${count}人", 40f, 28f + i * 30, paint)
        }
    } else {
        val p = cluster.people.single()
        drawState(canvas, paint, p.state, 30f, 35f, 21f)
        fun sensor(resource: Int, left: Int) {
            paint.color = Color.WHITE; canvas.drawCircle(left + 11f, 13f, 13f, paint)
            ContextCompat.getDrawable(context, resource)?.let { drawable -> drawable.setBounds(left, 2, left + 22, 24); drawable.draw(canvas) }
        }
        if (p.impactAt != null) sensor(R.drawable.impact, 36)
        if (p.enclosedAt != null) sensor(R.drawable.enclosed, 2)
        if (age.isNotEmpty()) {
            paint.color = Color.WHITE; canvas.drawRoundRect(8f, 61f, 52f, 79f, 5f, 5f, paint)
            paint.color = 0xFF697586.toInt(); paint.textSize = 10f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(age, 30f, 74f, paint)
        }
    }
    return bitmap
}

internal fun drawState(canvas: Canvas, paint: Paint, state: MapSafety, x: Float, y: Float, radius: Float) {
    paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(x, y, radius + 3, paint)
    paint.color = state.argb; canvas.drawCircle(x, y, radius, paint)
    if (state == MapSafety.UNANSWERED) { paint.color = Color.WHITE; canvas.drawCircle(x, y, radius - 2, paint); paint.color = state.argb }
    else paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    when (state) {
        MapSafety.SOS -> { paint.textSize = radius * .76f; canvas.drawText("SOS", x, y + radius * .27f, paint) }
        MapSafety.UNANSWERED -> { paint.textSize = radius * 1.48f; canvas.drawText("?", x, y + radius * .52f, paint) }
        MapSafety.SAFE -> {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .22f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(Path().apply { moveTo(x - radius * .52f, y); lineTo(x - radius * .1f, y + radius * .38f); lineTo(x + radius * .56f, y - radius * .4f) }, paint)
            paint.style = Paint.Style.FILL
        }
        MapSafety.RESCUED -> {
            // Match the adopted demo's user-check silhouette, distinct from the safe checkmark.
            canvas.drawCircle(x - radius * .18f, y - radius * .35f, radius * .29f, paint)
            canvas.drawPath(Path().apply {
                moveTo(x - radius * .73f, y + radius * .48f)
                cubicTo(x - radius * .65f, y - radius * .17f, x + radius * .24f, y - radius * .17f, x + radius * .3f, y + radius * .48f)
                close()
            }, paint)
            // The background cutout separates the check from the person's shoulder.
            paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            val check = Path().apply {
                moveTo(x + radius * .13f, y + radius * .17f)
                lineTo(x + radius * .36f, y + radius * .39f)
                lineTo(x + radius * .77f, y - radius * .07f)
            }
            paint.strokeWidth = radius * .37f; paint.color = state.argb; canvas.drawPath(check, paint)
            paint.strokeWidth = radius * .18f; paint.color = Color.WHITE; canvas.drawPath(check, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
