package io.github.nevaseme.plandmk1.maps

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import kotlin.math.cos

data class OfflineMapState(
    val exists: Boolean = false,
    val busy: Boolean = true,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val resources: Long = 0,
    val expected: Long = 0,
    val bytes: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val error: String? = null,
)

class OfflineMapRepository(private val context: Context) {
    private val manager = OfflineManager.getInstance(context)
    private var region: OfflineRegion? = null
    private var downloadRequested = false
    private val mutable = MutableStateFlow(OfflineMapState())
    val state = mutable.asStateFlow()

    init { restore() }

    private fun restore() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val owned = regions.orEmpty().filter { it.metadata.decodeToString().startsWith("pland-v1:") }
                region = owned.maxByOrNull { it.id }
                region?.let { bind(it); refresh(it) } ?: run { mutable.value = OfflineMapState(busy = false) }
            }
            override fun onError(error: String) { mutable.value = OfflineMapState(busy = false, error = "地図の保存状態を確認できません：$error") }
        })
    }

    fun download(latitude: Double, longitude: Double) {
        if (mutable.value.busy || region != null) return
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -84.98..84.98 || longitude !in -179.89..179.89) {
            mutable.value = OfflineMapState(busy = false, error = "この位置は保存範囲外です。中心位置を変更してください"); return
        }
        if (StatFs(context.filesDir.path).availableBytes < 100L * 1024 * 1024) {
            mutable.value = OfflineMapState(busy = false, error = "地図を保存するには100MB以上の空き容量を確保してください"); return
        }
        mutable.value = OfflineMapState(busy = true, latitude = latitude, longitude = longitude)
        downloadRequested = true
        val dy = 1.0 / 111.32
        val dx = 1.0 / (111.32 * cos(Math.toRadians(latitude)))
        val bounds = LatLngBounds.from(latitude + dy, longitude + dx, latitude - dy, longitude - dx)
        val definition = OfflineTilePyramidRegionDefinition(STYLE, bounds, 14.0, 18.0, context.resources.displayMetrics.density)
        manager.createOfflineRegion(definition, "pland-v1:$latitude,$longitude".encodeToByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                region = offlineRegion; bind(offlineRegion)
                if (downloadRequested) resume() else refresh(offlineRegion)
            }
            override fun onError(error: String) { mutable.value = mutable.value.copy(busy = false, error = "保存を開始できません：$error") }
        })
    }

    private fun bind(value: OfflineRegion) {
        val definition = value.definition as? OfflineTilePyramidRegionDefinition
        mutable.value = mutable.value.copy(exists = true, busy = false,
            latitude = definition?.bounds?.center?.latitude, longitude = definition?.bounds?.center?.longitude)
        value.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                mutable.value = mutable.value.copy(busy = false, complete = status.isComplete,
                    downloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE,
                    resources = status.completedResourceCount, expected = status.requiredResourceCount,
                    bytes = status.completedResourceSize, error = if (status.isComplete) null else mutable.value.error)
                if (status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE) value.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) {
                value.setDownloadState(OfflineRegion.STATE_INACTIVE)
                mutable.value = mutable.value.copy(busy = false, downloading = false, error = "地図保存を中断しました：${error.message}。通信・空き容量を確認して再開してください")
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                value.setDownloadState(OfflineRegion.STATE_INACTIVE)
                mutable.value = mutable.value.copy(downloading = false, error = "地図の保存上限に達しました（$limit）")
            }
        })
    }
    private fun refresh(value: OfflineRegion) {
        value.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) { mutable.value = mutable.value.copy(busy = false, error = "地図の状態を取得できません"); return }
                mutable.value = mutable.value.copy(busy = false, complete = status.isComplete, downloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE,
                    resources = status.completedResourceCount, expected = status.requiredResourceCount, bytes = status.completedResourceSize)
            }
            override fun onError(error: String?) { mutable.value = mutable.value.copy(busy = false, error = error ?: "地図の状態を取得できません") }
        })
    }
    fun resume() {
        if (region == null) return
        downloadRequested = true
        mutable.value = mutable.value.copy(error = null, downloading = true)
        region?.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
    fun pause() {
        downloadRequested = false
        region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        mutable.value = mutable.value.copy(downloading = false)
    }
    fun delete() {
        val value = region ?: return
        pause(); mutable.value = mutable.value.copy(busy = true)
        value.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { region = null; mutable.value = OfflineMapState(busy = false) }
            override fun onError(error: String) { mutable.value = mutable.value.copy(busy = false, error = "削除できません：$error") }
        })
    }
    companion object { const val STYLE = "https://tiles.openfreemap.org/styles/positron" }
}
