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

class IncidentLabTest {
    @Test fun nativeStartAndAnswerReachJsAndCareReturnsAfterSave()=runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
        val url=args.getString("labServer");val token=args.getString("labToken");val group=args.getString("incidentGroup")
        assumeTrue(url!=null&&token!=null&&group!=null)
        val app=ApplicationProvider.getApplicationContext<PlanDApplication>();val g=group!!;val id=UUID.randomUUID().toString()
        app.store.merge(Resident(g,app.installation.localId,1,1,"Android訓練A",35.0,139.0,"2階","北",Safety.UNREPORTED))
        app.store.mergeIncident(IncidentEvent(g,id,id,app.installation.localId,"Android訓練A",1,1,IncidentKind.START,title="相互運用試験",area="地区"))
        app.store.appendIncident(IncidentEvent(g,UUID.randomUUID().toString(),id,app.installation.localId,"Android訓練A",1,2,IncidentKind.ANSWER,safety=Safety.SOS))
        if(Build.VERSION.SDK_INT>=37)instrumentation.uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.ACCESS_LOCAL_NETWORK").close()
        try{
            instrumentation.runOnMainSync{app.lab.setVisible(true);app.lab.start(url!!,token!!,g)}
            val result=withTimeoutOrNull(30000){while(true){val v=incidentView(app.store.incidents(g),id)!!;if(v.care.isNotEmpty())return@withTimeoutOrNull v;delay(100)};error("unreachable")}
            assertNotNull(app.lab.state.value.toString(),result)
            result!!
            assertEquals(Safety.SOS,result.answers[app.installation.localId]!!.safety)
            assertEquals(CareAction.CHECKING,result.care.single().action)
            assertEquals("JS保存後に返信",result.care.single().note)
            assertTrue(app.lab.state.value.receipts>=1)
        }finally{instrumentation.runOnMainSync{app.lab.stop();app.lab.setVisible(false)}}
    }
}
