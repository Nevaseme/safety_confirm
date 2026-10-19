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

class BuildingFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun button(text: String)=compose.onNodeWithText(text).performScrollTo()
    @Test fun registerLinkScopeCorrectAndNewIncidentKeepsBuildingButNotCare()=runBlocking<Unit> {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication;val old=app.installation.group.value;val g="house-${UUID.randomUUID().toString().take(8)}"
        val me=app.installation.localId;val other=UUID.randomUUID().toString()
        app.store.merge(Resident(g,me,1,1,"住民A",35.0,139.0,"2階","北",Safety.SOS))
        app.store.merge(Resident(g,other,1,1,"住民B",35.0,139.0,"2階","南"))
        val start=UUID.randomUUID().toString();app.store.mergeIncident(IncidentEvent(g,start,start,me,"住民A",1,1,IncidentKind.START,title="建物訓練",area="地区"))
        compose.runOnIdle{app.installation.setGroup(g);app.installation.selectIncident(g,start)}
        try {
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].residents.value.size==2}
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("自分の建物・住戸").performClick()
            compose.onNodeWithText("建物を登録").performClick();compose.onNodeWithText("建物名").performTextReplacement("訓練住宅")
            compose.onNodeWithText("建物情報を保存").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value.any {it.kind==PlaceKind.BUILDING}}
            compose.onNodeWithText("訓練住宅").performClick();button("住戸を追加").performClick()
            compose.onNodeWithText("住戸名（例：201号室・戸建て）").performTextReplacement("201号室");compose.onNodeWithText("住戸を保存").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value.any {it.kind==PlaceKind.UNIT}}
            button("201号室 の住戸名を訂正").performClick()
            compose.onNodeWithText("住戸名（例：201号室・戸建て）").performTextReplacement("202号室");compose.onNodeWithText("住戸を保存").performClick()
            button("202号室 を自分の住戸に設定").performClick()
            compose.waitUntil(10000){homeFor(ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value,me)!=null}
            val rows=app.store.places(g);val home=homeFor(rows,me)!!;val building=home.buildingId!!
            app.store.appendPlace(PlaceEvent(g,UUID.randomUUID().toString(),other,"住民B",1,1,PlaceKind.HOME,buildingId=building,unitId=home.unitId))
            button("対応完了の記録").performClick()
            compose.onNodeWithText("確認した範囲（必須）").performScrollTo().performTextReplacement("1階共用部")
            compose.onNodeWithText("未確認事項（ない場合は「なし」）").performScrollTo().performTextReplacement("2階は未確認")
            button("建物の対応記録を保存").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value.any {it.kind==PlaceKind.CARE}}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value.any {it.kind==PlaceKind.CARE}}
            button("この建物記録を訂正").performClick();button("再確認が必要").performClick();button("建物の対応記録を保存").performClick()
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].places.value.count {it.kind==PlaceKind.CARE}==2}
            assertEquals(BuildingAction.RECHECK,currentBuildingCare(buildingCare(app.store.places(g),building,start)).single().action)
            assertEquals(2,listOf(me,other).count {validHome(app.store.places(g),it)?.unitId==home.unitId})
            assertEquals(Safety.SOS,app.store.get(g,me)!!.safety)
            compose.onNodeWithText("閉じる").performClick()
            val next=UUID.randomUUID().toString();app.store.mergeIncident(IncidentEvent(g,next,next,me,"住民A",1,2,IncidentKind.START,title="次",area="地区"))
            compose.runOnIdle{app.installation.selectIncident(g,next)}
            compose.waitUntil(10000){ViewModelProvider(compose.activity)[PlanViewModel::class.java].incidentUi.value.selection==next}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("建物の対応記録なし"))
            compose.onNodeWithText("建物の対応記録なし").assertIsDisplayed()
        }finally{compose.runOnIdle{app.installation.setGroup(old)}}
    }
}

