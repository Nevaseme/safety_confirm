package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test

class NavigationFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun menu(label:String){compose.onNodeWithContentDescription("メニューを開く").performClick();compose.onNodeWithText(label).performClick()}
    @Test fun drawerBackAndNotificationReturnToMain() {
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("メニュー").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("住民・建物の一覧").assertIsDisplayed()
        compose.onNodeWithText("メニュー").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.runOnIdle { androidx.lifecycle.ViewModelProvider(compose.activity)[PlanViewModel::class.java].openSafety.value = true }
        compose.onNodeWithText("自分の安否を知らせる").assertIsDisplayed()
        compose.onNodeWithText("メニュー").assertIsNotDisplayed()
    }
    @Test fun pickedLocationIsConsumedBeforeManualRefinement() {
        menu("自分の情報")
        compose.runOnIdle { androidx.lifecycle.ViewModelProvider(compose.activity)[PlanViewModel::class.java].pickedPoint.value = MapPoint(35.1,139.1) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("緯度"))
        compose.onNodeWithText("緯度").assertTextContains("35.100000").performTextReplacement("35.2")
        compose.onNodeWithContentDescription("戻る").performClick()
        menu("自分の情報")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("緯度"))
        compose.onNodeWithText("緯度").assertTextContains("35.2")
    }
    @Test fun mapFirstMenuRegistrationAndBackPreserveDraft() {
        compose.onNodeWithText("住民・建物の一覧").assertIsDisplayed()
        compose.onNodeWithText("設定").assertDoesNotExist()
        compose.onNodeWithText("建物を登録").assertDoesNotExist()
        compose.onNodeWithText("安否登録").performClick()
        compose.onNodeWithText("自分の安否を知らせる").assertIsDisplayed()
        compose.onNodeWithText("共有開始").assertDoesNotExist()
        menu("自分の情報")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("自分の名前"))
        compose.onNode(hasText("自分の名前") and hasSetTextAction()).performTextReplacement("残る下書き")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("地図から位置を選ぶ"))
        compose.onNodeWithText("地図から位置を選ぶ").performClick()
        compose.onNodeWithText("位置選択をやめる").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("自分の名前"))
        compose.onNode(hasText("自分の名前") and hasSetTextAction()).assertTextContains("残る下書き")
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("自分の名前") and hasSetTextAction()).assertTextContains("残る下書き")
        compose.onNodeWithContentDescription("戻る").performClick()
        compose.onNodeWithText("自分の安否を知らせる").assertIsDisplayed()
        menu("自分の建物・住戸")
        compose.onNodeWithText("建物を登録").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("安否登録").assertIsSelected()
        menu("共有・接続")
        compose.onNodeWithText("近くの端末と共有").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("地図").performClick()
        compose.onNodeWithText("住民・建物の一覧").performClick()
        compose.onNodeWithText("建物と住民").assertExists()
    }
}
