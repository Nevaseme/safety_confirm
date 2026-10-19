package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OperationFeedbackTest {
    @Test fun successfulGroupChangeClearsPriorResultsWithoutLosingCurrentProfileSuccess() = runTest {
        val operations = SaveOperations(MutableStateFlow(false))
        operations.begin("safety"); operations.complete("safety", "SOS", {}, {})
        operations.begin("profile"); operations.complete("profile", "登録情報", {}, { operations.clearOtherResults() })
        assertNull(operations.states.value["safety"])
        assertEquals(SavePhase.SAVED, operations.states.value.getValue("profile").phase)
    }
    @Test fun saveLocksConflictsAndKeepsDurableSuccessWhenPublishingFails() = runTest {
        val busy = MutableStateFlow(false)
        val operations = SaveOperations(busy) { 123L }
        assertTrue(operations.begin("care")); assertTrue(busy.value)
        assertFalse(operations.begin("safety"))
        operations.complete("care", "対応記録", persist = {}, publish = { error("切断") })
        assertFalse(busy.value)
        assertEquals(SavePhase.SAVED, operations.states.value.getValue("care").phase)
        assertTrue(operations.states.value.getValue("care").text.contains("保存済み"))
        assertEquals(123L, operations.states.value.getValue("care").savedAt)
    }
    @Test fun saveFailureDoesNotPublishAndSuccessfulRetryClearsItsErrorOnly() = runTest {
        val operations = SaveOperations(MutableStateFlow(false)); var published = false
        operations.begin("profile")
        operations.complete("profile", "登録情報", { error("容量不足") }, { published = true })
        assertFalse(published); assertEquals(SavePhase.ERROR, operations.states.value.getValue("profile").phase)
        operations.begin("care"); operations.complete("care", "記録", { error("対象なし") }, {})
        operations.begin("profile"); operations.complete("profile", "登録情報", {}, {})
        assertEquals(SavePhase.SAVED, operations.states.value.getValue("profile").phase)
        assertEquals(SavePhase.ERROR, operations.states.value.getValue("care").phase)
    }
    @Test fun connectionRequiresJoinAndDeadlineDoesNotOutliveItsSession() {
        val progress = ConnectionProgress()
        val epoch = progress.start()
        assertEquals(ConnectionPhase.CONNECTING, progress.state.phase)
        assertNull(progress.start())
        assertTrue(progress.joined(epoch!!)); assertEquals(ConnectionPhase.WAITING, progress.state.phase)
        assertFalse(progress.timeout(epoch))
        progress.peers(epoch, 1); assertEquals(ConnectionPhase.CONNECTED, progress.state.phase)
        progress.stop("画面を閉じたため停止")
        assertFalse(progress.joined(epoch)); assertFalse(progress.timeout(epoch))
        val next = progress.start()!!
        assertTrue(progress.timeout(next)); assertEquals(ConnectionPhase.STOPPED, progress.state.phase)
        assertTrue(progress.state.error!!.contains("再試行"))
    }
}
