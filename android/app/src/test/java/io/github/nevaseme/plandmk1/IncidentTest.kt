package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class IncidentTest {
    private val author = UUID.randomUUID().toString()
    private fun start(): IncidentEvent { val id=UUID.randomUUID().toString(); return IncidentEvent("test",id,id,author,"A",1,100,IncidentKind.START,title="訓練",area="地域") }
    @Test fun newIncidentHasNoPreviousAnswerOrCare() {
        val a=start(); val b=start()
        val answer=a.copy(eventId=UUID.randomUUID().toString(),kind=IncidentKind.ANSWER,sequence=2,title="",area="",safety=Safety.SOS)
        assertEquals(Safety.SOS, incidentView(listOf(a,b,answer),a.incidentId)!!.answers[author]!!.safety)
        assertTrue(incidentView(listOf(a,b,answer),b.incidentId)!!.answers.isEmpty())
    }
    @Test fun sequenceOverridesClockAndForeignEndCannotClose() {
        val a=start()
        val answer=a.copy(eventId=UUID.randomUUID().toString(),kind=IncidentKind.ANSWER,sequence=2,title="",area="",safety=Safety.SOS)
        val latest=answer.copy(eventId=UUID.randomUUID().toString(),sequence=3,createdAt=1,safety=Safety.SAFE)
        val end=answer.copy(eventId=UUID.randomUUID().toString(),kind=IncidentKind.END,authorId=UUID.randomUUID().toString(),safety=null)
        val view=incidentView(listOf(a,answer,latest,end),a.incidentId)!!
        assertFalse(view.closed); assertEquals(Safety.SAFE,view.answers[author]!!.safety)
        assertNull(incidentView(listOf(answer),a.incidentId))
    }
    @Test fun malformedStartAndAnswerRejected() {
        val a=start();a.validate()
        assertThrows(IllegalArgumentException::class.java){a.copy(area="").validate()}
        assertThrows(IllegalArgumentException::class.java){a.copy(kind=IncidentKind.ANSWER).validate()}
    }
}
