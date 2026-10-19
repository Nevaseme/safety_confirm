package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "residents", primaryKeys = ["groupId", "originId"])
data class ResidentRow(
    val groupId: String, val originId: String, val revision: Long, val updatedAt: Long,
    val name: String, val latitude: Double, val longitude: Double,
    val floor: String, val room: String, val safety: String,
) {
    fun record() = Resident(groupId, originId, revision, updatedAt, name, latitude, longitude, floor, room, Safety.valueOf(safety))
    companion object {
        fun from(r: Resident) = ResidentRow(r.groupId, r.originId, r.revision, r.updatedAt, r.name, r.latitude, r.longitude, r.floor, r.room, r.safety.name)
    }
}

@Dao
interface ResidentDao {
    @Query("SELECT * FROM residents WHERE groupId = :groupId ORDER BY name")
    fun observe(groupId: String): Flow<List<ResidentRow>>
    @Query("SELECT * FROM residents WHERE groupId = :groupId")
    suspend fun all(groupId: String): List<ResidentRow>
    @Query("SELECT * FROM residents WHERE groupId = :groupId AND originId = :originId")
    suspend fun get(groupId: String, originId: String): ResidentRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: ResidentRow)
}

@Entity(tableName = "care_events", primaryKeys = ["groupId", "eventId"])
data class CareEventRow(
    val groupId: String, val eventId: String, val subjectId: String, val authorId: String,
    val authorName: String, val createdAt: Long, val action: String, val note: String, val supersedes: String?,
) {
    fun record() = CareEvent(groupId, eventId, subjectId, authorId, authorName, createdAt, CareAction.valueOf(action), note, supersedes)
    companion object { fun from(e: CareEvent) = CareEventRow(e.groupId, e.eventId, e.subjectId, e.authorId, e.authorName, e.createdAt, e.action.name, e.note, e.supersedes) }
}
@Dao
interface CareEventDao {
    @Query("SELECT * FROM care_events WHERE groupId = :groupId ORDER BY createdAt, eventId")
    fun observe(groupId: String): Flow<List<CareEventRow>>
    @Query("SELECT * FROM care_events WHERE groupId = :groupId ORDER BY createdAt, eventId")
    suspend fun all(groupId: String): List<CareEventRow>
    @Query("SELECT * FROM care_events WHERE groupId = :groupId AND eventId = :eventId")
    suspend fun get(groupId: String, eventId: String): CareEventRow?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun put(row: CareEventRow)
}

@Entity(tableName = "incident_events", primaryKeys = ["groupId", "eventId"])
data class IncidentRow(val groupId: String, val eventId: String, val payload: String) {
    fun record() = Json.decodeFromString<IncidentEvent>(payload)
}
@Dao interface IncidentDao {
    @Query("SELECT * FROM incident_events WHERE groupId = :groupId")
    fun observe(groupId: String): Flow<List<IncidentRow>>
    @Query("SELECT * FROM incident_events WHERE groupId = :groupId")
    suspend fun all(groupId: String): List<IncidentRow>
    @Query("SELECT * FROM incident_events WHERE groupId = :groupId AND eventId = :eventId")
    suspend fun get(groupId: String, eventId: String): IncidentRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun put(row: IncidentRow)
}

@Entity(tableName="place_events",primaryKeys=["groupId","eventId"])
data class PlaceRow(val groupId: String,val eventId: String,val payload: String) {fun record()=Json.decodeFromString<PlaceEvent>(payload)}
@Dao interface PlaceDao {
    @Query("SELECT * FROM place_events WHERE groupId = :groupId") fun observe(groupId: String): Flow<List<PlaceRow>>
    @Query("SELECT * FROM place_events WHERE groupId = :groupId") suspend fun all(groupId: String): List<PlaceRow>
    @Query("SELECT * FROM place_events WHERE groupId = :groupId AND eventId = :eventId") suspend fun get(groupId: String,eventId: String): PlaceRow?
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun put(row: PlaceRow)
}

@Database(entities = [ResidentRow::class, CareEventRow::class, IncidentRow::class, PlaceRow::class], version = 4, exportSchema = true)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun incidents(): IncidentDao
    abstract fun residents(): ResidentDao
    abstract fun careEvents(): CareEventDao
    companion object {
        val MIGRATION_3_4=object: Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {db.execSQL("CREATE TABLE IF NOT EXISTS `place_events` (`groupId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`groupId`, `eventId`))")}
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `incident_events` (`groupId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`groupId`, `eventId`))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_events` (`groupId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `authorId` TEXT NOT NULL, `authorName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `action` TEXT NOT NULL, `note` TEXT NOT NULL, `supersedes` TEXT, PRIMARY KEY(`groupId`, `eventId`))")
            }
        }
    }
}

class RoomRecordStore(private val db: PlanDatabase) : RecordStore {
    fun observePlaces(groupId: String)=db.places().observe(groupId).map {it.map(PlaceRow::record)}
    override suspend fun places(groupId: String)=db.places().all(groupId).map(PlaceRow::record)
    override suspend fun getPlace(groupId: String,eventId: String)=db.places().get(groupId,eventId)?.record()
    override suspend fun mergePlace(event: PlaceEvent): Boolean=db.withTransaction {
        event.validate();val old=getPlace(event.groupId,event.eventId)
        if(old!=null) {require(old==event) {"同じIDの建物記録に異なる内容があります"};false}
        else {
            require(places(event.groupId).none {it.authorId==event.authorId&&it.sequence==event.sequence}) {"同じ更新番号の建物記録に異なる内容があります"}
            db.places().put(PlaceRow(event.groupId,event.eventId,Json.encodeToString(event)));true
        }
    }
    suspend fun appendPlace(event: PlaceEvent): PlaceEvent=db.withTransaction {
        require(get(event.groupId,event.authorId)!=null) {"先に自分の情報を登録してください"}
        preparePlace(event,places(event.groupId),incidents(event.groupId)).also {mergePlace(it)}
    }
    fun observeIncidents(groupId: String) = db.incidents().observe(groupId).map { it.map(IncidentRow::record) }
    override suspend fun incidents(groupId: String) = db.incidents().all(groupId).map(IncidentRow::record)
    override suspend fun getIncident(groupId: String,eventId: String) = db.incidents().get(groupId,eventId)?.record()
    override suspend fun mergeIncident(event: IncidentEvent): Boolean = db.withTransaction {
        event.validate()
        val old=getIncident(event.groupId,event.eventId)
        if(old!=null) { require(old==event) { "同じIDの記録に異なる内容があります" }; false }
        else {
            require(incidents(event.groupId).none { it.incidentId==event.incidentId && it.authorId==event.authorId && it.sequence==event.sequence }) { "同じ更新番号の記録に異なる内容があります" }
            db.incidents().put(IncidentRow(event.groupId,event.eventId,Json.encodeToString(event))); true
        }
    }
    suspend fun appendIncident(event: IncidentEvent): IncidentEvent = db.withTransaction {
        val rows=incidents(event.groupId)
        val view=incidentView(rows,event.incidentId)
        require(view!=null && !view.closed) { "開始中の災害を選んでください" }
        if(event.kind==IncidentKind.END) require(view.start.authorId==event.authorId) { "終了できるのは開始した人だけです" }
        if(event.kind==IncidentKind.CARE && event.supersedes!=null) {
            val old=view.care.find { it.eventId==event.supersedes }
            require(old!=null && old.authorId==event.authorId && old.subjectId==event.subjectId) { "同じ災害の自分の記録だけ訂正できます" }
        }
        val sequence=Math.addExact(rows.filter { it.incidentId==event.incidentId && it.authorId==event.authorId }.maxOfOrNull { it.sequence } ?: 0,1)
        event.copy(sequence=sequence).also { mergeIncident(it) }
    }
    fun observeEvents(groupId: String) = db.careEvents().observe(groupId).map { it.map(CareEventRow::record) }
    override suspend fun events(groupId: String) = db.careEvents().all(groupId).map(CareEventRow::record)
    override suspend fun getEvent(groupId: String, eventId: String) = db.careEvents().get(groupId, eventId)?.record()
    override suspend fun mergeEvent(event: CareEvent): Boolean = db.withTransaction {
        event.validate()
        val old = getEvent(event.groupId, event.eventId)
        if (old != null) { require(old == event) { "同じIDの記録に異なる内容があります" }; false }
        else { db.careEvents().put(CareEventRow.from(event)); true }
    }
    fun observe(groupId: String) = db.residents().observe(groupId).map { rows -> rows.map { it.record() } }
    override suspend fun all(groupId: String) = db.residents().all(groupId).map { it.record() }
    override suspend fun get(groupId: String, originId: String) = db.residents().get(groupId, originId)?.record()
    override suspend fun merge(record: Resident): Boolean = db.withTransaction {
        record.validate()
        val previous = db.residents().get(record.groupId, record.originId)
        if (previous != null && previous.revision >= record.revision) false
        else { db.residents().put(ResidentRow.from(record)); true }
    }
    suspend fun updateOwn(group: String, id: String, transform: (Resident?) -> Resident): Resident = db.withTransaction {
        val previous = get(group, id)
        val next = transform(previous).copy(groupId = group, originId = id, revision = Math.addExact(previous?.revision ?: 0, 1), updatedAt = System.currentTimeMillis())
        next.validate()
        db.residents().put(ResidentRow.from(next))
        next
    }
}
