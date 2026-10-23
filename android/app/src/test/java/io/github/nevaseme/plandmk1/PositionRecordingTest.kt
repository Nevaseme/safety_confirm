package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PositionRecordingTest {
    private val fix = PositionFix(32.79, 130.81, 8.0, 1_800_000_000_000)

    @Test fun requestsAtStartAndAgreedIntervalsAndRefreshAndStops() = runTest {
        var calls = 0; val saved = mutableListOf<PositionFix>()
        val recording = PositionRecording(backgroundScope, { calls++; fix }, { saved.add(it) })
        recording.start(false); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(299_999); runCurrent(); assertEquals(1, calls)
        advanceTimeBy(1); runCurrent(); assertEquals(2, calls)
        recording.disaster(true); runCurrent(); assertEquals(3, calls)
        advanceTimeBy(60_000); runCurrent(); assertEquals(4, calls)
        recording.refresh(); runCurrent(); assertEquals(5, calls)
        recording.stop(); advanceTimeBy(600_000); runCurrent(); assertEquals(5, calls)
        assertFalse(recording.state.value.active)
        assertEquals(5, saved.size)
    }

    @Test fun failedAcquisitionAndStorageDoNotErasePreviousFixAndCanRetry() = runTest {
        var failSource = false; var failSave = false; var stored: PositionFix? = null
        val recording = PositionRecording(backgroundScope,
            { if(failSource) error("位置情報をONにしてください"); fix },
            { if(failSave) error("保存できません"); stored = it; true })
        recording.start(false); runCurrent(); assertEquals(fix, stored)
        failSource = true; recording.refresh(); runCurrent()
        assertEquals(fix, stored); assertTrue(recording.state.value.message.contains("ON"))
        failSource = false; failSave = true; recording.refresh(); runCurrent()
        assertTrue(recording.state.value.message.contains("保存できません"))
        failSave = false; recording.refresh(); runCurrent()
        assertEquals("位置を保存しました", recording.state.value.message)
        recording.stop()
    }

    @Test fun stopCancelsPendingFixAndRepeatedStartDoesNotDuplicateRequests() = runTest {
        var calls=0; var saves=0
        val recording=PositionRecording(backgroundScope,{calls++;delay(10_000);fix},{saves++;true})
        recording.start(false);recording.start(false);runCurrent()
        assertEquals(1,calls)
        recording.stop();advanceTimeBy(30_000);runCurrent();assertEquals(0,saves)
        recording.start(true);runCurrent();advanceTimeBy(10_000);runCurrent();assertEquals(1,saves)
        recording.stop()
    }

    @Test fun rejectedOldFixIsNotReportedAsSaved() = runTest {
        val recording=PositionRecording(backgroundScope,{fix},{false})
        recording.start(false);runCurrent()
        assertEquals("最後の保存位置を保持しています",recording.state.value.message)
        recording.stop()
    }
}
