package io.github.nevaseme.plandmk1.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class VersionEntry(val originId: String, val revision: Long)

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class WireMessage(
    val version: Int = 1,
    val groupId: String,
    val type: String,
    val entries: List<VersionEntry> = emptyList(),
    val record: Resident? = null,
    val event: CareEvent? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val incident: IncidentEvent? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val place: PlaceEvent? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val position: PhonePosition? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val memo: SosMemo? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val quake: QuakeEvent? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val response: ResponseEvent? = null,
)

class SyncEngine(
    private val groupId: String,
    private val localId: String,
    private val store: RecordStore,
    private val send: suspend (String, ByteArray) -> Unit,
    private val onError: (String) -> Unit = {},
    private val onReceipt: () -> Unit = {},
    private val allowOwnPosition: () -> Boolean = { true },
) {
    private val peers = mutableSetOf<String>()
    private val pending = mutableMapOf<Pair<String, String>, Long>()
    private val pendingCare = mutableSetOf<Pair<String, String>>()
    private val pendingIncident = mutableSetOf<Pair<String, String>>()
    private val pendingPlace = mutableSetOf<Pair<String,String>>()
    private val pendingLocal = mutableMapOf<Triple<String,String,String>,Long>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun connected(peer: String) {
        peers.add(peer)
        guarded { summary(peer) }
    }
    fun disconnected(peer: String) {
        peers.remove(peer)
        pending.keys.removeAll { it.first == peer }
        pendingCare.removeAll { it.first == peer }
        pendingIncident.removeAll { it.first == peer }
        pendingPlace.removeAll {it.first==peer}
        pendingLocal.keys.removeAll {it.first==peer}
    }
    suspend fun changed() {
        trimStoppedPosition()
        peers.toList().forEach { peer -> guarded { summary(peer) } }
    }
    suspend fun receive(peer: String, bytes: ByteArray) {
        if (peer !in peers || bytes.size > 24_000) return
        val message = try { json.decodeFromString<WireMessage>(bytes.decodeToString(throwOnInvalidSequence = true)) }
        catch (_: Exception) { onError("読み取れない通信データを無視しました"); return }
        if (message.groupId != groupId || message.entries.size > 100) return
        trimStoppedPosition()
        if(message.version==6&&(message.type.startsWith("QUAKE_")||message.type.startsWith("RESPONSE_"))) {guarded {receiveJournal(peer,message)};return}
        if(message.version==5&&(message.type.startsWith("POSITION_")||message.type.startsWith("MEMO_"))) {guarded {receiveLocal(peer,message)};return}
        if(message.version==4&&message.type.startsWith("PLACE_")) {guarded {receivePlace(peer,message)};return}
        if (message.version == 3 && message.type.startsWith("INCIDENT_")) {
            guarded { receiveIncident(peer, message) }; return
        }
        if (message.version == 2 && message.type.startsWith("CARE_")) {
            guarded { receiveCare(peer, message) }; return
        }
        if (message.version != 1) return
        guarded {
            when (message.type) {
                "SUMMARY" -> {
                    val wanted = message.entries.filter { it.originId != localId && it.revision > 0 && (store.get(groupId, it.originId)?.revision ?: 0) < it.revision }
                    if (wanted.isNotEmpty()) emit(peer, WireMessage(groupId = groupId, type = "REQUEST", entries = wanted))
                }
                "REQUEST" -> message.entries.forEach { entry ->
                    store.get(groupId, entry.originId)?.let { record ->
                        pending[peer to record.originId] = record.revision
                        emit(peer, WireMessage(groupId = groupId, type = "RECORD", record = record))
                    }
                }
                "RECORD" -> {
                    val record = message.record ?: return@guarded
                    record.validate()
                    if (record.groupId != groupId || record.originId == localId) return@guarded
                    val changed = store.merge(record)
                    // Losing the original sender must not block forwarding to other peers.
                    guarded { emit(peer, WireMessage(groupId = groupId, type = "ACK", entries = listOf(VersionEntry(record.originId, record.revision)))) }
                    if (changed) peers.toList().filter { it != peer }.forEach { other ->
                        guarded { emit(other, WireMessage(groupId = groupId, type = "SUMMARY", entries = listOf(VersionEntry(record.originId, record.revision)))) }
                    }
                }
                "ACK" -> message.entries.forEach { entry ->
                    if (pending[peer to entry.originId] == entry.revision) {
                        pending.remove(peer to entry.originId); onReceipt()
                    }
                }
            }
        }
    }
    private suspend fun summary(peer: String) {
        journalSummary(peer,true)
        journalSummary(peer,false)
        store.positions(groupId).filter {it.originId!=localId||allowOwnPosition()}.map {VersionEntry(it.originId,it.revision)}.chunked(100).forEach {emit(peer,WireMessage(5,groupId,"POSITION_SUMMARY",it))}
        store.memos(groupId).map {VersionEntry(it.answerId,it.revision)}.chunked(100).forEach {emit(peer,WireMessage(5,groupId,"MEMO_SUMMARY",it))}
        store.places(groupId).map {VersionEntry(it.eventId,1)}.chunked(100).forEach {emit(peer,WireMessage(4,groupId,"PLACE_SUMMARY",it))}
        store.incidents(groupId).map { VersionEntry(it.eventId,1) }.chunked(100).forEach {
            emit(peer,WireMessage(3,groupId,"INCIDENT_SUMMARY",it))
        }
        val entries = store.all(groupId).map { VersionEntry(it.originId, it.revision) }
        val chunks = entries.chunked(100).ifEmpty { listOf(emptyList()) }
        chunks.forEach { emit(peer, WireMessage(groupId = groupId, type = "SUMMARY", entries = it)) }
        val events = store.events(groupId).map { VersionEntry(it.eventId, 1) }
        events.chunked(100).forEach {
            emit(peer, WireMessage(version = 2, groupId = groupId, type = "CARE_SUMMARY", entries = it))
        }
    }
    private suspend fun journalSummary(peer:String,quake:Boolean) {
        val prefix=if(quake)"QUAKE" else "RESPONSE"
        val ids=if(quake)store.quakeEvents(groupId).map {it.eventId} else store.responseEvents(groupId).map {it.eventId}
        ids.map {VersionEntry(it,1)}.chunked(100).forEach {emit(peer,WireMessage(6,groupId,"${prefix}_SUMMARY",it))}
    }
    private suspend fun receiveJournal(peer:String,message:WireMessage) {
        val quake=message.type.startsWith("QUAKE_");val prefix=if(quake)"QUAKE" else "RESPONSE"
        if(message.entries.any {it.revision!=1L||!recordIdPattern.matches(it.originId)})return
        val quakes=if(quake)store.quakeEvents(groupId).associateBy {it.eventId} else emptyMap()
        val responses=if(!quake)store.responseEvents(groupId).associateBy {it.eventId} else emptyMap()
        when(message.type.removePrefix("${prefix}_")) {
            "SUMMARY"->{
                val missing=message.entries.filter {it.originId !in quakes && it.originId !in responses}
                if(missing.isNotEmpty())emit(peer,WireMessage(6,groupId,"${prefix}_REQUEST",missing))
            }
            "REQUEST"->message.entries.forEach {entry->guarded {
                val q=quakes[entry.originId];val r=responses[entry.originId]
                if(q!=null||r!=null) {
                    pendingLocal[Triple(peer,prefix,entry.originId)]=1
                    emit(peer,WireMessage(6,groupId,"${prefix}_RECORD",quake=q,response=r))
                }
            } }
            "RECORD"->{
                val id:String;val changed:Boolean
                if(quake){val event=message.quake?:return;if(event.groupId!=groupId)return;id=event.eventId;changed=store.mergeQuake(event)}
                else {val event=message.response?:return;if(event.groupId!=groupId)return;id=event.eventId;changed=store.mergeResponse(event)}
                guarded {emit(peer,WireMessage(6,groupId,"${prefix}_ACK",listOf(VersionEntry(id,1))))}
                // Receiving reports can create a formation; announce it to the original sender too.
                if(changed)peers.toList().forEach {other->guarded {journalSummary(other,quake)}}
            }
            "ACK"->message.entries.forEach {if(pendingLocal.remove(Triple(peer,prefix,it.originId))!=null)onReceipt()}
        }
    }
    private suspend fun receiveCare(peer: String, message: WireMessage) {
        if (message.entries.any { it.revision != 1L || !it.originId.matches(Regex("[0-9a-fA-F-]{36}")) }) return
        when (message.type) {
            "CARE_SUMMARY" -> {
                val missing = message.entries.filter { store.getEvent(groupId, it.originId) == null }
                if (missing.isNotEmpty()) emit(peer, WireMessage(2, groupId, "CARE_REQUEST", missing))
            }
            "CARE_REQUEST" -> message.entries.forEach { entry ->
                store.getEvent(groupId, entry.originId)?.let {
                    pendingCare.add(peer to it.eventId)
                    emit(peer, WireMessage(version = 2, groupId = groupId, type = "CARE_RECORD", event = it))
                }
            }
            "CARE_RECORD" -> {
                val event = message.event ?: return
                event.validate(); if (event.groupId != groupId) return
                val changed = store.mergeEvent(event)
                val entries = listOf(VersionEntry(event.eventId, 1))
                guarded { emit(peer, WireMessage(2, groupId, "CARE_ACK", entries)) }
                if (changed) peers.toList().filter { it != peer }.forEach { other ->
                    guarded { emit(other, WireMessage(2, groupId, "CARE_SUMMARY", entries)) }
                }
            }
            "CARE_ACK" -> message.entries.forEach { if (pendingCare.remove(peer to it.originId)) onReceipt() }
        }
    }
    private suspend fun receiveIncident(peer: String, message: WireMessage) {
        if(message.entries.any { it.revision!=1L || !it.originId.matches(Regex("[0-9a-fA-F-]{36}")) }) return
        when(message.type) {
            "INCIDENT_SUMMARY" -> {
                val missing=message.entries.filter { store.getIncident(groupId,it.originId)==null }
                if(missing.isNotEmpty()) emit(peer,WireMessage(3,groupId,"INCIDENT_REQUEST",missing))
            }
            "INCIDENT_REQUEST" -> message.entries.forEach { entry -> store.getIncident(groupId,entry.originId)?.let {
                pendingIncident.add(peer to it.eventId)
                emit(peer,WireMessage(version=3,groupId=groupId,type="INCIDENT_RECORD",incident=it))
            } }
            "INCIDENT_RECORD" -> {
                val event=message.incident ?: return
                event.validate(); if(event.groupId!=groupId) return
                val changed=store.mergeIncident(event); val entries=listOf(VersionEntry(event.eventId,1))
                guarded { emit(peer,WireMessage(3,groupId,"INCIDENT_ACK",entries)) }
                if(changed) peers.toList().filter { it!=peer }.forEach { other -> guarded { emit(other,WireMessage(3,groupId,"INCIDENT_SUMMARY",entries)) } }
            }
            "INCIDENT_ACK" -> message.entries.forEach { if(pendingIncident.remove(peer to it.originId)) onReceipt() }
        }
    }
    private suspend fun receivePlace(peer: String,message: WireMessage) {
        if(message.entries.any {it.revision!=1L||!it.originId.matches(Regex("[0-9a-fA-F-]{36}"))})return
        when(message.type) {
            "PLACE_SUMMARY" -> {val missing=message.entries.filter {store.getPlace(groupId,it.originId)==null};if(missing.isNotEmpty())emit(peer,WireMessage(4,groupId,"PLACE_REQUEST",missing))}
            "PLACE_REQUEST" -> message.entries.forEach {entry->store.getPlace(groupId,entry.originId)?.let {pendingPlace.add(peer to it.eventId);emit(peer,WireMessage(version=4,groupId=groupId,type="PLACE_RECORD",place=it))}}
            "PLACE_RECORD" -> {
                val event=message.place?:return;event.validate();if(event.groupId!=groupId)return
                val changed=store.mergePlace(event);val entries=listOf(VersionEntry(event.eventId,1))
                guarded {emit(peer,WireMessage(4,groupId,"PLACE_ACK",entries))}
                if(changed)peers.toList().filter {it!=peer}.forEach {other->guarded {emit(other,WireMessage(4,groupId,"PLACE_SUMMARY",entries))}}
            }
            "PLACE_ACK" -> message.entries.forEach {if(pendingPlace.remove(peer to it.originId))onReceipt()}
        }
    }
    private fun trimStoppedPosition() {
        if(!allowOwnPosition())pendingLocal.keys.removeAll {it.second=="POSITION"&&it.third==localId}
    }
    private suspend fun receiveLocal(peer:String,message:WireMessage) {
        val position=message.type.startsWith("POSITION_")
        val prefix=if(position)"POSITION" else "MEMO"
        if(message.entries.any {it.revision !in 1..MAX_SEQUENCE||!recordIdPattern.matches(it.originId)})return
        when(message.type.removePrefix("${prefix}_")) {
            "SUMMARY"->{
                val revisions=if(position)store.positions(groupId).associate {it.originId to it.revision} else store.memos(groupId).associate {it.answerId to it.revision}
                val wanted=message.entries.filter {(!position||it.originId!=localId) && (revisions[it.originId]?:0)<it.revision}
                if(wanted.isNotEmpty())emit(peer,WireMessage(5,groupId,"${prefix}_REQUEST",wanted))
            }
            "REQUEST"->{
                val points=if(position)store.positions(groupId).associateBy {it.originId} else emptyMap()
                val notes=if(!position)store.memos(groupId).associateBy {it.answerId} else emptyMap()
                message.entries.forEach {entry->
                    if(position&&entry.originId==localId&&!allowOwnPosition())return@forEach
                    val point=points[entry.originId];val note=notes[entry.originId]
                    val revision=point?.revision?:note?.revision?:return@forEach
                    pendingLocal[Triple(peer,prefix,entry.originId)]=revision
                    emit(peer,WireMessage(5,groupId,"${prefix}_RECORD",position=point,memo=note))
                }
            }
            "RECORD"->{
                val changed:Boolean;val entry:VersionEntry
                if(position){
                    val point=message.position?:return;point.validate()
                    if(point.groupId!=groupId||point.originId==localId)return
                    changed=store.savePosition(point);entry=VersionEntry(point.originId,point.revision)
                }else{
                    val note=message.memo?:return;note.validate()
                    if(note.groupId!=groupId||note.authorId==localId)return
                    changed=store.mergeMemo(note);entry=VersionEntry(note.answerId,note.revision)
                }
                guarded {emit(peer,WireMessage(5,groupId,"${prefix}_ACK",listOf(entry)))}
                if(changed)peers.toList().filter {it!=peer}.forEach {other->guarded {emit(other,WireMessage(5,groupId,"${prefix}_SUMMARY",listOf(entry)))}}
            }
            "ACK"->message.entries.forEach {entry->
                val key=Triple(peer,prefix,entry.originId)
                if(pendingLocal[key]==entry.revision){pendingLocal.remove(key);onReceipt()}
            }
        }
    }
    private suspend fun emit(peer: String, message: WireMessage) {
        if(message.position?.originId==localId&&!allowOwnPosition())return
        val bytes = json.encodeToString(message).encodeToByteArray()
        require(bytes.size <= 24_000) { "通信データが大きすぎます" }
        send(peer, bytes)
    }
    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { onError("同期できませんでした。空き容量と接続を確認して再接続してください。${e.localizedMessage.orEmpty()}") }
    }
}
