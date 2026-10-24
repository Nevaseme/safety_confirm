package io.github.nevaseme.plandmk1.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class RegionDefinition(val id:String,val name:String,val parentId:String?=null)
@Serializable data class RegionCatalog(val version:Int=1,val regions:List<RegionDefinition>) {
    fun validate() {
        require(version==1 && regions.map {it.id}.distinct().size==regions.size) {"地域設定の形式が不正です"}
        val byId=regions.associateBy {it.id}
        regions.forEach {region->
            require(region.id.matches(Regex("[A-Za-z0-9_-]{1,80}"))&&region.name.isNotBlank()&&region.name.length<=120)
            val visited=mutableSetOf<String>();var next:String?=region.id
            while(next!=null) {
                require(visited.add(next)) {"地域の所属が循環しています"}
                next=requireNotNull(byId[next]) {"所属先の地域が見つかりません"}.parentId
            }
        }
    }
    fun encode():String {validate();return Json.encodeToString(this)}
    companion object {fun decode(text:String)=Json.decodeFromString<RegionCatalog>(text).also {it.validate()}}
}
data class RegionCase(val target:ResponseTarget,val regionIds:List<String>)
data class RegionCount(val regionId:String,val name:String,val targets:Set<ResponseTarget>) {val count:Int get()=targets.size}
data class RegionalSummary(val regions:List<RegionCount>,val unassigned:Set<ResponseTarget>)

/** Counts selected cases once per region and exposes the original IDs as supporting detail. */
fun regionalSummary(catalog:RegionCatalog,cases:List<RegionCase>):RegionalSummary {
    catalog.validate()
    val byId=catalog.regions.associateBy {it.id}
    val counts=catalog.regions.associate {it.id to linkedSetOf<ResponseTarget>()}
    val unassigned=linkedSetOf<ResponseTarget>()
    cases.groupBy {it.target}.forEach {(target,entries)->
        require(recordIdPattern.matches(target.id))
        val regions=entries.flatMap {it.regionIds}.toSet()
        if(regions.isEmpty())unassigned+=target
        regions.forEach {id->
            var next:String?=id
            while(next!=null) {
                val region=requireNotNull(byId[next]) {"案件の地域が見つかりません"}
                counts.getValue(region.id)+=target;next=region.parentId
            }
        }
    }
    return RegionalSummary(catalog.regions.map {RegionCount(it.id,it.name,counts.getValue(it.id))},unassigned)
}
