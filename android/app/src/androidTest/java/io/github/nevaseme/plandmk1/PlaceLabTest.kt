package io.github.nevaseme.plandmk1
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
class PlaceLabTest {
    @Test fun nativeBuildingAndHomeReachJsAndScopedCareReturns()=runBlocking<Unit> {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
        val url=args.getString("labServer");val token=args.getString("labToken");val g=args.getString("placeGroup");assumeTrue(url!=null&&token!=null&&g!=null)
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val group=g!!;val person=app.installation.localId;val incident=UUID.randomUUID().toString();val house=UUID.randomUUID().toString()
        app.store.merge(Resident(group,person,1,1,"Android住民A",35.0,139.0,"2階","北"))
        app.store.mergeIncident(IncidentEvent(group,incident,incident,person,"Android住民A",1,1,IncidentKind.START,title="建物通信",area="地区"))
        app.store.appendIncident(IncidentEvent(group,UUID.randomUUID().toString(),incident,person,"Android住民A",1,2,IncidentKind.ANSWER,safety=Safety.SOS))
        app.store.appendPlace(PlaceEvent(group,house,person,"Android住民A",1,1,PlaceKind.BUILDING,buildingId=house,label="相互運用住宅",latitude=35.0,longitude=139.0))
        app.store.appendPlace(PlaceEvent(group,UUID.randomUUID().toString(),person,"Android住民A",1,2,PlaceKind.HOME,buildingId=house))
        if(Build.VERSION.SDK_INT>=37)instrumentation.uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.ACCESS_LOCAL_NETWORK").use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        try {
            instrumentation.runOnMainSync{app.lab.setVisible(true);app.lab.start(url!!,token!!,group)}
            val care=withTimeoutOrNull(30000){while(true){val found=buildingCare(app.store.places(group),house,incident);if(found.isNotEmpty())return@withTimeoutOrNull found.single();delay(50)};error("unreachable")}
            assertNotNull(app.lab.state.value.toString(),care);assertEquals("北玄関",care!!.scope);assertEquals("2階は未確認",care.unresolved);assertEquals(BuildingAction.CHECKING,care.action)
            assertEquals(house,validHome(app.store.places(group),person)!!.buildingId);assertEquals(Safety.SOS,incidentView(app.store.incidents(group),incident)!!.answers[person]!!.safety)
            assertTrue(app.lab.state.value.receipts>0)
        } finally {instrumentation.runOnMainSync{app.lab.stop();app.lab.setVisible(false)}}
    }
}
