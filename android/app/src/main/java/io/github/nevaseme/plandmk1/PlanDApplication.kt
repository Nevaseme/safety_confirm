package io.github.nevaseme.plandmk1

import android.app.Application
import androidx.room.Room
import io.github.nevaseme.plandmk1.data.PlanDatabase
import io.github.nevaseme.plandmk1.data.RoomRecordStore
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import org.maplibre.android.MapLibre
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.nevaseme.plandmk1.data.InstallationSettings
import io.github.nevaseme.plandmk1.nearby.NearbyPort
import io.github.nevaseme.plandmk1.nearby.NearbyTransport
import io.github.nevaseme.plandmk1.sharing.SharingController
import kotlinx.coroutines.CoroutineScope

class PlanDApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, PlanDatabase::class.java, "pland.db").addMigrations(PlanDatabase.MIGRATION_1_2, PlanDatabase.MIGRATION_2_3, PlanDatabase.MIGRATION_3_4).build() }
    val store by lazy { RoomRecordStore(database) }
    val offlineMaps by lazy { OfflineMapRepository(this) }
    val installation by lazy { InstallationSettings(this) }
    val sharing by lazy { SharingController(this) }
    val lab by lazy { io.github.nevaseme.plandmk1.lab.LabController(store, installation.localId) }
    // In-process injection for lifecycle tests, never exposed through IPC or UI.
    var sharingPortFactory: (CoroutineScope) -> NearbyPort = { NearbyTransport(this, it) }
    override fun onCreate() {
        super.onCreate(); MapLibre.getInstance(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { sharing.setForeground(true); lab.setVisible(true) }
            override fun onStop(owner: LifecycleOwner) { sharing.setForeground(false); lab.setVisible(false) }
        })
    }
}
