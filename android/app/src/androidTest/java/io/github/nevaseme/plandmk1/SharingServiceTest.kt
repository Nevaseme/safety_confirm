package io.github.nevaseme.plandmk1

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.nearby.*
import io.github.nevaseme.plandmk1.sharing.SharingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.*
import java.util.UUID

/** Runs real Android service, ProcessLifecycleOwner, Room and notification PendingIntents.
 * Only radio transport is simulated. Never run this fixture on a user's physical phone. */
class SharingServiceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PlanDApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val ports = mutableListOf<FakePort>()
    private lateinit var originalFactory: (CoroutineScope) -> NearbyPort
    private lateinit var originalGroup: String
    private var originalNotificationPrompted = false
    private val group = "test-${UUID.randomUUID().toString().take(8)}"
    private val notificationRetryRun get() = InstrumentationRegistry.getArguments().getString("notificationRetry") == "true"
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(message: String, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < end) {
            var ok = false; main { ok = check() }; if (ok) return
            SystemClock.sleep(30)
        }
        fail("$message; state=${app.sharing.state.value}")
    }
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { p ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(p).readBytes()
        }
    }
    @Before fun setup() {
        assertTrue("Emulator only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        if (notificationRetryRun) Assume.assumeTrue("Notification permission requires API 33+", Build.VERSION.SDK_INT >= 33)
        (NearbyTransport.permissions().toList() + if (Build.VERSION.SDK_INT >= 33 && !notificationRetryRun) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()).forEach {
            shell("pm grant ${app.packageName} $it")
        }
        shell("svc wifi enable"); shell("svc bluetooth enable")
        originalGroup = app.installation.group.value
        originalFactory = app.sharingPortFactory
        originalNotificationPrompted = app.installation.notificationPrompted
        main {
            app.sharing.stop(); app.installation.setGroup(group)
            if (notificationRetryRun) app.installation.notificationPrompted = false
            app.sharingPortFactory = { FakePort().also { ports.add(it) } }
        }
        runBlocking { app.store.updateOwn(group, app.installation.localId) {
            Resident(group, app.installation.localId, 1, 1, "模擬住民", 35.0, 139.0, "2階", "北")
        } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("App foreground") { app.sharing.foreground }
    }
    @After fun cleanup() {
        if (!::originalFactory.isInitialized) return
        main {
            app.sharing.stop(); app.installation.setGroup(originalGroup); app.sharingPortFactory = originalFactory
            app.installation.notificationPrompted = originalNotificationPrompted
        }
        if (::scenario.isInitialized) scenario.close()
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        shell("svc wifi enable")
    }
    private fun start(): FakePort {
        val count = ports.size
        main { repeat(5) { app.sharing.start(group, "模擬住民") } }
        waitFor("Service running") { ports.size == count + 1 && app.sharing.state.value.active && !app.sharing.state.value.starting }
        return ports.last()
    }
    private fun connect(p: FakePort, vararg ids: String) = main { ids.forEach(p::join) }
    private fun background() { scenario.moveToState(Lifecycle.State.CREATED); waitFor("App background") { !app.sharing.foreground } }
    private fun foreground() { scenario.moveToState(Lifecycle.State.RESUMED); waitFor("App foreground") { app.sharing.foreground } }
    private fun notifications() = app.getSystemService(NotificationManager::class.java).activeNotifications
    private fun record() = Resident(group, UUID.randomUUID().toString(), 1, 1, "中継テストA", 35.0, 139.0, "1", "南", Safety.SOS)
    private fun bytes(r: Resident) = Json.encodeToString(WireMessage(groupId = group, type = "RECORD", record = r)).encodeToByteArray()

    @Test fun backgroundReceivePersistsAcksRelaysAndSurvivesActivityRecreation() {
        val p = start(); connect(p, "A", "C")
        scenario.recreate()
        assertEquals(1, ports.size)
        shell("input keyevent KEYCODE_SLEEP")
        waitFor("Screen off backgrounds app") { !app.sharing.foreground }
        assertFalse(p.searching); assertEquals(setOf("A", "C"), app.sharing.state.value.connected)
        val r = record(); main { p.onBytes("A", bytes(r)) }
        waitFor("ACK and relay") { p.packets.any { it.first == "A" && it.second.type == "ACK" } && p.packets.any { it.first == "C" && it.second.entries.any { e -> e.originId == r.originId } } }
        assertEquals(r, runBlocking { app.store.get(group, r.originId) })
        val incidentId=UUID.randomUUID().toString()
        val incident=IncidentEvent(group,incidentId,incidentId,r.originId,r.name,1,1,IncidentKind.START,title="背景受信訓練",area="試験地区")
        val answer=IncidentEvent(group,UUID.randomUUID().toString(),incidentId,r.originId,r.name,2,2,IncidentKind.ANSWER,safety=Safety.SOS)
        fun deliver(e: IncidentEvent)=main {p.onBytes("A",Json.encodeToString(WireMessage(version=3,groupId=group,type="INCIDENT_RECORD",incident=e)).encodeToByteArray())}
        deliver(incident);deliver(answer)
        waitFor("Incident durable ACK and relay") { p.packets.any {it.first=="A"&&it.second.type=="INCIDENT_ACK"&&it.second.entries.any { e->e.originId==answer.eventId }}&&p.packets.any {it.first=="C"&&it.second.type=="INCIDENT_SUMMARY"&&it.second.entries.any {e->e.originId==answer.eventId}} }
        assertEquals(Safety.SOS,runBlocking{incidentView(app.store.incidents(group),incidentId)!!.answers[r.originId]!!.safety})
        val houseId=UUID.randomUUID().toString()
        val house=PlaceEvent(group,houseId,r.originId,r.name,1,1,PlaceKind.BUILDING,buildingId=houseId,label="背景住宅",latitude=35.0,longitude=139.0)
        fun deliverPlace(e:PlaceEvent)=main {p.onBytes("A",Json.encodeToString(WireMessage(version=4,groupId=group,type="PLACE_RECORD",place=e)).encodeToByteArray())}
        deliverPlace(house)
        waitFor("Building durable ACK and relay"){p.packets.any {it.first=="A"&&it.second.type=="PLACE_ACK"&&it.second.entries.any {e->e.originId==houseId}}&&p.packets.any {it.first=="C"&&it.second.type=="PLACE_SUMMARY"&&it.second.entries.any {e->e.originId==houseId}}}
        assertEquals(house,runBlocking{app.store.getPlace(group,houseId)})
        assertTrue(notifications().any { it.id == SharingService.NOTIFICATION_ID })
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        foreground(); assertTrue(p.searching); assertEquals(1, p.starts)
        // Closing Activity destroys its ViewModel, unlike a configuration recreation.
        scenario.close(); waitFor("Activity closed") { !app.sharing.foreground }
        val next = r.copy(revision = 2); main { p.onBytes("A", bytes(next)) }
        val end = SystemClock.elapsedRealtime() + 5_000
        while (runBlocking { app.store.get(group, r.originId)?.revision } != 2L && SystemClock.elapsedRealtime() < end) SystemClock.sleep(30)
        assertEquals(2L, runBlocking { app.store.get(group, r.originId)?.revision })
        val safe=answer.copy(eventId=UUID.randomUUID().toString(),sequence=3,safety=Safety.SAFE)
        deliver(safe)
        waitFor("Incident continues after ViewModel destruction") {p.packets.any {it.second.type=="INCIDENT_ACK"&&it.second.entries.any {e->e.originId==safe.eventId}}}
        assertEquals(Safety.SAFE,runBlocking{incidentView(app.store.incidents(group),incidentId)!!.answers[r.originId]!!.safety})
        val houseCare=PlaceEvent(group,UUID.randomUUID().toString(),r.originId,r.name,2,2,PlaceKind.CARE,buildingId=houseId,incidentId=incidentId,action=BuildingAction.NEEDS_HELP,scope="1階",unresolved="2階")
        deliverPlace(houseCare)
        waitFor("Building care after ViewModel destroyed"){p.packets.any {it.second.type=="PLACE_ACK"&&it.second.entries.any {e->e.originId==houseCare.eventId}}}
        assertEquals(houseCare,runBlocking{app.store.getPlace(group,houseCare.eventId)})
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Return after ViewModel destruction") { p.searching }
        val notice = notifications().single { it.id == SharingService.NOTIFICATION_ID }.notification
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("自分の情報").performClick()
        notice.contentIntent.send()
        compose.onNodeWithText("自分の安否を知らせる").assertIsDisplayed()
        notice.actions.single().actionIntent.send()
        waitFor("Notification stop") { !app.sharing.state.value.active && notifications().none { it.id == SharingService.NOTIFICATION_ID } }
        assertEquals(1, p.stops)
    }

    @Test fun zeroPeersAndLastBackgroundDisconnectStopUntilManualRestart() {
        start(); background(); waitFor("Zero peers stopped") { !app.sharing.state.value.active }
        foreground(); assertFalse(app.sharing.state.value.active)
        val p = start(); connect(p, "A", "C"); background()
        main { p.leave("A") }; assertTrue(app.sharing.state.value.active)
        main { p.leave("C") }; waitFor("Last peer stopped") { !app.sharing.state.value.active }
        foreground(); assertEquals(2, ports.size)
    }

    @Test fun immediateStopAndGroupChangeRejectLateEvents() {
        main { app.sharing.start(group, "模擬住民"); app.sharing.stop() }
        SystemClock.sleep(500); assertFalse(app.sharing.state.value.active)
        val p = start(); connect(p, "A")
        val late = p.onBytes; val r = record()
        main { app.sharing.stop(); app.installation.setGroup("other-test"); late("A", bytes(r)) }
        SystemClock.sleep(300)
        assertNull(runBlocking { app.store.get(group, r.originId) }); assertEquals(1, p.stops)
        main { app.installation.setGroup(group) }; start()
    }

    @Test fun notificationPermissionCanBeRequestedAgainAfterDenial() {
        // Start with POST_NOTIFICATIONS revoked and permission flags cleared externally.
        // Denial and permission recovery below use the actual system dialog.
        Assume.assumeTrue(notificationRetryRun && Build.VERSION.SDK_INT >= 33)
        assertFalse(NotificationManagerCompat.from(app).areNotificationsEnabled())
        compose.onNodeWithContentDescription("メニューを開く").performClick()
        compose.onNodeWithText("共有・接続").performClick()
        compose.onNodeWithText("共有開始").performScrollTo().performClick()
        clickPermissionButton("permission_deny_button")
        compose.waitForIdle()
        assertFalse(NotificationManagerCompat.from(app).areNotificationsEnabled())
        assertTrue("Sharing waits for notification permission", ports.isEmpty())

        scenario.recreate()
        compose.onNodeWithText("共有開始").performScrollTo().performClick()
        clickPermissionButton("permission_allow_button")
        waitFor("Permission granted and sharing started") {
            NotificationManagerCompat.from(app).areNotificationsEnabled() && app.sharing.state.value.active
        }
        assertEquals(1, ports.size)
        waitFor("Sharing notification displayed") { notifications().any { it.id == SharingService.NOTIFICATION_ID } }
    }

    private fun clickPermissionButton(id: String) {
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            val end = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < end) {
                val root = automation.rootInActiveWindow
                val permissionPackage = root?.packageName?.toString()
                if (permissionPackage?.endsWith("permissioncontroller") == true) {
                    val button = root?.findAccessibilityNodeInfosByViewId("$permissionPackage:id/$id")?.firstOrNull()
                    if (button != null && button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
                }
                SystemClock.sleep(50)
            }
            fail("System notification permission dialog button not found: $id")
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test fun radioOffStopsSharing() {
        val p = start(); connect(p, "A")
        assertTrue(app.sharing.state.value.active)
        shell("svc wifi disable")
        waitFor("Radio off stops") { !app.sharing.state.value.active }
        assertEquals(1, p.stops)
    }

    @Test fun profileSaveBlocksNewSharingUntilGroupCommit() {
        lateinit var model: PlanViewModel
        main { model = androidx.lifecycle.ViewModelProvider(scenarioActivity())[PlanViewModel::class.java] }
        waitFor("Profile loaded") { model.residents.value.isNotEmpty() }
        main { model.saving.value = true; model.startSharing() }
        assertFalse("Sharing must not start during profile save", app.sharing.state.value.active)
        main { model.saving.value = false }
    }
    private fun scenarioActivity(): MainActivity {
        var activity: MainActivity? = null
        scenario.onActivity { activity = it }
        return checkNotNull(activity)
    }

    private class FakePort : NearbyPort {
        override val state = MutableStateFlow(NearbyState())
        override var onConnected: suspend (String) -> Unit = {}
        override var onDisconnected: (String) -> Unit = {}
        override var onBytes: (String, ByteArray) -> Unit = { _, _ -> }
        var starts = 0; var stops = 0; var searching = true
        val packets = mutableListOf<Pair<String, WireMessage>>()
        override fun start(name: String) { starts++; state.value = NearbyState(active = true, discovering = true) }
        override fun stop() { stops++; state.value = NearbyState() }
        override fun setVisible(visible: Boolean) { searching = visible; state.value = state.value.copy(discovering = visible) }
        override fun connect(peer: Peer) = Unit
        override fun verify(id: String, accept: Boolean) = Unit
        override fun error(message: String) { state.value = state.value.copy(error = message) }
        override fun receipt() = Unit
        override suspend fun send(peer: String, bytes: ByteArray) { packets.add(peer to Json.decodeFromString<WireMessage>(bytes.decodeToString())) }
        fun join(id: String) {
            state.value = state.value.copy(connected = state.value.connected + id)
            runBlocking { onConnected(id) }
        }
        fun leave(id: String) { state.value = state.value.copy(connected = state.value.connected - id); onDisconnected(id) }
    }
}

