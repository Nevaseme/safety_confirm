package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

@Serializable enum class IncidentKind { START, END, ANSWER, CARE }
@Serializable data class IncidentEvent(
    val groupId: String, val eventId: String, val incidentId: String,
    val authorId: String, val authorName: String, val sequence: Long, val createdAt: Long,
    val kind: IncidentKind, val title: String = "", val area: String = "", val training: Boolean = true,
    val safety: Safety? = null, val subjectId: String? = null, val action: CareAction? = null,
    val note: String = "", val supersedes: String? = null,
) {
    fun validate() {
        val uuid=Regex("[0-9a-fA-F-]{36}")
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,40}")) && listOf(eventId,incidentId,authorId).all { it.matches(uuid) })
        require(sequence in 1..9_007_199_254_740_991L && createdAt in 0..9_007_199_254_740_991L)
        require(authorName.isNotBlank() && authorName.length<=60 && title.length<=80 && area.length<=120 && note.length<=500)
        if(kind==IncidentKind.START) require(eventId==incidentId && sequence==1L && title.isNotBlank() && area.isNotBlank()) { "地震の名称と対象地域を入力してください" }
        else require(eventId!=incidentId && title.isEmpty() && area.isEmpty())
        require(if(kind==IncidentKind.ANSWER) safety==Safety.SAFE || safety==Safety.SOS else safety==null)
        if(kind==IncidentKind.CARE) {
            require(subjectId?.matches(uuid)==true && action!=null)
            require(supersedes==null || (supersedes.matches(uuid) && supersedes!=eventId))
            require(action!=CareAction.REVOKED || supersedes!=null)
        } else require(subjectId==null && action==null && supersedes==null && note.isEmpty())
    }
    fun care() = CareEvent(groupId,eventId,checkNotNull(subjectId),authorId,authorName,createdAt,checkNotNull(action),note,supersedes)
}

data class IncidentView(val start: IncidentEvent, val closed: Boolean, val answers: Map<String,IncidentEvent>, val care: List<CareEvent>)
fun incidentView(rows: List<IncidentEvent>, id: String?): IncidentView? {
    val start=rows.find { it.kind==IncidentKind.START && it.eventId==id } ?: return null
    val events=rows.filter { it.groupId==start.groupId && it.incidentId==id }
    return IncidentView(start,events.any { it.kind==IncidentKind.END && it.authorId==start.authorId },
        events.filter { it.kind==IncidentKind.ANSWER }.groupBy { it.authorId }.mapValues { (_,v)-> v.maxBy { it.sequence } },
        events.filter { it.kind==IncidentKind.CARE }.map { it.care() })
}
fun incidentOptions(rows: List<IncidentEvent>) = rows.filter { it.kind==IncidentKind.START }
    .mapNotNull { incidentView(rows,it.eventId) }.sortedWith(compareByDescending<IncidentView> { it.start.createdAt }.thenBy { it.start.eventId })
