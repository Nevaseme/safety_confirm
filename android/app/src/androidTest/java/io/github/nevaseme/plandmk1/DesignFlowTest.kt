package io.github.nevaseme.plandmk1

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.nevaseme.plandmk1.ui.DesignDemoScreen
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class DesignFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun answerAndMap() {
        compose.setContent { PlanDMk1Theme { DesignDemoScreen {} } }
        compose.onNodeWithText("地震発生を報告").performClick()
        compose.onNodeWithText("報告 1人 / 必要 2人").assertIsDisplayed()
        compose.onNodeWithText("もう1人の報告を受信（模擬）").performClick()
        compose.onNodeWithText("いまの安否を教えてください").assertIsDisplayed()
        compose.onNodeWithText("安否を登録して地図へ").assertIsNotEnabled()
        compose.onNodeWithText("無事").performClick()
        compose.onNodeWithText("安否を登録して地図へ").performClick()
        compose.onNodeWithText("位置不明 2人").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
    }
    private fun list() {
        compose.onNodeWithContentDescription("デモのメニュー").performClick()
        compose.onNodeWithText("住民一覧").performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "design-$name.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun firstAnswerUnknownListAndCompactLegend() {
        answerAndMap()
        capture("map")
        compose.onNodeWithText("位置不明 2人").performClick()
        compose.onNodeWithText("森 久美子").assertIsDisplayed()
        capture("unknown")
        compose.onNodeWithText("SOS").assertDoesNotExist()
        compose.onNodeWithContentDescription("詳細を閉じる").performClick()
        compose.onNodeWithText("凡例").performClick()
        capture("legend-after-click")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("建物の強さ").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("建物の強さ").assertExists()
    }
    @Test fun rescueHidesNameAndCorrectionRestoresSOS() {
        answerAndMap(); list()
        compose.onNodeWithText("山田 太郎").performClick()
        compose.onNodeWithText("普段の住居・寝室").assertExists()
        capture("sos-detail")
        compose.onNodeWithText("救助済みにする").performScrollTo().performClick()
        compose.onNodeWithText("救助済みの人").assertIsDisplayed()
        compose.onNodeWithText("山田 太郎").assertDoesNotExist()
        compose.onNodeWithText("救助済みの記録を訂正").performScrollTo().performClick()
        compose.onNodeWithText("山田 太郎").assertExists()
    }
    @Test fun changingOwnAnswerSupportsSOSMemo() {
        answerAndMap()
        compose.onNodeWithText("安否").performClick()
        compose.onNodeWithText("SOS").performClick()
        compose.onNodeWithText("SOSメモ").performTextInput("玄関前で待っています")
        compose.onNodeWithText("安否を更新する").performScrollTo().performClick()
        compose.onNodeWithText("位置不明 2人").assertIsDisplayed()
        compose.onNodeWithText("安否").performClick()
        compose.onNodeWithText("SOSメモ").assertTextContains("玄関前で待っています")
    }
    @Test fun mapRendersBuildingsAndSplitsClustersAtTheIndividualZoom() {
        answerAndMap()
        var map: org.maplibre.android.maps.MapLibreMap? = null
        fun find(view: android.view.View): org.maplibre.android.maps.MapView? {
            if (view is org.maplibre.android.maps.MapView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            find(activity.window.decorView)!!.getMapAsync { map = it }
        }
        compose.waitUntil(30_000) {
            var loaded = false
            compose.runOnIdle {
                val features = map?.queryRenderedFeatures(android.graphics.RectF(0f, 0f, 10000f, 10000f), "design-ratings").orEmpty()
                loaded = features.size >= 3 && features.mapNotNull { it.id()?.toDoubleOrNull()?.let { id -> kotlin.math.floor(id / 10).toLong() % 4 } }.toSet().size >= 3
            }
            loaded
        }
        compose.runOnIdle {
            org.junit.Assert.assertNull("No copied building source", map!!.style!!.getSource("design-ratings"))
            val layers = map!!.style!!.layers.map { it.id }
            org.junit.Assert.assertTrue("Rating layer must cover the base buildings: $layers", layers.indexOf("design-ratings") > layers.indexOf("building"))
        }
        compose.runOnIdle { map!!.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(14.0)) }
        compose.waitUntil(10_000) { var clustered = false; compose.runOnIdle { clustered = map!!.markers.any { it.title?.contains("SOS3人") == true } }; clustered }
        var clusterIds = emptySet<Long>()
        compose.runOnIdle { clusterIds = map!!.markers.map { it.id }.toSet() }
        compose.runOnIdle { map!!.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(16.0)) }
        compose.waitUntil(10_000) { var single = false; compose.runOnIdle { single = map!!.markers.size == 24 && map!!.markers.none { it.title?.contains("SOS3人") == true } }; single }
        var individualIds = emptySet<Long>()
        compose.runOnIdle {
            individualIds = map!!.markers.map { it.id }.toSet()
            org.junit.Assert.assertTrue("Splitting must keep existing markers", individualIds.intersect(clusterIds).isNotEmpty())
            map!!.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(16.3))
        }
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertEquals("Zoom without membership changes must preserve markers", individualIds, map!!.markers.map { it.id }.toSet()) }
        capture("loaded-map")
    }
}
