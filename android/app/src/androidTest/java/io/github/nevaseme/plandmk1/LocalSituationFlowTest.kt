package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LocalSituationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var previous: String? = null
    @After fun restore() { previous?.let { (compose.activity.application as PlanDApplication).installation.setGroup(it) } }

    @Test fun sosMemoPersistsAndEditingItKeepsTheAnswerAndRescue() {
        val app = compose.activity.application as PlanDApplication
        val group = "memo-${UUID.randomUUID().toString().take(8)}"
        val author = app.installation.localId
        previous = app.installation.group.value
        val incident = UUID.randomUUID().toString()
        runBlocking {
            app.store.updateOwn(group, author) { Resident(group, author, 1, 0, "メモ試験", 32.79, 130.81, "2階", "北の寝室") }
            app.store.mergeIncident(IncidentEvent(group, incident, incident, author, "メモ試験", 1, 1, IncidentKind.START, "訓練", "地域"))
            app.installation.selectIncident(group, incident)
        }
        app.installation.setGroup(group)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("いまの安否を教えてください").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SOS").performClick()
        compose.onNodeWithText("SOSメモ").performTextInput("北の寝室にいます")
        compose.onNodeWithText("安否を登録して地図へ").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("位置不明 1人").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("一覧").performClick()
        compose.onNodeWithText("全員 1").assertIsDisplayed()
        compose.onNodeWithText("SOS 1").performClick()
        compose.onNode(hasText("メモ試験") and hasClickAction()).assertIsDisplayed()
        val answer = runBlocking { incidentView(app.store.incidents(group), incident)!!.answers[author]!! }
        runBlocking {
            app.store.appendIncident(IncidentEvent(group, UUID.randomUUID().toString(), incident, author, "メモ試験", 1,
                answer.createdAt + 1, IncidentKind.CARE, subjectId = author, action = CareAction.RESCUED))
        }
        compose.onNodeWithText("安否").performClick()
        compose.onNodeWithText("SOSメモ").performTextReplacement("玄関で待っています")
        compose.onNodeWithText("メモだけ保存する").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { app.store.memos(group).singleOrNull()?.text == "玄関で待っています" }
        }
        // Persistence can finish before Compose observes the save feedback. Choose the
        // map explicitly so this assertion tests recreation, not incidental navigation.
        compose.onNodeWithText("地図").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("位置不明 1人").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("安否").performClick()
        compose.onNodeWithText("玄関で待っています").assertExists()
        runBlocking {
            val view = incidentView(app.store.incidents(group), incident)!!
            assertEquals(answer.eventId, view.answers[author]!!.eventId)
            assertEquals(1, view.care.size)
            assertEquals(1, app.store.incidents(group).count { it.kind == IncidentKind.ANSWER })
        }
        compose.onNodeWithText("SOSメモ").performTextReplacement("もう一度助けが必要です")
        compose.onNodeWithText("安否を更新する").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { incidentView(app.store.incidents(group), incident)!!.answers[author]!!.eventId != answer.eventId }
        }
        runBlocking {
            val latest = incidentView(app.store.incidents(group), incident)!!.answers[author]!!
            assertEquals(Safety.SOS, latest.safety)
            assertEquals("もう一度助けが必要です", app.store.memos(group).find { it.answerId == latest.eventId }!!.text)
        }
    }
}
