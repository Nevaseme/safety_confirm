package io.github.nevaseme.plandmk1

import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.ConnectionPhase
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LabFeedbackTest {
    @Test fun realWssJoinDistinguishesWaitingAndRepeatedStartDoesNotRestart() {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val server = args.getString("labServer")
        val token = args.getString("labToken")
        assumeTrue(server != null && token != null)
        val app = ApplicationProvider.getApplicationContext<PlanDApplication>()
        if (Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.executeShellCommand("pm grant ${app.packageName} android.permission.ACCESS_LOCAL_NETWORK").close()
        val controller = app.lab
        try {
            instrumentation.runOnMainSync {
                controller.setVisible(true)
                controller.start(server!!, token!!, "feedback-test")
                assertEquals(ConnectionPhase.CONNECTING, controller.state.value.connection.phase)
                assertNull(controller.state.value.phase)
                repeat(4) { controller.start(server, token, "feedback-test") }
            }
            val end = SystemClock.elapsedRealtime() + 20_000
            while (controller.state.value.connection.phase == ConnectionPhase.CONNECTING && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
            assertEquals(controller.state.value.toString(), ConnectionPhase.WAITING, controller.state.value.connection.phase)
            assertNotNull(controller.state.value.phase)
            instrumentation.runOnMainSync {
                controller.setVisible(false)
                assertFalse(controller.state.value.active)
                assertTrue(controller.state.value.connection.text.contains("画面を閉じた"))
            }
            SystemClock.sleep(300)
            assertFalse(controller.state.value.active)
        } finally { instrumentation.runOnMainSync { controller.stop(); controller.setVisible(false) } }
    }
}
