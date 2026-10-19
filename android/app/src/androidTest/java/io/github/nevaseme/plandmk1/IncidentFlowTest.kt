package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class IncidentFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun receivedSelectionPersistsAcrossAnotherStartEndAndRecreation()=runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication
        val previous=app.installation.group.value;val g="select-${UUID.randomUUID().toString().take(8)}"
        val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
        fun start(id: String)=IncidentEvent(g,id,id,app.installation.localId,"受信元",1,1,IncidentKind.START,title="受信訓練",area="地区")
        try {
            compose.runOnIdle {app.installation.setGroup(g)}
            app.store.mergeIncident(start(a))
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection==a}
            assertEquals(a,app.installation.incidentSelection(g))
            app.store.mergeIncident(start(b))
            app.store.mergeIncident(IncidentEvent(g,UUID.randomUUID().toString(),a,app.installation.localId,"受信元",2,2,IncidentKind.END))
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.options.size==2}
            assertEquals(a,ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.options.size==2}
            assertEquals(a,ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection)
        } finally {compose.runOnIdle{app.installation.setGroup(previous)}}
    }
    private fun button(text: String): SemanticsNodeInteraction {
        if(compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        else compose.onNodeWithText(text).performScrollTo()
        return compose.onNodeWithText(text)
    }
    @Test fun manualStartAnswerRecreationEndAndSecondIncidentDoNotCarryPreviousSafety()=runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication
        val oldGroup=app.installation.group.value;val group="incident-${UUID.randomUUID().toString().take(8)}"
        app.store.merge(Resident(group,app.installation.localId,1,1,"訓練者",35.0,139.0,"2階","北",Safety.SOS))
        compose.runOnIdle { app.installation.setGroup(group) }
        try {
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.isNotEmpty()}
            compose.onNodeWithText("安否登録").performClick()
            button("SOSを登録").assertIsNotEnabled()
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("災害・訓練の管理").performClick()
            button("地震発生").performClick()
            compose.onNodeWithText("対象地域").performTextReplacement("訓練地区")
            compose.onNodeWithText("地震発生を記録して開始").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit}
            compose.onNodeWithContentDescription("戻る").performClick()
            button("無事を登録").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.single().safety==Safety.SAFE}
            val first=ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection==first}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("災害・訓練の管理").performClick()
            button("この安否確認を終了").performClick();compose.onNodeWithText("終了する").performClick()
            compose.waitUntil(10000){!ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit}
            compose.onNodeWithContentDescription("戻る").performClick()
            compose.onNodeWithText("安否登録").performClick()
            button("SOSを登録").assertIsNotEnabled()
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("災害・訓練の管理").performClick()
            button("地震発生").performClick();compose.onNodeWithText("地震・訓練の名前").performTextReplacement("次の訓練")
            compose.onNodeWithText("対象地域").performTextReplacement("次の訓練地区")
            compose.onNodeWithText("地震発生を記録して開始").assertIsEnabled().performClick()
            compose.waitUntil(10000){val state=ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value;state.canEdit&&state.selection!=first}
            assertEquals(Safety.UNREPORTED,ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.single().safety)
            assertEquals(Safety.SAFE,incidentView(app.store.incidents(group),first)!!.answers[app.installation.localId]!!.safety)
            assertEquals(Safety.SOS,app.store.get(group,app.installation.localId)!!.safety)
        } finally {compose.runOnIdle { app.installation.setGroup(oldGroup) }}
    }
}

