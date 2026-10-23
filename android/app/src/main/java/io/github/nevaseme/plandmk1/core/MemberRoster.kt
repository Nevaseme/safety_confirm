package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val recordIdPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal const val MAX_SEQUENCE = 9_007_199_254_740_991L
@Serializable enum class MemberRole { MEMBER, LEADER, ADMIN }
@Serializable data class GroupMember(val memberId:String, val activityName:String, val role:MemberRole, val deviceIds:List<String>)
@Serializable data class MemberRoster(val version:Int=1, val groupId:String, val members:List<GroupMember>) {
    fun validate() {
        require(version==1 && groupId.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "参加者設定の形式が不正です" }
        require(members.isNotEmpty() && members.any { it.role==MemberRole.ADMIN }) { "参加者と管理者が必要です" }
        require(members.map {it.memberId}.distinct().size==members.size) { "同じ参加者が重複しています" }
        val devices=members.flatMap {it.deviceIds}
        require(devices.distinct().size==devices.size) { "端末が複数の参加者に登録されています" }
        members.forEach {
            require(recordIdPattern.matches(it.memberId) && it.deviceIds.isNotEmpty() && it.deviceIds.all(recordIdPattern::matches))
            require(it.activityName.isNotBlank() && it.activityName.length<=60)
        }
    }
    fun memberForDevice(device:String):GroupMember = requireNotNull(members.find {device in it.deviceIds}) { "参加者に登録されていない端末です" }
    fun canCoordinate(device:String) = memberForDevice(device).role in setOf(MemberRole.LEADER,MemberRole.ADMIN)
    fun encode():String {validate();return Json.encodeToString(this)}
    companion object { fun decode(text:String)=Json.decodeFromString<MemberRoster>(text).also {it.validate()} }
}
