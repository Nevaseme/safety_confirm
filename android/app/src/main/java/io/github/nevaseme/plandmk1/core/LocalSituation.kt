package io.github.nevaseme.plandmk1.core

const val SOS_MEMO_LIMIT = 500
private const val MAX_RECORD_NUMBER = 9_007_199_254_740_991L

private fun validateIdentity(group: String, vararg ids: String) {
    require(group.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "グループが不正です" }
    require(ids.all { it.matches(Regex("[0-9a-fA-F-]{36}")) }) { "記録IDが不正です" }
}

/** Phone measurements are independent of the registered home and of safety answers. */
@kotlinx.serialization.Serializable data class PhonePosition(
    val groupId: String, val originId: String, val revision: Long,
    val acquiredAt: Long, val latitude: Double, val longitude: Double, val accuracyMeters: Double,
) {
    fun validate() {
        validateIdentity(groupId, originId)
        require(revision in 1..MAX_RECORD_NUMBER && acquiredAt in 0..MAX_RECORD_NUMBER)
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(accuracyMeters.isFinite() && accuracyMeters >= 0)
    }
}

fun shouldReplacePosition(previous: PhonePosition?, next: PhonePosition): Boolean {
    next.validate()
    if (previous == null) return true
    require(previous.groupId == next.groupId && previous.originId == next.originId)
    if (previous.revision == next.revision) require(previous == next) { "同じ更新番号に異なる位置があります" }
    return next.revision > previous.revision
}

fun positionRequestInterval(disasterActive: Boolean): Long = if (disasterActive) 60_000L else 300_000L

/** Editing a memo leaves the answer ID and its safety/rescue ordering unchanged. */
@kotlinx.serialization.Serializable data class SosMemo(
    val groupId: String, val incidentId: String, val authorId: String, val answerId: String,
    val revision: Long, val updatedAt: Long, val text: String,
) {
    fun validate() {
        validateIdentity(groupId, incidentId, authorId, answerId)
        require(revision in 1..MAX_RECORD_NUMBER && updatedAt in 0..MAX_RECORD_NUMBER)
        require(text.length <= SOS_MEMO_LIMIT) { "SOSメモは${SOS_MEMO_LIMIT}文字以内で入力してください" }
    }
}

fun shouldReplaceMemo(previous:SosMemo?,next:SosMemo):Boolean {
    next.validate()
    if(previous==null)return true
    require(previous.groupId==next.groupId && previous.answerId==next.answerId && previous.incidentId==next.incidentId && previous.authorId==next.authorId) {"SOSメモの作成者・回答は変更できません"}
    if(previous.revision==next.revision)require(previous==next) {"同じ更新番号に異なるSOSメモがあります"}
    return next.revision>previous.revision
}
