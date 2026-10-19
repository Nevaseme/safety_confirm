package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

@Serializable
enum class CareAction { CHECKING, NEEDS_HELP, RESCUED, REVOKED }

@Serializable
data class CareEvent(
    val groupId: String, val eventId: String, val subjectId: String,
    val authorId: String, val authorName: String, val createdAt: Long,
    val action: CareAction, val note: String = "", val supersedes: String? = null,
) {
    fun validate() {
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,40}")))
        require(listOf(eventId, subjectId, authorId).all { it.matches(Regex("[0-9a-fA-F-]{36}")) })
        require(authorName.isNotBlank() && authorName.length <= 60 && note.length <= 500)
        require(createdAt in 0..9_007_199_254_740_991L)
        require(supersedes == null || (supersedes != eventId && supersedes.matches(Regex("[0-9a-fA-F-]{36}"))))
        require(action != CareAction.REVOKED || supersedes != null)
    }
}

fun currentCareEvents(events: List<CareEvent>): List<CareEvent> {
    val byId = events.associateBy { it.eventId }
    val replaced = events.filter { e -> byId[e.supersedes]?.let {
        it.groupId == e.groupId && it.authorId == e.authorId && it.subjectId == e.subjectId
    } == true }.mapNotNull { it.supersedes }.toSet()
    return events.filter { it.eventId !in replaced }
}
