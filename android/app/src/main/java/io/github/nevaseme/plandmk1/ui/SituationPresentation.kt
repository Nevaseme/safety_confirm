package io.github.nevaseme.plandmk1.ui

import io.github.nevaseme.plandmk1.MapPoint
import io.github.nevaseme.plandmk1.core.*
import kotlin.math.*

const val DAY = 86_400_000L
const val INDIVIDUAL_ZOOM = 16.0
const val CLUSTER_WIDTH = 82
fun clusterHeight(cluster: MapCluster) = cluster.counts.size * 30 + 14

enum class MapSafety(val label: String, val argb: Int) {
    SOS("SOS", 0xFFE51C46.toInt()), UNANSWERED("応答なし", 0xFFE4A319.toInt()),
    SAFE("無事", 0xFF008C7D.toInt()), RESCUED("救助済み", 0xFF008C7D.toInt())
}

data class MapHome(
    val id: String, val address: String, val point: MapPoint,
    val built: Int? = null, val material: String = "不明", val floors: Int? = null,
    val rating: Int? = null,
)

data class MapPerson(
    val id: String, val name: String, val state: MapSafety,
    val point: MapPoint? = null, val acquiredAt: Long? = null, val accuracy: Int? = null,
    val home: MapHome? = null, val bedroom: String = "未登録", val memo: String = "",
    val impactAt: Long? = null, val enclosedAt: Long? = null,
) {
    val publicName get() = when (state) {
        MapSafety.SAFE -> "無事の人"
        MapSafety.RESCUED -> "救助済みの人"
        else -> name.ifBlank { "登録住民" }
    }
    fun hasPosition(now: Long) = point != null && acquiredAt != null && now - acquiredAt <= 3 * DAY
}

enum class MainDestination { HOME, ANSWER, MAP }
fun mainDestination(active: Boolean, answered: Boolean) = when {
    !active -> MainDestination.HOME
    !answered -> MainDestination.ANSWER
    else -> MainDestination.MAP
}

data class MapCluster(val people: List<MapPerson>) {
    val point = MapPoint(people.map { it.point!!.latitude }.average(), people.map { it.point!!.longitude }.average())
    val counts = MapSafety.entries.mapNotNull { state -> people.count { it.state == state }.takeIf { it > 0 }?.let { state to it } }
}

fun markerAgeLabel(person: MapPerson, now: Long): String {
    val minutes = person.acquiredAt?.let { ((now - it) / 60_000).coerceAtLeast(0) } ?: return ""
    return when { minutes <= 5 -> ""; minutes < 60 -> "${minutes}分前"; else -> "${minutes / 60}時間前" }
}

fun personMarkerKey(person: MapPerson, now: Long) =
    "${person.state}:${person.impactAt != null}:${person.enclosedAt != null}:${markerAgeLabel(person, now)}"

/** Keep exact groups first, then reuse a surviving member's marker when groups merge or split. */
fun reuseClusterMarkers(previous: List<MapCluster>, next: List<MapCluster>): List<Int?> {
    val oldMembers = previous.map { it.people.map(MapPerson::id).toSet() }
    val newMembers = next.map { it.people.map(MapPerson::id).toSet() }
    val available = previous.indices.toMutableSet()
    val result = MutableList<Int?>(next.size) { null }
    newMembers.forEachIndexed { i, members ->
        available.firstOrNull { oldMembers[it] == members }?.let { result[i] = it; available.remove(it) }
    }
    newMembers.forEachIndexed { i, members ->
        if (result[i] == null) {
            available.maxByOrNull { oldMembers[it].intersect(members).size }
                ?.takeIf { oldMembers[it].any(members::contains) }
                ?.let { result[i] = it; available.remove(it) }
        }
    }
    return result
}

fun insideFootprint(point: MapPoint, rings: List<List<MapPoint>>): Boolean {
    fun inside(ring: List<MapPoint>): Boolean {
        if (ring.size < 3) return false
        var contained = false
        var previous = ring.last()
        for (current in ring) {
            if ((current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - current.longitude) * (point.latitude - current.latitude) / (previous.latitude - current.latitude) + current.longitude) contained = !contained
            previous = current
        }
        return contained
    }
    return rings.firstOrNull()?.let(::inside) == true && rings.drop(1).none(::inside)
}

/** Distances are evaluated in projected map pixels at the current zoom. */
fun mapClusters(people: List<MapPerson>, zoom: Double, showSafe: Boolean, now: Long): List<MapCluster> {
    val visible = people.filter { it.hasPosition(now) && (showSafe || it.state !in listOf(MapSafety.SAFE, MapSafety.RESCUED)) }
    if (zoom >= INDIVIDUAL_ZOOM) return visible.map { MapCluster(listOf(it)) }
    val scale = 512 * 2.0.pow(zoom)
    fun projected(point: MapPoint): Pair<Double, Double> {
        val latitude = point.latitude.coerceIn(-85.0, 85.0) * PI / 180
        return (point.longitude + 180) / 360 * scale to (1 - ln(tan(latitude) + 1 / cos(latitude)) / PI) / 2 * scale
    }
    val xy = visible.map { projected(it.point!!) }
    val roots = IntArray(visible.size) { it }
    fun root(i: Int): Int { var r = i; while (roots[r] != r) r = roots[r]; return r }
    for (i in visible.indices) for (j in 0 until i) {
        if (hypot(xy[i].first - xy[j].first, xy[i].second - xy[j].second) < 70) roots[root(i)] = root(j)
    }
    val clusters = visible.indices.groupBy { root(it) }.values.map { MapCluster(it.map(visible::get)) }.toMutableList()
    // Merging changes both the anchor and number of rows, so check again until the bubbles fit.
    while (true) {
        var merged = false
        outer@ for (i in clusters.indices) for (j in 0 until i) {
            val a = projected(clusters[i].point)
            val b = projected(clusters[j].point)
            if (abs(a.first - b.first) < CLUSTER_WIDTH + 8 &&
                abs(a.second - b.second) < maxOf(clusterHeight(clusters[i]), clusterHeight(clusters[j])) + 8) {
                clusters[j] = MapCluster(clusters[j].people + clusters[i].people)
                clusters.removeAt(i)
                merged = true
                break@outer
            }
        }
        if (!merged) return clusters
    }
}

fun residentMapState(person: Resident, care: List<CareEvent>): MapSafety {
    val current = currentCareEvents(care.filter { it.subjectId == person.originId })
    val rescued = current.any { it.action == CareAction.RESCUED && it.createdAt >= person.updatedAt }
    return when {
        rescued -> MapSafety.RESCUED
        person.safety == Safety.SOS -> MapSafety.SOS
        person.safety == Safety.SAFE -> MapSafety.SAFE
        else -> MapSafety.UNANSWERED
    }
}

fun publicRecordAuthor(authorId: String, people: List<Resident>, care: List<CareEvent>): String =
    people.find { it.originId == authorId }?.let { person ->
        when (residentMapState(person, care)) {
            MapSafety.SOS, MapSafety.UNANSWERED -> person.displayName()
            MapSafety.SAFE, MapSafety.RESCUED -> "メンバー"
        }
    } ?: "メンバー"

// The old schema stores home coordinates, not measured phone positions.
fun residentMapPerson(person: Resident, places: List<PlaceEvent>, care: List<CareEvent>): MapPerson {
    val building = validHome(places, person.originId)?.buildingId?.let { id -> placeBuildings(places).find { it.buildingId == id } }
    val home = building?.let { MapHome(it.buildingId!!, it.displayLabel(), MapPoint(it.latitude!!, it.longitude!!)) }
        ?: MapHome("home:${person.originId}", "登録住居", MapPoint(person.latitude, person.longitude))
    return MapPerson(person.originId, person.displayName(), residentMapState(person, care), home = home,
        bedroom = listOf(person.floor, person.room).filter { it.isNotBlank() }.joinToString("・").ifBlank { "未登録" })
}
