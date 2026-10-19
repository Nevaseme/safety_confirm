package io.github.nevaseme.plandmk1

import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.maps.OfflineMapRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Integration test: needs internet for the first download; retains the map for offline QA. */
class OfflineMapTest {
    @Test fun pausesPendingCreationAndPersistsTheCompletedRegion() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        lateinit var maps: OfflineMapRepository
        instrumentation.runOnMainSync { maps = OfflineMapRepository(context) }
        withTimeout(10_000) { maps.state.first { !it.busy } }
        if (maps.state.value.exists) {
            instrumentation.runOnMainSync { maps.delete() }
            withTimeout(10_000) { maps.state.first { !it.exists && !it.busy } }
        }
        if (!maps.state.value.exists) {
            instrumentation.runOnMainSync {
                maps.download(35.681236, 139.767125)
                maps.pause() // Simulates leaving before asynchronous region creation finishes.
            }
            withTimeout(10_000) { maps.state.first { it.exists && !it.busy } }
            instrumentation.runOnMainSync { /* Wait for the complete create callback. */ }
            assertFalse("Pending creation must respect pause", maps.state.value.downloading)
        }
        instrumentation.runOnMainSync { maps.resume() }
        val complete = withTimeout(240_000) { maps.state.first { it.complete || it.error != null } }
        assertNull(complete.error)
        assertTrue(complete.complete)
        assertTrue(complete.bytes > 0)
        lateinit var restored: OfflineMapRepository
        instrumentation.runOnMainSync { restored = OfflineMapRepository(context) }
        val saved = withTimeout(10_000) { restored.state.first { !it.busy && (it.complete || it.error != null) } }
        assertTrue(saved.complete)
        assertEquals(35.681236, saved.latitude!!, 0.0001)
    }
}
