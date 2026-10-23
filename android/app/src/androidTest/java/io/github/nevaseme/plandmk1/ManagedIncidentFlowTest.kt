package io.github.nevaseme.plandmk1

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ManagedIncidentFlowTest {
    @Test fun existingActionsUseQuorumAndAdminEndWithoutChangingLayout()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val original=app.installation.group.value
        val group="flow-${UUID.randomUUID().toString().take(8)}";val me=app.installation.localId;val admin=UUID.randomUUID().toString()
        val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"確認班",MemberRole.MEMBER,listOf(me)),GroupMember(admin,"管理者",MemberRole.ADMIN,listOf(admin))))
        app.quakes.importRoster(roster.encode(),me)
        app.store.updateOwn(group,me) {Resident(group,me,1,0,"試験",35.0,139.0,"2階","北")}
        val first=QuakeEvent(group,UUID.randomUUID().toString(),admin,System.currentTimeMillis()-1000,QuakeAction.REPORT,false,title="地震",area="地域")
        app.quakes.command(first)
        val models=ViewModelStore();lateinit var model:PlanViewModel
        fun main(action:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        suspend fun until(check:suspend()->Boolean){withTimeout(10_000){while(!check())delay(20)}}
        try {
            main {app.installation.setGroup(group);model=PlanViewModel(app);models.put("trial",model)}
            until {model.roster.value!=null}
            main {model.startIncident("地震","地域",false)}
            until {model.incidentUi.value.current!=null&&!model.saving.value}
            assertFalse(model.incidentUi.value.current!!.closed)
            assertFalse(model.canEndIncident(admin))
            main {model.setSafety(Safety.SOS,"北の寝室")}
            until {app.store.memos(group).isNotEmpty()&&!model.saving.value}
            assertEquals("北の寝室",app.store.memos(group).single().text)
            main {model.endIncident()}
            until {!model.saving.value}
            assertFalse(app.quakes.states(group).single().closed)
            app.quakes.command(QuakeEvent(group,UUID.randomUUID().toString(),admin,System.currentTimeMillis(),QuakeAction.END,false,targetId=first.eventId))
            until {model.incidentUi.value.current?.closed==true}
            val before=app.store.incidents(group).size
            main {model.setSafety(Safety.SAFE)}
            until {!model.saving.value}
            assertEquals(before,app.store.incidents(group).size)
        }finally{main {models.clear();app.installation.setGroup(original)}}
    }
}
