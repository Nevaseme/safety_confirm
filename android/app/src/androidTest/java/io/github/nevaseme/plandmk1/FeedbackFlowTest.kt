package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.nevaseme.plandmk1.core.Safety
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FeedbackFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun field(label: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNode(hasText(label) and hasAnyAncestor(hasScrollToIndexAction()))
    }
    @Test fun failedCorrectionDraftSurvivesActivityRecreationAndCanBeRetried() = runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val app = compose.activity.application as PlanDApplication
        // Recover the test-only failure trigger if a previous instrumented process crashed.
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS feedback_reject_events")
        val previous = app.installation.group.value
        val group = "draft-${UUID.randomUUID().toString().take(8)}"
        val person = Resident(group, app.installation.localId, 1, 1, "訂正下書き確認", 35.0, 139.0, "2階", "北", Safety.SOS)
        app.store.merge(person)
        val original = CareEvent(group, UUID.randomUUID().toString(), person.originId, person.originId, person.name, 1, CareAction.RESCUED)
        val incident=seedIncident(app,group)
        seedIncidentCare(app,incident,original)
        compose.runOnIdle { app.installation.setGroup(group) }
        try {
            compose.waitUntil(5_000) { ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit }
            compose.onNodeWithText("地図").performClick()
            compose.onNodeWithText("住民・建物の一覧").performClick()
            field(person.name + "（自分）").performClick()
            compose.onNodeWithText("この記録を訂正").performScrollTo().performClick()
            compose.onNode(hasText("要救助") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("確認内容・訂正理由").performScrollTo().performTextReplacement("失敗後も残す訂正理由")
            app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER feedback_reject_events BEFORE INSERT ON incident_events BEGIN SELECT RAISE(ABORT, 'test save failure'); END")
            compose.onNodeWithText("対応記録を保存").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("保存できませんでした", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("確認内容・訂正理由").performScrollTo().assertTextContains("失敗後も残す訂正理由")
            compose.onNodeWithText("自分の記録を訂正").performScrollTo().assertIsDisplayed()
            app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER feedback_reject_events")
            compose.onNodeWithText("対応記録を保存").performScrollTo().performClick()
            compose.waitUntil(5_000) { runBlocking { incidentCare(app,group,incident).size == 2 } }
            val correction = currentCareEvents(incidentCare(app,group,incident)).single()
            assertEquals(original.eventId, correction.supersedes)
            assertEquals(CareAction.NEEDS_HELP, correction.action)
        } finally {
            app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS feedback_reject_events")
            compose.runOnIdle { app.installation.setGroup(previous) }
        }
    }
    @Test fun failedSaveKeepsDraftAndRetrySucceedsWithoutErasingOtherDraftOnSafetyUpdate() {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("自分の情報").performClick()
        field("自分の名前").performTextReplacement("UI確認者")
        field("緯度").performTextReplacement("invalid")
        field("経度").performTextReplacement("139.0")
        field("寝る部屋・場所の説明").performTextReplacement("保存失敗でも残る部屋")
        field("登録情報を保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("保存できませんでした", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        field("寝る部屋・場所の説明").assertTextContains("保存失敗でも残る部屋")
        field("緯度").performTextReplacement("35.0")
        field("登録情報を保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("登録情報をこの端末に保存しました", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("保存できませんでした", substring = true).assertCountEquals(0)
        val app=compose.activity.application as PlanDApplication
        runBlocking { seedIncident(app,app.installation.group.value) }
        compose.waitUntil(10_000) { ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit }
        field("寝る部屋・場所の説明").performTextReplacement("未保存の下書き")
        compose.runOnIdle { ViewModelProvider(compose.activity)[PlanViewModel::class.java].setSafety(Safety.SOS) }
        compose.waitUntil(5_000) { !ViewModelProvider(compose.activity)[PlanViewModel::class.java].saving.value }
        field("寝る部屋・場所の説明").assertTextContains("未保存の下書き")
        compose.activityRule.scenario.recreate()
        field("寝る部屋・場所の説明").assertTextContains("未保存の下書き")
    }
}

