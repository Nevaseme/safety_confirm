package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.location.*
import io.github.nevaseme.plandmk1.ui.residentMapPerson
import io.github.nevaseme.plandmk1.ui.MapSafety
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class PositionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PlanDApplication
    private var previousGroup: String? = null
    private var sourceFactory: (() -> (suspend () -> PositionFix))? = null
    private val group = "gps-${UUID.randomUUID().toString().take(8)}"
    private val now = System.currentTimeMillis()
    private val fix = PositionFix(32.7904, 130.819, 7.0, now)

    @Before fun prepare() {
        previousGroup = app.installation.group.value; sourceFactory = app.positionSourceFactory
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { app.store.updateOwn(group, app.installation.localId) { Resident(group, app.installation.localId, 1, 0, "測位試験", 35.0, 139.0, "2階", "北の寝室") } }
        compose.runOnIdle { app.installation.setGroup(group) }
        compose.waitUntil(10_000) { app.position.foreground }
    }
    @After fun restore() {
        compose.runOnIdle {
            app.position.stop()
            sourceFactory?.let { app.positionSourceFactory = it }
            previousGroup?.let { app.installation.setGroup(it) }
        }
    }
    private fun awaitPosition(): PhonePosition {
        var found: PhonePosition? = null
        compose.waitUntil(40_000) { found = runBlocking { app.store.positions(group).singleOrNull() }; found != null }
        return found!!
    }
    private fun openPosition() {
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("位置記録").performScrollTo().performClick()
    }

    @Test fun menuServiceNotificationStorageAndStopPreserveLastPosition() {
        app.positionSourceFactory = { { fix } }
        openPosition()
        compose.onNodeWithText("位置記録を開始").performClick()
        val saved=awaitPosition()
        assertEquals(fix.latitude,saved.latitude,0.000001)
        assertEquals(fix.acquiredAt,saved.acquiredAt)
        compose.waitUntil(10_000) { app.position.state.value.message == "位置を保存しました" }
        val notification=app.getSystemService(NotificationManager::class.java).activeNotifications.first { it.id == 51 }.notification
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("位置記録を停止").assertExists()
        notification.actions.single().actionIntent.send()
        compose.waitUntil(10_000) { !app.position.state.value.active }
        assertEquals(saved,runBlocking { app.store.positions(group).single() })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("位置記録を開始").assertExists()
        assertTrue(app.position.state.value.message.contains("停止"))
    }

    @Test fun androidGpsProviderReachesRoomAndMapProjection() {
        compose.runOnIdle { app.position.start() }
        val saved=awaitPosition()
        assertEquals(32.7904,saved.latitude,0.002)
        assertEquals(130.819,saved.longitude,0.002)
        assertTrue(saved.acquiredAt > now - 60_000)
        val own=runBlocking { app.store.get(group,app.installation.localId)!! }
        val person=residentMapPerson(own.copy(safety=Safety.SOS),emptyList(),emptyList(),saved)
        assertEquals(saved.latitude,person.point!!.latitude,0.000001)
        assertEquals(MapSafety.SOS,person.state)
        assertEquals(35.0,person.home!!.point.latitude,0.000001)
        assertTrue(person.hasPosition(now + 10 * 86_400_000L))
    }

    @Test fun storageRejectsOlderMeasurementsAndGroupChangeStopsRecording() {
        runBlocking {
            assertTrue(app.store.recordOwnPosition(group,app.installation.localId,fix))
            assertFalse(app.store.recordOwnPosition(group,app.installation.localId,fix.copy(acquiredAt=now-1,latitude=0.0)))
            assertEquals(fix.latitude,app.store.positions(group).single().latitude,0.000001)
        }
        app.positionSourceFactory = { { delay(10_000);fix.copy(acquiredAt=now+1) } }
        compose.runOnIdle { app.position.start() }
        compose.waitUntil(10_000) { app.position.state.value.message == "位置を取得しています" }
        compose.runOnIdle { app.installation.setGroup("another-group") }
        compose.waitUntil(10_000) { !app.position.state.value.active }
        assertTrue(runBlocking { app.store.positions("another-group").isEmpty() })
        assertEquals(now,runBlocking { app.store.positions(group).single().acquiredAt })
    }

    @Test fun rapidStartStopStartAndRejectedFixKeepTheCurrentSession() {
        app.positionSourceFactory = { { delay(500); fix } }
        compose.runOnIdle { app.position.start(); app.position.stop(); app.position.start() }
        awaitPosition()
        compose.waitUntil(10_000) { app.position.state.value.message == "位置を保存しました" }
        compose.runOnIdle { app.position.refresh() }
        compose.waitUntil(10_000) { app.position.state.value.message == "最後の保存位置を保持しています" }
        assertTrue(app.position.state.value.active)
        assertEquals(1L,runBlocking { app.store.positions(group).single().revision })
        compose.runOnIdle { app.position.stop() }
        compose.waitUntil(10_000) { app.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 51 } }
    }
}
