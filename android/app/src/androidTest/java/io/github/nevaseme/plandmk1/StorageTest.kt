package io.github.nevaseme.plandmk1

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.core.Safety
import io.github.nevaseme.plandmk1.data.PlanDatabase
import io.github.nevaseme.plandmk1.data.RoomRecordStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageTest {
    @Test fun survivesDatabaseReopenAndRejectsOldUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, PlanDatabase::class.java, filename).build()
        var db = open()
        val record = Resident("demo", UUID.randomUUID().toString(), 2, 1, "テスト住民", 35.0, 139.0, "2階", "寝室", Safety.SAFE)
        try {
            assertTrue(RoomRecordStore(db).merge(record))
            db.close(); db = open()
            val store = RoomRecordStore(db)
            assertEquals(record, store.get("demo", record.originId))
            assertFalse(store.merge(record.copy(revision = 1, safety = Safety.SOS)))
            assertEquals(Safety.SAFE, store.get("demo", record.originId)?.safety)
            assertTrue(store.all("other").isEmpty())
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
