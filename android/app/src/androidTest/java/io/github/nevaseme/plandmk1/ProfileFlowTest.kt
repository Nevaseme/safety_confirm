package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ProfileFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun field(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return compose.onNode(hasText(text) and hasAnyAncestor(hasScrollToIndexAction()))
    }

    @Test fun registersProfileChangesSafetyAndRetainsItAcrossRecreation() {
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("自分の情報").performClick()
        field("自分の名前").performTextReplacement("実験テスト住民")
        field("緯度").performTextReplacement("35.681236")
        field("経度").performTextReplacement("139.767125")
        field("階数（例：2階）").performTextReplacement("2階")
        field("寝る部屋・場所の説明").performTextReplacement("北側の寝室")
        field("登録情報を保存").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("登録情報をこの端末に保存しました", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val app=compose.activity.application as PlanDApplication
        kotlinx.coroutines.runBlocking { seedIncident(app,app.installation.group.value) }
        compose.waitUntil(10_000) { androidx.lifecycle.ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit }
        compose.onNodeWithContentDescription("戻る").performClick()
        compose.onNodeWithText("安否登録").performClick()
        field("SOSを登録").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("SOS", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        field("実験テスト住民").assertIsDisplayed()
        compose.onNodeWithText("SOS", substring = false).assertIsDisplayed()
        field("無事を登録").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("無事", substring = false).fetchSemanticsNodes().isNotEmpty() }
    }
}

