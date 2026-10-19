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

class IncidentStorageTest {
    @Test fun versionTwoUpgradeKeepsLegacyAndRestartKeepsSeparateIncidentAnswers()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="incident-${UUID.randomUUID()}.db"
        val author=UUID.randomUUID().toString();val id=UUID.randomUUID().toString();val oldCare=UUID.randomUUID().toString()
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            old.execSQL("CREATE TABLE residents (groupId TEXT NOT NULL, originId TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, floor TEXT NOT NULL, room TEXT NOT NULL, safety TEXT NOT NULL, PRIMARY KEY(groupId, originId))")
            old.execSQL("INSERT INTO residents VALUES ('test', ?, 1, 1, '以前の住民',35.0,139.0,'2階','北','SOS')",arrayOf(author))
            old.execSQL("CREATE TABLE care_events (groupId TEXT NOT NULL, eventId TEXT NOT NULL, subjectId TEXT NOT NULL, authorId TEXT NOT NULL, authorName TEXT NOT NULL, createdAt INTEGER NOT NULL, action TEXT NOT NULL, note TEXT NOT NULL, supersedes TEXT, PRIMARY KEY(groupId,eventId))")
            old.execSQL("INSERT INTO care_events VALUES ('test',?,?,?,'確認者',1,'RESCUED','旧記録',NULL)",arrayOf(oldCare,author,author));old.version=2
        }
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).addMigrations(PlanDatabase.MIGRATION_2_3, PlanDatabase.MIGRATION_3_4).build()
        var db=open()
        try {
            var store=RoomRecordStore(db)
            assertEquals(Safety.SOS,store.all("test").single().safety);assertEquals(oldCare,store.events("test").single().eventId)
            val start=IncidentEvent("test",id,id,author,"確認者",1,1,IncidentKind.START,title="新訓練",area="地域")
            store.mergeIncident(start)
            val projected=projectIncidentUi("test",store.all("test"),store.events("test"),store.incidents("test"),id)
            assertEquals(Safety.UNREPORTED,projected.residents.single().safety);assertTrue(projected.care.isEmpty())
            store.appendIncident(IncidentEvent("test",UUID.randomUUID().toString(),id,author,"確認者",1,2,IncidentKind.ANSWER,safety=Safety.SAFE))
            db.close();db=open();store=RoomRecordStore(db)
            assertEquals(Safety.SAFE,incidentView(store.incidents("test"),id)!!.answers[author]!!.safety)
            store.appendIncident(IncidentEvent("test",UUID.randomUUID().toString(),id,author,"確認者",1,3,IncidentKind.END))
            try{store.appendIncident(IncidentEvent("test",UUID.randomUUID().toString(),id,author,"確認者",1,4,IncidentKind.ANSWER,safety=Safety.SOS));fail("closed must reject") }catch(_:IllegalArgumentException){}
        } finally { db.close();context.deleteDatabase(name) }
    }
}
