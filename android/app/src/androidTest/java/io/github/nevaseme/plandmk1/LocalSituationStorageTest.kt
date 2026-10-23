package io.github.nevaseme.plandmk1

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LocalSituationStorageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val author = UUID.randomUUID().toString()
    private fun open(name: String) = Room.databaseBuilder(context, PlanDatabase::class.java, name)
        .addMigrations(PlanDatabase.MIGRATION_1_2, PlanDatabase.MIGRATION_2_3, PlanDatabase.MIGRATION_3_4, PlanDatabase.MIGRATION_4_5).build()

    @Test fun everyExistingVersionKeepsRecordsAndNeverInventsPhoneLocations() = runBlocking {
        for (version in 1..4) {
            val name = "local-migration-${UUID.randomUUID()}.db"
            val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
            val schema = InstrumentationRegistry.getInstrumentation().context.assets
                .open("io.github.nevaseme.plandmk1.data.PlanDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()) }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val tables = schema.getJSONObject("database").getJSONArray("entities")
                for (i in 0 until tables.length()) {
                    val table = tables.getJSONObject(i)
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
                }
                old.execSQL("INSERT INTO residents VALUES ('test', ?, 7, 123, '以前の住民',35.0,139.0,'2階','北の寝室','SOS')", arrayOf(author))
                if (version >= 2) old.execSQL("INSERT INTO care_events VALUES ('test',?,?,?,'確認者',123,'RESCUED','保存する記録',NULL)", arrayOf(author, author, author))
                old.version = version
            }
            val db = open(name)
            try {
                val store = RoomRecordStore(db)
                assertEquals("北の寝室", store.all("test").single().room)
                assertEquals(7L, store.all("test").single().revision)
                if (version >= 2) assertEquals("保存する記録", store.events("test").single().note)
                assertTrue(store.positions("test").isEmpty())
                assertTrue(store.memos("test").isEmpty())
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun memoAndPositionSurviveReopenAndMemoEditDoesNotCreateAnotherSOS() = runBlocking {
        val name = "local-situation-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            var store = RoomRecordStore(db)
            store.updateOwn("test", author) { Resident("test", author, 1, 0, "試験", 35.0, 139.0, "2階", "北") }
            val id = UUID.randomUUID().toString()
            store.mergeIncident(IncidentEvent("test", id, id, author, "試験", 1, 1, IncidentKind.START, "訓練", "地域"))
            val answer = store.answerWithMemo(IncidentEvent("test", UUID.randomUUID().toString(), id, author, "試験", 1, 2, IncidentKind.ANSWER, safety = Safety.SOS), "助けて")
            store.saveSosMemo("test", id, author, answer.eventId, "2階です", 3)
            assertEquals(1, store.incidents("test").count { it.kind == IncidentKind.ANSWER })
            assertEquals(2L, store.memos("test").single().revision)
            store.savePosition(PhonePosition("test", author, 1, 4, 32.79, 130.81, 12.5))
            db.close(); db = open(name); store = RoomRecordStore(db)
            assertEquals("2階です", store.memos("test").single().text)
            assertEquals(32.79, store.positions("test").single().latitude, .000001)
            assertEquals(35.0, store.all("test").single().latitude, .000001)
            assertFalse(store.savePosition(PhonePosition("test", author, 1, 4, 32.79, 130.81, 12.5)))
            assertTrue(store.positions("other").isEmpty())
            assertTrue(store.memos("other").isEmpty())
            val before = store.incidents("test").size
            try {
                store.answerWithMemo(answer.copy(eventId = UUID.randomUUID().toString()), "長".repeat(501)); fail("limit")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, store.incidents("test").size)
            assertEquals("2階です", store.memos("test").single().text)
            store.appendIncident(answer.copy(eventId = UUID.randomUUID().toString(), safety = Safety.SAFE, createdAt = 5))
            try { store.saveSosMemo("test", id, author, answer.eventId, "古い回答", 6); fail("stale answer") }
            catch (_: IllegalArgumentException) { }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
