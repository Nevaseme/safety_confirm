package io.github.nevaseme.plandmk1
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
class PlaceStorageTest {
    @Test fun upgradeFromThreeRetainsHistoryAndRestartRetainsBuildingLinksAndCare()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="places-${UUID.randomUUID()}.db";val author=UUID.randomUUID().toString();val incidentId=UUID.randomUUID().toString()
        val start=IncidentEvent("test",incidentId,incidentId,author,"A",1,1,IncidentKind.START,title="旧訓練",area="地区")
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use {old->
            old.execSQL("CREATE TABLE residents (groupId TEXT NOT NULL, originId TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, floor TEXT NOT NULL, room TEXT NOT NULL, safety TEXT NOT NULL, PRIMARY KEY(groupId, originId))")
            old.execSQL("INSERT INTO residents VALUES ('test', ?, 1, 1, '旧住民',35.0,139.0,'2階','北','SOS')",arrayOf(author))
            old.execSQL("CREATE TABLE care_events (groupId TEXT NOT NULL, eventId TEXT NOT NULL, subjectId TEXT NOT NULL, authorId TEXT NOT NULL, authorName TEXT NOT NULL, createdAt INTEGER NOT NULL, action TEXT NOT NULL, note TEXT NOT NULL, supersedes TEXT, PRIMARY KEY(groupId,eventId))")
            old.execSQL("CREATE TABLE incident_events (groupId TEXT NOT NULL,eventId TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(groupId,eventId))")
            old.execSQL("INSERT INTO incident_events VALUES ('test',?,?)",arrayOf(incidentId,Json.encodeToString(start)));old.version=3
        }
        fun open()=Room.databaseBuilder(context,PlanDatabase::class.java,name).addMigrations(PlanDatabase.MIGRATION_3_4, PlanDatabase.MIGRATION_4_5, PlanDatabase.MIGRATION_5_6, PlanDatabase.MIGRATION_6_7).build()
        var db=open()
        try {
            var store=RoomRecordStore(db);assertEquals(start,store.incidents("test").single());assertEquals(Safety.SOS,store.all("test").single().safety)
            val buildingId=UUID.randomUUID().toString();val building=store.appendPlace(PlaceEvent("test",buildingId,author,"A",1,1,PlaceKind.BUILDING,buildingId=buildingId,label="家",latitude=35.0,longitude=139.0))
            store.appendPlace(building.copy(eventId=UUID.randomUUID().toString(),kind=PlaceKind.HOME,label="",latitude=null,longitude=null))
            store.appendPlace(building.copy(eventId=UUID.randomUUID().toString(),kind=PlaceKind.CARE,label="",latitude=null,longitude=null,incidentId=incidentId,action=BuildingAction.COMPLETED,scope="1階",unresolved="2階"))
            db.close();db=open();store=RoomRecordStore(db)
            assertEquals(buildingId,validHome(store.places("test"),author)!!.buildingId);assertEquals("2階",buildingCare(store.places("test"),buildingId,incidentId).single().unresolved)
            assertEquals(3,store.places("test").size);assertEquals(start,store.incidents("test").single())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_place BEFORE INSERT ON place_events BEGIN SELECT RAISE(ABORT, 'test full'); END")
            try{store.appendPlace(building.copy(eventId=UUID.randomUUID().toString(),label="更新"));fail("failure must surface")}catch(_:android.database.sqlite.SQLiteException){}
            assertEquals("家",placeBuildings(store.places("test")).single().label)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
