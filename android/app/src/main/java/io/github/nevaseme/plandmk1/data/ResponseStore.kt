package io.github.nevaseme.plandmk1.data

import androidx.room.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName="response_events",primaryKeys=["groupId","eventId"])
data class ResponseRow(val groupId:String,val eventId:String,val payload:String)
@Dao interface ResponseDao {
    @Query("SELECT * FROM response_events WHERE groupId=:group ORDER BY eventId") suspend fun all(group:String):List<ResponseRow>
    @Query("SELECT * FROM response_events WHERE groupId=:group ORDER BY eventId") fun observe(group:String):Flow<List<ResponseRow>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun put(row:ResponseRow)
}
class ResponseStore(private val db:PlanDatabase) {
    private val quakes=QuakeStore(db)
    suspend fun exportCsv(group:String,incidentId:String,regions:Map<ResponseTarget,List<String>> = emptyMap()):String=db.withTransaction {
        val roster=requireNotNull(quakes.roster(group)) {"参加者設定を読み込んでください"};val records=RoomRecordStore(db)
        operationCsv(operationRows(roster,incidentId,records.all(group),records.incidents(group),quakes.events(group),events(group),regions))
    }
    suspend fun events(group:String)=db.responses().all(group).map {Json.decodeFromString<ResponseEvent>(it.payload)}
    fun observe(group:String)=db.responses().observe(group).map {rows->rows.map {Json.decodeFromString<ResponseEvent>(it.payload)}}
    suspend fun heads(group:String):List<ResponseEvent> {
        val roster=quakes.roster(group)?:return emptyList();val quakes=quakes.events(group)
        return responseHeads(roster,events(group).map {it.copy(incidentId=canonicalIncidentId(roster,quakes,it.incidentId))})
    }
    suspend fun merge(event:ResponseEvent):Boolean=db.withTransaction {
        val roster=requireNotNull(quakes.roster(event.groupId)) {"参加者設定を読み込んでください"}
        validateResponseShape(roster,event)
        val previous=events(event.groupId)
        val old=previous.find {it.eventId==event.eventId}
        if(old!=null) {require(old==event) {"同じIDに異なる対応記録があります"};false}
        else {
            require(previous.none {it.actorDeviceId==event.actorDeviceId&&it.sequence==event.sequence}) {"同じ更新番号に異なる対応記録があります"}
            db.responses().put(ResponseRow(event.groupId,event.eventId,Json.encodeToString(event)));true
        }
    }
    suspend fun append(event:ResponseEvent):ResponseEvent=db.withTransaction {
        val roster=requireNotNull(quakes.roster(event.groupId)) {"参加者設定を読み込んでください"}
        val history=events(event.groupId)
        val quakeHistory=quakes.events(event.groupId)
        val projected=history.map {it.copy(incidentId=canonicalIncidentId(roster,quakeHistory,it.incidentId))}
        require(history.none {it.eventId==event.eventId})
        val next=event.copy(sequence=Math.addExact(history.filter {it.actorDeviceId==event.actorDeviceId}.maxOfOrNull {it.sequence}?:0,1))
        if(next.incidentId!=null) {
            require(quakes.states(next.groupId).any {next.incidentId in it.incidentIds&&!it.closed}) {"成立済みで開始中の災害を指定してください"}
        }
        next.change.target()?.let {target->
            if(target.kind==TargetKind.PERSON)require(db.residents().get(next.groupId,target.id)!=null) {"対象住民が見つかりません"}
            else if(next.change !is ResponseChange.Report)require(projected.any {it.eventId==target.id && it.incidentId==canonicalIncidentId(roster,quakeHistory,next.incidentId) && it.change is ResponseChange.Report}) {"対象の通報が見つかりません"}
        }
        validateResponseCommand(roster,projected,next.copy(incidentId=canonicalIncidentId(roster,quakeHistory,next.incidentId)))
        merge(next);next
    }
}
