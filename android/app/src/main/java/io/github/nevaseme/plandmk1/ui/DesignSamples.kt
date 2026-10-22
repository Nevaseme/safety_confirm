package io.github.nevaseme.plandmk1.ui

import android.content.Context
import io.github.nevaseme.plandmk1.MapPoint
import kotlinx.serialization.json.*

data class DesignSamples(val people: List<MapPerson>, val homes: List<MapHome>)

fun loadDesignSamples(context: Context, now: Long): DesignSamples {
    val root = Json.parseToJsonElement(context.assets.open("design-sample.json").bufferedReader().use { it.readText() }).jsonObject
    val reference = root.getValue("reference").jsonPrimitive.long
    fun JsonObject.text(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    fun JsonObject.point() = get("location")?.takeUnless { it is JsonNull }?.jsonArray?.let { MapPoint(it[1].jsonPrimitive.double, it[0].jsonPrimitive.double) }
    val homes = root.getValue("homes").jsonArray.map { item ->
        val h = item.jsonObject
        MapHome(h.text("id"), h.text("name"), h.point()!!, h["built"]!!.jsonPrimitive.int,
            h.text("material"), h["floors"]!!.jsonPrimitive.int, h["rating"]!!.jsonPrimitive.int)
    }
    val people = root.getValue("people").jsonArray.map { item ->
        val p = item.jsonObject
        val time = p["acquiredAt"]?.jsonPrimitive?.longOrNull?.let { now - (reference - it) }
        MapPerson(p.text("id"), p.text("name").ifBlank { if (p.text("id") == "p5") "自分" else "" }, when (p.text("s")) {
            "safe" -> MapSafety.SAFE; "rescued" -> MapSafety.RESCUED; "sos" -> MapSafety.SOS; else -> MapSafety.UNANSWERED
        }, p.point(), time, p["accuracy"]?.jsonPrimitive?.intOrNull,
            homes.find { it.id == p.text("homeId") }, p.text("bedroom"), p.text("memo"),
            if (p["impact"]?.jsonPrimitive?.booleanOrNull == true) now - 28 * 60_000 else null,
            if (p["enclosed"]?.jsonPrimitive?.booleanOrNull == true) now - 27 * 60_000 else null)
    }
    return DesignSamples(people, homes)
}
