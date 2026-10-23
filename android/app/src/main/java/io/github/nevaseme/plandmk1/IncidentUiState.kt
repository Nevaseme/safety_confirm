package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*

data class IncidentUiState(
    val groupId: String = "", val selection: String = "", val options: List<IncidentView> = emptyList(),
    val current: IncidentView? = null, val residents: List<Resident> = emptyList(), val care: List<CareEvent> = emptyList(),
) { val canEdit get() = current != null && !current.closed }

fun projectIncidentUi(group: String, profile: List<Resident>, legacyCare: List<CareEvent>, journal: List<IncidentEvent>, preferred: String?, optionsOverride:List<IncidentView>?=null): IncidentUiState {
    val options=optionsOverride?:incidentOptions(journal); val active=options.filter { !it.closed }
    val selected=if(preferred=="legacy" || options.any { it.start.eventId==preferred }) preferred.orEmpty() else if(preferred==null) active.singleOrNull()?.start?.eventId.orEmpty() else ""
    val current=options.find {it.start.eventId==selected}
    return IncidentUiState(group,selected,options,current,
        if(selected=="legacy") profile else profile.map { r -> r.copy(safety=current?.answers?.get(r.originId)?.safety ?: Safety.UNREPORTED,updatedAt=current?.answers?.get(r.originId)?.createdAt ?: 0) },
        if(selected=="legacy") legacyCare else current?.care.orEmpty())
}
