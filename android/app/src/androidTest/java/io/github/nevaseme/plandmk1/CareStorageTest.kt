package io.github.nevaseme.plandmk1

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CareStorageTest {
    @Test fun migratesVersionOneWithoutLosingResidentsAndPersistsCorrectionHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val person = UUID.randomUUID().toString(); val author = UUID.randomUUID().toString()
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE residents (groupId TEXT NOT NULL, originId TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, floor TEXT NOT NULL, room TEXT NOT NULL, safety TEXT NOT NULL, PRIMARY KEY(groupId, originId))")
            old.execSQL("INSERT INTO residents VALUES (?, ?, 3, 1, '旧版の住民', 35.0, 139.0, '2階', '北', 'SOS')", arrayOf("demo", person))
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, PlanDatabase::class.java, name).addMigrations(PlanDatabase.MIGRATION_1_2, PlanDatabase.MIGRATION_2_3, PlanDatabase.MIGRATION_3_4, PlanDatabase.MIGRATION_4_5).build()
        var db = open()
        try {
            var store = RoomRecordStore(db)
            assertEquals(3L, store.get("demo", person)!!.revision)
            val first = CareEvent("demo", UUID.randomUUID().toString(), person, author, "確認者", 1, CareAction.RESCUED)
            val correction = first.copy(eventId = UUID.randomUUID().toString(), action = CareAction.NEEDS_HELP, supersedes = first.eventId)
            assertTrue(store.mergeEvent(first)); assertTrue(store.mergeEvent(correction)); assertFalse(store.mergeEvent(first))
            try { store.mergeEvent(first.copy(note = "改変")); fail("Immutable event must reject conflict") } catch (_: IllegalArgumentException) {}
            db.close(); db = open(); store = RoomRecordStore(db)
            assertEquals(2, store.events("demo").size)
            assertEquals(listOf(correction), currentCareEvents(store.events("demo")))
            assertEquals(Safety.SOS, store.get("demo", person)!!.safety)
            assertTrue(store.events("other").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
