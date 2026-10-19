package io.github.nevaseme.plandmk1

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.util.UUID

class NavigationPresentationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String){compose.waitForIdle();android.os.SystemClock.sleep(750);val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(compose.activity.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun safetyRegistrationAndFieldDetailStaySeparate()=runBlocking<Unit>{
        Assert.assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication;val previous=app.installation.group.value;val g="nav-${UUID.randomUUID().toString().take(8)}";val me=app.installation.localId;val house=UUID.randomUUID().toString()
        app.store.merge(Resident(g,me,1,1,"田中 はる",35.681236,139.767125,"2階","北側の寝室"))
        val second=UUID.randomUUID().toString();val u1=UUID.randomUUID().toString();val u2=UUID.randomUUID().toString()
        app.store.merge(Resident(g,second,1,1,"佐藤 みどり",35.682,139.768,"2階","南側の和室"))
        val incident=seedIncident(app,g)
        app.store.appendPlace(PlaceEvent(g,house,me,"田中 はる",1,1,PlaceKind.BUILDING,buildingId=house,label="ひかり住宅",latitude=35.681236,longitude=139.767125))
        app.store.appendPlace(PlaceEvent(g,u1,me,"田中 はる",1,2,PlaceKind.UNIT,buildingId=house,unitId=u1,label="201号室"))
        app.store.appendPlace(PlaceEvent(g,u2,me,"田中 はる",1,2,PlaceKind.UNIT,buildingId=house,unitId=u2,label="202号室"))
        app.store.appendPlace(PlaceEvent(g,UUID.randomUUID().toString(),me,"田中 はる",1,2,PlaceKind.HOME,buildingId=house,unitId=u1))
        app.store.appendPlace(PlaceEvent(g,UUID.randomUUID().toString(),second,"佐藤 みどり",1,2,PlaceKind.HOME,buildingId=house,unitId=u2))
        app.store.appendPlace(PlaceEvent(g,UUID.randomUUID().toString(),me,"田中 はる",1,3,PlaceKind.CARE,buildingId=house,incidentId=incident,action=BuildingAction.COMPLETED,scope="1階共用部",unresolved="2階未確認"))
        app.store.appendIncident(IncidentEvent(g,UUID.randomUUID().toString(),incident,second,"佐藤 みどり",1,3,IncidentKind.ANSWER,safety=Safety.SAFE))
        compose.runOnIdle{app.installation.setGroup(g);app.installation.selectIncident(g,incident)}
        try{
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.canEdit}
            compose.onNodeWithText("安否登録").performClick()
            compose.onNodeWithText("無事を登録").assertIsDisplayed()
            compose.onNodeWithText("SOSを登録").assertIsDisplayed().performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.any {it.originId==me&&it.safety==Safety.SOS}}
            compose.waitUntil(10000){compose.onAllNodesWithText("SOS").fetchSemanticsNodes().isNotEmpty()}
            capture("navigation-safety.png")
            compose.onNodeWithContentDescription("メニューを開く").performClick();capture("navigation-menu.png")
            compose.onNodeWithContentDescription("メニューを閉じる").performClick()
            compose.onNodeWithText("地図").performClick()
            compose.onNodeWithText("住民・建物の一覧").performClick()
            compose.onNodeWithText("建物名").assertExists()
            compose.onAllNodesWithText("住民の名前").assertCountEquals(2)
            compose.onAllNodesWithText("部屋・住戸").assertCountEquals(2)
            compose.onNodeWithText("201号室").assertExists()
            compose.onNodeWithText("202号室").assertExists()
            compose.onNodeWithText("田中 はる（自分）").assertExists()
            compose.onNodeWithText("佐藤 みどり").assertExists()
            compose.onNodeWithText("SOS").assertExists()
            compose.onNodeWithText("無事").assertExists()
            compose.onNodeWithText("対応完了の記録").assertExists()
            capture("relationships-list.png")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("ひかり住宅"))
            compose.onNodeWithText("ひかり住宅").performClick()
            compose.onNodeWithText("この建物を自分の住居に設定（住戸未指定）").assertDoesNotExist()
            compose.onNodeWithText("建物情報を訂正").assertDoesNotExist()
            compose.onNodeWithText("建物への対応履歴").assertExists()
            capture("navigation-building-detail.png")
            compose.onNodeWithText("田中 はる（自分）").performScrollTo().performClick()
            compose.onNodeWithText("本人の安否").assertExists()
            compose.onNodeWithText("SOS").assertExists()
            compose.onNodeWithText("住民の名前（自分）").assertExists()
            compose.onNodeWithText("建物名").assertExists()
            compose.onNodeWithText("部屋・住戸：201号室").assertExists()
            capture("relationships-person.png")
            compose.onNodeWithText("建物の詳細を見る").performScrollTo().performClick()
            compose.onNodeWithText("この建物に登録されている人").assertExists()
            Assert.assertEquals(Safety.SOS,ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.single {it.originId==me}.safety)
        }finally{compose.runOnIdle{app.installation.setGroup(previous)}}
    }
}
