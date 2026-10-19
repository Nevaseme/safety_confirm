package io.github.nevaseme.plandmk1.ui

import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.core.PlaceEvent
import io.github.nevaseme.plandmk1.core.PlaceKind

// Presentation aliases for the original lab fixture only. IDs, received records,
// revisions and historical records remain unchanged; other groups are untouched.
fun samplePersonName(group: String, name: String): String {
    if (group != "lab-1007") return name
    return when (name.lowercase()) {
        "drill-a" -> "山田 太郎"
        "訓練b" -> "佐藤 花子"
        "訓練c・確認担当" -> "鈴木 一郎（確認担当）"
        else -> name
    }
}

fun Resident.displayName() = samplePersonName(groupId, name)

fun PlaceEvent.displayLabel(): String =
    if (groupId == "lab-1007" && kind == PlaceKind.BUILDING && label.equals("Drill-House", ignoreCase = true)) "さくらハイツ" else label

fun sampleIncidentTitle(group: String, title: String): String =
    if (group == "lab-1007" && title == "Building-1008") "建物の確認訓練" else title

fun sampleAreaName(group: String, area: String): String =
    if (group == "lab-1007" && area == "Lab-Area") "訓練エリア" else area
