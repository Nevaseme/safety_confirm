package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import java.util.UUID

suspend fun seedIncident(app: PlanDApplication, group: String): String {
    val id=UUID.randomUUID().toString()
    app.store.mergeIncident(IncidentEvent(group,id,id,app.installation.localId,"確認者",1,1,IncidentKind.START,title="試験",area="地区"))
    app.installation.selectIncident(group,id)
    return id
}
suspend fun seedIncidentCare(app: PlanDApplication, incident: String, care: CareEvent) {
    app.store.appendIncident(IncidentEvent(care.groupId,care.eventId,incident,care.authorId,care.authorName,1,care.createdAt,IncidentKind.CARE,subjectId=care.subjectId,action=care.action,note=care.note,supersedes=care.supersedes))
}
suspend fun incidentCare(app: PlanDApplication, group: String, id: String) = incidentView(app.store.incidents(group),id)!!.care
