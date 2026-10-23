package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName="member_rosters",primaryKeys=["groupId"])
data class RosterRow(val groupId:String,val payload:String)
@Entity(tableName="quake_events",primaryKeys=["groupId","eventId"])
data class QuakeRow(val groupId:String,val eventId:String,val payload:String)
@Dao interface QuakeDao {
    @Query("SELECT * FROM member_rosters WHERE groupId=:group") suspend fun roster(group:String):RosterRow?
    @Query("SELECT * FROM member_rosters WHERE groupId=:group") fun observeRoster(group:String):Flow<RosterRow?>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun putRoster(row:RosterRow)
    @Query("SELECT * FROM quake_events WHERE groupId=:group ORDER BY eventId") suspend fun events(group:String):List<QuakeRow>
    @Query("SELECT * FROM quake_events WHERE groupId=:group ORDER BY eventId") fun observe(group:String):Flow<List<QuakeRow>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun put(row:QuakeRow)
}

class QuakeStore(private val db:PlanDatabase) {
    suspend fun roster(group:String)=db.quakes().roster(group)?.let {MemberRoster.decode(it.payload)}
    fun observeRoster(group:String)=db.quakes().observeRoster(group).map {it?.let {MemberRoster.decode(it.payload)}}
    suspend fun importRoster(text:String,ownDeviceId:String):MemberRoster=db.withTransaction {
        val candidate=MemberRoster.decode(text);candidate.memberForDevice(ownDeviceId)
        val previous=roster(candidate.groupId)
        require(previous==null || previous==candidate) {"この試験グループには異なる参加者設定が保存されています"}
        if(previous==null)db.quakes().putRoster(RosterRow(candidate.groupId,candidate.encode()))
        candidate
    }
    suspend fun events(group:String)=db.quakes().events(group).map {Json.decodeFromString<QuakeEvent>(it.payload)}
    fun observe(group:String)=db.quakes().observe(group).map {rows->rows.map {Json.decodeFromString<QuakeEvent>(it.payload)}}
    suspend fun states(group:String)=roster(group)?.let {quakeStates(it,events(group))}.orEmpty()
    suspend fun evaluate(group:String,localDeviceId:String,at:Long):List<QuakeState> =db.withTransaction {
        val roster=requireNotNull(roster(group));roster.memberForDevice(localDeviceId)
        formationCandidates(roster,events(group)).forEach {merge(it.event(UUID.randomUUID().toString(),localDeviceId,at))}
        states(group)
    }
    private suspend fun materialize(group:String) {
        val roster=requireNotNull(roster(group));val rows=events(group)
        quakeStates(roster,rows).forEach {state->
            val report=rows.first {it.eventId==state.formation.incidentId}
            val start=IncidentEvent(group,report.eventId,report.eventId,report.actorDeviceId,roster.memberForDevice(report.actorDeviceId).activityName,1,report.operatedAt,IncidentKind.START,report.title,report.area,report.training)
            if(db.incidents().get(group,start.eventId)==null)db.incidents().put(IncidentRow(group,start.eventId,Json.encodeToString(start)))
        }
    }

    /** Received history can arrive before its referenced report; it is projected only when its proof is available. */
    suspend fun merge(event:QuakeEvent):Boolean=db.withTransaction {
        val roster=requireNotNull(roster(event.groupId)) {"参加者設定を読み込んでください"}
        event.validate(roster)
        if(event.action==QuakeAction.END)require(roster.memberForDevice(event.actorDeviceId).role==MemberRole.ADMIN)
        val old=events(event.groupId).find {it.eventId==event.eventId}
        if(old!=null) {require(old==event) {"同じIDに異なる災害記録があります"};false}
        else {db.quakes().put(QuakeRow(event.groupId,event.eventId,Json.encodeToString(event)));materialize(event.groupId);true}
    }
    suspend fun command(event:QuakeEvent):List<QuakeState> =db.withTransaction {
        val roster=requireNotNull(roster(event.groupId)) {"参加者設定を読み込んでください"}
        val previous=events(event.groupId)
        val old=previous.find {it.eventId==event.eventId}
        if(old!=null) {require(old==event || (event.action==QuakeAction.END&&event.endedIncidentIds.isEmpty()&&old.copy(endedIncidentIds=emptyList())==event));return@withTransaction quakeStates(roster,previous)}
        val command=if(event.action==QuakeAction.END)event.copy(endedIncidentIds=quakeStates(roster,previous).find {event.targetId in it.incidentIds}?.incidentIds.orEmpty().sorted())else event
        validateQuakeCommand(roster,previous,command)
        merge(command)
        if(event.action==QuakeAction.REPORT)evaluate(event.groupId,event.actorDeviceId,event.operatedAt)
        quakeStates(roster,events(event.groupId))
    }
}
