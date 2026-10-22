package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nevaseme.plandmk1.core.*

data class ResidenceGroup(val unitId: String?, val label: String, val people: List<Resident>)

@Composable fun NamedEntity(label: String, name: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.titleLarge)
    }
}

fun residentsByUnit(rows: List<PlaceEvent>, buildingId: String, residents: List<Resident>): List<ResidenceGroup> {
    val members = residents.filter { validHome(rows, it.originId)?.buildingId == buildingId }
    val groups = placeUnits(rows, buildingId).map { unit ->
        ResidenceGroup(unit.unitId, unit.label, members.filter { validHome(rows, it.originId)?.unitId == unit.unitId })
    }
    val unspecified = members.filter { validHome(rows, it.originId)?.unitId == null }
    return groups + if (unspecified.isEmpty()) emptyList() else listOf(ResidenceGroup(null, "住戸未登録", unspecified))
}

@Composable fun ResidentEntry(person: Resident, ownId: String, care: List<CareEvent> = emptyList(), onClick: () -> Unit) {
    val state = residentMapState(person, care)
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "本人の安否と住まいを見る", onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("住民の名前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((when(state) { MapSafety.SAFE -> "無事の人"; MapSafety.RESCUED -> "救助済みの人"; else -> person.displayName() }) + if(person.originId == ownId) "（自分）" else "", style = MaterialTheme.typography.titleSmall)
            Text("この人の詳細を見る", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        StateSymbol(state)
    }
}

@Composable fun BuildingPeople(rows: List<PlaceEvent>, buildingId: String, residents: List<Resident>, ownId: String, onResident: (Resident) -> Unit, care: List<CareEvent> = emptyList()) {
    val groups = residentsByUnit(rows, buildingId, residents)
    if(groups.isEmpty()) Text("住民の登録なし", style = MaterialTheme.typography.bodyMedium)
    groups.forEach { group ->
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("部屋・住戸", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
            if(group.people.isEmpty()) Text("住民の登録なし", Modifier.padding(vertical=8.dp), style=MaterialTheme.typography.bodySmall)
            group.people.sortedWith(compareBy<Resident> { if(it.safety == Safety.SOS) 0 else 1 }.thenBy { it.name }).forEach { person ->
                ResidentEntry(person, ownId, care) { onResident(person) }
            }
        }
    }
}

@Composable fun BuildingResponse(rows: List<PlaceEvent>, buildingId: String, incident: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("建物への対応", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(buildingSummary(rows, buildingId, incident), style = MaterialTheme.typography.bodyLarge)
        currentBuildingCare(buildingCare(rows, buildingId, incident)).filter {it.action != BuildingAction.REVOKED}.forEach { record ->
            Text("確認範囲：${record.scope}", style=MaterialTheme.typography.bodySmall)
            if(record.unresolved.isNotBlank() && record.unresolved != "なし") Text("未確認：${record.unresolved}", style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun HouseholdCard(building: PlaceEvent, rows: List<PlaceEvent>, incident: String, residents: List<Resident>, ownId: String, onBuilding: () -> Unit, onResident: (Resident) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuildingNameButton(building.displayLabel(), onBuilding)
            BuildingResponse(rows, building.buildingId!!, incident)
            HorizontalDivider()
            Text("この建物に登録されている人", style=MaterialTheme.typography.labelLarge)
            BuildingPeople(rows, building.buildingId, residents, ownId, onResident)
        }
    }
}

@Composable fun BuildingNameButton(name: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "建物の詳細を見る", onClick = onClick).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NamedEntity("建物名", name)
        Text("建物の詳細を見る", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
