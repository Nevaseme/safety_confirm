package io.github.nevaseme.plandmk1
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import java.util.UUID
import java.io.File
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
class BuildingMapTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun views(v:View):List<MapView> = if(v is MapView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {views(v.getChildAt(it))}else emptyList()
    @Suppress("DEPRECATION")
    @Test fun mapHasDistinctBuildingMarkerAndChangesWithIncident()=runBlocking<Unit> {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val app=compose.activity.application as PlanDApplication;val old=app.installation.group.value;val g="map-${UUID.randomUUID().toString().take(8)}";val id=app.installation.localId;val house=UUID.randomUUID().toString();val incident=UUID.randomUUID().toString()
        app.store.merge(Resident(g,id,1,1,"地図住民",35.681236,139.767125,"2階","北"))
        val start=IncidentEvent(g,incident,incident,id,"地図住民",1,1,IncidentKind.START,title="地図訓練",area="地区");app.store.mergeIncident(start)
        app.store.appendIncident(IncidentEvent(g,UUID.randomUUID().toString(),incident,id,"地図住民",1,2,IncidentKind.ANSWER,safety=Safety.SOS))
        app.store.appendPlace(PlaceEvent(g,house,id,"地図住民",1,1,PlaceKind.BUILDING,buildingId=house,label="地図住宅",latitude=35.682,longitude=139.768))
        app.store.appendPlace(PlaceEvent(g,UUID.randomUUID().toString(),id,"地図住民",1,2,PlaceKind.CARE,buildingId=house,incidentId=incident,action=BuildingAction.COMPLETED,scope="1階のみ",unresolved="2階未確認"))
        compose.runOnIdle{app.installation.setGroup(g);app.installation.selectIncident(g,incident)}
        try {
            compose.onNodeWithText("地図",useUnmergedTree=true).performClick()
            var map:MapLibreMap?=null;val frames=java.util.concurrent.atomic.AtomicInteger()
            compose.waitUntil(15000){var found=false;compose.runOnIdle{views(compose.activity.window.decorView).firstOrNull()?.let {found=true;it.addOnDidFinishRenderingFrameListener {fully,_,_->if(fully)frames.incrementAndGet()};it.getMapAsync {m->map=m}}};found}
            compose.waitUntil(20000){var found=false;compose.runOnIdle{found=map?.markers?.any {it.title=="住民の名前：地図住民"&&it.snippet?.contains("SOS")==true}==true};found}
            compose.runOnIdle{assertEquals(1,map!!.markers.size)}
            compose.onNodeWithText("建物への対応").performClick()
            compose.waitUntil(10000){var found=false;compose.runOnIdle{found=map?.markers?.singleOrNull()?.let {it.title=="建物名：地図住宅"&&it.snippet?.contains("対応完了の記録")==true}==true};found}
            val before=frames.get();compose.waitUntil(20000){frames.get()>before}
            var rendered:Bitmap?=null
            compose.runOnIdle {map!!.snapshot {rendered=it}}
            compose.waitUntil(20000){rendered!=null}
            val pixels=IntArray(rendered!!.width*rendered!!.height);rendered!!.getPixels(pixels,0,rendered!!.width,0,0,rendered!!.width,rendered!!.height)
            File(app.getExternalFilesDir(null),"building-map-render.png").outputStream().use {rendered!!.compress(Bitmap.CompressFormat.PNG,100,it)};rendered!!.recycle()
            assertTrue("Building icon stays neutral blue",pixels.count {it==0xFF244F69.toInt()}>10)
            assertEquals("People are hidden in building mode",0,pixels.count {it==0xFFB3261E.toInt()})
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(app.getExternalFilesDir(null),"building-map.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val next=UUID.randomUUID().toString();app.store.mergeIncident(start.copy(eventId=next,incidentId=next,title="次の地図訓練"))
            compose.runOnIdle{app.installation.selectIncident(g,next)}
            compose.waitUntil(10000){var cleared=false;compose.runOnIdle{cleared=map?.markers?.any {it.title=="建物名：地図住宅"&&it.snippet=="建物の対応記録なし"}==true};cleared}
            compose.onNodeWithText("人の安否").performClick()
            compose.waitUntil(10000){var onlyPerson=false;compose.runOnIdle{onlyPerson=map?.markers?.singleOrNull()?.title=="住民の名前：地図住民"};onlyPerson}
        } finally {compose.runOnIdle{app.installation.setGroup(old)}}
    }
}
