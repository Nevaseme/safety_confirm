package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.CareHistoryAndEditor
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class CareFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun consecutiveCorrectionsAdvanceTheEditorTarget() = runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>()
        val previous=app.installation.group.value
        val group="care-${UUID.randomUUID().toString().take(8)}"
        val person=Resident(group,app.installation.localId,1,1,"確認者",35.0,139.0,"2階","北",Safety.SOS)
        app.store.merge(person)
        val initial=CareEvent(group,UUID.randomUUID().toString(),person.originId,person.originId,"確認者",1,CareAction.RESCUED)
        val incident=seedIncident(app,group)
        seedIncidentCare(app,incident,initial)
        lateinit var model: PlanViewModel
        compose.runOnIdle { app.installation.setGroup(group); model=PlanViewModel(app) }
        try {
            compose.setContent {
                val events by model.careEvents.collectAsState()
                val saving by model.saving.collectAsState()
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CareHistoryAndEditor(model,person,events,true,saving) } }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("この記録を訂正").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("この記録を訂正").performScrollTo().performClick()
            compose.onNode(hasText("要救助") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("対応記録を保存").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { incidentCare(app,group,incident).size==2 } }
            compose.onNode(hasText("本人の救助完了") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("対応記録を保存").performScrollTo().performClick()
            compose.waitUntil(10_000) { runBlocking { incidentCare(app,group,incident).size==3 } }
            val current=currentCareEvents(incidentCare(app,group,incident))
            assertEquals(1,current.size);assertEquals(CareAction.RESCUED,current.single().action)
        } finally { compose.runOnIdle { app.installation.setGroup(previous) } }
    }
}
