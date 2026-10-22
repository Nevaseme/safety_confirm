package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FirstAnswerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var previous: String? = null
    @After fun restoreGroup() { previous?.let { (compose.activity.application as PlanDApplication).installation.setGroup(it) } }
    @Test fun persistedAnswerOpensMapAfterActivityRecreation() {
        val app = compose.activity.application as PlanDApplication
        val g = "gate-${UUID.randomUUID().toString().take(8)}"
        previous = app.installation.group.value
        runBlocking {
            app.store.updateOwn(g, app.installation.localId) { Resident(g, app.installation.localId, 1, 0, "回答試験", 32.79, 130.819, "1階", "北側の寝室") }
            seedIncident(app, g)
        }
        app.installation.setGroup(g)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("いまの安否を教えてください").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("無事を表示").assertDoesNotExist()
        compose.onNodeWithText("無事").performClick()
        compose.onNodeWithText("安否を登録して地図へ").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("位置不明 1人").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("位置不明 1人").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("いまの安否を教えてください").assertDoesNotExist()
        compose.onNodeWithText("安否").performClick()
        compose.onNodeWithText("自分の安否").assertIsDisplayed()
    }
}
