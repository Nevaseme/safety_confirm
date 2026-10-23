package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable

@Serializable
enum class Safety { UNREPORTED, SAFE, SOS }

@Serializable
data class Resident(
    val groupId: String,
    val originId: String,
    val revision: Long,
    val updatedAt: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val floor: String,
    val room: String,
    val safety: Safety = Safety.UNREPORTED,
) {
    fun validate() {
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "グループは半角英数字・ハイフン・_で40文字以内です" }
        require(originId.matches(Regex("[0-9a-fA-F-]{36}"))) { "端末IDが不正です" }
        require(revision in 1..9_007_199_254_740_991L && updatedAt in 0..9_007_199_254_740_991L) { "更新情報が不正です" }
        require(name.isNotBlank() && name.length <= 60) { "表示名は1〜60文字で入力してください" }
        require(latitude.isFinite() && latitude in -85.0..85.0) { "緯度は-85〜85で入力してください" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "経度は-180〜180で入力してください" }
        require(floor.length <= 40 && room.length <= 240) { "階数は40文字、部屋は240文字以内です" }
    }
}

interface RecordStore {
    suspend fun quakeEvents(groupId:String):List<QuakeEvent> = emptyList()
    suspend fun responseEvents(groupId:String):List<ResponseEvent> = emptyList()
    suspend fun mergeQuake(event:QuakeEvent):Boolean = error("災害報告を保存できません")
    suspend fun mergeResponse(event:ResponseEvent):Boolean = error("対応記録を保存できません")
    suspend fun positions(groupId:String):List<PhonePosition> = emptyList()
    suspend fun memos(groupId:String):List<SosMemo> = emptyList()
    suspend fun savePosition(position:PhonePosition):Boolean = error("位置を保存できません")
    suspend fun mergeMemo(memo:SosMemo):Boolean = error("SOSメモを保存できません")
    suspend fun places(groupId: String): List<PlaceEvent> = emptyList()
    suspend fun getPlace(groupId: String,eventId: String): PlaceEvent? = null
    suspend fun mergePlace(event: PlaceEvent): Boolean = error("建物記録を保存できません")
    suspend fun incidents(groupId: String): List<IncidentEvent> = emptyList()
    suspend fun getIncident(groupId: String, eventId: String): IncidentEvent? = null
    suspend fun mergeIncident(event: IncidentEvent): Boolean = error("災害記録を保存できません")
    suspend fun events(groupId: String): List<CareEvent> = emptyList()
    suspend fun getEvent(groupId: String, eventId: String): CareEvent? = null
    suspend fun mergeEvent(event: CareEvent): Boolean = error("救助記録を保存できません")
    suspend fun all(groupId: String): List<Resident>
    suspend fun get(groupId: String, originId: String): Resident?
    suspend fun merge(record: Resident): Boolean
}
